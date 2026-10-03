package com.pixelcraft.blocklab;

import android.graphics.Bitmap;
import android.graphics.Color;

import java.util.ArrayDeque;
import java.util.Random;

/** In-memory pixel document plus a small, bounded undo/redo history. */
final class TextureDocument {
    static final int MAX_SIZE = 256;
    private static final int HISTORY_LIMIT = 24;

    String name;
    final int size;
    final int[] pixels;
    int revision = 0;

    private final ArrayDeque<int[]> undo = new ArrayDeque<>();
    private final ArrayDeque<int[]> redo = new ArrayDeque<>();

    TextureDocument(String name, int size, int[] pixels) {
        this.name = name == null || name.trim().isEmpty() ? "Без названия" : name.trim();
        this.size = Math.max(1, Math.min(MAX_SIZE, size));
        this.pixels = pixels == null || pixels.length != this.size * this.size
                ? new int[this.size * this.size] : pixels.clone();
    }

    static TextureDocument blank(String name, int size) {
        int safeSize = Math.max(1, Math.min(MAX_SIZE, size));
        return new TextureDocument(name, safeSize, new int[safeSize * safeSize]);
    }

    static TextureDocument grass(String name, int size) {
        int safeSize = Math.max(1, Math.min(MAX_SIZE, size));
        int[] colors = new int[safeSize * safeSize];
        int[] shades = new int[] {
                Color.rgb(48, 112, 54), Color.rgb(58, 132, 58), Color.rgb(71, 153, 62),
                Color.rgb(83, 170, 69), Color.rgb(99, 186, 76), Color.rgb(112, 196, 86),
                Color.rgb(39, 94, 48), Color.rgb(132, 202, 91)
        };
        Random random = new Random(0xB10C + safeSize * 17L);
        for (int y = 0; y < safeSize; y++) {
            for (int x = 0; x < safeSize; x++) {
                int shade = random.nextInt(shades.length);
                if (y < Math.max(1, safeSize / 8) && random.nextInt(4) == 0) shade = 4;
                if ((x + y) % 11 == 0 && random.nextBoolean()) shade = 6;
                colors[y * safeSize + x] = shades[shade];
            }
        }
        return new TextureDocument(name, safeSize, colors);
    }

    static TextureDocument amethyst(String name, int size) {
        int safeSize = Math.max(1, Math.min(MAX_SIZE, size));
        int[] colors = new int[safeSize * safeSize];
        Random random = new Random(0xA113 + safeSize * 31L);
        int[] palette = new int[] {
                Color.rgb(37, 24, 61), Color.rgb(48, 30, 78), Color.rgb(75, 44, 112),
                Color.rgb(113, 66, 153), Color.rgb(169, 104, 205), Color.rgb(213, 160, 231)
        };
        for (int y = 0; y < safeSize; y++) {
            for (int x = 0; x < safeSize; x++) {
                int value = random.nextInt(100);
                int shade = value < 57 ? 0 : value < 77 ? 1 : value < 91 ? 2 : value < 97 ? 3 : 4;
                if ((x + y * 2) % Math.max(4, safeSize / 2) == 0 && random.nextInt(3) == 0) shade = 5;
                colors[y * safeSize + x] = palette[shade];
            }
        }
        return new TextureDocument(name, safeSize, colors);
    }

    static TextureDocument stone(String name, int size) {
        int safeSize = Math.max(1, Math.min(MAX_SIZE, size));
        int[] colors = new int[safeSize * safeSize];
        Random random = new Random(0x570E + safeSize * 7L);
        int[] palette = new int[] {
                Color.rgb(86, 91, 91), Color.rgb(101, 106, 105), Color.rgb(117, 120, 117),
                Color.rgb(130, 133, 129), Color.rgb(73, 78, 79), Color.rgb(148, 149, 141)
        };
        for (int y = 0; y < safeSize; y++) {
            for (int x = 0; x < safeSize; x++) {
                int shade = random.nextInt(100) < 7 ? 4 : random.nextInt(palette.length);
                colors[y * safeSize + x] = palette[shade];
            }
        }
        return new TextureDocument(name, safeSize, colors);
    }

    static TextureDocument fromBitmap(Bitmap bitmap, String name) {
        if (bitmap == null) return grass(name, 16);
        int side = Math.min(bitmap.getWidth(), bitmap.getHeight());
        if (side < 1) return blank(name, 16);
        int left = (bitmap.getWidth() - side) / 2;
        int top = (bitmap.getHeight() - side) / 2;
        Bitmap square = Bitmap.createBitmap(bitmap, left, top, side, side);
        if (side > MAX_SIZE) {
            square = Bitmap.createScaledBitmap(square, MAX_SIZE, MAX_SIZE, false);
            side = MAX_SIZE;
        }
        int[] pixels = new int[side * side];
        square.getPixels(pixels, 0, side, 0, 0, side, side);
        if (square != bitmap && !square.isRecycled()) square.recycle();
        if (bitmap != square && !bitmap.isRecycled()) bitmap.recycle();
        return new TextureDocument(name, side, pixels);
    }

    TextureDocument copy(String newName) {
        return new TextureDocument(newName == null ? name : newName, size, pixels);
    }

    Bitmap toBitmap() {
        return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888);
    }

    int getPixel(int x, int y) {
        if (x < 0 || y < 0 || x >= size || y >= size) return Color.TRANSPARENT;
        return pixels[y * size + x];
    }

    void setPixel(int x, int y, int color) {
        if (x < 0 || y < 0 || x >= size || y >= size) return;
        int index = y * size + x;
        if (pixels[index] != color) {
            pixels[index] = color;
            revision++;
        }
    }

    void beginAction() {
        undo.addLast(pixels.clone());
        while (undo.size() > HISTORY_LIMIT) undo.removeFirst();
        redo.clear();
    }

    boolean canUndo() { return !undo.isEmpty(); }
    boolean canRedo() { return !redo.isEmpty(); }

    boolean undo() {
        if (undo.isEmpty()) return false;
        redo.addLast(pixels.clone());
        int[] previous = undo.removeLast();
        System.arraycopy(previous, 0, pixels, 0, pixels.length);
        revision++;
        return true;
    }

    boolean redo() {
        if (redo.isEmpty()) return false;
        undo.addLast(pixels.clone());
        while (undo.size() > HISTORY_LIMIT) undo.removeFirst();
        int[] next = redo.removeLast();
        System.arraycopy(next, 0, pixels, 0, pixels.length);
        revision++;
        return true;
    }

    void floodFill(int startX, int startY, int newColor) {
        if (startX < 0 || startY < 0 || startX >= size || startY >= size) return;
        int start = startY * size + startX;
        int oldColor = pixels[start];
        if (oldColor == newColor) return;

        int[] queue = new int[pixels.length];
        int head = 0;
        int tail = 0;
        queue[tail++] = start;
        pixels[start] = newColor;
        while (head < tail) {
            int index = queue[head++];
            int x = index % size;
            int y = index / size;
            if (x > 0 && pixels[index - 1] == oldColor) {
                pixels[index - 1] = newColor;
                queue[tail++] = index - 1;
            }
            if (x + 1 < size && pixels[index + 1] == oldColor) {
                pixels[index + 1] = newColor;
                queue[tail++] = index + 1;
            }
            if (y > 0 && pixels[index - size] == oldColor) {
                pixels[index - size] = newColor;
                queue[tail++] = index - size;
            }
            if (y + 1 < size && pixels[index + size] == oldColor) {
                pixels[index + size] = newColor;
                queue[tail++] = index + size;
            }
        }
        revision++;
    }
}

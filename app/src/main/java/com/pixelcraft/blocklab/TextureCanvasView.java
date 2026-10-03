package com.pixelcraft.blocklab;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

/** Zoom-to-fit pixel canvas. Every touch maps directly to one texture pixel. */
final class TextureCanvasView extends View {
    static final int TOOL_BRUSH = 0;
    static final int TOOL_FILL = 1;
    static final int TOOL_PICKER = 2;
    static final int TOOL_ERASER = 3;

    interface Listener {
        void onColorPicked(int color);
        void onTextureChanged();
        void onHistoryChanged();
    }

    private final Paint paint = new Paint();
    private TextureDocument document;
    private Bitmap bitmap;
    private int bitmapRevision = -1;
    private int tool = TOOL_BRUSH;
    private int color = Color.rgb(157, 229, 108);
    private boolean gridEnabled = true;
    private boolean drawing = false;
    private float boardLeft;
    private float boardTop;
    private float cellSize;
    private int lastX;
    private int lastY;
    private Listener listener;

    TextureCanvasView(Context context) {
        super(context);
        setLayerType(View.LAYER_TYPE_HARDWARE, null);
        setFocusable(true);
        setContentDescription("Полотно для рисования текстуры");
    }

    void setDocument(TextureDocument document) {
        this.document = document;
        recycleBitmap();
        invalidate();
    }

    void setListener(Listener listener) { this.listener = listener; }
    void setTool(int tool) { this.tool = tool; }
    void setColor(int color) { this.color = color; }
    void setGridEnabled(boolean enabled) { gridEnabled = enabled; invalidate(); }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawColor(Color.TRANSPARENT);
        if (document == null || getWidth() == 0 || getHeight() == 0) return;

        float padding = dp(18);
        float side = Math.min(getWidth() - padding * 2, getHeight() - padding * 2);
        side = Math.max(1, side);
        boardLeft = (getWidth() - side) / 2f;
        boardTop = (getHeight() - side) / 2f;
        cellSize = side / document.size;
        RectF board = new RectF(boardLeft, boardTop, boardLeft + side, boardTop + side);

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.rgb(31, 42, 35));
        canvas.drawRoundRect(new RectF(board.left - dp(5), board.top - dp(5), board.right + dp(5), board.bottom + dp(5)), dp(9), dp(9), paint);

        float tile = dp(10);
        paint.setColor(Color.rgb(47, 58, 50));
        for (float y = board.top; y < board.bottom; y += tile) {
            for (float x = board.left; x < board.right; x += tile) {
                int column = (int) ((x - board.left) / tile);
                int row = (int) ((y - board.top) / tile);
                if (((column + row) & 1) == 0) {
                    canvas.drawRect(x, y, Math.min(x + tile, board.right), Math.min(y + tile, board.bottom), paint);
                }
            }
        }

        if (bitmap == null || bitmapRevision != document.revision) {
            recycleBitmap();
            bitmap = document.toBitmap();
            bitmapRevision = document.revision;
        }
        paint.setFilterBitmap(false);
        paint.setAntiAlias(false);
        canvas.drawBitmap(bitmap, null, board, paint);

        if (gridEnabled && document.size <= 128) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(Math.max(0.65f, dp(0.65f)));
            int step = document.size > 64 ? 4 : 1;
            for (int i = step; i < document.size; i += step) {
                float atX = boardLeft + i * cellSize;
                float atY = boardTop + i * cellSize;
                paint.setColor(i % (step * 4) == 0 ? 0x4D101612 : 0x24101612);
                canvas.drawLine(atX, boardTop, atX, board.bottom, paint);
                canvas.drawLine(boardLeft, atY, board.right, atY, paint);
            }
        }

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1));
        paint.setColor(0xFF52665A);
        canvas.drawRoundRect(board, dp(2), dp(2), paint);
        paint.setStyle(Paint.Style.FILL);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (document == null) return false;
        int x = pixelX(event.getX());
        int y = pixelY(event.getY());
        boolean inside = x >= 0 && y >= 0 && x < document.size && y < document.size;

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (!inside) return false;
                performClick();
                if (tool == TOOL_PICKER) {
                    if (listener != null) listener.onColorPicked(document.getPixel(x, y));
                    return true;
                }
                document.beginAction();
                if (tool == TOOL_FILL) {
                    document.floodFill(x, y, tool == TOOL_ERASER ? Color.TRANSPARENT : color);
                } else {
                    drawing = true;
                    lastX = x;
                    lastY = y;
                    paintPixel(x, y);
                }
                invalidate();
                return true;

            case MotionEvent.ACTION_MOVE:
                if (!drawing) return false;
                int count = event.getHistorySize();
                for (int i = 0; i < count; i++) {
                    drawSegment(pixelX(event.getHistoricalX(i)), pixelY(event.getHistoricalY(i)));
                }
                drawSegment(x, y);
                invalidate();
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (drawing || tool == TOOL_FILL) {
                    drawing = false;
                    if (listener != null) {
                        listener.onTextureChanged();
                        listener.onHistoryChanged();
                    }
                    invalidate();
                    return true;
                }
                return false;
            default:
                return true;
        }
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    private int pixelX(float x) {
        if (cellSize <= 0) return -1;
        return (int) Math.floor((x - boardLeft) / cellSize);
    }

    private int pixelY(float y) {
        if (cellSize <= 0) return -1;
        return (int) Math.floor((y - boardTop) / cellSize);
    }

    private void drawSegment(int x, int y) {
        int dx = Math.abs(x - lastX);
        int dy = Math.abs(y - lastY);
        int steps = Math.max(dx, dy);
        if (steps == 0) {
            paintPixel(x, y);
        } else {
            for (int i = 1; i <= steps; i++) {
                int px = lastX + Math.round((x - lastX) * (i / (float) steps));
                int py = lastY + Math.round((y - lastY) * (i / (float) steps));
                paintPixel(px, py);
            }
        }
        lastX = x;
        lastY = y;
    }

    private void paintPixel(int x, int y) {
        if (x < 0 || y < 0 || x >= document.size || y >= document.size) return;
        document.setPixel(x, y, tool == TOOL_ERASER ? Color.TRANSPARENT : color);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private void recycleBitmap() {
        if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
        bitmap = null;
        bitmapRevision = -1;
    }
}

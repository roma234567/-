package com.pixelcraft.blocklab;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/** Compact, nearest-neighbour texture preview used on project cards. */
final class TexturePreviewView extends View {
    private final Paint paint = new Paint();
    private TextureDocument document;
    private Bitmap bitmap;
    private int revision = -1;

    TexturePreviewView(Context context) {
        super(context);
        setLayerType(View.LAYER_TYPE_HARDWARE, null);
        setContentDescription("Предпросмотр пиксельной текстуры");
    }

    void setDocument(TextureDocument document) {
        this.document = document;
        if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
        bitmap = null;
        revision = -1;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float side = Math.min(getWidth(), getHeight());
        if (side <= 0) return;
        float inset = Math.max(4, side * 0.08f);
        RectF box = new RectF((getWidth() - side) / 2f + inset,
                (getHeight() - side) / 2f + inset,
                (getWidth() + side) / 2f - inset,
                (getHeight() + side) / 2f - inset);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.rgb(38, 50, 42));
        canvas.drawRoundRect(box, side * 0.08f, side * 0.08f, paint);

        float tile = Math.max(5, side * 0.105f);
        paint.setColor(Color.rgb(53, 65, 57));
        int row = 0;
        for (float y = box.top; y < box.bottom; y += tile, row++) {
            int column = 0;
            for (float x = box.left; x < box.right; x += tile, column++) {
                if (((row + column) & 1) == 0) {
                    canvas.drawRect(x, y, Math.min(x + tile, box.right), Math.min(y + tile, box.bottom), paint);
                }
            }
        }

        if (document != null) {
            if (bitmap == null || revision != document.revision) {
                if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
                bitmap = document.toBitmap();
                revision = document.revision;
            }
            paint.setFilterBitmap(false);
            paint.setAntiAlias(false);
            canvas.drawBitmap(bitmap, null, box, paint);
        }
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1, side * 0.012f));
        paint.setColor(0xFF516357);
        canvas.drawRoundRect(box, side * 0.08f, side * 0.08f, paint);
        paint.setStyle(Paint.Style.FILL);
    }
}

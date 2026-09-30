/* Copyright (C) 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.lineageos.mondrian.display;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.View;

final class ResolutionPreviewView extends View {
    private static final int PIXEL_SAMPLE_WIDTH = 44;

    private final Bitmap mClear;
    private final Bitmap mPixelated;
    private final Paint mBitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mOverlayPaint = new Paint();
    private final Paint mGridPaint = new Paint();
    private final Path mClip = new Path();
    private boolean mUsePixelated;

    ResolutionPreviewView(Context context) {
        super(context);
        mClear = BitmapFactory.decodeResource(
                getResources(), R.drawable.resolution_landscape_preview);
        if (mClear != null) {
            int sampleHeight = Math.max(1, Math.round(
                    PIXEL_SAMPLE_WIDTH * mClear.getHeight() / (float) mClear.getWidth()));
            mPixelated = Bitmap.createScaledBitmap(
                    mClear, PIXEL_SAMPLE_WIDTH, sampleHeight, false);
        } else {
            mPixelated = null;
        }
        mGridPaint.setStyle(Paint.Style.STROKE);
        mGridPaint.setStrokeWidth(Math.max(1f, dp(0.35f)));
        mGridPaint.setColor(0x30000000);
    }

    void setPixelated(boolean pixelated) {
        mUsePixelated = pixelated;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (mClear == null || getWidth() <= 0 || getHeight() <= 0) return;

        RectF dst = new RectF(0, 0, getWidth(), getHeight());
        float radius = dp(14);
        mClip.reset();
        mClip.addRoundRect(dst, radius, radius, Path.Direction.CW);

        int save = canvas.save();
        canvas.clipPath(mClip);

        Bitmap bitmap = mUsePixelated && mPixelated != null ? mPixelated : mClear;
        mBitmapPaint.setFilterBitmap(!mUsePixelated);
        mBitmapPaint.setAlpha(255);

        Rect src = centerCrop(bitmap, getWidth(), getHeight());
        canvas.drawBitmap(bitmap, src, dst, mBitmapPaint);

        if (mUsePixelated) {
            mOverlayPaint.setColor(0x16000000);
            canvas.drawRect(dst, mOverlayPaint);
            drawPixelGrid(canvas, src, dst);
        }
        canvas.restoreToCount(save);
    }

    private void drawPixelGrid(Canvas canvas, Rect src, RectF dst) {
        if (src.width() <= 1 || src.height() <= 1) return;
        float cellW = dst.width() / src.width();
        float cellH = dst.height() / src.height();
        for (int i = 1; i < src.width(); i++) {
            float x = dst.left + i * cellW;
            canvas.drawLine(x, dst.top, x, dst.bottom, mGridPaint);
        }
        for (int i = 1; i < src.height(); i++) {
            float y = dst.top + i * cellH;
            canvas.drawLine(dst.left, y, dst.right, y, mGridPaint);
        }
    }

    private static Rect centerCrop(Bitmap bitmap, int outWidth, int outHeight) {
        float srcRatio = bitmap.getWidth() / (float) bitmap.getHeight();
        float dstRatio = outWidth / (float) outHeight;
        if (srcRatio > dstRatio) {
            int width = Math.round(bitmap.getHeight() * dstRatio);
            int left = (bitmap.getWidth() - width) / 2;
            return new Rect(left, 0, left + width, bitmap.getHeight());
        } else {
            int height = Math.round(bitmap.getWidth() / dstRatio);
            int top = (bitmap.getHeight() - height) / 2;
            return new Rect(0, top, bitmap.getWidth(), top + height);
        }
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}

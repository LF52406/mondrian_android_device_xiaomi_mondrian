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
    private final Bitmap mClear;
    private final Bitmap mPixelated;
    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path mClip = new Path();
    private boolean mUsePixelated;

    ResolutionPreviewView(Context context) {
        super(context);
        mClear = BitmapFactory.decodeResource(
                getResources(), R.drawable.resolution_landscape_preview);
        mPixelated = mClear != null
                ? Bitmap.createScaledBitmap(mClear, 28, 28, false)
                : null;
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
        mPaint.setFilterBitmap(!mUsePixelated);

        Rect src = centerCrop(bitmap, getWidth(), getHeight());
        canvas.drawBitmap(bitmap, src, dst, mPaint);

        if (mUsePixelated) {
            mPaint.setColor(0x26000000);
            canvas.drawRect(dst, mPaint);
            mPaint.setColor(0xffffffff);
        }
        canvas.restoreToCount(save);
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

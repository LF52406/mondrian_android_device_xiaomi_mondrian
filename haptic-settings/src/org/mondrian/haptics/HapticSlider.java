/* Copyright (C) 2026 The Android Open Source Project; SPDX-License-Identifier: Apache-2.0 */
package org.mondrian.haptics;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.drawable.ColorDrawable;
import android.widget.SeekBar;

/** Keeps native SeekBar input, keyboard and accessibility behavior; draws a continuous track. */
final class HapticSlider extends SeekBar {
    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int mPrimary, mTrack, mOnPrimary, mThumb;
    private final float mDensity;
    private LinearGradient mFill;

    HapticSlider(Context context, int primary, int track, int onPrimary, int thumb) {
        super(context);
        mPrimary = primary;
        mTrack = track;
        mOnPrimary = onPrimary;
        mThumb = thumb;
        mDensity = getResources().getDisplayMetrics().density;
        setMax(100);
        setKeyProgressIncrement(1);
        setSplitTrack(false);
        setProgressDrawable(new ColorDrawable(Color.TRANSPARENT));
        setThumb(new ColorDrawable(Color.TRANSPARENT));
        setThumbOffset(0);
        setPadding(dp(18), 0, dp(18), 0);
        setMinHeight(dp(48));
        setMinimumHeight(dp(48));
        setContentDescription(context.getString(R.string.strength));
    }

    private int dp(float value) { return Math.round(value * mDensity); }

    @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        updateFill();
    }

    @Override public void onRtlPropertiesChanged(int layoutDirection) {
        super.onRtlPropertiesChanged(layoutDirection);
        updateFill();
    }

    private void updateFill() {
        if (getWidth() == 0) return;
        int highlight = Color.rgb((Color.red(mPrimary) * 9 + Color.red(mThumb)) / 10,
                (Color.green(mPrimary) * 9 + Color.green(mThumb)) / 10,
                (Color.blue(mPrimary) * 9 + Color.blue(mThumb)) / 10);
        boolean rtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
        mFill = new LinearGradient(rtl ? getWidth() : 0, 0, rtl ? 0 : getWidth(), 0,
                highlight, mPrimary, Shader.TileMode.CLAMP);
        invalidate();
    }

    @Override protected synchronized void onDraw(Canvas canvas) {
        float start = getPaddingLeft();
        float end = getWidth() - getPaddingRight();
        float y = getHeight() / 2f;
        boolean rtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
        float fraction = getProgress() / 100f;
        float thumbX = rtl ? end - fraction * (end - start) : start + fraction * (end - start);
        float radius = dp(17);
        mPaint.setShader(null);
        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setAlpha(255);
        mPaint.setColor(mTrack);
        canvas.drawRoundRect(start - radius, y - radius, end + radius, y + radius,
                radius, radius, mPaint);
        mPaint.setColor(mPrimary);
        mPaint.setShader(mFill);
        canvas.drawRoundRect(rtl ? thumbX - radius : start - radius, y - radius,
                rtl ? end + radius : thumbX + radius, y + radius, radius, radius, mPaint);
        mPaint.setShader(null);
        for (int i = 0; i < 8; i++) {
            float x = start + i * (end - start) / 7f;
            if (Math.abs(x - thumbX) < dp(19)) continue;
            boolean active = rtl ? x > thumbX : x < thumbX;
            mPaint.setColor(active ? mOnPrimary : mPrimary);
            mPaint.setAlpha(active ? 170 : 130);
            canvas.drawCircle(x, y, dp(3), mPaint);
        }
        mPaint.setAlpha(255);
        mPaint.setColor(mThumb);
        canvas.drawCircle(thumbX, y, dp(16), mPaint);
        if (isFocused()) {
            mPaint.setColor(mPrimary);
            mPaint.setStyle(Paint.Style.STROKE);
            mPaint.setStrokeWidth(dp(1.5f));
            canvas.drawCircle(thumbX, y, dp(20), mPaint);
        }
    }
}

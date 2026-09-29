/* Copyright (C) 2026 The Android Open Source Project; SPDX-License-Identifier: Apache-2.0 */
package org.mondrian.haptics;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.ColorDrawable;
import android.widget.SeekBar;

/** Keeps native SeekBar input, keyboard and accessibility behavior; draws a continuous track. */
final class HapticSlider extends SeekBar {
    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int mPrimary, mTrack, mOnPrimary, mThumb;
    private final float mDensity;

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
        setPadding(dp(24), 0, dp(24), 0);
        setMinHeight(dp(56));
        setContentDescription(context.getString(R.string.strength));
    }

    private int dp(float value) { return Math.round(value * mDensity); }

    @Override protected synchronized void onDraw(Canvas canvas) {
        float start = getPaddingLeft();
        float end = getWidth() - getPaddingRight();
        float y = getHeight() / 2f;
        boolean rtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
        float fraction = getProgress() / 100f;
        float thumbX = rtl ? end - fraction * (end - start) : start + fraction * (end - start);
        float radius = dp(22);
        mPaint.setColor(mTrack);
        canvas.drawRoundRect(start - radius, y - radius, end + radius, y + radius,
                radius, radius, mPaint);
        mPaint.setColor(mPrimary);
        canvas.drawRoundRect(rtl ? thumbX - radius : start - radius, y - radius,
                rtl ? end + radius : thumbX + radius, y + radius, radius, radius, mPaint);
        for (int i = 0; i <= 10; i++) {
            float x = start + i * (end - start) / 10f;
            if (Math.abs(x - thumbX) < dp(23)) continue;
            boolean active = rtl ? x > thumbX : x < thumbX;
            mPaint.setColor(active ? mOnPrimary : mPrimary);
            mPaint.setAlpha(active ? 170 : 130);
            canvas.drawCircle(x, y, dp(2), mPaint);
        }
        mPaint.setAlpha(255);
        mPaint.setColor(mThumb);
        canvas.drawCircle(thumbX, y, dp(19), mPaint);
    }
}

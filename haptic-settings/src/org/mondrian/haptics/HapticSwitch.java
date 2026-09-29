/* Copyright (C) 2026 The Android Open Source Project; SPDX-License-Identifier: Apache-2.0 */
package org.mondrian.haptics;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.widget.Switch;

/** Reference artwork with the platform's drag, animation, keyboard and accessibility behavior. */
final class HapticSwitch extends Switch {
    private final float mDensity;
    private final int mPrimary, mTrack, mOnPrimary, mSecondary, mCheck;

    HapticSwitch(Context context, int primary, int track, int onPrimary, int secondary, int check) {
        super(context);
        mDensity = getResources().getDisplayMetrics().density;
        mPrimary = primary;
        mTrack = track;
        mOnPrimary = onPrimary;
        mSecondary = secondary;
        mCheck = check;
        setShowText(false);
        setSplitTrack(false);
        setSwitchMinWidth(dp(60));
        setSwitchPadding(0);
        setPadding(0, 0, 0, 0);
        setMinimumWidth(0);
        setMinimumHeight(dp(48));
        setGravity(Gravity.CENTER_VERTICAL);
        setTrackTintList(null);
        setThumbTintList(null);
        setTrackDrawable(new Artwork(false));
        setThumbDrawable(new Artwork(true));
    }

    private int dp(float value) { return Math.round(value * mDensity); }

    private final class Artwork extends Drawable {
        private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final boolean mThumb;
        private int mAlpha = 255;

        Artwork(boolean thumb) { mThumb = thumb; }
        @Override public int getIntrinsicWidth() { return dp(mThumb ? 28 : 60); }
        @Override public int getIntrinsicHeight() { return dp(34); }
        @Override public boolean isStateful() { return true; }
        @Override protected boolean onStateChange(int[] state) { invalidateSelf(); return true; }
        @Override public boolean getPadding(Rect padding) {
            padding.set(mThumb ? 0 : dp(2), 0, mThumb ? 0 : dp(2), 0);
            return !mThumb;
        }
        @Override public void draw(Canvas canvas) {
            boolean checked = false, enabled = false;
            for (int state : getState()) {
                checked |= state == android.R.attr.state_checked;
                enabled |= state == android.R.attr.state_enabled;
            }
            Rect bounds = getBounds();
            int alpha = enabled ? mAlpha : Math.round(mAlpha * .38f);
            mPaint.setStyle(Paint.Style.FILL);
            mPaint.setColor(mThumb ? (checked ? mOnPrimary : mSecondary)
                    : (checked ? mPrimary : mTrack));
            mPaint.setAlpha(alpha);
            if (!mThumb) {
                float radius = bounds.height() / 2f;
                canvas.drawRoundRect(bounds.left, bounds.top, bounds.right, bounds.bottom,
                        radius, radius, mPaint);
                if (!checked) {
                    mPaint.setStyle(Paint.Style.STROKE);
                    mPaint.setStrokeWidth(dp(1));
                    mPaint.setColor(mSecondary);
                    mPaint.setAlpha(alpha / 2);
                    canvas.drawRoundRect(bounds.left + 1, bounds.top + 1,
                            bounds.right - 1, bounds.bottom - 1, radius, radius, mPaint);
                }
                return;
            }
            float x = bounds.exactCenterX(), y = bounds.exactCenterY();
            canvas.drawCircle(x, y, dp(14), mPaint);
            if (checked) {
                mPaint.setStyle(Paint.Style.STROKE);
                mPaint.setStrokeWidth(dp(2));
                mPaint.setStrokeCap(Paint.Cap.ROUND);
                mPaint.setColor(mCheck);
                mPaint.setAlpha(alpha);
                canvas.drawLine(x - dp(6), y, x - dp(2), y + dp(4), mPaint);
                canvas.drawLine(x - dp(2), y + dp(4), x + dp(6), y - dp(5), mPaint);
            }
        }
        @Override public void setAlpha(int alpha) { mAlpha = alpha; invalidateSelf(); }
        @Override public void setColorFilter(ColorFilter filter) {
            mPaint.setColorFilter(filter);
            invalidateSelf();
        }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}

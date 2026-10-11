/* Copyright (C) 2026 The Android Open Source Project; SPDX-License-Identifier: Apache-2.0 */
package org.mondrian.haptics;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

/** Resolution-independent artwork for the two reference screens. */
final class HapticWaveView extends View {
    enum Shape { HERO, PROFILE_HERO, SOFT, BALANCED, CRISP, CLICK, DOUBLE, DOUBLE_EVEN, STEPS }

    private static final float[] HERO = {
            .08f, .18f, .32f, .46f, .61f, .79f, 1f, .79f, .61f, .46f, .32f, .18f, .08f};
    private static final float[] PROFILE_HERO = {
            .07f, .07f, .18f, .27f, .42f, .62f, .37f, .12f, .31f, .68f, 1f,
            .68f, .31f, .12f, .37f, .62f, .42f, .27f, .18f, .07f, .07f};
    private static final float[] SOFT = {.12f, .22f, .52f, 1f, .52f, .22f, .12f};
    private static final float[] BALANCED = {.25f, .60f, 1f, .60f, .25f};
    private static final float[] CRISP = {.42f, .78f, 1f, .78f, .42f};
    private static final float[] CLICK = {1f};
    private static final float[] DOUBLE = {.65f, 1f};
    private static final float[] DOUBLE_EVEN = {1f, 1f};
    private static final float[] STEPS = {1f, 1f, 1f, 1f, 1f};

    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Shape mShape;
    private final float[] mHeights;
    private final float mWidth, mHeight, mBarWidth;
    private int mTint;

    HapticWaveView(Context context, Shape shape, int tint) {
        super(context);
        mShape = shape;
        mTint = tint;
        mHeights = switch (shape) {
            case HERO -> HERO;
            case PROFILE_HERO -> PROFILE_HERO;
            case SOFT -> SOFT;
            case BALANCED -> BALANCED;
            case CRISP -> CRISP;
            case CLICK -> CLICK;
            case DOUBLE -> DOUBLE;
            case DOUBLE_EVEN -> DOUBLE_EVEN;
            case STEPS -> STEPS;
        };
        float density = getResources().getDisplayMetrics().density;
        mWidth = density * switch (shape) {
            case HERO -> 208;
            case PROFILE_HERO -> 328;
            case SOFT -> 46;
            case BALANCED, CRISP -> 34;
            case CLICK -> 5;
            case DOUBLE, DOUBLE_EVEN -> 13;
            case STEPS -> 44;
        };
        mHeight = density * switch (shape) {
            case HERO -> 92;
            case PROFILE_HERO -> 64;
            case SOFT -> 18;
            case BALANCED -> 32;
            case CRISP -> 34;
            default -> 26;
        };
        mBarWidth = density * switch (shape) {
            case HERO -> 10;
            case PROFILE_HERO -> 7;
            case SOFT -> 3;
            case BALANCED, CRISP -> 3.5f;
            default -> 5;
        };
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    void setTint(int tint) {
        if (mTint != tint) {
            mTint = tint;
            invalidate();
        }
    }

    @Override protected void onDraw(Canvas canvas) {
        boolean hero = mShape == Shape.HERO || mShape == Shape.PROFILE_HERO;
        float width = Math.min(mWidth, getWidth() * (hero ? .94f : .76f));
        float height = Math.min(mHeight, getHeight() * (hero ? .90f : .76f));
        float spacing = mHeights.length == 1 ? 0 : (width - mBarWidth) / (mHeights.length - 1);
        for (int i = 0; i < mHeights.length; i++) {
            float level = mHeights[i];
            float barWidth = mBarWidth * (mShape == Shape.HERO ? .50f + .50f * level
                    : mShape == Shape.PROFILE_HERO ? .72f + .28f * level : 1f);
            float barHeight = Math.max(barWidth, height * level);
            float x = getWidth() / 2f + (i - (mHeights.length - 1) / 2f) * spacing;
            float y = getHeight() / 2f;
            mPaint.setColor(mTint);
            mPaint.setAlpha(hero ? Math.round(255 * (.28f + .72f * level)) : 255);
            canvas.drawRoundRect(x - barWidth / 2, y - barHeight / 2,
                    x + barWidth / 2, y + barHeight / 2, barWidth / 2, barWidth / 2, mPaint);
        }
    }
}

/* Copyright (C) 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.lineageos.mondrian.display;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

public final class PartialUpdateDiagramView extends View {
    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int mAccent;
    private final int mSecondary;
    private final int mPrimary;

    public PartialUpdateDiagramView(Context context) {
        this(context, null);
    }

    public PartialUpdateDiagramView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public PartialUpdateDiagramView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mAccent = resolveColor(android.R.attr.colorAccent, 0xff8ee8c4);
        mSecondary = resolveColor(android.R.attr.textColorSecondary, 0xff9aa0a6);
        mPrimary = resolveColor(android.R.attr.textColorPrimary, Color.WHITE);
        setClickable(false);
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        if (w <= 0 || h <= 0) return;

        float phoneW = w * 0.30f;
        float phoneH = h * 0.88f;
        float top = h * 0.04f;
        float leftX = w * 0.10f;
        float rightX = w * 0.60f;
        float radius = dp(18);

        drawPhone(canvas, new RectF(leftX, top, leftX + phoneW, top + phoneH), true, radius);
        drawPhone(canvas, new RectF(rightX, top, rightX + phoneW, top + phoneH), false, radius);

        float cy = top + phoneH * 0.52f;
        float x1 = leftX + phoneW + w * 0.055f;
        float x2 = rightX - w * 0.055f;
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(dp(2));
        mPaint.setStrokeCap(Paint.Cap.ROUND);
        mPaint.setColor(withAlpha(mPrimary, 180));
        canvas.drawLine(x1, cy, x2, cy, mPaint);
        canvas.drawLine(x2, cy, x2 - dp(8), cy - dp(7), mPaint);
        canvas.drawLine(x2, cy, x2 - dp(8), cy + dp(7), mPaint);
        mPaint.setStrokeCap(Paint.Cap.BUTT);
    }

    private void drawPhone(Canvas canvas, RectF phone, boolean full, float radius) {
        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setColor(withAlpha(mSecondary, 22));
        canvas.drawRoundRect(phone, radius, radius, mPaint);

        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(dp(2));
        mPaint.setColor(full ? mAccent : withAlpha(mSecondary, 190));
        canvas.drawRoundRect(phone, radius, radius, mPaint);

        float pad = phone.width() * 0.10f;
        RectF inner = new RectF(
                phone.left + pad,
                phone.top + pad,
                phone.right - pad,
                phone.bottom - pad);

        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setColor(full ? withAlpha(mAccent, 76) : withAlpha(mSecondary, 48));
        canvas.drawRoundRect(inner, dp(10), dp(10), mPaint);

        float dot = phone.width() * 0.065f;
        float headerY = inner.top + inner.height() * 0.12f;
        mPaint.setColor(full ? withAlpha(mAccent, 145) : withAlpha(mSecondary, 150));
        canvas.drawCircle(inner.left + dot, headerY, dot, mPaint);
        canvas.drawRoundRect(
                inner.left + dot * 2.7f,
                headerY - dot * 0.55f,
                inner.right - dot,
                headerY + dot * 0.55f,
                dot,
                dot,
                mPaint);

        RectF card = new RectF(
                inner.left,
                inner.top + inner.height() * 0.27f,
                inner.right,
                inner.top + inner.height() * 0.70f);
        mPaint.setColor(full ? withAlpha(mAccent, 88) : withAlpha(mSecondary, 65));
        canvas.drawRoundRect(card, dp(8), dp(8), mPaint);

        if (!full) {
            RectF changed = new RectF(
                    card.left,
                    card.top,
                    card.left + card.width() * 0.52f,
                    card.bottom);
            mPaint.setColor(withAlpha(mAccent, 128));
            canvas.drawRect(changed, mPaint);
            mPaint.setStyle(Paint.Style.STROKE);
            mPaint.setStrokeWidth(dp(1.5f));
            mPaint.setColor(mAccent);
            canvas.drawRect(changed, mPaint);
            mPaint.setStyle(Paint.Style.FILL);
        }

        mPaint.setColor(full ? withAlpha(mAccent, 170) : withAlpha(mSecondary, 165));
        float lineY = card.top + card.height() * 0.43f;
        for (int i = 0; i < 3; i++) {
            float width = card.width() * (i == 0 ? 0.72f : i == 1 ? 0.58f : 0.44f);
            canvas.drawRoundRect(
                    card.left + card.width() * 0.14f,
                    lineY + i * dp(8),
                    card.left + card.width() * 0.14f + width,
                    lineY + i * dp(8) + dp(4),
                    dp(2),
                    dp(2),
                    mPaint);
        }

        RectF footer = new RectF(
                inner.left,
                inner.bottom - inner.height() * 0.16f,
                inner.right,
                inner.bottom - inner.height() * 0.05f);
        mPaint.setColor(full ? withAlpha(mAccent, 115) : withAlpha(mSecondary, 115));
        canvas.drawRoundRect(footer, dp(8), dp(8), mPaint);
    }

    private int resolveColor(int attr, int fallback) {
        TypedValue value = new TypedValue();
        if (!getContext().getTheme().resolveAttribute(attr, value, true)) {
            return fallback;
        }
        if (value.resourceId != 0) {
            try {
                return getContext().getColor(value.resourceId);
            } catch (RuntimeException ignored) {
            }
        }
        if (value.type >= TypedValue.TYPE_FIRST_COLOR_INT
                && value.type <= TypedValue.TYPE_LAST_COLOR_INT) {
            return value.data;
        }
        return fallback;
    }

    private static int withAlpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}

/* Copyright (C) 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.lineageos.mondrian.display;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.TextView;

public final class ResolutionCardView extends LinearLayout {
    private final ResolutionPreviewView mPreview;
    private final RadioButton mRadio;
    private final TextView mTitle;
    private final TextView mPixels;
    private final TextView mSummary;

    private final int mAccent;
    private final int mSecondary;
    private final int mSurface;
    private boolean mChecked;
    private String mAccessibilityText = "";

    public ResolutionCardView(Context context) {
        this(context, null);
    }

    public ResolutionCardView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ResolutionCardView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setOrientation(VERTICAL);
        setGravity(Gravity.TOP);
        setClickable(true);
        setFocusable(true);
        setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        setPadding(dp(10), dp(10), dp(10), dp(12));
        setMinimumHeight(dp(224));

        mAccent = resolveColor(android.R.attr.colorAccent, 0xff8ee8c4);
        mSecondary = resolveColor(android.R.attr.textColorSecondary, 0xff9aa0a6);
        mSurface = resolveColor(android.R.attr.colorBackgroundFloating, 0xff202124);

        mPreview = new ResolutionPreviewView(context);
        addView(mPreview, new LayoutParams(
                LayoutParams.MATCH_PARENT, dp(140)));

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.TOP);
        LayoutParams rowParams = new LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        rowParams.topMargin = dp(10);
        addView(row, rowParams);

        mRadio = new RadioButton(context);
        mRadio.setClickable(false);
        mRadio.setFocusable(false);
        mRadio.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        mRadio.setButtonTintList(new ColorStateList(
                new int[][] {
                        new int[] { android.R.attr.state_checked },
                        new int[] {}
                },
                new int[] { mAccent, withAlpha(mSecondary, 210) }));
        row.addView(mRadio, new LayoutParams(dp(40), dp(44)));

        LinearLayout text = new LinearLayout(context);
        text.setOrientation(VERTICAL);
        text.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        row.addView(text, new LayoutParams(
                0, LayoutParams.WRAP_CONTENT, 1f));

        mTitle = makeText(20, true, resolveColor(android.R.attr.textColorPrimary, Color.WHITE));
        mPixels = makeText(15, false, mSecondary);
        mSummary = makeText(15, false, mSecondary);
        mSummary.setMaxLines(2);

        text.addView(mTitle);
        text.addView(mPixels);
        text.addView(mSummary);

        setOnTouchListener((v, event) -> {
            if (!isEnabled()) return false;
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                animate().cancel();
                animate().scaleX(0.985f).scaleY(0.985f).setDuration(70).start();
            } else if (action == MotionEvent.ACTION_UP
                    || action == MotionEvent.ACTION_CANCEL) {
                animate().cancel();
                animate().scaleX(1f).scaleY(1f).setDuration(120).start();
            }
            return false;
        });

        updateBackground();
    }

    public void bind(String title, String pixels, String summary, boolean pixelated) {
        mTitle.setText(title);
        mPixels.setText(pixels);
        mSummary.setText(summary);
        mPreview.setPixelated(pixelated);
        mAccessibilityText = title + ", " + pixels + ", " + summary;
        setContentDescription(mAccessibilityText);
    }

    public void setChecked(boolean checked) {
        if (mChecked == checked) return;
        mChecked = checked;
        mRadio.setChecked(checked);
        setSelected(checked);
        updateBackground();
        refreshDrawableState();
    }

    public boolean isChecked() {
        return mChecked;
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setClassName(RadioButton.class.getName());
        info.setCheckable(true);
        info.setChecked(mChecked);
        info.setSelected(mChecked);
        info.setClickable(isEnabled());
        info.setContentDescription(mAccessibilityText);
    }

    private void updateBackground() {
        GradientDrawable content = new GradientDrawable();
        content.setShape(GradientDrawable.RECTANGLE);
        content.setCornerRadius(dp(20));
        content.setColor(mSurface);
        content.setStroke(
                dp(mChecked ? 2 : 1),
                mChecked ? mAccent : withAlpha(mSecondary, 95));

        int ripple = withAlpha(mAccent, 40);
        setBackground(new RippleDrawable(ColorStateList.valueOf(ripple), content, null));
    }

    private TextView makeText(float sp, boolean bold, int color) {
        TextView view = new TextView(getContext());
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        return view;
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

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}

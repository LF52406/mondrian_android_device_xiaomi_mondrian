/* Copyright (C) 2026 The Android Open Source Project; SPDX-License-Identifier: Apache-2.0 */
package org.mondrian.haptics;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.HapticEngineConfig;
import android.os.Looper;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.window.OnBackInvokedDispatcher;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;

/** Standalone system settings surface; no dependency on a particular ROM's Settings resources. */
public final class HapticActivity extends Activity implements SettingsStore.Listener {
    private final Handler mMain = new Handler(Looper.getMainLooper());
    private final List<View> mPreviews = new ArrayList<>();
    private final List<LinearLayout> mProfileCards = new ArrayList<>();
    private final List<RadioButton> mProfileRadios = new ArrayList<>();
    private final List<HapticWaveView> mProfileIcons = new ArrayList<>();
    private SettingsStore mStore;
    private HapticEngineConfig mConfig = HapticEngineConfig.DEFAULT;
    private LinearLayout mRoot;
    private TextView mValue, mProfileValue, mEnableSummary;
    private HapticSwitch mSwitch;
    private HapticSlider mSlider;
    private View mStrengthCard, mCharacterCard;
    private boolean mProfiles, mBinding, mDragging, mSavePending;
    private boolean mSystemAllowed = true, mInputRedirected, mReady;
    private boolean mCanCompose;
    private long mLastPreview;
    private int mBackground, mSurface, mPrimary, mText, mSecondary, mTonal, mOnPrimary, mThumb;

    private final Runnable mSaveStrength = () -> {
        mSavePending = false;
        mStore.save(mConfig, HapticEngineConfig.PREVIEW_CLICK);
    };

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        int supported = getResources().getIdentifier(
                "config_mondrianHapticEngineSupported", "bool", "android");
        if (supported == 0 || !getResources().getBoolean(supported)) {
            Toast.makeText(this, R.string.unavailable, Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        Vibrator vibrator = getSystemService(Vibrator.class);
        mCanCompose = vibrator != null && vibrator.hasVibrator()
                && vibrator.areAllPrimitivesSupported(
                        VibrationEffect.Composition.PRIMITIVE_CLICK,
                        VibrationEffect.Composition.PRIMITIVE_TICK,
                        VibrationEffect.Composition.PRIMITIVE_LOW_TICK,
                        VibrationEffect.Composition.PRIMITIVE_THUD,
                        VibrationEffect.Composition.PRIMITIVE_QUICK_RISE,
                        VibrationEffect.Composition.PRIMITIVE_QUICK_FALL);
        mStore = new SettingsStore(this);
        mProfiles = savedInstanceState != null && savedInstanceState.getBoolean("profiles");
        applyPalette();
        getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT, this::goBack);
        render();
    }

    @Override protected void onResume() {
        super.onResume();
        if (mStore != null) {
            mReady = false;
            bind();
            mStore.start(this);
        }
    }

    @Override protected void onPause() {
        if (mStore != null) {
            flushStrength();
            mDragging = false;
            mReady = false;
            mStore.stop();
        }
        super.onPause();
    }

    @Override protected void onDestroy() {
        mMain.removeCallbacks(mSaveStrength);
        if (mStore != null) mStore.close();
        super.onDestroy();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putBoolean("profiles", mProfiles);
        super.onSaveInstanceState(state);
    }

    @Override public void onState(HapticEngineConfig config, boolean allowed, boolean redirected) {
        // A drag owns the local value until its final write has been queued.
        if (!mDragging && !mSavePending) mConfig = config;
        mSystemAllowed = allowed;
        mInputRedirected = redirected;
        mReady = true;
        bind();
    }

    @Override public void onSaved(int preview) {
        if (preview != 0) preview(preview);
    }

    @Override public void onError() {
        mMain.removeCallbacks(mSaveStrength);
        mSavePending = false;
        mDragging = false;
        mReady = false;
        bind();
        Toast.makeText(this, R.string.save_failed, Toast.LENGTH_LONG).show();
    }

    private void applyPalette() {
        boolean night = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        mPrimary = getColor(night ? android.R.color.system_accent1_200
                : android.R.color.system_accent1_600);
        mOnPrimary = getColor(night ? android.R.color.system_accent1_800
                : android.R.color.system_accent1_0);
        mBackground = night ? mix(getColor(android.R.color.system_neutral1_900), Color.BLACK, .32f)
                : getColor(android.R.color.system_neutral1_50);
        mText = getColor(night ? android.R.color.system_neutral1_50
                : android.R.color.system_neutral1_900);
        mSecondary = getColor(night ? android.R.color.system_neutral2_200
                : android.R.color.system_neutral2_600);
        mSurface = mix(mBackground, mPrimary, night ? .085f : .065f);
        mTonal = mix(mSurface, mPrimary, night ? .10f : .11f);
        mThumb = night ? getColor(android.R.color.system_accent1_10) : Color.WHITE;
        getWindow().setDecorFitsSystemWindows(false);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        int appearance = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
        getWindow().getInsetsController().setSystemBarsAppearance(night ? 0 : appearance, appearance);
    }

    private void render() {
        mPreviews.clear();
        mProfileCards.clear();
        mProfileRadios.clear();
        mProfileIcons.clear();
        mSwitch = null;
        mSlider = null;
        mEnableSummary = null;
        mProfileValue = null;
        mStrengthCard = null;
        mCharacterCard = null;
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(mBackground);
        scroll.setClipToPadding(false);
        scroll.setOnApplyWindowInsetsListener((view, insets) -> {
            Insets bars = insets.getInsets(WindowInsets.Type.systemBars()
                    | WindowInsets.Type.displayCutout());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
        mRoot = column(14);
        mRoot.setPadding(dp(14), dp(8), dp(14), dp(8));
        scroll.addView(mRoot, new ScrollView.LayoutParams(-1, -2));
        setContentView(scroll);
        LinearLayout top = row();
        Button back = button("", 20, false);
        back.setCompoundDrawablesRelativeWithIntrinsicBounds(new Arrow(false), null, null, null);
        back.setContentDescription(getString(R.string.back));
        back.setOnClickListener(v -> goBack());
        top.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));
        mRoot.addView(top);
        TextView title = text(getString(mProfiles ? R.string.character : R.string.app_name), 34, true);
        title.setPaddingRelative(dp(8), 0, dp(8), 0);
        title.setAccessibilityHeading(true);
        add(mRoot, title, 8);
        TextView subtitle = secondary(mProfiles ? R.string.character_hint : R.string.subtitle);
        subtitle.setTextSize(16);
        subtitle.setPaddingRelative(dp(8), 0, dp(8), 0);
        add(mRoot, subtitle, 6);
        HapticWaveView wave = new HapticWaveView(this, mProfiles
                ? HapticWaveView.Shape.PROFILE_HERO : HapticWaveView.Shape.HERO, mPrimary);
        LinearLayout.LayoutParams waveParams = new LinearLayout.LayoutParams(-1, dp(mProfiles ? 128 : 108));
        waveParams.topMargin = dp(mProfiles ? 12 : 6);
        waveParams.bottomMargin = dp(mProfiles ? 0 : 16);
        mRoot.addView(wave, waveParams);
        if (mProfiles) buildProfiles(); else buildMain();
        bind();
    }

    private void buildMain() {
        LinearLayout enable = card();
        LinearLayout row = row();
        LinearLayout labels = column(0);
        labels.addView(text(getString(R.string.enable), 18, true));
        mEnableSummary = secondary(R.string.enable_summary);
        add(labels, mEnableSummary, 6);
        row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
        mSwitch = new HapticSwitch(this, mPrimary, mTonal, mOnPrimary, mSecondary, mText);
        mSwitch.setContentDescription(getString(R.string.enable));
        mSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (mBinding) return;
            mConfig = new HapticEngineConfig(checked, mConfig.profile, mConfig.strength);
            bind();
            mStore.save(mConfig, 0);
        });
        LinearLayout.LayoutParams switchParams = new LinearLayout.LayoutParams(dp(60), dp(48));
        switchParams.setMarginStart(dp(12));
        row.addView(mSwitch, switchParams);
        enable.addView(row);
        enable.setMinimumHeight(dp(76));
        add(mRoot, enable, 0);

        LinearLayout strength = card();
        mStrengthCard = strength;
        LinearLayout heading = row();
        heading.addView(text(getString(R.string.strength), 18, true),
                new LinearLayout.LayoutParams(0, -2, 1));
        mValue = text(percent(mConfig.strength), 18, true);
        mValue.setTextColor(mPrimary);
        heading.addView(mValue);
        strength.addView(heading);
        mSlider = new HapticSlider(this, mPrimary, mTonal, mOnPrimary, mThumb);
        mSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (!fromUser || mBinding) return;
                mConfig = new HapticEngineConfig(mConfig.enabled, mConfig.profile, progress);
                mValue.setText(percent(progress));
                bar.setStateDescription(percent(progress));
                if (!mSavePending) {
                    mSavePending = true;
                    mMain.postDelayed(mSaveStrength, 120);
                }
            }
            @Override public void onStartTrackingTouch(SeekBar bar) { mDragging = true; }
            @Override public void onStopTrackingTouch(SeekBar bar) {
                mDragging = false;
                flushStrength();
            }
        });
        add(strength, mSlider, 10);
        LinearLayout ends = row();
        ends.addView(secondary(R.string.weaker), new LinearLayout.LayoutParams(0, -2, 1));
        ends.addView(secondary(R.string.stronger));
        add(strength, ends, 0);
        TextView hint = secondary(R.string.strength_hint);
        hint.setTextSize(14);
        add(strength, hint, 12);
        add(mRoot, strength, 12);

        LinearLayout character = card();
        mCharacterCard = character;
        LinearLayout content = row();
        content.addView(new HapticWaveView(this, HapticWaveView.Shape.BALANCED, mPrimary),
                new LinearLayout.LayoutParams(dp(40), dp(44)));
        LinearLayout labelsProfile = column(0);
        labelsProfile.addView(text(getString(R.string.character), 18, true));
        mProfileValue = secondary(profileName(mConfig.profile));
        add(labelsProfile, mProfileValue, 6);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(0, -2, 1);
        labelParams.setMarginStart(dp(16));
        content.addView(labelsProfile, labelParams);
        TextView chevron = text("", 18, false);
        chevron.setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, new Arrow(true), null);
        content.addView(chevron, new LinearLayout.LayoutParams(dp(24), dp(32)));
        character.addView(content);
        character.setMinimumHeight(dp(76));
        character.setOnClickListener(v -> { flushStrength(); mProfiles = true; render(); });
        asButton(character, getString(R.string.character));
        add(mRoot, character, 12);
        add(mRoot, previewCard(false), 12);
        Button reset = button(getString(R.string.reset), 16, false);
        reset.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle(R.string.reset)
                .setMessage(getString(R.string.reset_message, HapticEngineConfig.DEFAULT_STRENGTH))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.reset, (dialog, which) -> {
                    mMain.removeCallbacks(mSaveStrength);
                    mSavePending = false;
                    mConfig = HapticEngineConfig.DEFAULT;
                    bind();
                    mStore.save(mConfig, 0);
                }).show());
        LinearLayout.LayoutParams resetParams = new LinearLayout.LayoutParams(-1, dp(48));
        resetParams.topMargin = dp(10);
        mRoot.addView(reset, resetParams);
    }

    private void buildProfiles() {
        int[] summaries = {R.string.soft_summary, R.string.balanced_summary, R.string.crisp_summary};
        HapticWaveView.Shape[] shapes = {HapticWaveView.Shape.SOFT,
                HapticWaveView.Shape.BALANCED, HapticWaveView.Shape.CRISP};
        Configuration configuration = getResources().getConfiguration();
        int iconSize = dp(configuration.screenWidthDp < 380 || configuration.fontScale > 1.2f
                ? 52 : 66);
        for (int i = 0; i < 3; i++) {
            final int profile = i;
            LinearLayout card = card();
            card.setPadding(dp(14), dp(14), dp(14), dp(14));
            LinearLayout row = row();
            HapticWaveView icon = new HapticWaveView(this, shapes[i], mSecondary);
            icon.setBackground(surface(mTonal, false, 14));
            LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(iconSize, iconSize);
            iconParams.setMarginEnd(dp(16));
            row.addView(icon, iconParams);
            LinearLayout labels = column(0);
            labels.addView(text(getString(profileName(profile)), 18, true));
            add(labels, secondary(summaries[profile]), 6);
            row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
            RadioButton radio = new RadioButton(this);
            radio.setButtonTintList(null);
            radio.setButtonDrawable(new RadioMark());
            radio.setBackground(null);
            radio.setPadding(0, 0, 0, 0);
            radio.setGravity(Gravity.CENTER_VERTICAL);
            radio.setClickable(false);
            radio.setFocusable(false);
            radio.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            LinearLayout.LayoutParams radioParams = new LinearLayout.LayoutParams(dp(28), dp(48));
            radioParams.setMarginStart(dp(12));
            row.addView(radio, radioParams);
            card.addView(row);
            card.setMinimumHeight(dp(96));
            card.setFocusable(true);
            card.setContentDescription(getString(profileName(profile)) + ". " + getString(summaries[profile]));
            card.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
            row.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
            card.setAccessibilityDelegate(new View.AccessibilityDelegate() {
                @Override public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfo info) {
                    super.onInitializeAccessibilityNodeInfo(host, info);
                    info.setClassName(RadioButton.class.getName());
                    info.setCheckable(true);
                    info.setChecked(mConfig.profile == profile);
                }
            });
            card.setOnClickListener(v -> {
                mConfig = new HapticEngineConfig(mConfig.enabled, profile, mConfig.strength);
                bind();
                mStore.save(mConfig, HapticEngineConfig.PREVIEW_CLICK);
            });
            mProfileCards.add(card);
            mProfileRadios.add(radio);
            mProfileIcons.add(icon);
            add(mRoot, card, i == 0 ? 0 : 10);
        }
        add(mRoot, previewCard(true), 16);
        TextView footer = secondary(R.string.applies_immediately);
        footer.setGravity(Gravity.CENTER);
        footer.setTextSize(13);
        add(mRoot, footer, 20);
    }

    private LinearLayout previewCard(boolean profiles) {
        LinearLayout card = card();
        card.addView(text(getString(profiles ? R.string.compare : R.string.try_feedback), 18, true));
        add(card, secondary(profiles ? R.string.compare_hint : R.string.try_hint), 4);
        LinearLayout row = row();
        row.setGravity(Gravity.CENTER);
        int[] labels = {R.string.click, R.string.double_click, R.string.steps};
        int[] effects = {HapticEngineConfig.PREVIEW_CLICK, HapticEngineConfig.PREVIEW_DOUBLE,
                HapticEngineConfig.PREVIEW_STEPS};
        for (int i = 0; i < labels.length; i++) {
            final int effect = effects[i];
            LinearLayout button = column(10);
            button.setGravity(Gravity.CENTER);
            button.setMinimumHeight(dp(profiles ? 94 : 70));
            button.setBackground(surface(mTonal, false, 18));
            HapticWaveView.Shape shape = i == 0 ? HapticWaveView.Shape.CLICK
                    : i == 1 ? (profiles ? HapticWaveView.Shape.DOUBLE : HapticWaveView.Shape.DOUBLE_EVEN)
                    : (profiles ? HapticWaveView.Shape.STEPS : HapticWaveView.Shape.BALANCED);
            button.addView(new HapticWaveView(this, shape, mPrimary),
                    new LinearLayout.LayoutParams(dp(48), dp(profiles ? 40 : 28)));
            TextView label = text(getString(labels[i]), 14, true);
            label.setTextColor(profiles ? mText : mPrimary);
            label.setGravity(Gravity.CENTER);
            add(button, label, profiles ? 8 : 6);
            button.setOnClickListener(v -> requestPreview(effect));
            asButton(button, getString(R.string.preview_description, getString(labels[i])));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -1, 1);
            if (i != 0) params.setMarginStart(dp(6));
            row.addView(button, params);
            mPreviews.add(button);
        }
        add(card, row, 10);
        return card;
    }

    private void bind() {
        mBinding = true;
        boolean enabled = mReady && mCanCompose && mConfig.enabled;
        if (mSwitch != null) {
            mSwitch.setChecked(mConfig.enabled);
            mSwitch.setEnabled(mReady && mCanCompose);
            mEnableSummary.setText(!mCanCompose ? R.string.unavailable
                    : !mSystemAllowed ? R.string.system_off
                    : mInputRedirected ? R.string.input_redirected : R.string.enable_summary);
            mValue.setText(percent(mConfig.strength));
            if (!mDragging) mSlider.setProgress(mConfig.strength);
            mSlider.setStateDescription(percent(mConfig.strength));
            mSlider.setEnabled(enabled);
            mStrengthCard.setAlpha(enabled ? 1f : .45f);
            mCharacterCard.setEnabled(enabled);
            mCharacterCard.setAlpha(enabled ? 1f : .45f);
            mProfileValue.setText(profileName(mConfig.profile));
            mCharacterCard.setContentDescription(getString(R.string.character) + ", "
                    + getString(profileName(mConfig.profile)));
        }
        for (int i = 0; i < mProfileCards.size(); i++) {
            boolean selected = mConfig.profile == i;
            mProfileCards.get(i).setBackground(surface(selected ? mTonal : mSurface, selected));
            mProfileCards.get(i).setEnabled(enabled);
            mProfileCards.get(i).setAlpha(enabled ? 1f : .45f);
            mProfileRadios.get(i).setChecked(selected);
            mProfileIcons.get(i).setTint(selected ? mPrimary : mSecondary);
            mProfileIcons.get(i).setBackground(surface(
                    selected ? mix(mTonal, mPrimary, .07f) : mTonal, false, 14));
        }
        for (View preview : mPreviews) {
            boolean active = enabled && mSystemAllowed && !mInputRedirected && mConfig.strength > 0;
            preview.setEnabled(active);
            preview.setAlpha(active ? 1f : .45f);
        }
        mBinding = false;
    }

    private void requestPreview(int effect) {
        if (!mReady) return;
        if (mSavePending) {
            mMain.removeCallbacks(mSaveStrength);
            mSavePending = false;
            mStore.save(mConfig, effect);
        } else {
            mStore.previewAfterWrites(effect);
        }
    }

    private void preview(int effect) {
        if (!mReady || !mConfig.enabled || !mSystemAllowed || mInputRedirected
                || !mCanCompose || mConfig.strength == 0) return;
        long now = SystemClock.uptimeMillis();
        if (now - mLastPreview < 90) return;
        mLastPreview = now;
        // The service resolves the same recipes and checks the same policy as real UI events.
        mRoot.performHapticFeedback(effect);
    }

    private void flushStrength() {
        if (!mSavePending) return;
        mMain.removeCallbacks(mSaveStrength);
        mSaveStrength.run();
    }

    private void goBack() {
        flushStrength();
        if (mProfiles) { mProfiles = false; render(); } else finish();
    }

    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private String percent(int value) {
        return NumberFormat.getPercentInstance(getResources().getConfiguration().getLocales().get(0))
                .format(value / 100.0);
    }
    private int profileName(int profile) {
        return profile == HapticEngineConfig.SOFT ? R.string.soft
                : profile == HapticEngineConfig.CRISP ? R.string.crisp : R.string.balanced;
    }
    private TextView text(String text, int size, boolean medium) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(mText);
        view.setTypeface(Typeface.create(Typeface.DEFAULT, medium ? 600 : 400, false));
        view.setIncludeFontPadding(false);
        return view;
    }
    private TextView secondary(int resource) {
        TextView view = text(getString(resource), 15, false);
        view.setTextColor(mSecondary);
        return view;
    }
    private LinearLayout column(int padding) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(padding), dp(padding), dp(padding), dp(padding));
        return layout;
    }
    private LinearLayout row() {
        LinearLayout layout = new LinearLayout(this);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        return layout;
    }
    private LinearLayout card() {
        LinearLayout card = column(16);
        card.setBackground(surface(mSurface, false));
        return card;
    }
    private Button button(String label, int size, boolean tonal) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(size);
        button.setAllCaps(false);
        button.setTextColor(mPrimary);
        button.setBackground(surface(tonal ? mTonal : Color.TRANSPARENT, false));
        button.setPadding(dp(12), dp(8), dp(12), dp(8));
        button.setMinimumWidth(0);
        return button;
    }
    private Drawable surface(int color, boolean selected) {
        return surface(color, selected, 20);
    }
    private Drawable surface(int color, boolean selected, int radius) {
        GradientDrawable fill = color == Color.TRANSPARENT ? new GradientDrawable()
                : new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                        new int[]{mix(color, mPrimary, .025f), color});
        if (color == Color.TRANSPARENT) fill.setColor(color);
        fill.setCornerRadius(dp(radius));
        if (selected) fill.setStroke(dp(1), mPrimary);
        GradientDrawable mask = new GradientDrawable();
        mask.setColor(Color.WHITE);
        mask.setCornerRadius(dp(radius));
        return new RippleDrawable(ColorStateList.valueOf((mPrimary & 0xffffff) | 0x26000000), fill, mask);
    }
    private void add(LinearLayout parent, View child, int topMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(topMargin);
        parent.addView(child, params);
    }
    private void asButton(View view, String description) {
        view.setFocusable(true);
        view.setContentDescription(description);
        if (view instanceof ViewGroup group) {
            for (int i = 0; i < group.getChildCount(); i++) {
                group.getChildAt(i).setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
            }
        }
        view.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.setClassName(Button.class.getName());
            }
        });
    }
    private static int mix(int a, int b, float fraction) {
        return Color.rgb(Math.round(Color.red(a) * (1 - fraction) + Color.red(b) * fraction),
                Math.round(Color.green(a) * (1 - fraction) + Color.green(b) * fraction),
                Math.round(Color.blue(a) * (1 - fraction) + Color.blue(b) * fraction));
    }

    private final class RadioMark extends Drawable {
        private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        @Override public int getIntrinsicWidth() { return dp(28); }
        @Override public int getIntrinsicHeight() { return dp(28); }
        @Override public boolean isStateful() { return true; }
        @Override protected boolean onStateChange(int[] state) { invalidateSelf(); return true; }
        @Override public void draw(Canvas canvas) {
            boolean checked = false;
            for (int state : getState()) checked |= state == android.R.attr.state_checked;
            float x = getBounds().exactCenterX(), y = getBounds().exactCenterY();
            mPaint.setColor(checked ? mPrimary : mSecondary);
            mPaint.setStyle(Paint.Style.STROKE);
            mPaint.setStrokeWidth(dp(1.5f));
            canvas.drawCircle(x, y, dp(12), mPaint);
            if (checked) {
                mPaint.setStyle(Paint.Style.FILL);
                canvas.drawCircle(x, y, dp(6), mPaint);
            }
        }
        @Override public void setAlpha(int alpha) { mPaint.setAlpha(alpha); invalidateSelf(); }
        @Override public void setColorFilter(android.graphics.ColorFilter filter) {
            mPaint.setColorFilter(filter);
            invalidateSelf();
        }
        @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
    }

    private final class Arrow extends Drawable {
        private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final boolean mForward;
        Arrow(boolean forward) { mForward = forward; }
        @Override public int getIntrinsicWidth() { return dp(24); }
        @Override public int getIntrinsicHeight() { return dp(24); }
        @Override public void draw(Canvas canvas) {
            canvas.save();
            canvas.translate(getBounds().left, getBounds().top);
            canvas.scale(getBounds().width() / 24f, getBounds().height() / 24f);
            boolean rtl = getResources().getConfiguration().getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
            if (mForward != rtl) { canvas.translate(24, 0); canvas.scale(-1, 1); }
            mPaint.setColor(mSecondary);
            mPaint.setStrokeWidth(2);
            mPaint.setStrokeCap(Paint.Cap.ROUND);
            canvas.drawLine(14, 5, 7, 12, mPaint);
            canvas.drawLine(7, 12, 14, 19, mPaint);
            if (!mForward) canvas.drawLine(7, 12, 21, 12, mPaint);
            canvas.restore();
        }
        @Override public void setAlpha(int alpha) { mPaint.setAlpha(alpha); }
        @Override public void setColorFilter(android.graphics.ColorFilter filter) { mPaint.setColorFilter(filter); }
        @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
    }
}

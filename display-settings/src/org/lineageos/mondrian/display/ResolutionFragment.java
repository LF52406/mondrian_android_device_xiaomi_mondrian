/* Copyright (C) 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.lineageos.mondrian.display;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.os.UserHandle;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.preference.Preference;

import com.android.settingslib.widget.SelectorWithWidgetPreference;
import com.android.settingslib.widget.SettingsBasePreferenceFragment;

/** Uses the same expressive SettingsLib widgets/theme as the ROM's Settings and Xiaomi Parts. */
public final class ResolutionFragment extends SettingsBasePreferenceFragment {
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final Runnable mTick = this::tick;
    private ResolutionController mController;
    private SelectorWithWidgetPreference mFhd;
    private SelectorWithWidgetPreference mWqhd;
    private Preference mFooter;
    private AlertDialog mDialog;
    private ResolutionEngine.Pending mPending;
    private boolean mBusy;
    private boolean mAllowed;

    @Override
    public void onCreatePreferences(Bundle state, String rootKey) {
        setPreferencesFromResource(R.xml.screen_resolution, rootKey);
        mController = ResolutionController.get(requireContext());
        mFhd = findPreference("fhd");
        mWqhd = findPreference("wqhd");
        mFooter = findPreference("resolution_footer");
        mFhd.setOnClickListener(preference -> preview(1080));
        mWqhd.setOnClickListener(preference -> preview(1440));
        updateEnabled();
    }

    @Override
    public void onResume() {
        super.onResume();
        refresh();
    }

    @Override
    public void onStop() {
        mHandler.removeCallbacks(mTick);
        // A real departure cancels the preview. WM density/size recreation must retain it.
        if (mPending != null && !requireActivity().isChangingConfigurations()) {
            String token = mPending.token;
            mController.execute(() -> {
                mController.engine.rollback(token);
                return null;
            }, (unused, error) -> {});
        }
        if (mDialog != null) {
            mDialog.dismiss();
            mDialog = null;
        }
        super.onStop();
    }

    private void preview(int width) {
        if (mBusy || !mAllowed || mPending != null) return;
        run(() -> {
            mController.engine.preview(width);
            return readUi();
        });
    }

    private void refresh() {
        if (mBusy) return;
        run(() -> {
            if (UserHandle.myUserId() == UserHandle.USER_SYSTEM) {
                mController.engine.recover(false);
            }
            return readUi();
        });
    }

    private Ui readUi() throws Exception {
        boolean allowed;
        try {
            mController.backend.checkCanChange();
            allowed = true;
        } catch (SecurityException | IllegalStateException e) {
            allowed = false;
        }
        return new Ui(mController.backend.read(), mController.engine.pending(), allowed);
    }

    private void run(ResolutionController.Work<Ui> work) {
        mBusy = true;
        updateEnabled();
        mController.execute(work, (ui, error) -> {
            mBusy = false;
            if (!isAdded() || !isResumed()) return;
            if (error != null) {
                Toast.makeText(requireContext(), R.string.resolution_error, Toast.LENGTH_LONG)
                        .show();
                // Read the post-rollback result, rather than reporting the requested option.
                mController.execute(this::readUi, (recovered, readError) -> {
                    if (!isAdded() || !isResumed()) return;
                    if (recovered != null) render(recovered);
                    else {
                        mAllowed = false;
                        updateEnabled();
                    }
                });
            } else {
                render(ui);
            }
        });
    }

    private void render(Ui ui) {
        mAllowed = ui.allowed;
        mPending = ui.pending;
        mFhd.setChecked(ui.frame.width == 1080 && ui.frame.height == 2400);
        mWqhd.setChecked(ui.frame.width == 1440 && ui.frame.height == 3200);
        mFooter.setTitle(mAllowed ? R.string.resolution_footer : R.string.resolution_owner_only);
        updateEnabled();
        mHandler.removeCallbacks(mTick);
        if (mPending == null) {
            if (mDialog != null) mDialog.dismiss();
            mDialog = null;
            return;
        }
        if (mDialog == null) {
            final String token = mPending.token;
            mDialog = new AlertDialog.Builder(requireContext())
                    .setTitle(R.string.resolution_confirm_title)
                    .setMessage(confirmationMessage())
                    .setPositiveButton(R.string.resolution_keep, (dialog, which) -> run(() -> {
                        mController.engine.confirm(token);
                        return readUi();
                    }))
                    .setNegativeButton(R.string.resolution_revert, (dialog, which) -> revert(token))
                    .setOnCancelListener(dialog -> revert(token))
                    .create();
            mDialog.setCanceledOnTouchOutside(false);
            mDialog.setOnDismissListener(dialog -> {
                if (mDialog == dialog) mDialog = null;
            });
            mDialog.show();
        }
        tick();
    }

    private void revert(String token) {
        run(() -> {
            mController.engine.rollback(token);
            return readUi();
        });
    }

    private void tick() {
        if (!isResumed() || mPending == null) return;
        if (SystemClock.elapsedRealtime() >= mPending.deadline) {
            refresh();
        } else {
            if (mDialog != null) mDialog.setMessage(confirmationMessage());
            mHandler.postDelayed(mTick, 1000);
        }
    }

    private String confirmationMessage() {
        int seconds = (int) Math.max(1,
                (mPending.deadline - SystemClock.elapsedRealtime() + 999) / 1000);
        return getResources().getQuantityString(R.plurals.resolution_countdown, seconds, seconds);
    }

    private void updateEnabled() {
        boolean enabled = mAllowed && !mBusy && mPending == null;
        mFhd.setEnabled(enabled);
        mWqhd.setEnabled(enabled);
        if (mDialog != null) {
            mDialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(!mBusy);
            mDialog.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(!mBusy);
        }
    }

    private static final class Ui {
        final ResolutionEngine.Frame frame;
        final ResolutionEngine.Pending pending;
        final boolean allowed;

        Ui(ResolutionEngine.Frame frame, ResolutionEngine.Pending pending, boolean allowed) {
            this.frame = frame;
            this.pending = pending;
            this.allowed = allowed;
        }
    }
}

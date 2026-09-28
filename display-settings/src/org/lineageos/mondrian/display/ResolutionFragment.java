/* Copyright (C) 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.lineageos.mondrian.display;

import android.os.Bundle;
import android.os.UserHandle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Switch;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.io.IOException;

public final class ResolutionFragment extends Fragment {
    private ResolutionController mController;
    private ResolutionCardView mFhd;
    private ResolutionCardView mWqhd;
    private Switch mPartialSwitch;

    private boolean mBusy;
    private boolean mAllowed;
    private boolean mPartialAvailable;
    private boolean mUpdatingSwitch;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
            Bundle state) {
        return inflater.inflate(R.layout.fragment_screen_resolution, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        super.onViewCreated(view, state);
        mController = ResolutionController.get(requireContext());
        mFhd = view.findViewById(R.id.resolution_fhd);
        mWqhd = view.findViewById(R.id.resolution_wqhd);
        mPartialSwitch = view.findViewById(R.id.partial_update_switch);

        mFhd.bind(
                getString(R.string.resolution_fhd_title),
                getString(R.string.resolution_fhd_pixels),
                getString(R.string.resolution_fhd_summary),
                true);
        mWqhd.bind(
                getString(R.string.resolution_wqhd_title),
                getString(R.string.resolution_wqhd_pixels),
                getString(R.string.resolution_wqhd_summary),
                false);

        mFhd.setOnClickListener(v -> applyResolution(1080));
        mWqhd.setOnClickListener(v -> applyResolution(1440));
        mPartialSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (!mUpdatingSwitch) {
                setPartialUpdateEnabled(checked);
            }
        });

        updateEnabled();
    }

    @Override
    public void onResume() {
        super.onResume();
        refresh();
    }

    private void applyResolution(int width) {
        if (mBusy || !mAllowed) return;

        run(R.string.resolution_error, () -> {
            ResolutionEngine.Frame before = mController.backend.read();
            if (width == 1080
                    && (before.width != 1080 || before.height != 2400)) {
                // FHD does not support this M11A ROI path. This precondition is strict:
                // verify the real module parameter is disabled before shrinking the logical mode.
                mController.partialUpdate.ensureDisabledForFhd();
            }

            try {
                ResolutionEngine.Pending pending = mController.engine.preview(width);
                if (pending != null && !mController.engine.confirm(pending.token)) {
                    throw new IOException("Resolution preview could not be confirmed");
                }
            } catch (Exception failure) {
                try {
                    ResolutionEngine.Frame actual = mController.backend.read();
                    mController.partialUpdate.reconcile(actual.width, actual.height);
                } catch (Exception restoreFailure) {
                    failure.addSuppressed(restoreFailure);
                }
                throw failure;
            }

            ResolutionEngine.Frame actual = mController.backend.read();
            // Once the resolution transaction is committed, failure to restore the optional
            // WQHD Partial Update preference must not be misreported as a resolution failure.
            reconcilePartialBestEffort(actual);
            return readUi();
        });
    }

    private void setPartialUpdateEnabled(boolean enabled) {
        if (mBusy || !mAllowed || !mPartialAvailable) {
            refresh();
            return;
        }

        run(R.string.partial_update_error, () -> {
            ResolutionEngine.Frame frame = mController.backend.read();
            mController.partialUpdate.setUserEnabled(
                    enabled, frame.width, frame.height);
            return readUi();
        });
    }

    private void refresh() {
        if (mBusy) return;

        run(R.string.resolution_error, () -> {
            if (UserHandle.myUserId() == UserHandle.USER_SYSTEM) {
                // Immediate-confirm operations are serialized on ResolutionController's single
                // worker. A pending transaction observed by this later refresh is interrupted
                // state and should be restored, not presented as a confirmation dialog.
                mController.engine.recover(true);
            }
            ResolutionEngine.Frame frame = mController.backend.read();
            reconcilePartialBestEffort(frame);
            return readUi();
        });
    }

    private void reconcilePartialBestEffort(ResolutionEngine.Frame frame) {
        try {
            mController.partialUpdate.reconcile(frame.width, frame.height);
        } catch (Exception e) {
            Log.w(ResolutionController.TAG,
                    "Unable to reconcile Partial Update with the active resolution", e);
        }
    }

    private Ui readUi() throws Exception {
        ResolutionEngine.Frame frame = mController.backend.read();
        boolean allowed;
        try {
            mController.backend.checkCanChange();
            allowed = true;
        } catch (SecurityException | IllegalStateException e) {
            allowed = false;
        }
        return new Ui(
                frame,
                mController.partialUpdate.readState(frame.width, frame.height),
                allowed);
    }

    private void run(int errorMessage, ResolutionController.Work<Ui> work) {
        mBusy = true;
        updateEnabled();

        mController.execute(work, (ui, error) -> {
            mBusy = false;
            if (!isAdded() || getView() == null) return;

            if (error == null && ui != null) {
                render(ui);
                return;
            }

            Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_LONG).show();
            mController.execute(this::readUi, (recovered, readError) -> {
                if (!isAdded() || getView() == null) return;
                if (recovered != null) {
                    render(recovered);
                } else {
                    mAllowed = false;
                    mPartialAvailable = false;
                    updateEnabled();
                }
            });
        });
    }

    private void render(Ui ui) {
        mAllowed = ui.allowed;
        mPartialAvailable = ui.partial.available;

        mFhd.setChecked(ui.frame.width == 1080 && ui.frame.height == 2400);
        mWqhd.setChecked(ui.frame.width == 1440 && ui.frame.height == 3200);

        mUpdatingSwitch = true;
        mPartialSwitch.setChecked(ui.partial.available && ui.partial.enabled);
        mUpdatingSwitch = false;

        updateEnabled();
    }

    private void updateEnabled() {
        boolean cardsEnabled = mAllowed && !mBusy;
        if (mFhd != null) mFhd.setEnabled(cardsEnabled);
        if (mWqhd != null) mWqhd.setEnabled(cardsEnabled);
        if (mPartialSwitch != null) {
            mPartialSwitch.setEnabled(mAllowed && !mBusy && mPartialAvailable);
        }
    }

    private static final class Ui {
        final ResolutionEngine.Frame frame;
        final PartialUpdateBackend.State partial;
        final boolean allowed;

        Ui(ResolutionEngine.Frame frame, PartialUpdateBackend.State partial, boolean allowed) {
            this.frame = frame;
            this.partial = partial;
            this.allowed = allowed;
        }
    }
}

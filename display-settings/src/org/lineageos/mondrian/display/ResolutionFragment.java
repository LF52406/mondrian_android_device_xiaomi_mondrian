/* Copyright (C) 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.lineageos.mondrian.display;

import android.os.Bundle;
import android.os.UserHandle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Switch;
import android.widget.Toast;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public final class ResolutionFragment extends Fragment {
    private ResolutionController mController;
    private ResolutionCardView mFhd;
    private ResolutionCardView mWqhd;
    private Switch mPartialSwitch;
    private PartialUpdateDiagramView mPartialDiagram;
    private TextView mPartialStatus;

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
        mPartialDiagram = view.findViewById(R.id.partial_update_diagram);
        mPartialStatus = view.findViewById(R.id.partial_update_status);

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
                // Switch reflects acknowledged state while the request is in flight.
                mUpdatingSwitch = true;
                button.setChecked(!checked);
                mUpdatingSwitch = false;
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
            mController.applyResolution(width);
            return readUi();
        });
    }

    private void setPartialUpdateEnabled(boolean enabled) {
        if (mBusy || !mAllowed || !mPartialAvailable) {
            refresh();
            return;
        }

        run(R.string.partial_update_error, () -> {
            mController.setPartialUpdateEnabled(enabled);
            return readUi();
        });
    }

    private void refresh() {
        if (mBusy) return;

        run(R.string.resolution_error, () -> {
            if (UserHandle.myUserId() == UserHandle.USER_SYSTEM) {
                mController.recover();
            }
            return readUi();
        });
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
        mPartialSwitch.setChecked(ui.partial.enabled);
        mUpdatingSwitch = false;
        mPartialDiagram.setState(ui.partial.verified, ui.partial.enabled);
        int status = !ui.partial.verified ? R.string.partial_update_unknown
                : (!ui.partial.available && ui.partial.enabled) ? R.string.partial_update_error
                : !ui.partial.available ? R.string.partial_update_wqhd_only
                : ui.partial.enabled ? R.string.partial_update_on : R.string.partial_update_off;
        mPartialStatus.setText(status);
        mPartialSwitch.setStateDescription(getString(status));

        updateEnabled();
    }

    private void updateEnabled() {
        boolean cardsEnabled = mAllowed && !mBusy;
        if (mFhd != null) mFhd.setEnabled(cardsEnabled);
        if (mWqhd != null) mWqhd.setEnabled(cardsEnabled);
        if (mPartialStatus != null && mBusy) {
            mPartialStatus.setText(R.string.display_applying);
        }
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

/* Copyright (C) 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.lineageos.mondrian.display;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.RadioButton;

import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

/** Two side-by-side visual resolution cards. */
public final class ResolutionCardsPreference extends Preference {
    interface OnResolutionSelectedListener {
        void onResolutionSelected(int width);
    }

    private int mSelectedWidth;
    private OnResolutionSelectedListener mListener;

    public ResolutionCardsPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setLayoutResource(R.layout.preference_resolution_cards);
        setSelectable(false);
    }

    public ResolutionCardsPreference(Context context) {
        this(context, null);
    }

    void setOnResolutionSelectedListener(OnResolutionSelectedListener listener) {
        mListener = listener;
    }

    void setSelectedWidth(int width) {
        if (mSelectedWidth == width) return;
        mSelectedWidth = width;
        notifyChanged();
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        final View fhd = holder.findViewById(R.id.resolution_fhd_card);
        final View wqhd = holder.findViewById(R.id.resolution_wqhd_card);
        final RadioButton fhdRadio = (RadioButton) holder.findViewById(R.id.resolution_fhd_radio);
        final RadioButton wqhdRadio = (RadioButton) holder.findViewById(R.id.resolution_wqhd_radio);

        final boolean enabled = isEnabled();
        final boolean fhdSelected = mSelectedWidth == 1080;
        final boolean wqhdSelected = mSelectedWidth == 1440;

        fhd.setSelected(fhdSelected);
        wqhd.setSelected(wqhdSelected);
        fhdRadio.setChecked(fhdSelected);
        wqhdRadio.setChecked(wqhdSelected);

        fhd.setEnabled(enabled);
        wqhd.setEnabled(enabled);
        fhdRadio.setEnabled(enabled);
        wqhdRadio.setEnabled(enabled);
        holder.itemView.setAlpha(enabled ? 1f : 0.45f);

        fhd.setOnClickListener(enabled && mListener != null
                ? view -> mListener.onResolutionSelected(1080) : null);
        wqhd.setOnClickListener(enabled && mListener != null
                ? view -> mListener.onResolutionSelected(1440) : null);
    }
}

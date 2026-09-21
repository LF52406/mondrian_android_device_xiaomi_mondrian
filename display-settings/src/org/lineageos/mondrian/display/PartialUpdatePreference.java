/* Copyright (C) 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.lineageos.mondrian.display;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.Switch;

import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

/** Large visual Partial Update card with a single ON/OFF control. */
public final class PartialUpdatePreference extends Preference {
    interface OnCheckedChangeListener {
        void onCheckedChanged(boolean checked);
    }

    private boolean mChecked;
    private OnCheckedChangeListener mListener;

    public PartialUpdatePreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setLayoutResource(R.layout.preference_partial_update);
        setSelectable(false);
    }

    public PartialUpdatePreference(Context context) {
        this(context, null);
    }

    void setOnCheckedChangeListener(OnCheckedChangeListener listener) {
        mListener = listener;
    }

    void setChecked(boolean checked) {
        if (mChecked == checked) return;
        mChecked = checked;
        notifyChanged();
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        final View container = holder.findViewById(R.id.partial_update_container);
        final Switch toggle = (Switch) holder.findViewById(R.id.partial_update_switch);
        final boolean enabled = isEnabled();

        toggle.setOnCheckedChangeListener(null);
        toggle.setChecked(mChecked);
        toggle.setEnabled(enabled);
        container.setEnabled(enabled);
        holder.itemView.setAlpha(enabled ? 1f : 0.45f);

        final CompoundButton.OnCheckedChangeListener listener =
                (button, checked) -> {
                    if (enabled && mListener != null && checked != mChecked) {
                        mListener.onCheckedChanged(checked);
                    }
                };
        toggle.setOnCheckedChangeListener(listener);

        container.setOnClickListener(enabled && mListener != null
                ? view -> mListener.onCheckedChanged(!mChecked) : null);
    }
}

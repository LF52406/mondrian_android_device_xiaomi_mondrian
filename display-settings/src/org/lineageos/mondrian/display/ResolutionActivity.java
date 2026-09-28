/* Copyright (C) 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.lineageos.mondrian.display;

import android.app.ActionBar;
import android.os.Bundle;
import android.view.Display;

import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseActivity;
import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.appbar.CollapsingToolbarLayout;

public final class ResolutionActivity extends CollapsingToolbarBaseActivity {
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (getDisplay() == null || getDisplay().getDisplayId() != Display.DEFAULT_DISPLAY) {
            finish();
            return;
        }

        CollapsingToolbarLayout collapsing = getCollapsingToolbarLayout();
        if (collapsing != null) {
            collapsing.setTitleEnabled(false);
        }
        AppBarLayout appBar = getAppBarLayout();
        if (appBar != null) {
            appBar.setExpanded(false, false);
        }

        setTitle(R.string.resolution_title);
        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.setTitle(R.string.resolution_title);
        }

        if (state == null) {
            getSupportFragmentManager().beginTransaction().replace(
                    com.android.settingslib.collapsingtoolbar.R.id.content_frame,
                    new ResolutionFragment()).commit();
        }
    }
}

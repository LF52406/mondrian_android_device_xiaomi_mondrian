/* Copyright (C) 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.lineageos.mondrian.display;

import android.app.LocaleManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.LocaleList;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.os.Bundle;
import android.provider.SearchIndexablesContract;
import android.provider.SearchIndexablesProvider;

/** Search entry and the summary under Settings > Display. No mutation API is exported. */
public final class ResolutionSearchProvider extends SearchIndexablesProvider {
    @Override
    public boolean onCreate() { return true; }

    @Override
    public Cursor queryXmlResources(String[] projection) {
        return new MatrixCursor(SearchIndexablesContract.INDEXABLES_XML_RES_COLUMNS);
    }

    @Override
    public Cursor queryRawData(String[] projection) {
        MatrixCursor cursor = new MatrixCursor(SearchIndexablesContract.INDEXABLES_RAW_COLUMNS);
        cursor.newRow()
                .add(SearchIndexablesContract.RawData.COLUMN_RANK, 1)
                .add(SearchIndexablesContract.RawData.COLUMN_TITLE,
                        localizedString(R.string.display_mode_title))
                .add(SearchIndexablesContract.RawData.COLUMN_KEYWORDS,
                        localizedString(R.string.resolution_search_keywords))
                .add(SearchIndexablesContract.RawData.COLUMN_SCREEN_TITLE,
                        localizedString(R.string.display_mode_title))
                .add(SearchIndexablesContract.RawData.COLUMN_INTENT_ACTION, Intent.ACTION_MAIN)
                .add(SearchIndexablesContract.RawData.COLUMN_INTENT_TARGET_PACKAGE,
                        getContext().getPackageName())
                .add(SearchIndexablesContract.RawData.COLUMN_INTENT_TARGET_CLASS,
                        ResolutionActivity.class.getName())
                .add(SearchIndexablesContract.RawData.COLUMN_KEY, "mondrian_resolution");
        return cursor;
    }

    @Override
    public Cursor queryNonIndexableKeys(String[] projection) {
        return new MatrixCursor(SearchIndexablesContract.NON_INDEXABLES_KEYS_COLUMNS);
    }

    private String localizedString(int resId) {
        final Context context = getContext();
        // Settings may use a per-app locale different from the provider process.
        // Align the injected title and summary with the Settings UI locale.
        try {
            LocaleManager locales = context.getSystemService(LocaleManager.class);
            if (locales != null) {
                LocaleList settingsLocales = locales.getApplicationLocales("com.android.settings");
                if (settingsLocales != null && !settingsLocales.isEmpty()) {
                    Configuration config = new Configuration(context.getResources().getConfiguration());
                    config.setLocales(settingsLocales);
                    return context.createConfigurationContext(config).getString(resId);
                }
            }
        } catch (SecurityException ignored) {
            // A restricted ROM may omit permission support. Preserve the normal
            // locale-resolution behavior rather than breaking Settings search.
        }
        return context.getString(resId);
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        getContext().enforceCallingOrSelfPermission("android.permission.READ_SEARCH_INDEXABLES",
                "Only Settings may query display-mode metadata");
        if (!"get_dynamic_summary".equals(method)) return Bundle.EMPTY;

        Bundle result = new Bundle();
        result.putString("com.android.settings.summary",
                localizedString(R.string.display_mode_summary));
        return result;
    }
}

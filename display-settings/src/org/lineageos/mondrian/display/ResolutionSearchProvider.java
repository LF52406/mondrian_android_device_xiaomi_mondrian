/* Copyright (C) 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.lineageos.mondrian.display;

import android.content.Intent;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.graphics.Point;
import android.os.Binder;
import android.os.Bundle;
import android.provider.SearchIndexablesContract;
import android.provider.SearchIndexablesProvider;
import android.view.Display;
import android.view.WindowManagerGlobal;

/** Search entry and the live summary under Settings > Display. No mutation API is exported. */
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
                        getContext().getString(R.string.resolution_title))
                .add(SearchIndexablesContract.RawData.COLUMN_KEYWORDS,
                        getContext().getString(R.string.resolution_search_keywords))
                .add(SearchIndexablesContract.RawData.COLUMN_SCREEN_TITLE,
                        getContext().getString(R.string.resolution_title))
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

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        // ContentProvider.call does not enforce readPermission automatically.
        getContext().enforceCallingOrSelfPermission("android.permission.READ_SEARCH_INDEXABLES",
                "Only Settings may query resolution metadata");
        if (!"get_dynamic_summary".equals(method)) return Bundle.EMPTY;
        long identity = Binder.clearCallingIdentity();
        try {
            Point size = new Point();
            WindowManagerGlobal.getWindowManagerService()
                    .getBaseDisplaySize(Display.DEFAULT_DISPLAY, size);
            String label = size.x == 1080 && size.y == 2400 ? "FHD+"
                    : size.x == 1440 && size.y == 3200 ? "WQHD+"
                    : getContext().getString(R.string.resolution_custom);
            Bundle result = new Bundle();
            result.putString("com.android.settings.summary", getContext().getString(
                    R.string.resolution_current, label, size.x, size.y));
            return result;
        } catch (Exception e) {
            return Bundle.EMPTY;
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }
}

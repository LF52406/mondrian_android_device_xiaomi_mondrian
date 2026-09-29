/* Copyright (C) 2026 The Android Open Source Project; SPDX-License-Identifier: Apache-2.0 */
package org.mondrian.haptics;

import android.content.ContentResolver;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.HapticEngineConfig;
import android.os.Looper;
import android.provider.Settings;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Serializes writes off the UI thread; an older observer result cannot undo a newer edit. */
final class SettingsStore implements AutoCloseable {
    interface Listener {
        void onState(HapticEngineConfig config, boolean systemAllowed, boolean inputRedirected);
        void onSaved(int preview);
        void onError();
    }

    private final ContentResolver mResolver;
    private final Handler mMain = new Handler(Looper.getMainLooper());
    private final ExecutorService mIo = Executors.newSingleThreadExecutor();
    private final ContentObserver mObserver = new ContentObserver(mMain) {
        @Override public void onChange(boolean selfChange) { refresh(); }
    };
    private Listener mListener;
    private int mGeneration;
    private boolean mStarted;
    private boolean mClosed;

    SettingsStore(ContentResolver resolver) { mResolver = resolver; }

    void start(Listener listener) {
        if (mClosed) return;
        mListener = listener;
        if (!mStarted) {
            mResolver.registerContentObserver(Settings.Secure.getUriFor(
                    HapticEngineConfig.SETTINGS_KEY), false, mObserver);
            for (String key : new String[]{Settings.System.HAPTIC_FEEDBACK_ENABLED,
                    Settings.System.HAPTIC_FEEDBACK_INTENSITY, Settings.System.VIBRATE_ON,
                    Settings.System.VIBRATE_INPUT_DEVICES}) {
                mResolver.registerContentObserver(Settings.System.getUriFor(key), false, mObserver);
            }
            mStarted = true;
        }
        refresh();
    }

    void stop() {
        mListener = null;
        if (mStarted) mResolver.unregisterContentObserver(mObserver);
        mStarted = false;
    }

    void refresh() {
        if (mClosed) return;
        int generation = mGeneration;
        mIo.execute(() -> {
            try {
                HapticEngineConfig config = HapticEngineConfig.parse(Settings.Secure.getString(
                        mResolver, HapticEngineConfig.SETTINGS_KEY));
                boolean allowed = Settings.System.getInt(mResolver,
                        Settings.System.HAPTIC_FEEDBACK_ENABLED, 1) != 0
                        && Settings.System.getInt(mResolver,
                                Settings.System.HAPTIC_FEEDBACK_INTENSITY, 2) != 0
                        && Settings.System.getInt(mResolver, Settings.System.VIBRATE_ON, 1) != 0;
                boolean redirected = Settings.System.getInt(mResolver,
                        Settings.System.VIBRATE_INPUT_DEVICES, 0) != 0;
                mMain.post(() -> {
                    if (mListener != null && generation == mGeneration) {
                        mListener.onState(config, allowed, redirected);
                    }
                });
            } catch (RuntimeException e) { reportError(generation); }
        });
    }

    void save(HapticEngineConfig config, int preview) {
        if (mClosed) return;
        int generation = ++mGeneration;
        mIo.execute(() -> {
            try {
                if (!Settings.Secure.putString(mResolver, HapticEngineConfig.SETTINGS_KEY,
                        config.toString())) {
                    reportError(generation);
                    return;
                }
                mMain.post(() -> {
                    if (mListener != null && generation == mGeneration) mListener.onSaved(preview);
                });
            } catch (RuntimeException e) { reportError(generation); }
        });
    }

    private void reportError(int generation) {
        mMain.post(() -> {
            if (mListener != null && generation == mGeneration) mListener.onError();
        });
    }

    @Override public void close() {
        stop();
        mClosed = true;
        // Complete queued writes even when the user immediately leaves the page.
        mIo.shutdown();
    }
}

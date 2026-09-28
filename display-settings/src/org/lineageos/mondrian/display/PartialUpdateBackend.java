/* Copyright (C) 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.lineageos.mondrian.display;

import android.os.SystemClock;
import android.os.SystemProperties;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

final class PartialUpdateBackend {
    private static final String TAG = "MondrianPartialUpdate";

    private static final String PROFILE_PATH =
            "/sys/module/msm_drm/parameters/m11a_partial_update_profile";
    private static final String PROP_DESIRED =
            "persist.sys.mondrian.partial_update_enabled";
    private static final String PROP_RUNTIME =
            "sys.mondrian.partial_update_profile";

    private static final int PROFILE_UNKNOWN = -1;
    private static final int PROFILE_DISABLED = 0;
    private static final int PROFILE_SAFE = 1;
    private static final long APPLY_TIMEOUT_MS = 1000;
    private static final long POLL_MS = 25;

    static final class State {
        final boolean supported;
        final boolean available;
        final boolean enabled;
        final boolean desiredEnabled;
        final int actualProfile;

        State(boolean supported, boolean available, boolean enabled,
                boolean desiredEnabled, int actualProfile) {
            this.supported = supported;
            this.available = available;
            this.enabled = enabled;
            this.desiredEnabled = desiredEnabled;
            this.actualProfile = actualProfile;
        }
    }

    State readState(int width, int height) {
        int actual = readProfileOrUnknown();
        boolean supported = actual != PROFILE_UNKNOWN;
        boolean wqhd = isWqhd(width, height);
        return new State(
                supported,
                supported && wqhd,
                supported && actual != PROFILE_DISABLED,
                desiredEnabled(),
                actual);
    }

    void reconcile(int width, int height) throws Exception {
        int actual = readProfileOrUnknown();
        if (actual == PROFILE_UNKNOWN) {
            return;
        }
        int target = isWqhd(width, height) && desiredEnabled()
                ? PROFILE_SAFE : PROFILE_DISABLED;
        if (actual != target) {
            requestProfile(target);
        }
    }

    void ensureDisabledForFhd() throws Exception {
        int actual = readProfile();
        if (actual != PROFILE_DISABLED) {
            requestProfile(PROFILE_DISABLED);
        }
    }

    void setUserEnabled(boolean enabled, int width, int height) throws Exception {
        if (!isWqhd(width, height)) {
            throw new IllegalStateException("Partial update is available only at WQHD+");
        }
        if (readProfileOrUnknown() == PROFILE_UNKNOWN) {
            throw new IOException("M11A partial-update control is unavailable");
        }

        boolean previousDesired = desiredEnabled();
        try {
            setDesiredEnabled(enabled);
            requestProfile(enabled ? PROFILE_SAFE : PROFILE_DISABLED);
        } catch (Exception failure) {
            try {
                setDesiredEnabled(previousDesired);
                requestProfile(previousDesired ? PROFILE_SAFE : PROFILE_DISABLED);
            } catch (Exception restoreFailure) {
                failure.addSuppressed(restoreFailure);
            }
            throw failure;
        }
    }

    private boolean desiredEnabled() {
        return !"0".equals(SystemProperties.get(PROP_DESIRED, "1"));
    }

    private void setDesiredEnabled(boolean enabled) {
        SystemProperties.set(PROP_DESIRED, enabled ? "1" : "0");
    }

    private void requestProfile(int profile) throws Exception {
        if (profile != PROFILE_DISABLED && profile != PROFILE_SAFE) {
            throw new IllegalArgumentException("Unsupported partial-update profile");
        }

        String requested = Integer.toString(profile);
        // Force an init property edge even if a previous request already left the
        // transient property at the same value while the kernel state changed later.
        if (requested.equals(SystemProperties.get(PROP_RUNTIME, ""))) {
            SystemProperties.set(PROP_RUNTIME, "");
        }
        SystemProperties.set(PROP_RUNTIME, requested);
        long deadline = SystemClock.elapsedRealtime() + APPLY_TIMEOUT_MS;
        IOException lastReadFailure = null;

        while (SystemClock.elapsedRealtime() < deadline) {
            try {
                if (readProfile() == profile) {
                    return;
                }
                lastReadFailure = null;
            } catch (IOException e) {
                lastReadFailure = e;
            }
            SystemClock.sleep(POLL_MS);
        }

        IOException failure = new IOException(
                "Partial-update profile did not reach " + profile);
        if (lastReadFailure != null) {
            failure.addSuppressed(lastReadFailure);
        }
        throw failure;
    }

    private int readProfileOrUnknown() {
        try {
            return readProfile();
        } catch (IOException e) {
            Log.w(TAG, "Partial-update profile is not readable", e);
            return PROFILE_UNKNOWN;
        }
    }

    private int readProfile() throws IOException {
        File file = new File(PROFILE_PATH);
        if (!file.isFile()) {
            throw new IOException("Missing " + PROFILE_PATH);
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new FileInputStream(file), StandardCharsets.US_ASCII))) {
            String value = reader.readLine();
            if (value == null) {
                throw new IOException("Empty partial-update profile");
            }
            final int profile;
            try {
                profile = Integer.parseInt(value.trim());
            } catch (NumberFormatException e) {
                throw new IOException("Invalid partial-update profile: " + value, e);
            }
            if (profile < PROFILE_DISABLED || profile > 2) {
                throw new IOException("Out-of-range partial-update profile: " + profile);
            }
            return profile;
        }
    }

    private static boolean isWqhd(int width, int height) {
        return width == ResolutionEngine.NATIVE_WIDTH
                && height == ResolutionEngine.NATIVE_HEIGHT;
    }
}

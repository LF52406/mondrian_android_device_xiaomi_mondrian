/* Copyright (C) 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.lineageos.mondrian.display;

import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.UserHandle;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Owner-only requests; init bridges them to HWC and remains the sysfs writer. */
final class PartialUpdateBackend {
    private static final String PROFILE_PATH =
            "/sys/module/msm_drm/parameters/m11a_partial_update_profile";
    private static final String PROP_DESIRED = "persist.sys.mondrian.partial_update_enabled";
    private static final String PROP_REQUEST = "sys.mondrian.partial_update_request";
    private static final String PROP_STATUS = "sys.mondrian.partial_update_status";
    private static final long APPLY_TIMEOUT_MS = 6000;

    static final int PROFILE_UNKNOWN = -1;

    static final class State {
        final boolean available;
        final boolean enabled;
        final boolean desiredEnabled;
        final boolean verified;
        final int actualProfile;

        State(int profile, boolean wqhd, boolean desired, boolean verified) {
            actualProfile = profile;
            available = profile != PROFILE_UNKNOWN && wqhd;
            enabled = profile > 0;
            desiredEnabled = desired;
            this.verified = verified;
        }
    }

    State readState(int width, int height) {
        int profile = readProfileOrUnknown();
        String request = SystemProperties.get(PROP_REQUEST, "");
        boolean verified = !request.isEmpty() && (profile == 0 || profile == 1)
                && request.endsWith(":" + profile)
                && (request + ":ok").equals(SystemProperties.get(PROP_STATUS, ""));
        return new State(profile, isWqhd(width, height), desiredEnabled(), verified);
    }

    void reconcile(int width, int height, boolean allowEnable) throws Exception {
        requireOwnerProcess();
        State state = readState(width, height);
        int target = allowEnable && isWqhd(width, height) && desiredEnabled() ? 1 : 0;
        if (!state.verified || state.actualProfile != target) requestProfile(target);
    }

    void ensureDisabledForTransition() throws Exception {
        // Always require a fresh full-frame fence, even if sysfs already says 0.
        requestProfile(0);
    }

    void setUserEnabled(boolean enabled, int width, int height) throws Exception {
        requireOwnerProcess();
        if (!isWqhd(width, height)) {
            throw new IllegalStateException("Partial update is available only at WQHD+");
        }
        String previous = desiredEnabled() ? "1" : "0";
        try {
            SystemProperties.set(PROP_DESIRED, enabled ? "1" : "0");
            requestProfile(enabled ? 1 : 0);
        } catch (Exception failure) {
            try {
                SystemProperties.set(PROP_DESIRED, previous);
                // Keep the preference, but recover runtime conservatively to OFF.
                requestProfile(0);
            } catch (Exception restoreFailure) {
                failure.addSuppressed(restoreFailure);
            }
            throw failure;
        }
    }

    private static void requireOwnerProcess() {
        if (UserHandle.myUserId() != UserHandle.USER_SYSTEM) {
            throw new SecurityException("Display policy requires the owner process");
        }
    }

    private boolean desiredEnabled() {
        return "1".equals(SystemProperties.get(PROP_DESIRED, ""));
    }

    private void requestProfile(int profile) throws Exception {
        requireOwnerProcess();
        String request = UUID.randomUUID() + ":" + profile;
        SystemProperties.set(PROP_REQUEST, request);
        long deadline = SystemClock.elapsedRealtime() + APPLY_TIMEOUT_MS;
        while (SystemClock.elapsedRealtime() < deadline) {
            String status = SystemProperties.get(PROP_STATUS, "");
            if ((request + ":ok").equals(status) && readProfileOrUnknown() == profile) return;
            if ((request + ":error").equals(status)) break;
            // Poll an explicit retire-fence acknowledgement, not a settling delay.
            SystemClock.sleep(20);
        }
        // Supersede any late enable. HWC rechecks the generation before allowing PU.
        SystemProperties.set(PROP_REQUEST, UUID.randomUUID() + ":0");
        throw new IOException("Partial-update full-frame handshake failed for " + profile);
    }

    private int readProfileOrUnknown() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new FileInputStream(PROFILE_PATH), StandardCharsets.US_ASCII))) {
            String value = reader.readLine();
            int profile = Integer.parseInt(value == null ? "" : value.trim());
            return profile >= 0 && profile <= 2 ? profile : PROFILE_UNKNOWN;
        } catch (IOException | NumberFormatException e) {
            return PROFILE_UNKNOWN;
        }
    }

    private static boolean isWqhd(int width, int height) {
        return width == ResolutionEngine.NATIVE_WIDTH && height == ResolutionEngine.NATIVE_HEIGHT;
    }
}

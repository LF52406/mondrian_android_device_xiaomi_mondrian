/* Copyright (C) 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.lineageos.mondrian.display;

import android.app.ActivityManager;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.pm.UserInfo;
import android.graphics.Point;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.Settings;
import android.util.Log;
import android.view.Display;
import android.view.IWindowManager;
import android.view.WindowManagerGlobal;

import com.android.settingslib.display.DisplayDensityUtils;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class DisplayBackend implements ResolutionEngine.Backend {
    private static final String TAG = ResolutionController.TAG;

    private final Context mContext;
    private final IWindowManager mWm = WindowManagerGlobal.getWindowManagerService();
    private final UserManager mUsers;

    DisplayBackend(Context context) {
        mContext = context;
        mUsers = context.getSystemService(UserManager.class);
    }

    boolean supported() throws Exception {
        Point nativeSize = new Point();
        mWm.getInitialDisplaySize(Display.DEFAULT_DISPLAY, nativeSize);

        DisplayManager displayManager = mContext.getSystemService(DisplayManager.class);
        Display display = displayManager != null
                ? displayManager.getDisplay(Display.DEFAULT_DISPLAY)
                : null;

        boolean internal = display != null && display.getType() == Display.TYPE_INTERNAL;
        boolean nativeSizeMatches = nativeSize.x == ResolutionEngine.NATIVE_WIDTH
                && nativeSize.y == ResolutionEngine.NATIVE_HEIGHT;

        if (!internal || !nativeSizeMatches) {
            Log.w(TAG, "Unsupported display: buildDevice=" + Build.DEVICE
                    + ", vendorDevice="
                    + SystemProperties.get("ro.product.vendor.device", "")
                    + ", displayType=" + (display != null ? display.getType() : -1)
                    + ", initialSize=" + nativeSize.x + "x" + nativeSize.y);
            return false;
        }

        // Build.DEVICE is process-local and may be rewritten by ROM/root spoofing.
        // This app is built only for mondrian; validate the actual display capability instead.
        if (!"mondrian".equals(Build.DEVICE)) {
            Log.i(TAG, "Ignoring spoofed Build.DEVICE=" + Build.DEVICE
                    + "; physical mondrian display capability is valid");
        }
        return true;
    }

    @Override
    public void checkCanChange() throws Exception {
        if (!supported()) {
            throw new IllegalStateException("Unsupported display");
        }

        // Size is global. Only the foreground system owner may change it.
        int appUser = UserHandle.myUserId();
        if (appUser != UserHandle.USER_SYSTEM) {
            Log.w(TAG, "Resolution denied: appUser=" + appUser
                    + ", expected=" + UserHandle.USER_SYSTEM);
            throw new SecurityException("Resolution requires the system owner process");
        }

        int currentUser = ActivityManager.getCurrentUser();
        if (currentUser != UserHandle.USER_SYSTEM) {
            Log.w(TAG, "Resolution denied: currentUser=" + currentUser
                    + ", expected=" + UserHandle.USER_SYSTEM);
            throw new SecurityException("Resolution requires the foreground device owner");
        }

        KeyguardManager keyguard = mContext.getSystemService(KeyguardManager.class);
        if (keyguard == null) {
            Log.w(TAG, "Resolution denied: KeyguardManager unavailable");
            throw new IllegalStateException("Keyguard service unavailable");
        }
        if (keyguard.isKeyguardLocked()) {
            Log.w(TAG, "Resolution denied: keyguard is locked");
            throw new SecurityException("Resolution requires an unlocked device");
        }
    }

    private List<UserInfo> users() {
        return mUsers.getAliveUsers();
    }

    @Override
    public ResolutionEngine.Frame read() throws Exception {
        Point size = new Point();
        mWm.getBaseDisplaySize(Display.DEFAULT_DISPLAY, size);
        int initialDensity = mWm.getInitialDisplayDensity(Display.DEFAULT_DISPLAY);
        if (size.x <= 0 || size.y <= 0 || initialDensity <= 0) {
            throw new IOException("Default display is not ready");
        }
        int foreground = ActivityManager.getCurrentUser();
        Map<Integer, Integer> densities = new LinkedHashMap<>();
        for (UserInfo user : users()) {
            if (user.isProfile()) continue;
            int density = user.id == foreground
                    ? mWm.getBaseDisplayDensity(Display.DEFAULT_DISPLAY)
                    : Settings.Secure.getIntForUser(mContext.getContentResolver(),
                            Settings.Secure.DISPLAY_DENSITY_FORCED, initialDensity, user.id);
            density = density > 0 ? density : initialDensity;
            if (density > 10000) throw new IOException("Unsupported existing display density");
            densities.put(user.id, density);
        }
        if (densities.isEmpty()) throw new IOException("Cannot enumerate display users");
        int scaling = Settings.Global.getInt(mContext.getContentResolver(),
                Settings.Global.DISPLAY_SCALING_FORCE, 0);
        if (scaling != 0 && scaling != 1) throw new IOException("Invalid display scaling mode");
        return new ResolutionEngine.Frame(size.x, size.y, scaling, densities);
    }

    @Override
    public void apply(ResolutionEngine.Frame frame) throws Exception {
        if (!supported()) throw new IllegalStateException("Unsupported display");
        // All calls are typed Binder APIs; no shell, root, hidden transaction IDs or properties.
        mWm.setForcedDisplayScalingMode(Display.DEFAULT_DISPLAY, frame.scaling);
        if (frame.width == ResolutionEngine.NATIVE_WIDTH
                && frame.height == ResolutionEngine.NATIVE_HEIGHT) {
            mWm.clearForcedDisplaySize(Display.DEFAULT_DISPLAY);
        } else {
            mWm.setForcedDisplaySize(Display.DEFAULT_DISPLAY, frame.width, frame.height);
        }
        int foreground = ActivityManager.getCurrentUser();
        for (UserInfo user : users()) {
            if (user.id != foreground && frame.densities.containsKey(user.id)) {
                setDensity(user.id, frame.densities.get(user.id));
            }
        }
        // WM's density-forced flag is display-wide. Always finish with the foreground user.
        if (frame.densities.containsKey(foreground)) {
            setDensity(foreground, frame.densities.get(foreground));
        }
    }

    private void setDensity(int userId, int density) throws Exception {
        if (density == mWm.getInitialDisplayDensity(Display.DEFAULT_DISPLAY)) {
            mWm.clearForcedDisplayDensityForUser(Display.DEFAULT_DISPLAY, userId);
        } else {
            mWm.setForcedDisplayDensityForUser(Display.DEFAULT_DISPLAY, density, userId);
        }
    }

    void initializeNewUser(int userId) throws Exception {
        if (!supported() || userId < 0) return;
        UserInfo user = mUsers.getUserInfo(userId);
        if (user == null || user.isProfile()) return;
        Point size = new Point();
        mWm.getBaseDisplaySize(Display.DEFAULT_DISPLAY, size);
        if (size.x != 1080 || size.y != 2400) return;
        int forced = Settings.Secure.getIntForUser(mContext.getContentResolver(),
                Settings.Secure.DISPLAY_DENSITY_FORCED, 0, userId);
        if (forced != 0) return;
        // Public getter is supplied by the required A17 SettingsLib companion patch.
        int defaultDensity = DisplayDensityUtils.getDefaultDensityForDisplay(Display.DEFAULT_DISPLAY);
        if (defaultDensity <= 0) throw new IOException("Default display density is unavailable");
        int currentUser = ActivityManager.getCurrentUser();
        int currentDensity = mWm.getBaseDisplayDensity(Display.DEFAULT_DISPLAY);
        setDensity(userId, defaultDensity);
        if (userId != currentUser) setDensity(currentUser, currentDensity);
    }

    void initializeUntrackedUsers(Set<Integer> knownUsers) throws Exception {
        for (UserInfo user : users()) {
            if (!knownUsers.contains(user.id)) initializeNewUser(user.id);
        }
    }
}

/* Copyright (C) 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.lineageos.mondrian.display;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.UserHandle;

/** Cold-process and direct-boot recovery; confirmed WM settings need no boot reapplication. */
public final class ResolutionReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (UserHandle.myUserId() != UserHandle.USER_SYSTEM) return;
        String action = intent.getAction();
        boolean alarm = ResolutionController.ACTION_ROLLBACK.equals(action);
        boolean added = Intent.ACTION_USER_ADDED.equals(action);
        boolean recovery = Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(action)
                || Intent.ACTION_BOOT_COMPLETED.equals(action)
                || Intent.ACTION_USER_INITIALIZE.equals(action)
                || Intent.ACTION_USER_SWITCHED.equals(action)
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action);
        if (!alarm && !added && !recovery) return;
        PendingResult result = goAsync();
        ResolutionController controller = ResolutionController.get(context);
        controller.execute(() -> {
            controller.engine.recover(recovery || added);
            if (added) {
                controller.backend.initializeNewUser(
                        intent.getIntExtra(Intent.EXTRA_USER_HANDLE, UserHandle.USER_NULL));
            }
            if (recovery && controller.engine.pending() == null) {
                // Covers first start of a user created while this app's process was absent.
                controller.backend.initializeUntrackedUsers(controller.engine.knownUsers());
            }
            return null;
        }, (unused, error) -> result.finish());
    }
}

/* Copyright (C) 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.lineageos.mondrian.display;

import android.os.SystemProperties;

/** Persistent HWC policy. Kernel ROI capabilities stay immutable after probe. */
final class PartialUpdate {
    private static final String PROPERTY = "persist.sys.mondrian.partial_update";

    static int read() {
        int mode = SystemProperties.getInt(PROPERTY, 2);
        return mode == 0 ? 0 : 2;
    }

    static void write(DisplayBackend backend, ResolutionEngine engine, int mode) throws Exception {
        if (mode != 0 && mode != 2) throw new IllegalArgumentException("Invalid partial update mode");
        backend.checkCanChange();
        if (engine.pending() != null) throw new IllegalStateException("Resolution preview active");
        SystemProperties.set(PROPERTY, Integer.toString(mode));
        if (read() != mode) throw new IllegalStateException("Partial update policy was not saved");
    }

    private PartialUpdate() {}
}

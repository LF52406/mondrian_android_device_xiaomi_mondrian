/* Copyright (C) 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.lineageos.mondrian.display;

/** The same guarded path is used by apply, rollback and interrupted-journal recovery. */
final class ResolutionTransition implements ResolutionEngine.Backend {
    interface Display extends ResolutionEngine.Backend {
        void checkMutationProcess() throws Exception;
        void flushTransactions() throws Exception;
    }
    interface FullFrame { void await() throws Exception; }

    private final Display mDisplay;
    private final FullFrame mFullFrame;

    ResolutionTransition(Display display, FullFrame fullFrame) {
        mDisplay = display;
        mFullFrame = fullFrame;
    }

    public void checkCanChange() throws Exception { mDisplay.checkCanChange(); }
    public ResolutionEngine.Frame read() throws Exception { return mDisplay.read(); }

    public void apply(ResolutionEngine.Frame frame) throws Exception {
        mDisplay.checkMutationProcess();
        mFullFrame.await();
        mDisplay.apply(frame);
        mDisplay.flushTransactions();
        mFullFrame.await();
    }
}

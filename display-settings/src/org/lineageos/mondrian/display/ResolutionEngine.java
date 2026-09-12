/*
 * Copyright (C) 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.lineageos.mondrian.display;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Serialized, durable preview transaction. Has no Android dependencies. */
final class ResolutionEngine {
    static final int NATIVE_WIDTH = 1440;
    static final int NATIVE_HEIGHT = 3200;
    static final long PREVIEW_MS = 20_000;

    interface Backend {
        void checkCanChange() throws Exception;
        Frame read() throws Exception;
        void apply(Frame frame) throws Exception;
    }

    interface Store {
        State read() throws IOException;
        void write(State state) throws IOException;
    }

    interface Clock {
        long elapsedRealtime();
        int bootCount();
    }

    interface Watchdog {
        void schedule(String token, long deadline) throws Exception;
        void cancel();
    }

    static final class Frame {
        final int width;
        final int height;
        final int scaling;
        final Map<Integer, Integer> densities;

        Frame(int width, int height, int scaling, Map<Integer, Integer> densities) {
            this.width = width;
            this.height = height;
            this.scaling = scaling;
            this.densities = new LinkedHashMap<>(densities);
        }

        boolean matches(Frame actual) {
            if (width != actual.width || height != actual.height || scaling != actual.scaling) {
                return false;
            }
            for (Map.Entry<Integer, Integer> entry : densities.entrySet()) {
                // A user removed during a preview no longer needs a density override.
                Integer density = actual.densities.get(entry.getKey());
                if (density != null && !density.equals(entry.getValue())) return false;
            }
            return true;
        }
    }

    /** Keep a rational scale, so e.g. 562 -> 422 -> 562 never drifts to 563. */
    static final class Anchor {
        final int density;
        final int width;
        final int lastDensity;
        final int lastWidth;

        Anchor(int density, int width, int lastDensity, int lastWidth) {
            this.density = density;
            this.width = width;
            this.lastDensity = lastDensity;
            this.lastWidth = lastWidth;
        }

        int atWidth(int targetWidth) {
            return (int) (((long) density * targetWidth + width / 2L) / width);
        }
    }

    static final class Pending {
        final String token;
        final long deadline;
        final int boot;
        final Frame before;
        final Frame after;
        final Map<Integer, Anchor> anchors;

        Pending(String token, long deadline, int boot, Frame before, Frame after,
                Map<Integer, Anchor> anchors) {
            this.token = token;
            this.deadline = deadline;
            this.boot = boot;
            this.before = before;
            this.after = after;
            this.anchors = new LinkedHashMap<>(anchors);
        }
    }

    static final class State {
        final Map<Integer, Anchor> anchors;
        final Pending pending;

        State(Map<Integer, Anchor> anchors, Pending pending) {
            this.anchors = new LinkedHashMap<>(anchors);
            this.pending = pending;
        }

        static State empty() {
            return new State(new LinkedHashMap<>(), null);
        }
    }

    private final Backend mBackend;
    private final Store mStore;
    private final Clock mClock;
    private final Watchdog mWatchdog;

    ResolutionEngine(Backend backend, Store store, Clock clock, Watchdog watchdog) {
        mBackend = backend;
        mStore = store;
        mClock = clock;
        mWatchdog = watchdog;
    }

    synchronized Pending preview(int width) throws Exception {
        if (width != 1080 && width != NATIVE_WIDTH) {
            throw new IllegalArgumentException("Unsupported resolution");
        }
        recover(false);
        mBackend.checkCanChange();
        State state = mStore.read();
        if (state.pending != null) throw new IllegalStateException("Preview already pending");
        Frame before = mBackend.read();
        int height = width == 1080 ? 2400 : NATIVE_HEIGHT;
        if (before.width == width && before.height == height && before.scaling == 0) return null;

        Map<Integer, Integer> densities = new LinkedHashMap<>();
        Map<Integer, Anchor> anchors = new LinkedHashMap<>();
        for (Map.Entry<Integer, Integer> user : before.densities.entrySet()) {
            int current = user.getValue();
            Anchor anchor = state.anchors.get(user.getKey());
            if (anchor == null || anchor.lastWidth != before.width
                    || anchor.lastDensity != current) {
                // Respect changes made with Display size or the developer density setting.
                anchor = new Anchor(current, before.width, current, before.width);
            }
            int density = anchor.atWidth(width);
            if (density < 72 || density > 10000) {
                throw new IllegalArgumentException("Unsupported density");
            }
            densities.put(user.getKey(), density);
            anchors.put(user.getKey(), new Anchor(anchor.density, anchor.width, density, width));
        }
        Frame after = new Frame(width, height, 0, densities);
        Pending pending = new Pending(UUID.randomUUID().toString(),
                mClock.elapsedRealtime() + PREVIEW_MS, mClock.bootCount(), before, after, anchors);

        // The journal and process-independent rollback alarm MUST precede the first WM mutation.
        mStore.write(new State(state.anchors, pending));
        try {
            mWatchdog.schedule(pending.token, pending.deadline);
        } catch (Exception failure) {
            // No WM operation has run yet. Do not change the display without a watchdog.
            try {
                mStore.write(state);
            } catch (Exception journalFailure) {
                failure.addSuppressed(journalFailure);
            }
            throw failure;
        }
        try {
            mBackend.apply(after);
            if (!after.matches(mBackend.read())) throw new IOException("Display change incomplete");
            mBackend.checkCanChange();
        } catch (Exception failure) {
            try {
                rollback(pending.token);
            } catch (Exception rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
            }
            throw failure;
        }
        return pending;
    }

    synchronized boolean confirm(String token) throws Exception {
        State state = mStore.read();
        Pending pending = state.pending;
        if (pending == null || !pending.token.equals(token)) return false;
        if (expired(pending)) {
            rollback(token);
            return false;
        }
        mBackend.checkCanChange();
        if (!pending.after.matches(mBackend.read())) {
            rollback(token);
            throw new IOException("Display changed during confirmation");
        }
        // Commit before cancelling the alarm; a stale alarm cannot undo a committed choice.
        mStore.write(new State(pending.anchors, null));
        mWatchdog.cancel();
        return true;
    }

    synchronized void rollback(String token) throws Exception {
        State state = mStore.read();
        Pending pending = state.pending;
        if (pending == null || (token != null && !pending.token.equals(token))) return;
        try {
            mBackend.apply(pending.before);
            if (!pending.before.matches(mBackend.read())) {
                throw new IOException("Display rollback incomplete");
            }
            mStore.write(new State(state.anchors, null));
            mWatchdog.cancel();
        } catch (Exception failure) {
            // Retain the journal for process/boot recovery and retry transient binder failures.
            try {
                mWatchdog.schedule(pending.token, mClock.elapsedRealtime() + 5_000);
            } catch (Exception alarmFailure) {
                failure.addSuppressed(alarmFailure);
            }
            throw failure;
        }
    }

    synchronized Pending recover(boolean forceRollback) throws Exception {
        Pending pending = mStore.read().pending;
        if (pending == null) return null;
        if (forceRollback || expired(pending)) {
            rollback(pending.token);
            return null;
        }
        mWatchdog.schedule(pending.token, pending.deadline);
        return pending;
    }

    synchronized Pending pending() throws IOException {
        return mStore.read().pending;
    }

    synchronized Set<Integer> knownUsers() throws IOException {
        return new HashSet<>(mStore.read().anchors.keySet());
    }

    private boolean expired(Pending pending) {
        return pending.boot != mClock.bootCount()
                || mClock.elapsedRealtime() >= pending.deadline;
    }
}

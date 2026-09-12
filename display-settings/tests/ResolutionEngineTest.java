/* Copyright (C) 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.lineageos.mondrian.display;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/** Host regression tests exercise the shipping engine and journal codec, including failures. */
public final class ResolutionEngineTest {
    private static int sChecks;

    public static void main(String[] args) throws Exception {
        acceptedChoiceSurvivesRestart();
        unconfirmedChoiceRollsBackAfterProcessDeath();
        rebootDuringPreviewRestoresSnapshot();
        lateAndStaleConfirmationsCannotCommit();
        sizeAndDensityFailureIsRolledBack();
        rollbackFailureRetainsRecoveryJournal();
        journalAndAlarmFailuresDoNotChangeDisplay();
        failedCommitRemainsRecoverable();
        densityDoesNotDriftAndUserChangesAreRespected();
        multipleUsersAreScaledIndependently();
        customSizeAndScalingAreRestored();
        userSwitchAndExternalChangeCannotCommit();
        invalidAndRepeatedRequestsAreRejected();
        corruptJournalFailsClosed();
        System.out.println("PASS: 14 scenarios, " + sChecks + " assertions");
    }

    private static void acceptedChoiceSurvivesRestart() throws Exception {
        Rig r = new Rig(560);
        var p = r.engine.preview(1080);
        equal(r.backend.frame.width, 1080);
        equal(r.backend.frame.densities.get(0), 420);
        check(r.timer.armed, "rollback armed");
        check(r.engine.confirm(p.token), "confirmation accepted");
        r.restart();
        r.engine.recover(true);
        equal(r.backend.frame.width, 1080);
        check(r.engine.pending() == null, "no boot reapply/rollback after commit");
        check(!r.timer.armed, "alarm cancelled");
    }

    private static void unconfirmedChoiceRollsBackAfterProcessDeath() throws Exception {
        Rig r = new Rig(560);
        r.engine.preview(1080);
        r.restart();
        check(r.engine.recover(false) != null, "recreation retains live preview");
        r.clock.now += ResolutionEngine.PREVIEW_MS;
        r.engine.recover(false);
        equal(r.backend.frame.width, 1440);
        equal(r.backend.frame.densities.get(0), 560);
        check(r.engine.pending() == null, "journal cleared after recovery");
    }

    private static void rebootDuringPreviewRestoresSnapshot() throws Exception {
        Rig r = new Rig(562);
        r.engine.preview(1080);
        r.clock.boot++;
        r.clock.now = 0;
        r.restart();
        r.engine.recover(false);
        equal(r.backend.frame.width, 1440);
        equal(r.backend.frame.densities.get(0), 562);
    }

    private static void lateAndStaleConfirmationsCannotCommit() throws Exception {
        Rig r = new Rig(560);
        var old = r.engine.preview(1080);
        r.clock.now = old.deadline;
        check(!r.engine.confirm(old.token), "deadline is inclusive");
        var current = r.engine.preview(1080);
        check(!r.engine.confirm(old.token), "old token cannot confirm new preview");
        r.engine.rollback(old.token);
        equal(r.backend.frame.width, 1080);
        check(r.engine.pending().token.equals(current.token), "new preview retained");
        r.engine.rollback(current.token);
        equal(r.backend.frame.width, 1440);
    }

    private static void sizeAndDensityFailureIsRolledBack() throws Exception {
        Rig r = new Rig(560);
        r.backend.failures = 1;
        fails(() -> r.engine.preview(1080));
        equal(r.backend.frame.width, 1440);
        equal(r.backend.frame.densities.get(0), 560);
        check(r.engine.pending() == null, "partial WM failure recovered");
    }

    private static void rollbackFailureRetainsRecoveryJournal() throws Exception {
        Rig r = new Rig(560);
        r.backend.failures = 2;
        fails(() -> r.engine.preview(1080));
        check(r.engine.pending() != null, "failed rollback retains journal");
        check(r.timer.armed, "retry scheduled");
        r.restart();
        r.engine.recover(true);
        equal(r.backend.frame.width, 1440);
        equal(r.backend.frame.densities.get(0), 560);
    }

    private static void journalAndAlarmFailuresDoNotChangeDisplay() throws Exception {
        Rig r = new Rig(560);
        r.store.failNext = true;
        fails(() -> r.engine.preview(1080));
        equal(r.backend.writes, 0);
        r.timer.failNext = true;
        fails(() -> r.engine.preview(1080));
        equal(r.backend.writes, 0);
        check(r.engine.pending() == null, "alarm failure clears unstarted preview");
    }

    private static void failedCommitRemainsRecoverable() throws Exception {
        Rig r = new Rig(560);
        var p = r.engine.preview(1080);
        r.store.failNext = true;
        fails(() -> r.engine.confirm(p.token));
        check(r.engine.pending() != null, "uncommitted confirmation remains pending");
        r.clock.now = p.deadline;
        r.restart();
        r.engine.recover(false);
        equal(r.backend.frame.width, 1440);
    }

    private static void densityDoesNotDriftAndUserChangesAreRespected() throws Exception {
        Rig r = new Rig(562);
        for (int i = 0; i < 100; i++) {
            var down = r.engine.preview(1080);
            equal(r.backend.frame.densities.get(0), 422);
            r.engine.confirm(down.token);
            r.restart();
            var up = r.engine.preview(1440);
            equal(r.backend.frame.densities.get(0), 562);
            r.engine.confirm(up.token);
        }
        var down = r.engine.preview(1080);
        r.engine.confirm(down.token);
        r.backend.frame.densities.put(0, 450); // User changes Display size in Settings.
        var up = r.engine.preview(1440);
        equal(r.backend.frame.densities.get(0), 600);
        r.engine.confirm(up.token);
    }

    private static void multipleUsersAreScaledIndependently() throws Exception {
        Rig r = new Rig(560);
        r.backend.frame.densities.put(10, 640);
        var p = r.engine.preview(1080);
        equal(r.backend.frame.densities.get(0), 420);
        equal(r.backend.frame.densities.get(10), 480);
        r.engine.rollback(p.token);
        equal(r.backend.frame.densities.get(10), 640);
    }

    private static void customSizeAndScalingAreRestored() throws Exception {
        Rig r = new Rig(500);
        r.backend.frame = new ResolutionEngine.Frame(1200, 2666, 1,
                r.backend.frame.densities);
        var p = r.engine.preview(1080);
        equal(r.backend.frame.densities.get(0), 450);
        equal(r.backend.frame.scaling, 0);
        r.engine.rollback(p.token);
        equal(r.backend.frame.width, 1200);
        equal(r.backend.frame.height, 2666);
        equal(r.backend.frame.scaling, 1);
        equal(r.backend.frame.densities.get(0), 500);
    }

    private static void userSwitchAndExternalChangeCannotCommit() throws Exception {
        Rig r = new Rig(560);
        var p = r.engine.preview(1080);
        r.backend.allowed = false;
        String firstToken = p.token;
        fails(() -> r.engine.confirm(firstToken));
        r.engine.recover(true); // Protected USER_SWITCHED broadcast.
        equal(r.backend.frame.width, 1440);
        r.backend.allowed = true;
        p = r.engine.preview(1080);
        r.backend.frame.densities.put(0, 444);
        String token = p.token;
        fails(() -> r.engine.confirm(token));
        equal(r.backend.frame.densities.get(0), 560);
    }

    private static void invalidAndRepeatedRequestsAreRejected() throws Exception {
        Rig r = new Rig(560);
        fails(() -> r.engine.preview(720));
        equal(r.backend.writes, 0);
        check(r.engine.preview(1440) == null, "unchanged mode does not create a preview");
        r.engine.preview(1080);
        fails(() -> r.engine.preview(1440));
        equal(r.backend.frame.width, 1080);
    }

    private static void corruptJournalFailsClosed() throws Exception {
        Rig r = new Rig(560);
        r.store.bytes = new byte[] {1, 2, 3};
        fails(() -> r.engine.preview(1080));
        equal(r.backend.writes, 0);
    }

    private interface Operation { void run() throws Exception; }

    private static void fails(Operation operation) throws Exception {
        try {
            operation.run();
        } catch (Exception expected) {
            sChecks++;
            return;
        }
        throw new AssertionError("Expected operation to fail");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        sChecks++;
    }

    private static void equal(int actual, int expected) {
        check(actual == expected, "Expected " + expected + ", got " + actual);
    }

    private static final class Rig {
        final FakeBackend backend;
        final MemoryStore store = new MemoryStore();
        final FakeClock clock = new FakeClock();
        final FakeTimer timer = new FakeTimer();
        ResolutionEngine engine;
        Rig(int density) {
            backend = new FakeBackend(density);
            restart();
        }
        void restart() { engine = new ResolutionEngine(backend, store, clock, timer); }
    }

    private static final class FakeBackend implements ResolutionEngine.Backend {
        ResolutionEngine.Frame frame;
        int failures;
        int writes;
        boolean allowed = true;
        FakeBackend(int density) {
            Map<Integer, Integer> densities = new LinkedHashMap<>();
            densities.put(0, density);
            frame = new ResolutionEngine.Frame(1440, 3200, 0, densities);
        }
        public void checkCanChange() {
            if (!allowed) throw new SecurityException("User switched");
        }
        public ResolutionEngine.Frame read() {
            return new ResolutionEngine.Frame(frame.width, frame.height, frame.scaling,
                    frame.densities);
        }
        public void apply(ResolutionEngine.Frame target) throws Exception {
            writes++;
            frame = new ResolutionEngine.Frame(target.width, target.height, target.scaling,
                    frame.densities);
            if (failures > 0) {
                failures--;
                throw new IOException("Binder failed after size but before density");
            }
            frame = new ResolutionEngine.Frame(target.width, target.height, target.scaling,
                    target.densities);
        }
    }

    private static final class MemoryStore implements ResolutionEngine.Store {
        byte[] bytes;
        boolean failNext;
        public ResolutionEngine.State read() throws IOException {
            return bytes == null ? ResolutionEngine.State.empty()
                    : StateCodec.read(new ByteArrayInputStream(bytes));
        }
        public void write(ResolutionEngine.State state) throws IOException {
            if (failNext) {
                failNext = false;
                throw new IOException("Disk write failed");
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            StateCodec.write(out, state);
            bytes = out.toByteArray();
        }
    }

    private static final class FakeClock implements ResolutionEngine.Clock {
        long now = 1000;
        int boot = 1;
        public long elapsedRealtime() { return now; }
        public int bootCount() { return boot; }
    }

    private static final class FakeTimer implements ResolutionEngine.Watchdog {
        boolean armed;
        boolean failNext;
        public void schedule(String token, long deadline) throws Exception {
            if (failNext) {
                failNext = false;
                throw new IOException("Exact alarm unavailable");
            }
            armed = true;
        }
        public void cancel() { armed = false; }
    }
}

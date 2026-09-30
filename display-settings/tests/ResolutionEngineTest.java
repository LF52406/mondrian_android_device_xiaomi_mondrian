/* Copyright (C) 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.lineageos.mondrian.display;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Failure injection runs the shipping engine, guarded transition and MRS1 codec. */
public final class ResolutionEngineTest {
    private static int checks;
    public static void main(String[] args) throws Exception {
        Rig r = new Rig(560);
        r.engine.apply(1080);
        equal(r.display.frame.width, 1080);
        equal(r.display.frame.densities.get(0), 420);
        check(r.store.read().pending == null && !r.alarm.armed, "tap is committed immediately");
        r.clock.now += 120_000;
        r.restart();
        r.engine.recover();
        equal(r.display.frame.width, 1080); // Includes stale legacy 20-second alarm delivery.

        for (int initial : new int[] {1080, 1440}) {
            for (boolean pu : new boolean[] {false, true}) {
                r = new Rig(560);
                if (initial == 1080) r.engine.apply(1080);
                r.events.clear(); r.pu = pu;
                r.engine.apply(initial == 1080 ? 1440 : 1080);
                check(r.events.equals(List.of("OFF", "WM", "FLUSH", "OFF")),
                        "every direction starts and finishes with a full-frame fence");
                check(!r.pu, "engine never restores PU before its journal is committed");
            }
        }

        r = new Rig(562); r.display.die = true;
        try { r.engine.apply(1080); throw new AssertionError("death missing"); }
        catch (ProcessDeath expected) { }
        check(r.store.read().pending != null && r.alarm.armed, "death leaves durable recovery");
        r.clock.boot++; r.clock.now = 0; r.pu = true; r.events.clear(); r.restart();
        r.engine.recover();
        equal(r.display.frame.width, 1440); equal(r.display.frame.densities.get(0), 562);
        check(r.events.equals(List.of("OFF", "WM", "FLUSH", "OFF")), "boot rollback guarded");

        // Journal remains binary-compatible with the old preview model; ignore its live deadline.
        r = new Rig(560);
        var before = r.display.read();
        var after = new ResolutionEngine.Frame(1080, 2400, 0, Map.of(0, 420));
        r.store.write(new ResolutionEngine.State(Map.of(), new ResolutionEngine.Pending(
                "old-preview", 21_000, 1, before, after, Map.of())));
        r.display.frame = after; r.pu = true; r.engine.recover();
        equal(r.display.frame.width, 1440);
        check(r.store.read().pending == null, "legacy pending migrated by rollback");

        r = new Rig(560); r.failBarrier = 2;
        fails(r, () -> rRef.engine.apply(1080));
        equal(r.display.writes, 0);
        check(r.store.read().pending != null, "failed guard/rollback retains recovery");
        r.engine.recover(); check(r.store.read().pending == null, "guard retry recovers");

        r = new Rig(560); r.display.failures = 1;
        fails(r, () -> rRef.engine.apply(1080));
        equal(r.display.frame.width, 1440);
        check(r.store.read().pending == null, "size/density failure rolls back");

        r = new Rig(560); r.display.failures = 2;
        fails(r, () -> rRef.engine.apply(1080));
        check(r.store.read().pending != null && r.alarm.armed, "failed rollback retained");
        r.restart(); r.engine.recover(); equal(r.display.frame.densities.get(0), 560);

        r = new Rig(560); r.store.failPending = true;
        fails(r, () -> rRef.engine.apply(1080)); equal(r.display.writes, 0);
        r.alarm.fail = true;
        fails(r, () -> rRef.engine.apply(1080)); equal(r.display.writes, 0);

        r = new Rig(560); r.store.failCommit = true;
        fails(r, () -> rRef.engine.apply(1080));
        equal(r.display.frame.width, 1440);
        check(r.store.read().pending == null && !r.pu, "commit failure rolls back with PU OFF");

        r = new Rig(560); r.failAfterFlush = true;
        fails(r, () -> rRef.engine.apply(1080));
        equal(r.display.frame.width, 1440);
        check(r.store.read().pending == null, "missing post-WM fence cannot commit");

        r = new Rig(562);
        for (int i = 0; i < 100; i++) {
            r.engine.apply(1080); equal(r.display.frame.densities.get(0), 422);
            r.restart(); r.engine.apply(1440); equal(r.display.frame.densities.get(0), 562);
        }
        r.engine.apply(1080); r.display.frame.densities.put(0, 450);
        r.engine.apply(1440); equal(r.display.frame.densities.get(0), 600);

        r = new Rig(560); r.display.frame.densities.put(10, 640);
        r.engine.apply(1080); equal(r.display.frame.densities.get(10), 480);
        r.engine.apply(1440); equal(r.display.frame.densities.get(10), 640);

        r = new Rig(500);
        r.display.frame = new ResolutionEngine.Frame(1200, 2666, 1, Map.of(0, 500));
        r.display.failures = 1; fails(r, () -> rRef.engine.apply(1080));
        equal(r.display.frame.width, 1200); equal(r.display.frame.height, 2666);
        equal(r.display.frame.scaling, 1); equal(r.display.frame.densities.get(0), 500);

        r = new Rig(560); r.display.switchUser = true;
        fails(r, () -> rRef.engine.apply(1080));
        equal(r.display.frame.width, 1440);
        check(!r.pu && r.store.read().pending == null, "owner loss forces guarded rollback");

        r = new Rig(560); r.display.ownerProcess = false;
        fails(r, () -> rRef.engine.apply(1080)); equal(r.display.writes, 0);
        check(r.events.isEmpty(), "secondary process cannot even request PU");

        r = new Rig(560); fails(r, () -> rRef.engine.apply(720));
        r.engine.apply(1440); equal(r.display.writes, 0);
        r.store.bytes = new byte[] {1, 2, 3};
        fails(r, () -> rRef.engine.apply(1080)); equal(r.display.writes, 0);
        System.out.println("PASS: 18 scenarios, " + checks + " assertions");
    }

    // Keep failure lambdas terse while each rig is replaced between scenarios.
    private static Rig rRef;
    private interface Action { void run() throws Exception; }
    private static void fails(Rig rig, Action action) throws Exception {
        rRef = rig;
        try { action.run(); } catch (Exception expected) { checks++; return; }
        throw new AssertionError("Expected failure");
    }
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
        checks++;
    }
    private static void equal(int actual, int expected) {
        check(actual == expected, "Expected " + expected + ", got " + actual);
    }
    private static final class ProcessDeath extends Error { }
    private static final class Rig {
        final List<String> events = new ArrayList<>();
        final MemoryStore store = new MemoryStore();
        final Clock clock = new Clock();
        final Alarm alarm = new Alarm();
        final FakeDisplay display;
        ResolutionEngine engine;
        int failBarrier;
        boolean failAfterFlush;
        boolean pu = true;
        Rig(int density) { display = new FakeDisplay(this, density); restart(); }
        void restart() {
            engine = new ResolutionEngine(new ResolutionTransition(display, () -> {
                if (failBarrier > 0) { failBarrier--; throw new IOException("No full-frame ACK"); }
                pu = false; events.add("OFF");
            }), store, clock, alarm);
        }
    }
    private static final class FakeDisplay implements ResolutionTransition.Display {
        final Rig rig;
        ResolutionEngine.Frame frame;
        int failures, writes;
        boolean die, switchUser;
        boolean allowed = true, ownerProcess = true;
        FakeDisplay(Rig rig, int density) {
            this.rig = rig;
            frame = new ResolutionEngine.Frame(1440, 3200, 0, Map.of(0, density));
        }
        public void checkCanChange() { checkMutationProcess(); if (!allowed) throw new SecurityException(); }
        public void checkMutationProcess() { if (!ownerProcess) throw new SecurityException(); }
        public ResolutionEngine.Frame read() {
            return new ResolutionEngine.Frame(frame.width, frame.height, frame.scaling, frame.densities);
        }
        public void apply(ResolutionEngine.Frame target) throws Exception {
            check(!rig.pu, "WM write while PU enabled");
            writes++; rig.events.add("WM");
            frame = new ResolutionEngine.Frame(target.width, target.height, target.scaling, frame.densities);
            if (die) { die = false; throw new ProcessDeath(); }
            if (failures > 0) { failures--; throw new IOException("Density binder failure"); }
            frame = target;
            if (switchUser) { switchUser = false; allowed = false; }
        }
        public void flushTransactions() {
            rig.events.add("FLUSH");
            if (rig.failAfterFlush) { rig.failAfterFlush = false; rig.failBarrier = 1; }
        }
    }
    private static final class MemoryStore implements ResolutionEngine.Store {
        byte[] bytes;
        boolean failPending, failCommit;
        public ResolutionEngine.State read() throws IOException {
            return bytes == null ? ResolutionEngine.State.empty()
                    : StateCodec.read(new ByteArrayInputStream(bytes));
        }
        public void write(ResolutionEngine.State state) throws IOException {
            if ((state.pending != null && failPending) || (state.pending == null && failCommit)) {
                failPending = false; failCommit = false; throw new IOException("Journal failed");
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            StateCodec.write(out, state); bytes = out.toByteArray();
        }
    }
    private static final class Clock implements ResolutionEngine.Clock {
        long now = 1000; int boot = 1;
        public long elapsedRealtime() { return now; }
        public int bootCount() { return boot; }
    }
    private static final class Alarm implements ResolutionEngine.Watchdog {
        boolean armed, fail;
        public void schedule(String token, long deadline) throws Exception {
            if (fail) { fail = false; throw new IOException("Alarm unavailable"); }
            armed = true;
        }
        public void cancel() { armed = false; }
    }
}

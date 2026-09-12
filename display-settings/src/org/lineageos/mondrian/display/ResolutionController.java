/* Copyright (C) 2026 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.lineageos.mondrian.display;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.AtomicFile;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class ResolutionController {
    static final String TAG = "MondrianResolution";
    static final String ACTION_ROLLBACK = "org.lineageos.mondrian.display.ROLLBACK";
    static final Uri SUMMARY_URI = Uri.parse(
            "content://org.lineageos.mondrian.display/get_dynamic_summary/mondrian_resolution");
    private static ResolutionController sInstance;

    interface Work<T> { T run() throws Exception; }
    interface Callback<T> { void complete(T result, Exception error); }

    final DisplayBackend backend;
    final ResolutionEngine engine;
    private final Context mContext;
    private final Handler mMain = new Handler(Looper.getMainLooper());
    private final ExecutorService mWorker = Executors.newSingleThreadExecutor();

    static synchronized ResolutionController get(Context context) {
        if (sInstance == null) sInstance = new ResolutionController(context);
        return sInstance;
    }

    private ResolutionController(Context context) {
        mContext = context.getApplicationContext().createDeviceProtectedStorageContext();
        backend = new DisplayBackend(mContext);
        engine = new ResolutionEngine(backend, new Journal(mContext),
                new ResolutionEngine.Clock() {
                    public long elapsedRealtime() { return SystemClock.elapsedRealtime(); }
                    public int bootCount() {
                        return Settings.Global.getInt(mContext.getContentResolver(),
                                Settings.Global.BOOT_COUNT, -1);
                    }
                }, new AlarmWatchdog(mContext));
        if (UserHandle.myUserId() == UserHandle.USER_SYSTEM) {
            // A17 sends USER_SWITCHED with FLAG_RECEIVER_REGISTERED_ONLY.
            IntentFilter users = new IntentFilter();
            users.addAction(Intent.ACTION_USER_SWITCHED);
            users.addAction(Intent.ACTION_USER_ADDED);
            mContext.registerReceiver(new ResolutionReceiver(), users,
                    Context.RECEIVER_NOT_EXPORTED);
        }
    }

    <T> void execute(Work<T> work, Callback<T> callback) {
        mWorker.execute(() -> {
            T result = null;
            Exception error = null;
            try {
                result = work.run();
            } catch (Exception e) {
                error = e;
                Log.e(TAG, "Resolution operation failed", e);
            }
            try {
                mContext.getContentResolver().notifyChange(SUMMARY_URI, null);
            } catch (RuntimeException e) {
                Log.w(TAG, "Unable to notify the Settings summary", e);
            }
            T value = result;
            Exception failure = error;
            mMain.post(() -> callback.complete(value, failure));
        });
    }

    private static final class AlarmWatchdog implements ResolutionEngine.Watchdog {
        private final Context mContext;
        private final AlarmManager mAlarms;

        AlarmWatchdog(Context context) {
            mContext = context;
            mAlarms = context.getSystemService(AlarmManager.class);
        }

        private PendingIntent intent(int flags) {
            return PendingIntent.getBroadcast(mContext, 0,
                    new Intent(mContext, ResolutionReceiver.class).setAction(ACTION_ROLLBACK),
                    flags | PendingIntent.FLAG_IMMUTABLE);
        }

        public void schedule(String token, long deadline) {
            // The receiver checks the durable deadline. Even a previously queued alarm is safe.
            mAlarms.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    deadline, intent(PendingIntent.FLAG_UPDATE_CURRENT));
        }

        public void cancel() {
            PendingIntent alarm = intent(PendingIntent.FLAG_NO_CREATE);
            if (alarm != null) mAlarms.cancel(alarm);
        }
    }

    private static final class Journal implements ResolutionEngine.Store {
        private final AtomicFile mFile;

        Journal(Context context) {
            mFile = new AtomicFile(new File(context.getFilesDir(), "resolution-state.bin"));
        }

        public ResolutionEngine.State read() throws IOException {
            try (FileInputStream stream = mFile.openRead()) {
                return StateCodec.read(stream);
            } catch (FileNotFoundException e) {
                if (mFile.getBaseFile().exists()) throw e;
                return ResolutionEngine.State.empty();
            }
        }

        public void write(ResolutionEngine.State state) throws IOException {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            StateCodec.write(bytes, state);
            byte[] data = bytes.toByteArray();
            FileOutputStream stream = null;
            try {
                stream = mFile.startWrite();
                stream.write(data);
                stream.getFD().sync();
                mFile.finishWrite(stream);
                stream = null;
                if (!Arrays.equals(data, mFile.readFully())) {
                    throw new IOException("Resolution journal could not be committed");
                }
            } catch (IOException e) {
                if (stream != null) mFile.failWrite(stream);
                throw e;
            }
        }
    }
}

package com.silentui;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class LogBus {
    private static final String TAG = "SilentUI";
    private static final int MAX = 400;
    private static final List<String> lines = new ArrayList<>();
    private static final List<Runnable> listeners = new ArrayList<>();
    private static final SimpleDateFormat FMT =
            new SimpleDateFormat("HH:mm:ss.SSS", Locale.US);
    private static File logFile;

    private LogBus() {}

    public static synchronized void init(Context c) {
        try {
            logFile = new File(c.getFilesDir(), "silentui.log");
        } catch (Throwable ignored) {
        }
    }

    public static synchronized void log(String msg) {
        String line = "[" + FMT.format(new Date()) + "] " + msg;
        lines.add(line);
        while (lines.size() > MAX) {
            lines.remove(0);
        }
        Log.d(TAG, msg);
        if (logFile != null) {
            try {
                FileWriter fw = new FileWriter(logFile, true);
                fw.write(line + "\n");
                fw.close();
            } catch (Throwable ignored) {
            }
        }
        List<Runnable> copy = new ArrayList<>(listeners);
        for (Runnable r : copy) {
            try {
                r.run();
            } catch (Throwable ignored) {
            }
        }
    }

    public static synchronized List<String> snapshot() {
        return new ArrayList<>(lines);
    }

    public static synchronized void addListener(Runnable r) {
        if (!listeners.contains(r)) {
            listeners.add(r);
        }
    }

    public static synchronized void removeListener(Runnable r) {
        listeners.remove(r);
    }
}

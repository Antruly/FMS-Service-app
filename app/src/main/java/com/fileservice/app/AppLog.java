package com.fileservice.app;

import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class AppLog {
    private static final String TAG = "FileService";
    private static final List<String> buffer = new ArrayList<>();
    private static File logFile;

    public static void init() {
        try {
            File dir = new File(Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOCUMENTS), "FileService");
            dir.mkdirs();
            logFile = new File(dir, "app_log.txt");
            i("AppLog", "=== App started " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date()) + " ===");
        } catch (Exception e) {
            Log.e(TAG, "Log init failed", e);
        }
    }

    public static void i(String tag, String msg) {
        String line = now() + " [" + tag + "] " + msg;
        Log.i(TAG, line);
        synchronized (buffer) {
            buffer.add(line);
            if (buffer.size() > 500) buffer.remove(0);
        }
    }

    public static void e(String tag, String msg, Throwable t) {
        String line = now() + " [" + tag + "] ERROR: " + msg;
        if (t != null) line += " | " + t.toString();
        Log.e(TAG, line);
        synchronized (buffer) {
            buffer.add(line);
            if (buffer.size() > 500) buffer.remove(0);
        }
    }

    public static String export() {
        try {
            if (logFile != null) {
                FileWriter fw = new FileWriter(logFile, true);
                PrintWriter pw = new PrintWriter(fw);
                synchronized (buffer) {
                    for (String line : buffer) {
                        pw.println(line);
                    }
                }
                pw.close();
                return logFile.getAbsolutePath();
            }
        } catch (Exception e) {
            Log.e(TAG, "Export failed", e);
        }
        return null;
    }

    public static String getRecent() {
        StringBuilder sb = new StringBuilder();
        synchronized (buffer) {
            int start = Math.max(0, buffer.size() - 100);
            for (int i = start; i < buffer.size(); i++) {
                sb.append(buffer.get(i)).append("\n");
            }
        }
        return sb.toString();
    }

    private static String now() {
        return new SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(new Date());
    }
}

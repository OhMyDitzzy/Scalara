package id.ditzzy.scalara.app;

import android.content.Context;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

/**
 * In-memory log store for the app's lifetime.
 *
 * Entries live only in RAM for as long as the process does: they are never
 * written to disk during normal operation, so there's nothing to clear when
 * the app is closed, force-stopped, or killed by the system — a new process
 * simply starts with an empty log. Every entry is also mirrored to Logcat
 * via {@link Log} so normal adb/Android Studio log viewing keeps working.
 *
 * The one exception is {@link #writeDumpToCrashFile}, used only by
 * {@link CrashHandler} to hand the log to the separate-process crash dialog
 * ({@code CrashReportActivity}) — that file is deleted as soon as it's read.
 *
 * Thread-safe: crashes can be reported from any thread, not just the one
 * that caused them.
 */
public final class InternalLogger {

    /** Caps memory use for long-running sessions; oldest entries drop first. */
    private static final int MAX_ENTRIES = 500;

    private static final String CRASH_LOG_FILE_NAME = "crash_log_dump.txt";

    public enum Level { DEBUG, INFO, WARN, ERROR }

    public static final class Entry {
        public final long timestampMillis;
        public final Level level;
        public final String tag;
        public final String message;
        public final String throwableStackTrace;

        private Entry(long timestampMillis, Level level, String tag, String message, String throwableStackTrace) {
            this.timestampMillis = timestampMillis;
            this.level = level;
            this.tag = tag;
            this.message = message;
            this.throwableStackTrace = throwableStackTrace;
        }

        public String format() {
            SimpleDateFormat fmt = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US);
            StringBuilder sb = new StringBuilder()
                    .append(fmt.format(timestampMillis))
                    .append(" ")
                    .append(level)
                    .append("/")
                    .append(tag)
                    .append(": ")
                    .append(message);
            if (throwableStackTrace != null) {
                sb.append("\n").append(throwableStackTrace);
            }
            return sb.toString();
        }
    }

    private static final Deque<Entry> entries = new ArrayDeque<>(MAX_ENTRIES);
    private static final Object lock = new Object();

    private InternalLogger() {
    }

    public static void d(String tag, String message) {
        log(Level.DEBUG, tag, message, null);
    }

    public static void i(String tag, String message) {
        log(Level.INFO, tag, message, null);
    }

    public static void w(String tag, String message) {
        log(Level.WARN, tag, message, null);
    }

    public static void w(String tag, String message, Throwable throwable) {
        log(Level.WARN, tag, message, throwable);
    }

    public static void e(String tag, String message) {
        log(Level.ERROR, tag, message, null);
    }

    public static void e(String tag, String message, Throwable throwable) {
        log(Level.ERROR, tag, message, throwable);
    }

    private static void log(Level level, String tag, String message, Throwable throwable) {
        String stackTrace = throwable != null ? Log.getStackTraceString(throwable) : null;
        Entry entry = new Entry(System.currentTimeMillis(), level, tag, message, stackTrace);

        synchronized (lock) {
            if (entries.size() >= MAX_ENTRIES) {
                entries.removeFirst();
            }
            entries.addLast(entry);
        }

        mirrorToLogcat(level, tag, message, throwable);
    }

    private static void mirrorToLogcat(Level level, String tag, String message, Throwable throwable) {
        switch (level) {
            case DEBUG:
                Log.d(tag, message, throwable);
                break;
            case INFO:
                Log.i(tag, message, throwable);
                break;
            case WARN:
                Log.w(tag, message, throwable);
                break;
            case ERROR:
                Log.e(tag, message, throwable);
                break;
        }
    }

    /** Snapshot of all entries currently held, oldest first. */
    public static List<Entry> getEntries() {
        synchronized (lock) {
            return new ArrayList<>(entries);
        }
    }

    /** Renders the full in-memory log as a single string, oldest first. */
    public static String dump() {
        StringBuilder sb = new StringBuilder();
        for (Entry entry : getEntries()) {
            sb.append(entry.format()).append("\n");
        }
        return sb.toString();
    }

    /**
     * Writes the current dump to a file in the cache dir so the crash dialog,
     * which runs as a separate process ({@code CrashReportActivity}), can read
     * it — in-memory state doesn't cross process boundaries. Called only by
     * {@link CrashHandler} in the instant before the crashing process dies.
     *
     * @return true if the file was written successfully.
     */
    static boolean writeDumpToCrashFile(Context context) {
        File file = new File(context.getCacheDir(), CRASH_LOG_FILE_NAME);
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(dump().getBytes(StandardCharsets.UTF_8));
            return true;
        } catch (IOException e) {
            Log.e("InternalLogger", "Failed to write crash log dump", e);
            return false;
        }
    }

    /**
     * Reads back the crash dump written by {@link #writeDumpToCrashFile}, then
     * deletes the file so a later crash doesn't accidentally show a stale log.
     * Called by {@code CrashReportActivity} in its own process.
     *
     * @return the dumped log text, or null if no crash file was found.
     */
    static String readAndDeleteCrashFile(Context context) {
        File file = new File(context.getCacheDir(), CRASH_LOG_FILE_NAME);
        if (!file.exists()) {
            return null;
        }
        try (FileInputStream in = new FileInputStream(file)) {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[4096];
            int bytesRead;
            while ((bytesRead = in.read(chunk)) != -1) {
                buffer.write(chunk, 0, bytesRead);
            }
            return buffer.toString(StandardCharsets.UTF_8.name());
        } catch (IOException e) {
            Log.e("InternalLogger", "Failed to read crash log dump", e);
            return null;
        } finally {
            //noinspection ResultOfMethodCallIgnored
            file.delete();
        }
    }
}


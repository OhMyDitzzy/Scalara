package id.ditzzy.scalara.app;

import android.app.Application;
import android.os.Build;
import android.os.Process;

import androidx.appcompat.app.AppCompatDelegate;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

import id.ditzzy.scalara.settings.AppSettings;
import id.ditzzy.scalara.settings.ThemeUtils;

public class ScalaraApplication extends Application {

    private static final String TAG = "ScalaraApplication";

    @Override
    public void onCreate() {
        super.onCreate();

        if (isMainProcess()) {
            Thread.setDefaultUncaughtExceptionHandler(
                    new CrashHandler(getApplicationContext())
            );

            // Applied here, before any Activity inflates, so the very first
            // frame already reflects the user's saved theme choice rather
            // than briefly showing the system default and then switching.
            // Locale (LocaleUtils) doesn't need the same treatment:
            // AppCompatDelegate persists/restores it on its own once set,
            // independent of Application.onCreate().
            AppCompatDelegate.setDefaultNightMode(
                    ThemeUtils.toNightMode(new AppSettings(this).getThemeMode())
            );

            InternalLogger.i(
                    TAG,
                    "Main process started — CrashHandler installed"
            );
        } else {
            InternalLogger.i(
                    TAG,
                    "Secondary process started — CrashHandler NOT installed"
            );
        }
    }

    private boolean isMainProcess() {
        String processName;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // Android 9+
            processName = getProcessName();
        } else {
            // Android 7.0 - 8.1
            processName = getProcessNameCompat();
        }

        return getPackageName().equals(processName);
    }

    private String getProcessNameCompat() {
        int pid = Process.myPid();

        try (BufferedReader reader = new BufferedReader(
                new FileReader("/proc/" + pid + "/cmdline")
        )) {
            String processName = reader.readLine();

            if (processName != null) {
                return processName.trim();
            }
        } catch (IOException e) {
            InternalLogger.e(
                    TAG,
                    "Failed to get process name",
                    e
            );
        }

        return null;
    }
}
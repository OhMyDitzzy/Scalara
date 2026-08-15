package id.ditzzy.scalara.app;

import android.app.Application;
import android.os.Build;
import android.os.Process;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

public class ScalaraApplication extends Application {

    private static final String TAG = "ScalaraApplication";

    @Override
    public void onCreate() {
        super.onCreate();

        if (isMainProcess()) {
            Thread.setDefaultUncaughtExceptionHandler(
                    new CrashHandler(getApplicationContext())
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
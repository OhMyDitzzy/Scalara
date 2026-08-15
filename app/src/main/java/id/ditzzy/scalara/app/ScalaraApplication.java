package id.ditzzy.scalara.app;

import android.app.Application;
import android.os.Build;

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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return getPackageName().equals(getProcessName());
        }

        /*
         * Android < 9.
         *
         * For modern projects, this section is usually not necessary,
         * but we still provide it for security purposes..
         */
        return getPackageName().equals(
                android.app.Application.getProcessName()
        );
    }
}
package id.ditzzy.scalara.app;

import android.content.Context;
import android.content.Intent;
import android.os.Process;

final class CrashHandler implements Thread.UncaughtExceptionHandler {

    private static final String TAG = "CrashHandler";

    private final Context appContext;

    CrashHandler(Context appContext) {
        this.appContext = appContext.getApplicationContext();
    }

    @Override
    public void uncaughtException(
            Thread thread,
            Throwable throwable
    ) {

        InternalLogger.e(
                TAG,
                "Uncaught exception on thread \"" +
                        thread.getName() +
                        "\" — app will now close",
                throwable
        );

        InternalLogger.writeDumpToCrashFile(appContext);

        try {
            Intent intent = new Intent(
                    appContext,
                    CrashReportActivity.class
            );

            intent.addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK |
                    Intent.FLAG_ACTIVITY_CLEAR_TASK
            );

            appContext.startActivity(intent);

        } catch (Exception e) {

            InternalLogger.e(
                    TAG,
                    "Failed to launch CrashReportActivity",
                    e
            );
        }
        
        Process.killProcess(Process.myPid());
        System.exit(1);
    }
}
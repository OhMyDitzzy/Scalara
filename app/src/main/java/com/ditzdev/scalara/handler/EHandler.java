package com.ditzdev.scalara.handler;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Looper;
import android.util.Log;
import com.ditzdev.scalara.activity.EActivity;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Date;
import android.icu.text.SimpleDateFormat;
import java.util.Locale;

public class EHandler implements Thread.UncaughtExceptionHandler {

  // Default system uncaught exception handler
  private final Thread.UncaughtExceptionHandler defaultHandler =
      Thread.getDefaultUncaughtExceptionHandler();
  private final Context context;

  private static volatile EHandler instance = null;

  private EHandler(Context ctx) {
    this.context = ctx;
  }

  public static void initialize(Application application) {
    if (instance == null) {
      synchronized (EHandler.class) {
        if (instance == null) {
          instance = new EHandler(application);
          Thread.setDefaultUncaughtExceptionHandler(instance);
        }
      }
    }
  }

  @Override
  public void uncaughtException(Thread thread, Throwable throwable) {
    try {
      String crashLog = generateCrashLog(throwable);
      File crashFile = saveCrashToFile(crashLog);
      context
          .getSharedPreferences("crash_prefs", Context.MODE_PRIVATE)
          .edit()
          .putString("last_crash_file", crashFile.getAbsolutePath())
          .apply();

      new Thread(
              () -> {
                Looper.prepare();

                Intent i = new Intent(context, EActivity.class);
                i.addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                i.putExtra(EActivity.EXTRA_CRASH_INFO, crashLog);

                try {
                  context.startActivity(i);
                } catch (Exception e) {
                  e.printStackTrace();
                }

                Looper.loop();
              })
          .start();

      // Clean up the process so it doesn't pile up
      // In process id.
      Thread.sleep(1000);
      android.os.Process.killProcess(android.os.Process.myPid());
      System.exit(1);
    } catch (Exception err) {
      if (defaultHandler != null) {
        defaultHandler.uncaughtException(thread, throwable);
      }
    }
  }

  private String generateCrashLog(Throwable throwable) {
    StringBuilder sb = new StringBuilder();
    sb.append("Time: ")
        .append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date()))
        .append("\n\n");
    sb.append("========== DEVICE INFORMATION =========\n");
    sb.append("Brand: ").append(Build.BRAND).append("\n");
    sb.append("Device: ").append(Build.DEVICE).append("\n");
    sb.append("Model: ").append(Build.MODEL).append("\n");
    sb.append("Android Version: ").append(Build.VERSION.RELEASE).append("\n");
    sb.append("SDK: ").append(Build.VERSION.SDK_INT).append("\n");
    sb.append("========== END OF DEVICE INFORMATION =========\n\n");
    sb.append("========== STACK TRACE ===============\n\n");
    sb.append(Log.getStackTraceString(throwable));
    sb.append("========== END OF STACK TRACE ========\n\n");
    return sb.toString();
  }

  private File saveCrashToFile(String crashLog) throws IOException {
    String fileName = "crash_" + System.currentTimeMillis() + ".txt";
    File file = new File(context.getExternalFilesDir(null), fileName);
    try (FileOutputStream fos = new FileOutputStream(file)) {
      fos.write(crashLog.getBytes());
    }
    return file;
  }
}

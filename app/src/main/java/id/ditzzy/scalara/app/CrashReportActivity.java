package id.ditzzy.scalara.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.os.Process;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import id.ditzzy.scalara.R;

public class CrashReportActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String rawLogText =
                InternalLogger.readAndDeleteCrashFile(this);

        final String logText =
                (rawLogText == null || rawLogText.isEmpty())
                        ? getString(R.string.crash_dialog_message)
                        : rawLogText;

        TextView logView = new TextView(this);

        logView.setText(logText);
        logView.setTextIsSelectable(true);

        int padding = (int) (
                16 * getResources()
                        .getDisplayMetrics()
                        .density
        );

        logView.setPadding(
                padding,
                padding,
                padding,
                padding
        );

        ScrollView scrollView = new ScrollView(this);
        scrollView.addView(logView);

        new AlertDialog.Builder(this)
                .setTitle(R.string.crash_dialog_title)
                .setView(scrollView)
                .setCancelable(false)

                .setNegativeButton(
                        R.string.crash_dialog_copy_log,
                        (dialog, which) -> {
                            copyAndExit(logText);
                        }
                )

                .setPositiveButton(
                        R.string.crash_dialog_ok,
                        (dialog, which) -> {
                            exitApplication();
                        }
                )

                .show();
    }

    private void copyAndExit(String logText) {

        ClipboardManager clipboard =
                (ClipboardManager)
                        getSystemService(Context.CLIPBOARD_SERVICE);

        if (clipboard != null) {
            clipboard.setPrimaryClip(
                    ClipData.newPlainText(
                            getString(R.string.app_name),
                            logText
                    )
            );
        }

        exitApplication();
    }

    private void exitApplication() {
        finishAndRemoveTask();
        Process.killProcess(Process.myPid());
        System.exit(0);
    }
}
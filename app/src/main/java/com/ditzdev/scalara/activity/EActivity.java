package com.ditzdev.scalara.activity;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import com.ditzdev.scalara.databinding.ActivityEBinding;
import java.io.File;

public class EActivity extends AppCompatActivity {

    private ActivityEBinding binding;
    private String crashInfo;
    public static final String EXTRA_CRASH_INFO = "extra_crash_info";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityEBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        crashInfo = getIntent().getStringExtra(EXTRA_CRASH_INFO);

        binding.tvErrorMessage.setText(crashInfo);
        
        binding.btnRestartApp.setOnClickListener(v -> restartApp());
        binding.btnShareLog.setOnClickListener(v -> shareELog());
    }

    private void restartApp() {
        Intent intent = getPackageManager().getLaunchIntentForPackage(getPackageName());
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        }
        finish();
    }

    private void shareELog() {
        try {
            String eFile =
                    getSharedPreferences("crash_prefs", MODE_PRIVATE)
                            .getString("last_crash_file", null);
            if (eFile != null) {
                File crashFile = new File(eFile);
                if (crashFile.exists()) {
                    Uri fileUri =
                            FileProvider.getUriForFile(
                                    this, getPackageName() + ".fileprovider", crashFile);

                    Intent shareIntent = new Intent(Intent.ACTION_SEND);
                    shareIntent.setType("text/plain");
                    shareIntent.putExtra(Intent.EXTRA_STREAM, fileUri);
                    shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

                    startActivity(Intent.createChooser(shareIntent, "Share Crash Log"));
                }
            }
        } catch (Exception err) {
            err.printStackTrace();
        }
    }
}

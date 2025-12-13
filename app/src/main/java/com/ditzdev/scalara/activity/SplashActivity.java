package com.ditzdev.scalara.activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import androidx.appcompat.app.AppCompatActivity;
import com.ditzdev.scalara.databinding.ActivitySplashBinding;
import com.ditzdev.scalara.utils.GetVersion;
import com.ditzdev.scalara.utils.Perm;

public class SplashActivity extends AppCompatActivity {
    private ActivitySplashBinding binding;
    private static final int SPLASH_DELAY = 2000;
        
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySplashBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        
        var versionName = GetVersion.getAppVersionName(this);
        binding.textVersion.setText(versionName);
        
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            checkPermAndNavi();
        }, SPLASH_DELAY);
    }
    
    private void checkPermAndNavi() {
        boolean hasPerm = Perm.checkSecureSettingsPerm(this);
        Intent intent;
        if (hasPerm) {
            intent = new Intent(this, MainActivity.class);
            startActivity(intent);
            finish();
        } else {
            intent = new Intent(this, SetupActivity.class);
            startActivity(intent);
            finish();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}

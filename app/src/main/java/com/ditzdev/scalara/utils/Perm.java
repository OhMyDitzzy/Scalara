package com.ditzdev.scalara.utils;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import androidx.core.content.ContextCompat;

public class Perm {
    public static boolean checkSecureSettingsPerm(Context ctx) {
        return ContextCompat.checkSelfPermission(ctx, Manifest.permission.WRITE_SECURE_SETTINGS)
                == PackageManager.PERMISSION_GRANTED;
    }
}

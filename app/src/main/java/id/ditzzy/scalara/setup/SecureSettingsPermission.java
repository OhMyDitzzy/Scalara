package id.ditzzy.scalara.setup;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;

import androidx.core.content.ContextCompat;

/**
 * Checks whether {@code android.permission.WRITE_SECURE_SETTINGS} is
 * currently granted to this app.
 *
 * <p>This permission has {@code signature|privileged} protection, so it can
 * never be requested through the normal runtime permission dialog — it can
 * only be granted out-of-band, either once via {@code adb shell pm grant}
 * (see the ADB flow in {@code SetupActivity}) or live through a Shizuku user
 * service (see {@link ShizukuManager}). Either way, once granted it shows up
 * as {@link PackageManager#PERMISSION_GRANTED} through the normal
 * {@link Context#checkSelfPermission} check, so a single utility covers
 * verifying both methods and re-checking the grant on every app resume.
 */
public final class SecureSettingsPermission {

    private SecureSettingsPermission() {
    }

    public static boolean isGranted(Context context) {
        return ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_SECURE_SETTINGS
        ) == PackageManager.PERMISSION_GRANTED;
    }
}

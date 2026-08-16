package id.ditzzy.scalara;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import id.ditzzy.scalara.app.InternalLogger;
import id.ditzzy.scalara.databinding.ActivityMainBinding;
import id.ditzzy.scalara.setup.AppPreferences;
import id.ditzzy.scalara.setup.SecureSettingsPermission;
import id.ditzzy.scalara.setup.SetupActivity;
import id.ditzzy.scalara.setup.ShizukuManager;

/**
 * Placeholder landing screen reached only after the disclaimer has been
 * accepted and permission setup has completed. Its only real job right now
 * is proving that whichever method the wizard finished with (ADB or
 * Shizuku) actually resulted in a working {@code WRITE_SECURE_SETTINGS}
 * grant — Scalara's real resolution-changing UI isn't built yet.
 *
 * <p>Every time this activity resumes, it re-checks that the permission
 * backing the configured method is still active. If it finds the grant is
 * gone — the system revoked it, the user ran {@code pm revoke}, or the
 * Shizuku service was stopped — it shows a non-dismissible dialog and sends
 * the user back into {@link SetupActivity} to reconfigure, since there's
 * nothing useful this activity (or the real app it stands in for) can do
 * without that permission.
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    private ActivityMainBinding binding;
    private AppPreferences preferences;

    /**
     * Only used to observe Shizuku's binder dying while this activity is in
     * the foreground (e.g. the user force-stops Shizuku from Recents). The
     * onResume check below covers the case where it was already dead before
     * returning to the app.
     */
    private ShizukuManager shizukuManager;

    private boolean permissionLostDialogShowing = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        preferences = new AppPreferences(this);

        if (preferences.getSetupMethod() == AppPreferences.SetupMethod.SHIZUKU) {
            shizukuManager = new ShizukuManager(new ShizukuManager.Listener() {
                @Override
                public void onBinderAvailable() {
                    // No action needed: a live binder is a good sign, and
                    // checkCurrentPermissionState() re-verifies on its own
                    // schedule (onResume) rather than reacting to this.
                }

                @Override
                public void onBinderUnavailable() {
                    checkCurrentPermissionState();
                }

                @Override
                public void onPermissionResult(boolean granted) {
                    // MainActivity never requests the Shizuku permission
                    // itself, so this listener only exists to satisfy the
                    // interface; nothing to react to here.
                }
            });
        }

        renderPermissionStatus();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (shizukuManager != null) {
            shizukuManager.attach();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkCurrentPermissionState();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (shizukuManager != null) {
            shizukuManager.detach();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        this.binding = null;
    }

    private void renderPermissionStatus() {
        int statusRes = preferences.getSetupMethod() == AppPreferences.SetupMethod.SHIZUKU
                ? R.string.main_permission_status_shizuku
                : R.string.main_permission_status_adb;
        binding.textSetupMethod.setText(statusRes);
    }

    /**
     * Re-verifies that the permission backing the configured setup method
     * is still active, and surfaces the non-dismissible guard dialog if
     * it's not. Safe to call repeatedly — {@link #permissionLostDialogShowing}
     * prevents stacking multiple dialogs if this fires more than once before
     * the user acts on the first one.
     */
    private void checkCurrentPermissionState() {
        if (permissionLostDialogShowing) {
            return;
        }

        AppPreferences.SetupMethod method = preferences.getSetupMethod();
        boolean permissionActive;

        if (method == AppPreferences.SetupMethod.SHIZUKU) {
            // A Shizuku-managed grant is not necessarily visible through
            // this app's own PackageManager — it lives behind Shizuku's own
            // server process. shizukuManager.isPermissionGranted() is the
            // one that actually calls Shizuku.checkSelfPermission(), the
            // same check used during setup; it also folds in the
            // service-running check, so a dead binder alone is enough to
            // report "not active" without a separate ping here.
            permissionActive = shizukuManager != null && shizukuManager.isPermissionGranted();
        } else {
            // ADB (or, defensively, NONE — treated the same as "not set up").
            permissionActive = SecureSettingsPermission.isGranted(this);
        }

        if (!permissionActive) {
            InternalLogger.w(TAG, "Permission for method " + method + " is no longer active");
            showPermissionLostDialog(method);
        }
    }

    private void showPermissionLostDialog(AppPreferences.SetupMethod method) {
        permissionLostDialogShowing = true;

        int bodyRes = method == AppPreferences.SetupMethod.SHIZUKU
                ? R.string.permission_lost_body_shizuku
                : R.string.permission_lost_body_adb;

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.permission_lost_title)
                .setMessage(bodyRes)
                .setCancelable(false)
                .setPositiveButton(R.string.permission_lost_button_setup, (dialog, which) -> goToSetup())
                .show();
    }

    private void goToSetup() {
        preferences.resetSetupState();
        startActivity(new Intent(this, SetupActivity.class));
        finish();
    }
}

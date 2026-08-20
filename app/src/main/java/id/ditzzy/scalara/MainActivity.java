package id.ditzzy.scalara;

import android.content.ContentResolver;
import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import id.ditzzy.scalara.about.AboutActivity;
import id.ditzzy.scalara.app.InternalLogger;
import id.ditzzy.scalara.databinding.ActivityMainBinding;
import id.ditzzy.scalara.main.AddPresetSheet;
import id.ditzzy.scalara.main.ApplyResolutionRequest;
import id.ditzzy.scalara.main.MainViewModel;
import id.ditzzy.scalara.main.PresetAdapter;
import id.ditzzy.scalara.main.PresetOptionsSheet;
import id.ditzzy.scalara.presets.ExportImportSheet;
import id.ditzzy.scalara.presets.ResolutionPreset;
import id.ditzzy.scalara.resolution.DeviceResolution;
import id.ditzzy.scalara.resolution.DisplayResolutionController;
import id.ditzzy.scalara.resolution.PreviewRevertService;
import id.ditzzy.scalara.settings.AppSettings;
import id.ditzzy.scalara.settings.SettingsActivity;
import id.ditzzy.scalara.setup.AppPreferences;
import id.ditzzy.scalara.setup.SecureSettingsPermission;
import id.ditzzy.scalara.setup.SetupActivity;
import id.ditzzy.scalara.setup.ShizukuManager;

/**
 * Scalara's main screen: shows the device's default resolution, the user's
 * saved presets, and lets the user add, apply, preview, and delete presets.
 *
 * <p>Every time this activity resumes, it re-checks that the permission
 * backing the configured setup method (ADB or Shizuku) is still active. If
 * it finds the grant is gone — the system revoked it, the user ran
 * {@code pm revoke}, or the Shizuku service was stopped — it shows a
 * non-dismissible dialog and sends the user back into {@link SetupActivity}
 * to reconfigure, since none of this screen's resolution-changing actions
 * can succeed without that permission. This guard predates and is
 * independent of the preset/resolution features below; it protects them
 * rather than the other way around.
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    private ActivityMainBinding binding;
    private AppPreferences preferences;
    private MainViewModel viewModel;
    private PresetAdapter presetAdapter;

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
        viewModel = new ViewModelProvider(this).get(MainViewModel.class);

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
        setUpToolbar();
        setUpPresetsList();
        setUpClickListeners();
        observeViewModel();
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

    // ================================================================
    // Permission guard
    // ================================================================

    private void renderPermissionStatus() {
        int statusRes = preferences.getSetupMethod() == AppPreferences.SetupMethod.SHIZUKU
                ? R.string.main_permission_status_shizuku
                : R.string.main_permission_status_adb;
        binding.textSetupMethod.setText(statusRes);
    }

    /**
     * Re-verifies that {@code WRITE_SECURE_SETTINGS} is still granted, and
     * surfaces the non-dismissible guard dialog if it's not. Safe to call
     * repeatedly — {@link #permissionLostDialogShowing} prevents stacking
     * multiple dialogs if this fires more than once before the user acts on
     * the first one.
     *
     * <p>This checks {@link SecureSettingsPermission} regardless of setup
     * method. Shizuku is only ever used as a one-time grantor during setup
     * (see {@link id.ditzzy.scalara.setup.ShizukuManager#grantSecureSettings});
     * once it runs {@code pm grant}, the resulting permission is tracked by
     * the system's own {@link android.content.pm.PackageManager}, the same
     * place {@link id.ditzzy.scalara.resolution.DisplayResolutionController}
     * checks before every resolution/DPI write. Stopping the Shizuku service
     * or revoking Scalara's Shizuku authorization afterward does not affect
     * this grant, so it must not be treated as a loss of permission here.
     */
    private void checkCurrentPermissionState() {
        if (permissionLostDialogShowing) {
            return;
        }

        AppPreferences.SetupMethod method = preferences.getSetupMethod();
        boolean permissionActive = SecureSettingsPermission.isGranted(this);

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

    // ================================================================
    // Toolbar overflow menu
    // ================================================================

    private void setUpToolbar() {
        binding.toolbar.setOnMenuItemClickListener(this::onMenuItemClicked);
    }

    private boolean onMenuItemClicked(MenuItem item) {
        int itemId = item.getItemId();

        if (itemId == R.id.menu_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }

        if (itemId == R.id.menu_about) {
            startActivity(new Intent(this, AboutActivity.class));
            return true;
        }

        if (itemId == R.id.menu_export_import_presets) {
            ExportImportSheet.show(getSupportFragmentManager());
            return true;
        }

        return false;
    }

    // ================================================================
    // Presets list
    // ================================================================

    private void setUpPresetsList() {
        presetAdapter = new PresetAdapter(
                this::onPresetRowClicked,
                this::onPresetOverflowClicked
        );
        binding.recyclerPresets.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerPresets.setAdapter(presetAdapter);
    }

    private void onPresetRowClicked(ResolutionPreset preset) {
        PresetOptionsSheet.show(getSupportFragmentManager(), preset);
    }

    private void onPresetOverflowClicked(ResolutionPreset preset, View anchor) {
        PresetOptionsSheet.show(getSupportFragmentManager(), preset);
    }

    // ================================================================
    // Click listeners
    // ================================================================

    private void setUpClickListeners() {
        binding.fabAddPreset.setOnClickListener(v -> AddPresetSheet.show(getSupportFragmentManager()));
        binding.buttonResetDefault.setOnClickListener(v -> viewModel.resetToDefault());
    }

    // ================================================================
    // ViewModel observation
    // ================================================================

    private void observeViewModel() {
        viewModel.getDefaultResolution().observe(this, this::renderDefaultResolution);

        viewModel.getPresets().observe(this, presets -> {
            presetAdapter.submitList(presets);
            boolean isEmpty = presets == null || presets.isEmpty();
            binding.recyclerPresets.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
            binding.layoutPresetsEmpty.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        });

        viewModel.getApplyRequestEvent().observe(this, event -> {
            ApplyResolutionRequest request = event != null ? event.consume() : null;
            if (request != null) {
                onApplyResolutionRequested(request);
            }
        });

        viewModel.getMessageEvent().observe(this, event -> {
            Integer messageRes = event != null ? event.consume() : null;
            if (messageRes != null) {
                showMessage(messageRes);
            }
        });
    }

    private void renderDefaultResolution(DeviceResolution resolution) {
        if (resolution == null) {
            return;
        }
        binding.textDefaultResolutionValue.setText(
                getString(R.string.default_resolution_size_format, resolution.getWidth(), resolution.getHeight())
        );
        binding.textDefaultDpiValue.setText(
                getString(R.string.default_resolution_dpi_format, resolution.getDpi())
        );
    }

    /**
     * Carries out a resolution change requested by {@link MainViewModel}.
     * This is the one place in the presets feature that actually touches
     * {@link DisplayResolutionController} / starts {@link PreviewRevertService}
     * — see {@link MainViewModel}'s class doc for why that responsibility
     * lives here rather than in the ViewModel.
     */
    private void onApplyResolutionRequested(ApplyResolutionRequest request) {
        if (request.getMode() == ApplyResolutionRequest.Mode.PREVIEW) {
            long timeoutMillis = new AppSettings(this).getPreviewTimeoutMillis();
            PreviewRevertService.start(this, request.getWidth(), request.getHeight(), request.getDpi(), timeoutMillis);
            return;
        }

        try {
            ContentResolver contentResolver = getContentResolver();
            DisplayResolutionController controller = new DisplayResolutionController(contentResolver);
            controller.setResolution(request.getWidth(), request.getHeight());
            controller.setDisplayDensity(request.getDpi());
            showMessage(R.string.message_apply_success);
        } catch (ReflectiveOperationException | RuntimeException e) {
            InternalLogger.e(TAG, "Failed to apply resolution", e);
            showMessage(R.string.message_apply_failed);
        }
    }

    private void showMessage(int stringRes) {
        if (binding != null) {
            Snackbar.make(binding.getRoot(), stringRes, Snackbar.LENGTH_SHORT).show();
        }
    }
}
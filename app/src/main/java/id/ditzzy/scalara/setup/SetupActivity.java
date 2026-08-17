package id.ditzzy.scalara.setup;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import id.ditzzy.scalara.MainActivity;
import id.ditzzy.scalara.R;
import id.ditzzy.scalara.app.InternalLogger;
import id.ditzzy.scalara.databinding.ActivitySetupBinding;

public class SetupActivity extends AppCompatActivity implements ShizukuManager.Listener {

    private static final String TAG = "SetupActivity";
    private static final String ADB_GRANT_COMMAND =
            "adb shell pm grant id.ditzzy.scalara android.permission.WRITE_SECURE_SETTINGS";
    private static final String SHIZUKU_PLAY_STORE_URL =
            "https://play.google.com/store/apps/details?id=moe.shizuku.privileged.api";

    private enum Step {
        CHOOSE_METHOD,
        METHOD_DETAIL,
        DONE
    }

    private enum PrimaryAction {
        GO_TO_METHOD_DETAIL,
        VERIFY_ADB_PERMISSION,
        REQUEST_SHIZUKU_PERMISSION,
        GRANT_SECURE_SETTINGS,
        OPEN_SHIZUKU_FOR_MANUAL_GRANT,
        WAITING_ON_SHIZUKU_SERVICE,
        FINISH_WIZARD
    }

    private ActivitySetupBinding binding;
    private AppPreferences preferences;
    private ShizukuManager shizukuManager;

    private Step currentStep = Step.CHOOSE_METHOD;

    private AppPreferences.SetupMethod selectedMethod = AppPreferences.SetupMethod.NONE;

    private PrimaryAction currentPrimaryAction = PrimaryAction.GO_TO_METHOD_DETAIL;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySetupBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        preferences = new AppPreferences(this);
        shizukuManager = new ShizukuManager(this);
        binding.textAdbCommand.setText(ADB_GRANT_COMMAND);
        wireClickListeners();
        renderStep();
    }

    @Override
    protected void onStart() {
        super.onStart();
        shizukuManager.attach();
    }

    @Override
    protected void onResume() {
        super.onResume();
        /*
         * ADB can be granted when the application is in the background.
         * So when user return to the activity, check again.
         */
        if (currentStep == Step.METHOD_DETAIL && selectedMethod == AppPreferences.SetupMethod.ADB) {
            refreshAdbStatus();
        }

        /*
         * For Shizuku, the Shizuku listener handles permission changes..
         * But doing a refresh here also helps when the user returns.
         * from Shizuku app.
         */
        if (currentStep == Step.METHOD_DETAIL
                && selectedMethod == AppPreferences.SetupMethod.SHIZUKU) {
            refreshShizukuStatus();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();

        shizukuManager.detach();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        binding = null;
    }

    private void wireClickListeners() {
        binding.cardAdb.setOnClickListener(v -> selectMethod(AppPreferences.SetupMethod.ADB));

        binding.cardShizuku.setOnClickListener(
                v -> selectMethod(AppPreferences.SetupMethod.SHIZUKU));

        binding.buttonCopyCommand.setOnClickListener(v -> copyAdbCommand());

        binding.buttonGetShizuku.setOnClickListener(v -> openShizukuListing());

        binding.buttonBack.setOnClickListener(v -> onBackPressedInWizard());

        binding.buttonPrimaryAction.setOnClickListener(v -> onPrimaryAction());
    }

    private void selectMethod(AppPreferences.SetupMethod method) {

        selectedMethod = method;

        boolean adbSelected = method == AppPreferences.SetupMethod.ADB;

        binding.cardAdb.setChecked(adbSelected);
        binding.radioAdb.setChecked(adbSelected);

        boolean shizukuSelected = method == AppPreferences.SetupMethod.SHIZUKU;

        binding.cardShizuku.setChecked(shizukuSelected);
        binding.radioShizuku.setChecked(shizukuSelected);

        setPrimaryAction(PrimaryAction.GO_TO_METHOD_DETAIL, R.string.setup_button_next, true);
    }

    private void onPrimaryAction() {
        switch (currentPrimaryAction) {
            case GO_TO_METHOD_DETAIL:
                currentStep = Step.METHOD_DETAIL;
                renderStep();
                break;
            case VERIFY_ADB_PERMISSION:
                refreshAdbStatus();
                break;
            case REQUEST_SHIZUKU_PERMISSION:
                if (shizukuManager.isPermissionGranted()) {
                    grantSecureSettingsViaShizuku();
                } else {
                    shizukuManager.requestPermission();
                }
                break;
            case GRANT_SECURE_SETTINGS:
                grantSecureSettingsViaShizuku();
                break;
            case OPEN_SHIZUKU_FOR_MANUAL_GRANT:
                openShizukuListing();
                break;
            case WAITING_ON_SHIZUKU_SERVICE:
                break;
            case FINISH_WIZARD:
                finishWizardAndLaunchMain();
                break;
        }
    }

    private void onBackPressedInWizard() {
        switch (currentStep) {
            case CHOOSE_METHOD:
                break;
            case METHOD_DETAIL:
                currentStep = Step.CHOOSE_METHOD;
                renderStep();
                break;
            case DONE:
                currentStep = Step.METHOD_DETAIL;
                renderStep();
                break;
        }
    }

    private void renderStep() {
        if (binding == null) {
            return;
        }

        binding.stepChoose.setVisibility(
                currentStep == Step.CHOOSE_METHOD ? View.VISIBLE : View.GONE);

        binding.stepAdb.setVisibility(
                currentStep == Step.METHOD_DETAIL
                                && selectedMethod == AppPreferences.SetupMethod.ADB
                        ? View.VISIBLE
                        : View.GONE);

        binding.stepShizuku.setVisibility(
                currentStep == Step.METHOD_DETAIL
                                && selectedMethod == AppPreferences.SetupMethod.SHIZUKU
                        ? View.VISIBLE
                        : View.GONE);

        binding.stepDone.setVisibility(currentStep == Step.DONE ? View.VISIBLE : View.GONE);

        binding.buttonBack.setVisibility(
                currentStep == Step.CHOOSE_METHOD ? View.INVISIBLE : View.VISIBLE);

        binding.stepProgress.setProgress(stepOrdinalForProgress());

        binding.scrollView.scrollTo(0, 0);
        switch (currentStep) {
            case CHOOSE_METHOD:
                setPrimaryAction(
                        PrimaryAction.GO_TO_METHOD_DETAIL,
                        R.string.setup_button_next,
                        selectedMethod != AppPreferences.SetupMethod.NONE);
                break;
            case METHOD_DETAIL:
                if (selectedMethod == AppPreferences.SetupMethod.ADB) {
                    refreshAdbStatus();
                } else if (selectedMethod == AppPreferences.SetupMethod.SHIZUKU) {

                    refreshShizukuStatus();
                }
                break;
            case DONE:
                setPrimaryAction(PrimaryAction.FINISH_WIZARD, R.string.setup_button_finish, true);
                binding.textDoneBody.setText(
                        selectedMethod == AppPreferences.SetupMethod.ADB
                                ? R.string.setup_done_body_adb
                                : R.string.setup_done_body_shizuku);

                break;
        }
    }

    private int stepOrdinalForProgress() {
        switch (currentStep) {
            case CHOOSE_METHOD:
                return 1;
            case METHOD_DETAIL:
                return 2;
            case DONE:
            default:
                return 3;
        }
    }

    private void setPrimaryAction(PrimaryAction action, int labelRes, boolean enabled) {

        if (binding == null) {
            return;
        }

        currentPrimaryAction = action;

        binding.buttonPrimaryAction.setText(labelRes);
        binding.buttonPrimaryAction.setEnabled(enabled);
    }

    private void copyAdbCommand() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.app_name), ADB_GRANT_COMMAND));
            Toast.makeText(this, R.string.setup_adb_command_copied, Toast.LENGTH_SHORT).show();
        }
    }

    private void refreshAdbStatus() {
        if (binding == null) {
            return;
        }

        boolean granted = SecureSettingsPermission.isGranted(this);

        if (granted) {
            currentStep = Step.DONE;
            renderStep();

            return;
        }

        binding.textAdbStatus.setVisibility(View.VISIBLE);
        setPrimaryAction(
                PrimaryAction.VERIFY_ADB_PERMISSION, R.string.setup_button_verify_permission, true);
    }

    private void openShizukuListing() {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(SHIZUKU_PLAY_STORE_URL)));
        } catch (Exception e) {
            InternalLogger.w(TAG, "No activity available to open Shizuku listing", e);

            Toast.makeText(this, SHIZUKU_PLAY_STORE_URL, Toast.LENGTH_LONG).show();
        }
    }

    private void refreshShizukuStatus() {
        if (binding == null) {
            return;
        }

        if (currentStep != Step.METHOD_DETAIL
                || selectedMethod != AppPreferences.SetupMethod.SHIZUKU) {
            return;
        }

        boolean serviceRunning = shizukuManager.isServiceRunning();

        if (!serviceRunning) {
            binding.iconShizukuStatus.setImageResource(R.drawable.ic_info_outline);

            binding.textShizukuStatus.setText(R.string.setup_shizuku_status_not_running);

            setPrimaryAction(
                    PrimaryAction.WAITING_ON_SHIZUKU_SERVICE,
                    R.string.setup_button_request_permission,
                    false);

            return;
        }

        boolean shizukuPermission = shizukuManager.isPermissionGranted();

        if (!shizukuPermission) {

            binding.iconShizukuStatus.setImageResource(R.drawable.ic_info_outline);

            binding.textShizukuStatus.setText(R.string.setup_shizuku_status_running_no_permission);

            setPrimaryAction(
                    PrimaryAction.REQUEST_SHIZUKU_PERMISSION,
                    R.string.setup_button_request_permission,
                    true);

            return;
        }

        boolean secureSettingsGranted = SecureSettingsPermission.isGranted(this);

        if (secureSettingsGranted) {

            binding.iconShizukuStatus.setImageResource(R.drawable.ic_check_circle);

            binding.textShizukuStatus.setText(R.string.setup_shizuku_status_granted);

            currentStep = Step.DONE;
            renderStep();

            return;
        }

        binding.iconShizukuStatus.setImageResource(R.drawable.ic_info_outline);

        binding.textShizukuStatus.setText(R.string.setup_shizuku_status_running_no_permission);

        setPrimaryAction(
                PrimaryAction.GRANT_SECURE_SETTINGS,
                R.string.setup_button_request_permission,
                true);
    }

    private void grantSecureSettingsViaShizuku() {
        if (binding == null) {
            return;
        }

        if (!shizukuManager.isServiceRunning()) {
            Toast.makeText(this, "Shizuku is not running", Toast.LENGTH_SHORT).show();

            refreshShizukuStatus();
            return;
        }

        if (!shizukuManager.isPermissionGranted()) {
            Toast.makeText(this, "Shizuku permission has not been granted", Toast.LENGTH_SHORT)
                    .show();

            refreshShizukuStatus();
            return;
        }

        setPrimaryAction(
                PrimaryAction.WAITING_ON_SHIZUKU_SERVICE,
                R.string.setup_button_request_permission,
                false);

        binding.iconShizukuStatus.setImageResource(R.drawable.ic_info_outline);

        shizukuManager.grantSecureSettings(
                this,
                (success, result) -> {
                    runOnUiThread(
                            () -> {
                                if (binding == null || isFinishing() || isDestroyed()) {
                                    return;
                                }

                                InternalLogger.i(TAG, "WRITE_SECURE_SETTINGS result: " + result);

                                binding.getRoot().postDelayed(this::verifyShizukuGrant, 300);
                            });
                });
    }

    private void verifyShizukuGrant() {
        if (binding == null || isFinishing() || isDestroyed()) {

            return;
        }

        boolean granted = SecureSettingsPermission.isGranted(this);

        InternalLogger.i(TAG, "WRITE_SECURE_SETTINGS actually granted: " + granted);

        if (granted) {
            binding.iconShizukuStatus.setImageResource(R.drawable.ic_check_circle);

            binding.textShizukuStatus.setText(R.string.setup_shizuku_status_granted);

            currentStep = Step.DONE;
            renderStep();

            return;
        }

        binding.iconShizukuStatus.setImageResource(R.drawable.ic_info_outline);

        binding.textShizukuStatus.setText(R.string.setup_shizuku_status_running_no_permission);

        setPrimaryAction(
                PrimaryAction.GRANT_SECURE_SETTINGS,
                R.string.setup_button_request_permission,
                true);

        Toast.makeText(this, "WRITE_SECURE_SETTINGS gagal diberikan", Toast.LENGTH_LONG).show();
    }

    private void finishWizardAndLaunchMain() {
        preferences.setSetupMethod(selectedMethod);

        preferences.setSetupCompleted(true);

        startActivity(new Intent(this, MainActivity.class));

        finish();
    }

    @Override
    public void onBinderAvailable() {
        if (currentStep == Step.METHOD_DETAIL
                && selectedMethod == AppPreferences.SetupMethod.SHIZUKU) {
            refreshShizukuStatus();
        }
    }

    @Override
    public void onBinderUnavailable() {
        if (currentStep == Step.METHOD_DETAIL
                && selectedMethod == AppPreferences.SetupMethod.SHIZUKU) {
            refreshShizukuStatus();
        }
    }

    @Override
    public void onPermissionResult(boolean granted) {
        if (!granted) {
            refreshShizukuStatus();
            return;
        }

        if (currentStep == Step.METHOD_DETAIL
                && selectedMethod == AppPreferences.SetupMethod.SHIZUKU) {
            grantSecureSettingsViaShizuku();
        } else {
            refreshShizukuStatus();
        }
    }
}

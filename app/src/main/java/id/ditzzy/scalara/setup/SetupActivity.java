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

/**
 * Permission setup wizard. Walks the user through granting
 * {@code WRITE_SECURE_SETTINGS} using whichever of the two supported methods
 * they pick:
 *
 * <ul>
 *   <li><b>ADB</b> — purely instructional. The app shows the exact command
 *       to run from a computer and only re-checks
 *       {@link SecureSettingsPermission#isGranted} when the user taps
 *       "Verify permission". Once verified, the method is recorded as
 *       {@link AppPreferences.SetupMethod#ADB} and Scalara never touches
 *       Shizuku for the rest of this install (unless the user redoes setup
 *       and picks Shizuku instead).</li>
 *   <li><b>Shizuku</b> — live integration via {@link ShizukuManager}. The
 *       wizard reflects the binder/permission state as it changes and lets
 *       the user trigger the Shizuku permission prompt directly.</li>
 * </ul>
 *
 * <p>The single bottom button ({@code binding.buttonPrimaryAction}) does
 * different things depending on where the wizard is — advance to the next
 * step, re-check a permission, trigger the Shizuku prompt, open the Shizuku
 * listing, or finish. Rather than infer the button's current meaning from
 * step + selected method + live state (fragile, easy to desync from what's
 * actually on screen), {@link #currentPrimaryAction} is set explicitly
 * everywhere the button's label is set, and {@link #onPrimaryAction()}
 * switches on that single field.
 */
public class SetupActivity extends AppCompatActivity implements ShizukuManager.Listener {

    private static final String TAG = "SetupActivity";

    private static final String ADB_GRANT_COMMAND =
            "adb shell pm grant id.ditzzy.scalara android.permission.WRITE_SECURE_SETTINGS";

    private static final String SHIZUKU_PLAY_STORE_URL =
            "https://play.google.com/store/apps/details?id=moe.shizuku.privileged.api";

    /** The wizard's linear steps. Only one is visible at a time. */
    private enum Step {
        CHOOSE_METHOD,
        METHOD_DETAIL,
        DONE
    }

    /** What tapping {@code binding.buttonPrimaryAction} currently does. */
    private enum PrimaryAction {
        GO_TO_METHOD_DETAIL,
        VERIFY_ADB_PERMISSION,
        REQUEST_SHIZUKU_PERMISSION,
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
        // Covers the user granting the ADB permission while this screen was
        // backgrounded (switched to a terminal app, ran the command, came
        // back); Shizuku's own state changes arrive via its listeners
        // instead, which stay registered from onStart to onStop.
        if (currentStep == Step.METHOD_DETAIL && selectedMethod == AppPreferences.SetupMethod.ADB) {
            refreshAdbStatus();
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
        this.binding = null;
    }

    private void wireClickListeners() {
        binding.cardAdb.setOnClickListener(v -> selectMethod(AppPreferences.SetupMethod.ADB));
        binding.cardShizuku.setOnClickListener(v -> selectMethod(AppPreferences.SetupMethod.SHIZUKU));

        binding.buttonCopyCommand.setOnClickListener(v -> copyAdbCommand());
        binding.buttonGetShizuku.setOnClickListener(v -> openShizukuListing());

        binding.buttonBack.setOnClickListener(v -> onBackPressedInWizard());
        binding.buttonPrimaryAction.setOnClickListener(v -> onPrimaryAction());
    }

    // ================================================================
    // Step 1: choose method
    // ================================================================

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

    // ================================================================
    // Step navigation
    // ================================================================

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
                shizukuManager.requestPermission();
                break;
            case OPEN_SHIZUKU_FOR_MANUAL_GRANT:
                openShizukuListing();
                break;
            case WAITING_ON_SHIZUKU_SERVICE:
                // Button is disabled in this state; nothing to do if
                // somehow invoked anyway (e.g. a queued click).
                break;
            case FINISH_WIZARD:
                finishWizardAndLaunchMain();
                break;
        }
    }

    private void onBackPressedInWizard() {
        switch (currentStep) {
            case CHOOSE_METHOD:
                // Nothing before this step within the wizard itself.
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
        binding.stepChoose.setVisibility(currentStep == Step.CHOOSE_METHOD ? View.VISIBLE : View.GONE);
        binding.stepAdb.setVisibility(
                currentStep == Step.METHOD_DETAIL && selectedMethod == AppPreferences.SetupMethod.ADB
                        ? View.VISIBLE : View.GONE
        );
        binding.stepShizuku.setVisibility(
                currentStep == Step.METHOD_DETAIL && selectedMethod == AppPreferences.SetupMethod.SHIZUKU
                        ? View.VISIBLE : View.GONE
        );
        binding.stepDone.setVisibility(currentStep == Step.DONE ? View.VISIBLE : View.GONE);

        binding.buttonBack.setVisibility(currentStep == Step.CHOOSE_METHOD ? View.INVISIBLE : View.VISIBLE);
        binding.stepProgress.setProgress(stepOrdinalForProgress());

        binding.scrollView.scrollTo(0, 0);

        switch (currentStep) {
            case CHOOSE_METHOD:
                setPrimaryAction(
                        PrimaryAction.GO_TO_METHOD_DETAIL,
                        R.string.setup_button_next,
                        selectedMethod != AppPreferences.SetupMethod.NONE
                );
                break;
            case METHOD_DETAIL:
                if (selectedMethod == AppPreferences.SetupMethod.ADB) {
                    refreshAdbStatus();
                } else {
                    refreshShizukuStatus();
                }
                break;
            case DONE:
                setPrimaryAction(PrimaryAction.FINISH_WIZARD, R.string.setup_button_finish, true);
                binding.textDoneBody.setText(
                        selectedMethod == AppPreferences.SetupMethod.ADB
                                ? R.string.setup_done_body_adb
                                : R.string.setup_done_body_shizuku
                );
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
        currentPrimaryAction = action;
        binding.buttonPrimaryAction.setText(labelRes);
        binding.buttonPrimaryAction.setEnabled(enabled);
    }

    // ================================================================
    // Step 2a: ADB detail
    // ================================================================

    private void copyAdbCommand() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.app_name), ADB_GRANT_COMMAND));
            Toast.makeText(this, R.string.setup_adb_command_copied, Toast.LENGTH_SHORT).show();
        }
    }

    /** Re-checks the ADB-granted permission and advances to DONE if found. */
    private void refreshAdbStatus() {
        boolean granted = SecureSettingsPermission.isGranted(this);

        if (granted) {
            currentStep = Step.DONE;
            renderStep();
            return;
        }

        binding.textAdbStatus.setVisibility(View.VISIBLE);
        setPrimaryAction(PrimaryAction.VERIFY_ADB_PERMISSION, R.string.setup_button_verify_permission, true);
    }

    // ================================================================
    // Step 2b: Shizuku detail
    // ================================================================

    private void openShizukuListing() {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(SHIZUKU_PLAY_STORE_URL)));
        } catch (Exception e) {
            InternalLogger.w(TAG, "No activity available to open Shizuku listing", e);
            Toast.makeText(this, SHIZUKU_PLAY_STORE_URL, Toast.LENGTH_LONG).show();
        }
    }

    /** Re-evaluates Shizuku binder/permission state and updates the status card + button. */
    private void refreshShizukuStatus() {
        if (binding == null) {
            // Defensive: detach() is called in onStop() so this shouldn't
            // normally fire after onDestroy(), but a callback already
            // in-flight at the moment of detach could still land here.
            return;
        }
        if (currentStep != Step.METHOD_DETAIL || selectedMethod != AppPreferences.SetupMethod.SHIZUKU) {
            return;
        }

        boolean serviceRunning = shizukuManager.isServiceRunning();
        boolean permissionGranted = serviceRunning && shizukuManager.isPermissionGranted();
        boolean permanentlyDenied = serviceRunning && !permissionGranted
                && shizukuManager.isPermissionPermanentlyDenied();

        if (permissionGranted) {
            binding.iconShizukuStatus.setImageResource(R.drawable.ic_check_circle);
            binding.textShizukuStatus.setText(R.string.setup_shizuku_status_granted);
            currentStep = Step.DONE;
            renderStep();
            return;
        }

        if (!serviceRunning) {
            binding.iconShizukuStatus.setImageResource(R.drawable.ic_info_outline);
            binding.textShizukuStatus.setText(R.string.setup_shizuku_status_not_running);
            setPrimaryAction(PrimaryAction.WAITING_ON_SHIZUKU_SERVICE, R.string.setup_button_request_permission, false);
        } else if (permanentlyDenied) {
            binding.iconShizukuStatus.setImageResource(R.drawable.ic_info_outline);
            binding.textShizukuStatus.setText(R.string.setup_shizuku_status_denied_permanently);
            setPrimaryAction(PrimaryAction.OPEN_SHIZUKU_FOR_MANUAL_GRANT, R.string.setup_button_open_shizuku, true);
        } else {
            binding.iconShizukuStatus.setImageResource(R.drawable.ic_info_outline);
            binding.textShizukuStatus.setText(R.string.setup_shizuku_status_running_no_permission);
            setPrimaryAction(PrimaryAction.REQUEST_SHIZUKU_PERMISSION, R.string.setup_button_request_permission, true);
        }
    }

    // ================================================================
    // Completion
    // ================================================================

    private void finishWizardAndLaunchMain() {
        preferences.setSetupMethod(selectedMethod);
        preferences.setSetupCompleted(true);
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }

    // ================================================================
    // ShizukuManager.Listener
    // ================================================================

    @Override
    public void onBinderAvailable() {
        refreshShizukuStatus();
    }

    @Override
    public void onBinderUnavailable() {
        refreshShizukuStatus();
    }

    @Override
    public void onPermissionResult(boolean granted) {
        refreshShizukuStatus();
    }
}

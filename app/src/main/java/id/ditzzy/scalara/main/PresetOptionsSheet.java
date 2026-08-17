package id.ditzzy.scalara.main;

import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Arrays;
import java.util.Locale;

import id.ditzzy.scalara.R;
import id.ditzzy.scalara.app.InternalLogger;
import id.ditzzy.scalara.databinding.SheetPresetOptionsBinding;
import id.ditzzy.scalara.presets.CryptoProgressDialog;
import id.ditzzy.scalara.presets.ExportImportManager;
import id.ditzzy.scalara.presets.PasswordPromptDialog;
import id.ditzzy.scalara.presets.PresetRepository;
import id.ditzzy.scalara.presets.ResolutionPreset;
import id.ditzzy.scalara.presets.ResultDialog;

/**
 * Per-preset options reached from that preset's overflow (three-dot) button:
 * Apply, Delete, and Export.
 *
 * <p>There's deliberately no per-preset Import here, unlike Export: importing
 * a single preset back into the list that already has it doesn't have the
 * same one-off shape Export does. App-level import (replacing or merging the
 * *entire* saved list from a {@code .scl} file) lives in
 * {@code MainActivity}'s overflow menu instead, backed by the same
 * {@link ExportImportManager} this sheet uses for Export.
 *
 * <p>Which preset this sheet is showing options for is passed via
 * {@link #ARG_PRESET_ID} rather than holding a {@link ResolutionPreset}
 * reference directly, since a raw object reference wouldn't survive this
 * fragment being recreated (e.g. after a configuration change while showing)
 * — looking it up fresh from {@link PresetRepository} each time also means
 * this sheet can't act on stale data if the preset was edited or deleted
 * elsewhere in between.
 *
 * <p>{@link #onExportClicked}'s {@link ExportImportManager} call runs on a
 * background thread and delivers its result via callback (see that class's
 * doc) — the {@code binding != null} check at the top of that callback
 * guards against it firing after {@link #onDestroyView} has already run
 * (e.g. the user backed out of this sheet while the export was still in
 * flight); without it, touching {@code binding} or calling
 * {@code requireView()} on a destroyed fragment's view would throw.
 */
public class PresetOptionsSheet extends BottomSheetDialogFragment {

    private static final String TAG = "PresetOptionsSheet";
    private static final String ARG_PRESET_ID = "arg_preset_id";

    private SheetPresetOptionsBinding binding;
    private MainViewModel viewModel;
    private PresetRepository presetRepository;
    private ExportImportManager exportImportManager;

    /**
     * Set together by {@link #onExportClicked} and consumed together by
     * {@link #onExportDestinationChosen}. Held as fields (rather than
     * passed directly) because {@link ActivityResultContracts.CreateDocument}'s
     * launch and result callback are necessarily two separate methods —
     * there's no way to hand the already-collected preset/password straight
     * from one to the other.
     */
    @Nullable
    private ResolutionPreset pendingExportPreset;
    @Nullable
    private char[] pendingExportPassword;

    /** Shown for the duration of the background export call; dismissed in that call's callback. */
    @Nullable
    private AlertDialog progressDialog;

    // Registered as a field initializer (runs before onAttach, per
    // ActivityResultRegistry's documented contract) rather than lazily
    // inside onExportClicked(), since registerForActivityResult() must be
    // called unconditionally on every instance before STARTED regardless of
    // whether that instance ever actually triggers an export.
    private final ActivityResultLauncher<String> createDocumentLauncher = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("application/octet-stream"),
            this::onExportDestinationChosen
    );

    public static void show(@NonNull FragmentManager fragmentManager, @NonNull ResolutionPreset preset) {
        PresetOptionsSheet sheet = new PresetOptionsSheet();
        Bundle args = new Bundle();
        args.putString(ARG_PRESET_ID, preset.getId());
        sheet.setArguments(args);
        sheet.show(fragmentManager, TAG);
    }

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState
    ) {
        binding = SheetPresetOptionsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);
        presetRepository = new PresetRepository(requireContext());
        exportImportManager = new ExportImportManager(requireContext().getContentResolver(), presetRepository);

        ResolutionPreset preset = resolvePreset();
        if (preset == null) {
            // The preset was deleted elsewhere (e.g. from another open
            // sheet instance) between this sheet being shown and its view
            // being created: nothing sensible to offer options for.
            dismiss();
            return;
        }

        binding.textOptionsPresetName.setText(preset.getName());
        binding.optionApply.setOnClickListener(v -> onApplyClicked(preset));
        binding.optionDelete.setOnClickListener(v -> onDeleteClicked(preset));
        binding.optionExport.setOnClickListener(v -> onExportClicked(preset));
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        dismissProgressDialog();
        // Interrupts the export if one is still running and drops it if
        // it's still queued, so its callback can't fire into this
        // now-destroyed view. The password array's wipe still happens
        // either way — see ExportImportManager's doc — since that wipe
        // lives in the background task's own finally block, not in a
        // callback this shutdown could prevent from running.
        exportImportManager.shutdown();
        binding = null;
    }

    @Nullable
    private ResolutionPreset resolvePreset() {
        String presetId = requireArguments().getString(ARG_PRESET_ID);
        if (presetId == null) {
            return null;
        }
        for (ResolutionPreset preset : presetRepository.getAll()) {
            if (preset.getId().equals(presetId)) {
                return preset;
            }
        }
        return null;
    }

    private void onApplyClicked(@NonNull ResolutionPreset preset) {
        viewModel.applyExistingPreset(preset);
        dismiss();
    }

    private void onDeleteClicked(@NonNull ResolutionPreset preset) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.preset_options_delete_confirm_title)
                .setMessage(getString(R.string.preset_options_delete_confirm_message, preset.getName()))
                .setPositiveButton(R.string.preset_options_delete_confirm_button, (dialog, which) -> {
                    viewModel.deletePreset(preset);
                    dismiss();
                })
                .setNegativeButton(R.string.preset_options_cancel_button, null)
                .show();
    }

    private void onExportClicked(@NonNull ResolutionPreset preset) {
        // The password is collected *before* the SAF picker rather than
        // after, so the destination-choosing UI (which the user is already
        // primed to interact with) isn't interrupted by a second, unrelated
        // dialog in between — by the time CreateDocument launches, all this
        // callback has left to do is encrypt and write.
        PasswordPromptDialog.showForNewPassword(
                requireContext(),
                R.string.export_password_dialog_message,
                password -> {
                    pendingExportPreset = preset;
                    pendingExportPassword = password;
                    createDocumentLauncher.launch(suggestedFileName(preset));
                }
        );
    }

    private void onExportDestinationChosen(@Nullable Uri destination) {
        ResolutionPreset preset = pendingExportPreset;
        char[] password = pendingExportPassword;
        pendingExportPreset = null;
        pendingExportPassword = null;

        if (destination == null || preset == null || password == null) {
            // User backed out of the SAF picker without choosing a
            // destination: not an error, just nothing left to do. Any
            // password already collected is discarded (wiped below) rather
            // than kept around for a future retry, since holding
            // decrypted-key material in memory for longer than one attempt
            // needs is an unnecessary risk for the rare case of a user
            // retrying export moments later. Nothing was queued onto
            // ExportImportManager in this branch, so this call site still
            // owns the wipe — unlike the success path below, where
            // ExportImportManager takes ownership of it instead (see that
            // class's doc).
            if (password != null) {
                Arrays.fill(password, '\0');
            }
            return;
        }

        progressDialog = CryptoProgressDialog.show(requireContext(), R.string.crypto_progress_exporting);
        exportImportManager.submitExportSingle(destination, preset, password, new ExportImportManager.ExportCallback() {
            @Override
            public void onSuccess() {
                if (binding == null) {
                    return;
                }
                dismissProgressDialog();
                ResultDialog.showSuccess(
                        requireContext(), R.string.result_dialog_export_success_title, R.string.message_export_success
                );
                dismiss();
            }

            @Override
            public void onFailure(@NonNull Exception error) {
                if (binding == null) {
                    return;
                }
                // GeneralSecurityException here would mean the platform's
                // own AES/GCM or PBKDF2 provider is missing or broken — not
                // something a wrong password could cause on the encrypt
                // path (there's no existing ciphertext to authenticate
                // against yet), so it's reported the same generic way as a
                // file-write failure rather than as a wrong-password
                // message.
                InternalLogger.e(TAG, "Failed to export preset " + preset.getId(), error);
                dismissProgressDialog();
                ResultDialog.showFailure(
                        requireContext(), R.string.result_dialog_export_failed_title, R.string.message_export_failed
                );
            }
        });
    }

    private void dismissProgressDialog() {
        if (progressDialog != null) {
            progressDialog.dismiss();
            progressDialog = null;
        }
    }

    @NonNull
    private static String suggestedFileName(@NonNull ResolutionPreset preset) {
        String sanitized = preset.getName().replaceAll("[^a-zA-Z0-9-_ ]", "").trim();
        if (sanitized.isEmpty()) {
            sanitized = "preset";
        }
        return String.format(Locale.ROOT, "%s.scl", sanitized);
    }
}

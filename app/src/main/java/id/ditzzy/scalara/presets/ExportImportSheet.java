package id.ditzzy.scalara.presets;

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
import com.google.android.material.snackbar.Snackbar;

import java.util.Arrays;
import java.util.List;

import id.ditzzy.scalara.R;
import id.ditzzy.scalara.app.InternalLogger;
import id.ditzzy.scalara.databinding.DialogImportChoiceBinding;
import id.ditzzy.scalara.databinding.SheetExportImportPresetsBinding;
import id.ditzzy.scalara.main.MainViewModel;

/**
 * App-level Export/Import entry point, reached from {@code MainActivity}'s
 * overflow menu — this is what replaced the old {@code menu_import_presets}
 * item once {@code .scl} encryption made a real implementation possible.
 *
 * <p>Distinct from the per-preset Export option on
 * {@link PresetOptionsSheet}: that one hands off a single preset, while
 * {@link #onExportAllClicked} here covers the user's whole saved list. Only
 * this sheet supports import — see {@link PresetOptionsSheet}'s class doc
 * for why a per-preset import was removed rather than kept alongside this.
 *
 * <p>Every {@link ExportImportManager} call this sheet makes runs on a
 * background thread and delivers its result via callback (see that class's
 * doc) — the {@code binding != null} check at the top of every one of those
 * callbacks below guards against the callback firing after
 * {@link #onDestroyView} has already run (e.g. the user backed out of this
 * sheet while a background export/import was still in flight); without it,
 * touching {@code binding} or calling {@code requireView()} on a destroyed
 * fragment's view would throw.
 */
public class ExportImportSheet extends BottomSheetDialogFragment {

    private static final String TAG = "ExportImportSheet";
    private static final String EXPORT_FILE_NAME = "scalara_presets.scl";

    private SheetExportImportPresetsBinding binding;
    private ExportImportManager exportImportManager;
    private MainViewModel viewModel;

    /** Set by {@link #onExportAllClicked} before launching {@link #createDocumentLauncher}; consumed by {@link #onExportDestinationChosen}. */
    @Nullable
    private char[] pendingExportPassword;

    /** Set by {@link #onImportClicked} before launching {@link #openDocumentLauncher}; consumed by {@link #onImportSourceChosen}. */
    @Nullable
    private Uri pendingImportSource;

    /** Shown for the duration of a background export/import call; dismissed in that call's callback. */
    @Nullable
    private AlertDialog progressDialog;

    private final ActivityResultLauncher<String> createDocumentLauncher = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("application/octet-stream"),
            this::onExportDestinationChosen
    );

    private final ActivityResultLauncher<String[]> openDocumentLauncher = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(),
            this::onImportSourceChosen
    );

    public static void show(@NonNull FragmentManager fragmentManager) {
        new ExportImportSheet().show(fragmentManager, TAG);
    }

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState
    ) {
        binding = SheetExportImportPresetsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        exportImportManager = new ExportImportManager(
                requireContext().getContentResolver(),
                new PresetRepository(requireContext())
        );
        // requireActivity() rather than `this`: resolves to the same
        // MainViewModel instance MainActivity itself holds, so
        // reloadPresets() below (see onImportApplied) republishes the exact
        // LiveData MainActivity's preset list is observing.
        viewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);

        binding.optionExportAll.setOnClickListener(v -> onExportAllClicked());
        binding.optionImport.setOnClickListener(v -> onImportClicked());
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        dismissProgressDialog();
        // Interrupts any export/import still running and drops anything
        // still queued, so a callback can't fire into this now-destroyed
        // view. The password-array wipe for whatever call was in flight
        // still happens either way — see ExportImportManager's doc — since
        // that wipe lives in the background task's own finally block, not
        // in a callback this shutdown could prevent from running.
        exportImportManager.shutdown();
        binding = null;
    }

    private void onExportAllClicked() {
        // Same ordering rationale as PresetOptionsSheet.onExportClicked:
        // password first, so the SAF destination picker isn't interrupted
        // by a second dialog once the user is already engaged with it.
        PasswordPromptDialog.showForNewPassword(
                requireContext(),
                R.string.export_password_dialog_message,
                password -> {
                    pendingExportPassword = password;
                    createDocumentLauncher.launch(EXPORT_FILE_NAME);
                }
        );
    }

    private void onExportDestinationChosen(@Nullable Uri destination) {
        char[] password = pendingExportPassword;
        pendingExportPassword = null;

        if (destination == null || password == null) {
            // User backed out of the SAF picker: nothing was queued onto
            // ExportImportManager, so (unlike every other early-return in
            // this file) this is the one spot still responsible for wiping
            // an already-collected password itself.
            if (password != null) {
                Arrays.fill(password, '\0');
            }
            return;
        }

        progressDialog = CryptoProgressDialog.show(requireContext(), R.string.crypto_progress_exporting);
        exportImportManager.submitExportAll(destination, password, new ExportImportManager.ExportCallback() {
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
                InternalLogger.e(TAG, "Failed to export all presets", error);
                dismissProgressDialog();
                ResultDialog.showFailure(
                        requireContext(), R.string.result_dialog_export_failed_title, R.string.message_export_failed
                );
            }
        });
    }

    private void onImportClicked() {
        // Deliberately "*/*" rather than a specific MIME type: a .scl file's
        // reported MIME type depends entirely on whatever provider it
        // arrived through (email attachment, cloud sync, USB transfer), and
        // narrowing this filter risks a validly-exported file simply not
        // being selectable if some provider along the way tagged it
        // differently than expected. PresetCrypto.decrypt's magic-byte
        // check (see PresetCrypto.InvalidFileException) is what actually
        // validates the file is a real .scl export, once one is chosen.
        openDocumentLauncher.launch(new String[]{"*/*"});
    }

    private void onImportSourceChosen(@Nullable Uri source) {
        if (source == null) {
            return;
        }
        pendingImportSource = source;

        // Unlike export, the password here is collected *after* the file is
        // already chosen: OpenDocument's picker doesn't need a filename
        // typed in beforehand the way CreateDocument's does, so there's no
        // equivalent "already engaged with a dialog" moment to protect —
        // asking for the password only once a file is actually selected
        // also means a user who cancels the picker was never asked for one
        // at all.
        PasswordPromptDialog.showForExistingPassword(
                requireContext(),
                R.string.import_password_dialog_message,
                this::onImportPasswordEntered
        );
    }

    private void onImportPasswordEntered(@NonNull char[] password) {
        Uri source = pendingImportSource;
        pendingImportSource = null;

        if (source == null) {
            // Same reasoning as the equivalent guard in
            // onExportDestinationChosen: nothing was queued onto
            // ExportImportManager, so this call site still owns the wipe.
            Arrays.fill(password, '\0');
            return;
        }

        progressDialog = CryptoProgressDialog.show(requireContext(), R.string.crypto_progress_importing);
        exportImportManager.submitReadImportCandidate(source, password, new ExportImportManager.ReadImportCallback() {
            @Override
            public void onSuccess(@NonNull List<ResolutionPreset> candidates) {
                if (binding == null) {
                    return;
                }
                dismissProgressDialog();
                if (candidates.isEmpty()) {
                    Snackbar.make(requireView(), R.string.message_import_empty_file, Snackbar.LENGTH_SHORT).show();
                    return;
                }
                showImportChoiceDialog(candidates);
            }

            @Override
            public void onWrongPassword() {
                if (binding == null) {
                    return;
                }
                dismissProgressDialog();
                Snackbar.make(requireView(), R.string.message_import_wrong_password, Snackbar.LENGTH_SHORT).show();
            }

            @Override
            public void onInvalidFile() {
                if (binding == null) {
                    return;
                }
                InternalLogger.w(TAG, "Selected file is not a valid .scl export");
                dismissProgressDialog();
                Snackbar.make(requireView(), R.string.message_import_invalid_file, Snackbar.LENGTH_SHORT).show();
            }

            @Override
            public void onFailure(@NonNull Exception error) {
                if (binding == null) {
                    return;
                }
                InternalLogger.e(TAG, "Failed to read import source", error);
                dismissProgressDialog();
                ResultDialog.showFailure(
                        requireContext(), R.string.result_dialog_import_failed_title, R.string.message_import_failed
                );
            }
        });
    }

    /**
     * Shows what was found and lets the user pick
     * {@link ExportImportManager.ImportMode#MERGE} or
     * {@link ExportImportManager.ImportMode#REPLACE} before anything is
     * written — decrypting and parsing the file (in
     * {@link #onImportPasswordEntered}) doesn't touch
     * {@link PresetRepository} on its own, precisely so this confirmation
     * can happen first for the destructive REPLACE option.
     */
    private void showImportChoiceDialog(@NonNull List<ResolutionPreset> candidates) {
        DialogImportChoiceBinding choiceBinding = DialogImportChoiceBinding.inflate(LayoutInflater.from(requireContext()));
        choiceBinding.textImportFoundCount.setText(
                getResources().getQuantityString(
                        R.plurals.import_choice_found_count, candidates.size(), candidates.size()
                )
        );

        // Same manual card<->radio pairing pattern as SetupActivity's
        // ADB/Shizuku cards: these two options are mutually exclusive but
        // presented as full cards rather than bare radio buttons, so a
        // plain RadioGroup (which only wraps RadioButton children directly)
        // doesn't apply here.
        final ExportImportManager.ImportMode[] selectedMode = {ExportImportManager.ImportMode.MERGE};
        choiceBinding.cardImportMerge.setOnClickListener(v -> {
            selectedMode[0] = ExportImportManager.ImportMode.MERGE;
            choiceBinding.cardImportMerge.setChecked(true);
            choiceBinding.radioImportMerge.setChecked(true);
            choiceBinding.cardImportReplace.setChecked(false);
            choiceBinding.radioImportReplace.setChecked(false);
        });
        choiceBinding.cardImportReplace.setOnClickListener(v -> {
            selectedMode[0] = ExportImportManager.ImportMode.REPLACE;
            choiceBinding.cardImportReplace.setChecked(true);
            choiceBinding.radioImportReplace.setChecked(true);
            choiceBinding.cardImportMerge.setChecked(false);
            choiceBinding.radioImportMerge.setChecked(false);
        });

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.import_choice_dialog_title)
                .setView(choiceBinding.getRoot())
                .setPositiveButton(R.string.import_choice_confirm_button, (dialog, which) -> onImportChoiceConfirmed(candidates, selectedMode[0]))
                .setNegativeButton(R.string.preset_options_cancel_button, null)
                .show();
    }

    private void onImportChoiceConfirmed(
            @NonNull List<ResolutionPreset> candidates, @NonNull ExportImportManager.ImportMode mode
    ) {
        progressDialog = CryptoProgressDialog.show(requireContext(), R.string.crypto_progress_importing);
        exportImportManager.submitApplyImport(candidates, mode, () -> {
            if (binding == null) {
                return;
            }
            dismissProgressDialog();
            // The fix for presets not appearing until an app restart:
            // submitApplyImport only persists to PresetRepository, it has
            // no reference to MainViewModel to update on its own (see that
            // method's doc) — this call is what republishes MainViewModel's
            // presets LiveData so MainActivity's list reflects the import
            // immediately, the same way savePreset/deletePreset already do
            // for their own mutations.
            viewModel.reloadPresets();
            ResultDialog.showSuccess(
                    requireContext(), R.string.result_dialog_import_success_title, R.string.message_import_success
            );
            dismiss();
        });
    }

    private void dismissProgressDialog() {
        if (progressDialog != null) {
            progressDialog.dismiss();
            progressDialog = null;
        }
    }
}

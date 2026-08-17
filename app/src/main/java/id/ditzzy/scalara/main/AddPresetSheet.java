package id.ditzzy.scalara.main;

import android.app.Dialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputLayout;

import id.ditzzy.scalara.R;
import id.ditzzy.scalara.databinding.SheetAddPresetBinding;
import id.ditzzy.scalara.resolution.DeviceResolution;
import id.ditzzy.scalara.resolution.PreviewRevertService;
import id.ditzzy.scalara.resolution.ResolutionUtils;

/**
 * The "Add preset" form: collects a name, width, height, and DPI, and offers
 * four independent actions per the app's design —
 * <ul>
 *     <li><b>Apply</b>: save the preset and apply it permanently right away</li>
 *     <li><b>Try it out</b>: apply it as a timed preview only, without saving
 *     anything; the sheet stays open and reflects the countdown live</li>
 *     <li><b>Save</b>: save the preset without applying anything</li>
 *     <li><b>Cancel</b>: dismiss without saving or applying</li>
 * </ul>
 *
 * <p>This fragment owns form validation and the danger-threshold confirmation
 * (via {@link ResolutionUtils#isResolutionDangerous}), but never calls
 * {@code DisplayResolutionController} or {@code PreviewRevertService} to
 * actually change the resolution — those side effects go through
 * {@link MainViewModel}, shared with the hosting {@code MainActivity} via
 * {@link ViewModelProvider}, so the Activity remains the single place that
 * carries out {@link MainViewModel#getApplyRequestEvent()}.
 */
public class AddPresetSheet extends BottomSheetDialogFragment {

    private static final String TAG = "AddPresetSheet";

    private SheetAddPresetBinding binding;
    private MainViewModel viewModel;

    public static void show(@NonNull FragmentManager fragmentManager) {
        new AddPresetSheet().show(fragmentManager, TAG);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        BottomSheetDialog dialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);
        // Expand fully by default: four buttons plus four inputs rarely fit
        // in the collapsed half-height state, and this avoids the sheet
        // opening looking cut off.
        dialog.setOnShowListener(dialogInterface -> {
            View sheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (sheet != null) {
                BottomSheetBehavior.from(sheet).setState(BottomSheetBehavior.STATE_EXPANDED);
            }
        });
        return dialog;
    }

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState
    ) {
        binding = SheetAddPresetBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Shared with MainActivity's own instance: both resolve to the same
        // MainViewModel because the ViewModelStoreOwner here is the host
        // Activity, not this fragment.
        viewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);

        binding.buttonPresetCancel.setOnClickListener(v -> dismiss());
        binding.buttonPresetApply.setOnClickListener(v -> onApplyClicked());
        binding.buttonPresetTryOut.setOnClickListener(v -> onTryOutClicked());
        binding.buttonPresetSave.setOnClickListener(v -> onSaveClicked());
        binding.buttonRevertPreviewNow.setOnClickListener(v -> PreviewRevertService.revertNow(requireContext()));

        viewModel.getPreviewSecondsRemaining().observe(getViewLifecycleOwner(), this::onPreviewSecondsChanged);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    // ================================================================
    // Actions
    // ================================================================

    private void onApplyClicked() {
        ParsedForm form = validateAndParse();
        if (form == null) {
            return;
        }
        confirmIfDangerous(form, () -> {
            viewModel.savePresetAndApply(form.name, form.width, form.height, form.dpi);
            dismiss();
        });
    }

    private void onTryOutClicked() {
        ParsedForm form = validateAndParse();
        if (form == null) {
            return;
        }
        confirmIfDangerous(form, () -> viewModel.previewOnly(form.width, form.height, form.dpi));
    }

    private void onSaveClicked() {
        ParsedForm form = validateAndParse();
        if (form == null) {
            return;
        }
        viewModel.savePreset(form.name, form.width, form.height, form.dpi);
        dismiss();
    }

    /**
     * Warns the user before applying/previewing a resolution that deviates
     * sharply from the device default, since that's exactly the kind of
     * change that can make the screen hard to use until reset. "Save" alone
     * skips this since it never touches the live display.
     */
    private void confirmIfDangerous(@NonNull ParsedForm form, @NonNull Runnable onConfirmed) {
        DeviceResolution defaultResolution = viewModel.getDefaultResolution().getValue();
        DeviceResolution candidate = new DeviceResolution(form.width, form.height, form.dpi);

        boolean isDangerous = defaultResolution != null
                && ResolutionUtils.isResolutionDangerous(defaultResolution, candidate);

        if (!isDangerous) {
            onConfirmed.run();
            return;
        }

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.add_preset_dangerous_resolution_title)
                .setMessage(getString(
                        R.string.add_preset_dangerous_resolution_message,
                        form.width, form.height, form.dpi
                ))
                .setPositiveButton(R.string.add_preset_dangerous_resolution_continue,
                        (dialogInterface, which) -> onConfirmed.run())
                .setNegativeButton(R.string.preset_options_cancel_button, null)
                .show();
    }

    // ================================================================
    // Preview countdown banner
    // ================================================================

    private void onPreviewSecondsChanged(@Nullable Integer secondsRemaining) {
        if (binding == null) {
            return;
        }
        boolean previewActive = secondsRemaining != null;
        binding.cardPreviewCountdown.setVisibility(previewActive ? View.VISIBLE : View.GONE);

        // Disable the actions that would start another change while one is
        // already in flight, rather than letting a second countdown or
        // apply race the first.
        binding.buttonPresetApply.setEnabled(!previewActive);
        binding.buttonPresetTryOut.setEnabled(!previewActive);

        if (previewActive) {
            binding.textPreviewCountdown.setText(
                    getString(R.string.add_preset_preview_countdown_format, secondsRemaining)
            );
        }
    }

    // ================================================================
    // Validation
    // ================================================================

    private static final class ParsedForm {
        final String name;
        final int width;
        final int height;
        final int dpi;

        ParsedForm(String name, int width, int height, int dpi) {
            this.name = name;
            this.width = width;
            this.height = height;
            this.dpi = dpi;
        }
    }

    /**
     * Validates every field, surfacing the first problem found as an inline
     * {@code TextInputLayout} error. Returns the parsed values only if the
     * whole form is valid.
     */
    @Nullable
    private ParsedForm validateAndParse() {
        binding.inputLayoutPresetName.setError(null);
        binding.inputLayoutPresetWidth.setError(null);
        binding.inputLayoutPresetHeight.setError(null);
        binding.inputLayoutPresetDpi.setError(null);

        String name = binding.inputPresetName.getText() != null
                ? binding.inputPresetName.getText().toString().trim() : "";
        if (TextUtils.isEmpty(name)) {
            binding.inputLayoutPresetName.setError(getString(R.string.add_preset_error_name_required));
            return null;
        }

        Integer width = parsePositiveInt(binding.inputLayoutPresetWidth, binding.inputPresetWidth.getText());
        if (width == null) {
            return null;
        }
        Integer height = parsePositiveInt(binding.inputLayoutPresetHeight, binding.inputPresetHeight.getText());
        if (height == null) {
            return null;
        }
        Integer dpi = parsePositiveInt(binding.inputLayoutPresetDpi, binding.inputPresetDpi.getText());
        if (dpi == null) {
            return null;
        }

        return new ParsedForm(name, width, height, dpi);
    }

    @Nullable
    private Integer parsePositiveInt(TextInputLayout inputLayout, @Nullable Editable text) {
        String raw = text != null ? text.toString().trim() : "";
        if (TextUtils.isEmpty(raw)) {
            inputLayout.setError(getString(R.string.add_preset_error_field_required));
            return null;
        }

        int value;
        try {
            value = Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            inputLayout.setError(getString(R.string.add_preset_error_field_required));
            return null;
        }

        if (value <= 0) {
            inputLayout.setError(getString(R.string.add_preset_error_value_too_low));
            return null;
        }

        return value;
    }
}

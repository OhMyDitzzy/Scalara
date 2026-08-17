package id.ditzzy.scalara.presets;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;

import java.util.Arrays;

import id.ditzzy.scalara.R;
import id.ditzzy.scalara.databinding.DialogPasswordPromptBinding;

/**
 * Prompts for the password used to encrypt (export) or decrypt (import) a
 * {@code .scl} file.
 *
 * <p>Export uses {@link #showForNewPassword}, which asks for the password
 * twice: {@link PresetCrypto}'s encryption can't be reversed without the
 * exact password, so a typo here — unlike a typo in almost anything else
 * this app does — would make the exported file permanently unreadable
 * rather than just wrong.
 *
 * <p>Import uses {@link #showForExistingPassword}, a single field, since
 * getting it wrong just means trying again against a file that's still
 * intact either way.
 */
public final class PasswordPromptDialog {

    /** Callback for a confirmed password, already validated as non-empty (and matching, for the new-password flow). */
    public interface OnPasswordConfirmed {
        void onConfirmed(@NonNull char[] password);
    }

    private PasswordPromptDialog() {
    }

    public static void showForNewPassword(
            @NonNull Context context,
            @StringRes int messageRes,
            @NonNull OnPasswordConfirmed callback
    ) {
        show(context, messageRes, true, callback);
    }

    public static void showForExistingPassword(
            @NonNull Context context,
            @StringRes int messageRes,
            @NonNull OnPasswordConfirmed callback
    ) {
        show(context, messageRes, false, callback);
    }

    private static void show(
            @NonNull Context context,
            @StringRes int messageRes,
            boolean requireConfirmation,
            @NonNull OnPasswordConfirmed callback
    ) {
        DialogPasswordPromptBinding binding = DialogPasswordPromptBinding.inflate(LayoutInflater.from(context));
        binding.textPasswordPromptMessage.setText(messageRes);
        binding.inputLayoutPasswordConfirm.setVisibility(requireConfirmation ? View.VISIBLE : View.GONE);

        AlertDialog dialog = new MaterialAlertDialogBuilder(context)
                .setTitle(requireConfirmation ? R.string.export_password_dialog_title : R.string.import_password_dialog_title)
                .setView(binding.getRoot())
                .setPositiveButton(requireConfirmation ? R.string.export_password_dialog_confirm_button : R.string.import_password_dialog_confirm_button, null)
                .setNegativeButton(R.string.preset_options_cancel_button, null)
                // Covers every way this dialog can close without a
                // successful submit — Cancel, back press, tapping outside —
                // so entered passwords don't linger in these fields
                // regardless of how the user backs out.
                .setOnDismissListener(d -> clearAll(binding))
                .create();

        // The positive button's click listener is attached after show()
        // (via getButton()) rather than passed to setPositiveButton()
        // above, specifically so returning without calling dialog.dismiss()
        // keeps the dialog open on a validation error — the default
        // behavior of a listener passed to setPositiveButton() is to always
        // dismiss, which would silently discard what the user typed.
        dialog.setOnShowListener(d -> {
            MaterialButton positiveButton = (MaterialButton) dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            positiveButton.setOnClickListener(v -> {
                if (validateAndSubmit(context, binding, requireConfirmation, callback)) {
                    dialog.dismiss();
                }
            });
        });

        // Clears whichever field's error is currently shown as soon as the
        // user edits either field, rather than leaving a stale "passwords
        // don't match" error visible after they've already started fixing it.
        TextWatcher clearErrorsOnEdit = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                binding.inputLayoutPassword.setError(null);
                binding.inputLayoutPasswordConfirm.setError(null);
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        };
        binding.inputPassword.addTextChangedListener(clearErrorsOnEdit);
        binding.inputPasswordConfirm.addTextChangedListener(clearErrorsOnEdit);

        dialog.show();
    }

    private static boolean validateAndSubmit(
            @NonNull Context context,
            @NonNull DialogPasswordPromptBinding binding,
            boolean requireConfirmation,
            @NonNull OnPasswordConfirmed callback
    ) {
        char[] password = editableToCharArray(binding.inputPassword.getText());

        if (password.length == 0) {
            binding.inputLayoutPassword.setError(context.getString(R.string.password_dialog_error_empty));
            return false;
        }

        if (requireConfirmation) {
            char[] confirmation = editableToCharArray(binding.inputPasswordConfirm.getText());
            boolean matches;
            try {
                matches = Arrays.equals(password, confirmation);
            } finally {
                Arrays.fill(confirmation, '\0');
            }
            if (!matches) {
                binding.inputLayoutPasswordConfirm.setError(context.getString(R.string.password_dialog_error_mismatch));
                // Deliberately does NOT clear inputPassword here: only the
                // confirmation field was wrong, so the user shouldn't have
                // to retype the (correct) main password too — see the
                // success path below, which does clear it, for why clearing
                // happens at all.
                return false;
            }
        }

        try {
            // The callback (ultimately PresetCrypto.deriveKey via
            // ExportImportManager) is responsible for wiping this same
            // array once it's done deriving a key from it — ownership
            // passes to the callback here rather than this method also
            // wiping it, since wiping before the callback runs would hand
            // it an already-zeroed array.
            callback.onConfirmed(password);
            return true;
        } finally {
            // Only reached once validation has fully passed and the
            // callback has run: the field is cleared here (rather than in a
            // finally around the whole method) precisely so the two early
            // "invalid input" returns above can leave it as the user typed
            // it.
            clearField(binding.inputPassword);
        }
    }

    @NonNull
    private static char[] editableToCharArray(Editable editable) {
        if (editable == null) {
            return new char[0];
        }
        char[] result = new char[editable.length()];
        editable.getChars(0, editable.length(), result, 0);
        return result;
    }

    private static void clearField(@NonNull TextInputEditText field) {
        Editable text = field.getText();
        if (text != null) {
            text.clear();
        }
    }

    private static void clearAll(@NonNull DialogPasswordPromptBinding binding) {
        clearField(binding.inputPassword);
        clearField(binding.inputPasswordConfirm);
    }
}
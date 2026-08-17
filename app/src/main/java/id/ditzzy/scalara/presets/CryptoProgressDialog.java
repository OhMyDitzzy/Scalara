package id.ditzzy.scalara.presets;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import id.ditzzy.scalara.databinding.DialogCryptoProgressBinding;

/**
 * Small indeterminate progress dialog shown while {@link PresetCrypto}'s
 * PBKDF2 key derivation and the accompanying {@code ContentResolver} file
 * I/O run on a background thread (see {@link ExportImportManager}'s
 * executor-backed methods).
 *
 * <p>Not cancelable and has no dismiss affordance of its own: there's no
 * safe way to interrupt an in-flight {@code Cipher}/{@code SecretKeyFactory}
 * call or a half-written {@code OutputStream}, so the caller that shows this
 * (via {@link #show}) is also the one responsible for dismissing it once the
 * background work's callback fires — on every path (success and failure
 * alike), or this dialog is left stuck on screen.
 *
 * <p>Public (rather than package-private, like {@link ExportImportManager}'s
 * own callback interfaces don't need to be) specifically because this is
 * used from both {@code ExportImportSheet} (this package) and
 * {@code PresetOptionsSheet}, which lives in {@code id.ditzzy.scalara.main}
 * for its per-preset Apply/Delete options — see that class's doc for why it
 * also needs this dialog for its own single-preset Export.
 */
public final class CryptoProgressDialog {

    private CryptoProgressDialog() {
    }

    @NonNull
    public static AlertDialog show(@NonNull Context context, @StringRes int messageRes) {
        DialogCryptoProgressBinding binding = DialogCryptoProgressBinding.inflate(
                android.view.LayoutInflater.from(context)
        );
        binding.textCryptoProgressMessage.setText(messageRes);

        AlertDialog dialog = new MaterialAlertDialogBuilder(context)
                .setView(binding.getRoot())
                .setCancelable(false)
                .create();
        dialog.show();
        return dialog;
    }
}

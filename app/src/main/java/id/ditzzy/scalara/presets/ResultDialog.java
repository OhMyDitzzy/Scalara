package id.ditzzy.scalara.presets;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import id.ditzzy.scalara.R;

/**
 * Small icon + title + message dialog shown once a background export or
 * import call (see {@link ExportImportManager}) has finished, replacing the
 * {@code Snackbar} previously used for that same success/failure feedback.
 *
 * <p>Unlike {@link CryptoProgressDialog} (shown for the duration of the
 * background work), this is shown once from inside that work's completion
 * callback, after {@link CryptoProgressDialog} has already been dismissed —
 * it has no relationship to the progress dialog beyond following it.
 *
 * <p>Dismissed only by the user tapping the single OK button; there's
 * nothing left in flight by this point (unlike
 * {@link CryptoProgressDialog}) that would need this to be
 * non-cancelable, so the platform back-press/outside-tap dismiss behavior
 * is left enabled.
 *
 * <p>Public for the same reason as {@link CryptoProgressDialog}: used from
 * both {@code ExportImportSheet} (this package, for its export-all and
 * import actions) and {@code PresetOptionsSheet} (in
 * {@code id.ditzzy.scalara.main}, for its per-preset export).
 */
public final class ResultDialog {

    private ResultDialog() {
    }

    /** Shows the success variant: {@code ic_check_circle}, tinted via that drawable's own {@code colorPrimary} tint. */
    @NonNull
    public static AlertDialog showSuccess(
            @NonNull Context context, @StringRes int titleRes, @StringRes int messageRes
    ) {
        return show(context, R.drawable.ic_check_circle, titleRes, messageRes);
    }

    /** Shows the failure variant: {@code ic_error}, tinted via that drawable's own {@code colorError} tint. */
    @NonNull
    public static AlertDialog showFailure(
            @NonNull Context context, @StringRes int titleRes, @StringRes int messageRes
    ) {
        return show(context, R.drawable.ic_error, titleRes, messageRes);
    }

    @NonNull
    private static AlertDialog show(
            @NonNull Context context, int iconRes, @StringRes int titleRes, @StringRes int messageRes
    ) {
        AlertDialog dialog = new MaterialAlertDialogBuilder(context)
                .setIcon(iconRes)
                .setTitle(titleRes)
                .setMessage(messageRes)
                .setPositiveButton(R.string.result_dialog_ok_button, null)
                .create();
        dialog.show();
        return dialog;
    }
}

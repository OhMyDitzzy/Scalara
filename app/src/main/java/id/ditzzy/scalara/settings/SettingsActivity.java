package id.ditzzy.scalara.settings;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.List;

import id.ditzzy.scalara.R;
import id.ditzzy.scalara.databinding.ActivitySettingsBinding;
import id.ditzzy.scalara.databinding.ItemLanguageOptionBinding;
import id.ditzzy.scalara.databinding.SheetLanguagePickerBinding;

/**
 * Settings screen reachable from {@code MainActivity}'s overflow menu:
 * theme (light/dark/follow system), in-app language, whether the
 * dangerous-resolution warning is shown, and the "Try it out" preview
 * timeout. All of it is read from and written to {@link AppSettings}.
 *
 * <p>Changing theme or language doesn't need this Activity to do anything
 * beyond calling {@link ThemeUtils#applyThemeMode} / {@link LocaleUtils#applyLocale}:
 * both trigger an automatic recreate of every active
 * {@code AppCompatActivity} (this one included) on their own — see each
 * method's javadoc — so the new choice is reflected immediately without
 * this class calling {@code recreate()} itself.
 */
public class SettingsActivity extends AppCompatActivity {

    private ActivitySettingsBinding binding;
    private AppSettings appSettings;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivitySettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        appSettings = new AppSettings(this);

        binding.toolbar.setNavigationOnClickListener(v -> finish());

        setUpThemeSection();
        setUpLanguageSection();
        setUpAppSettingsSection();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        this.binding = null;
    }

    // ================================================================
    // Theme section
    // ================================================================

    private void setUpThemeSection() {
        AppSettings.ThemeMode current = appSettings.getThemeMode();
        reflectThemeSelection(current);

        binding.cardThemeLight.setOnClickListener(v -> onThemeSelected(AppSettings.ThemeMode.LIGHT));
        binding.cardThemeDark.setOnClickListener(v -> onThemeSelected(AppSettings.ThemeMode.DARK));
        binding.cardThemeFollowSystem.setOnClickListener(v -> onThemeSelected(AppSettings.ThemeMode.FOLLOW_SYSTEM));
    }

    private void onThemeSelected(@NonNull AppSettings.ThemeMode mode) {
        if (mode == appSettings.getThemeMode()) {
            // Re-tapping the already-selected option would still trigger
            // AppCompatDelegate's automatic recreate below, which is a
            // pointless flicker for a choice that hasn't actually changed.
            return;
        }
        reflectThemeSelection(mode);
        ThemeUtils.applyThemeMode(appSettings, mode);
    }

    private void reflectThemeSelection(@NonNull AppSettings.ThemeMode mode) {
        setThemeCardChecked(binding.cardThemeLight, mode == AppSettings.ThemeMode.LIGHT);
        setThemeCardChecked(binding.cardThemeDark, mode == AppSettings.ThemeMode.DARK);
        setThemeCardChecked(binding.cardThemeFollowSystem, mode == AppSettings.ThemeMode.FOLLOW_SYSTEM);
    }

    private void setThemeCardChecked(@NonNull MaterialCardView card, boolean checked) {
        card.setChecked(checked);
    }

    // ================================================================
    // Language section
    // ================================================================

    private void setUpLanguageSection() {
        reflectCurrentLanguage();
        binding.rowLanguage.setOnClickListener(v -> showLanguagePicker());
    }

    private void reflectCurrentLanguage() {
        String currentTag = LocaleUtils.getCurrentLanguageTag();
        if (currentTag.equals(LocaleUtils.TAG_FOLLOW_SYSTEM)) {
            binding.textLanguageCurrentValue.setText(R.string.settings_language_follow_system);
            return;
        }
        // Reuses getAvailableLanguages' display names (already capitalized
        // there) rather than re-deriving one from Locale.forLanguageTag
        // here, so there's exactly one place that decides how a language
        // tag becomes display text.
        for (LocaleUtils.LanguageOption option : LocaleUtils.getAvailableLanguages(this)) {
            if (option.getTag().equals(currentTag)) {
                binding.textLanguageCurrentValue.setText(option.getDisplayName());
                return;
            }
        }
        // Defensive: the applied tag doesn't match any currently-available
        // language (e.g. a translation was removed in an app update after
        // the user had selected it). Falls back to the raw tag rather than
        // showing nothing.
        binding.textLanguageCurrentValue.setText(currentTag);
    }

    private void showLanguagePicker() {
        SheetLanguagePickerBinding sheetBinding = SheetLanguagePickerBinding.inflate(getLayoutInflater());
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        dialog.setContentView(sheetBinding.getRoot());

        List<LocaleUtils.LanguageOption> options = LocaleUtils.getAvailableLanguages(this);
        String currentTag = LocaleUtils.getCurrentLanguageTag();

        sheetBinding.listLanguageOptions.setLayoutManager(new LinearLayoutManager(this));
        sheetBinding.listLanguageOptions.setAdapter(new LanguageOptionAdapter(options, currentTag, tag -> {
            dialog.dismiss();
            onLanguageSelected(tag);
        }));

        dialog.show();
    }

    private void onLanguageSelected(@NonNull String languageTag) {
        if (languageTag.equals(LocaleUtils.getCurrentLanguageTag())) {
            return;
        }
        LocaleUtils.applyLocale(languageTag);
        // No need to also call reflectCurrentLanguage() here: applyLocale's
        // automatic Activity recreate means onCreate (and so
        // setUpLanguageSection) runs again from scratch right after this.
    }

    /**
     * Adapter for {@link #showLanguagePicker}'s option list, including the
     * synthetic "Follow system" entry ({@link LocaleUtils#TAG_FOLLOW_SYSTEM})
     * prepended ahead of every language {@link LocaleUtils#getAvailableLanguages}
     * returns. A plain {@link RecyclerView.Adapter} rather than
     * {@code ListAdapter}/{@code DiffUtil} — unlike {@code PresetAdapter},
     * this list is rebuilt fresh every time the sheet opens and never
     * updates while visible, so there's no diffing to do.
     */
    private static final class LanguageOptionAdapter extends RecyclerView.Adapter<LanguageOptionAdapter.ViewHolder> {

        interface OnLanguageChosen {
            void onChosen(@NonNull String languageTag);
        }

        /** One row's worth of data: either the synthetic "Follow system" entry or a real translated language. */
        private static final class Row {
            final String tag;
            final String displayName;

            Row(String tag, String displayName) {
                this.tag = tag;
                this.displayName = displayName;
            }
        }

        private final List<Row> rows;
        private final String currentTag;
        private final OnLanguageChosen callback;

        LanguageOptionAdapter(
                @NonNull List<LocaleUtils.LanguageOption> languageOptions,
                @NonNull String currentTag,
                @NonNull OnLanguageChosen callback
        ) {
            this.rows = new ArrayList<>(languageOptions.size() + 1);
            // "Follow system" always sits first: it's the default and the
            // option most people looking at this list want, rather than
            // being just another alphabetically-sorted entry among the
            // translated languages.
            this.rows.add(new Row(LocaleUtils.TAG_FOLLOW_SYSTEM, null));
            for (LocaleUtils.LanguageOption option : languageOptions) {
                this.rows.add(new Row(option.getTag(), option.getDisplayName()));
            }
            this.currentTag = currentTag;
            this.callback = callback;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ItemLanguageOptionBinding itemBinding = ItemLanguageOptionBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false
            );
            return new ViewHolder(itemBinding);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Row row = rows.get(position);
            boolean isFollowSystem = row.tag.equals(LocaleUtils.TAG_FOLLOW_SYSTEM);

            holder.binding.textLanguageOptionName.setText(
                    isFollowSystem
                            ? holder.binding.getRoot().getResources().getString(R.string.settings_language_follow_system)
                            : row.displayName
            );
            holder.binding.iconLanguageOptionSelected.setVisibility(
                    row.tag.equals(currentTag) ? View.VISIBLE : View.INVISIBLE
            );
            holder.binding.getRoot().setOnClickListener(v -> callback.onChosen(row.tag));
        }

        @Override
        public int getItemCount() {
            return rows.size();
        }

        static final class ViewHolder extends RecyclerView.ViewHolder {
            final ItemLanguageOptionBinding binding;

            ViewHolder(@NonNull ItemLanguageOptionBinding binding) {
                super(binding.getRoot());
                this.binding = binding;
            }
        }
    }

    // ================================================================
    // App settings section
    // ================================================================

    private void setUpAppSettingsSection() {
        binding.switchDangerousResolutionWarning.setChecked(appSettings.isDangerousResolutionWarningEnabled());
        binding.rowDangerousResolutionWarning.setOnClickListener(v -> {
            boolean newValue = !binding.switchDangerousResolutionWarning.isChecked();
            binding.switchDangerousResolutionWarning.setChecked(newValue);
            appSettings.setDangerousResolutionWarningEnabled(newValue);
        });

        binding.inputPreviewTimeout.setText(String.valueOf(appSettings.getPreviewTimeoutMillis()));
        binding.inputPreviewTimeout.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                binding.inputLayoutPreviewTimeout.setError(null);
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        binding.buttonSavePreviewTimeout.setOnClickListener(v -> onSavePreviewTimeoutClicked());
    }

    private void onSavePreviewTimeoutClicked() {
        Editable text = binding.inputPreviewTimeout.getText();
        String raw = text != null ? text.toString().trim() : "";

        long parsed;
        try {
            parsed = Long.parseLong(raw);
        } catch (NumberFormatException e) {
            showPreviewTimeoutRangeError();
            return;
        }

        if (parsed < AppSettings.MIN_PREVIEW_TIMEOUT_MILLIS || parsed > AppSettings.MAX_PREVIEW_TIMEOUT_MILLIS) {
            showPreviewTimeoutRangeError();
            return;
        }

        appSettings.setPreviewTimeoutMillis(parsed);
        binding.inputLayoutPreviewTimeout.setError(null);
        Snackbar.make(binding.getRoot(), R.string.settings_preview_timeout_saved, Snackbar.LENGTH_SHORT).show();
    }

    private void showPreviewTimeoutRangeError() {
        binding.inputLayoutPreviewTimeout.setError(
                getString(
                        R.string.settings_preview_timeout_error_range,
                        AppSettings.MIN_PREVIEW_TIMEOUT_MILLIS,
                        AppSettings.MAX_PREVIEW_TIMEOUT_MILLIS
                )
        );
    }
}
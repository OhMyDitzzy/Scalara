package id.ditzzy.scalara.settings;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import id.ditzzy.scalara.R;

/**
 * Applies and reports the user's chosen in-app language via
 * {@link AppCompatDelegate}'s per-app language APIs, and exposes the list of
 * languages Scalara actually has translations for.
 *
 * <p>That list is never hardcoded here: {@code R.array.available_locale_tags}
 * is generated at build time (see the {@code generateLocalesList} task in
 * {@code app/build.gradle.kts}) by scanning which {@code res/values-*}
 * directories exist. Adding a new translated {@code strings.xml} is enough,
 * on its own, to make that language selectable — nothing in this class
 * needs to change.
 *
 * <p>Persistence is handled by {@code AppCompatDelegate} itself (backed by
 * {@code AppLocalesStorageHelper}, which — on API 33+ — delegates to the
 * platform's own per-app language storage, and on older APIs keeps its own
 * file): once {@link #applyLocale} is called, the choice survives process
 * death without {@code AppSettings} needing to store it separately.
 */
public final class LocaleUtils {

    /** Sentinel used throughout this class (and by SettingsActivity's picker) to mean "no override, follow system". */
    public static final String TAG_FOLLOW_SYSTEM = "";

    private LocaleUtils() {
    }

    /**
     * Applies the given BCP-47 language tag as the app's locale override, or
     * clears the override (reverting to the system language) if
     * {@code languageTag} is {@link #TAG_FOLLOW_SYSTEM}.
     *
     * <p>{@link AppCompatDelegate} automatically recreates every active,
     * locale-aware {@code AppCompatActivity} in response to this call, so
     * callers don't need to manually call {@code recreate()} — the calling
     * Activity (and any other open Scalara Activity) will restart with the
     * new language on its own.
     */
    public static void applyLocale(@NonNull String languageTag) {
        LocaleListCompat locales = languageTag.equals(TAG_FOLLOW_SYSTEM)
                ? LocaleListCompat.getEmptyLocaleList()
                : LocaleListCompat.forLanguageTags(languageTag);
        AppCompatDelegate.setApplicationLocales(locales);
    }

    /**
     * The currently applied override tag, or {@link #TAG_FOLLOW_SYSTEM} if
     * none is set (i.e. the app is following the system language).
     */
    @NonNull
    public static String getCurrentLanguageTag() {
        LocaleListCompat applied = AppCompatDelegate.getApplicationLocales();
        if (applied.isEmpty()) {
            return TAG_FOLLOW_SYSTEM;
        }
        Locale first = applied.get(0);
        return first != null ? first.toLanguageTag() : TAG_FOLLOW_SYSTEM;
    }

    /**
     * Every language tag Scalara ships translated strings for, each paired
     * with that language's own display name (e.g. "Bahasa Indonesia" rather
     * than "Indonesian") so the picker reads naturally to a speaker of that
     * language regardless of the device's current locale.
     */
    @NonNull
    public static List<LanguageOption> getAvailableLanguages(@NonNull Context context) {
        String[] tags = context.getResources().getStringArray(R.array.available_locale_tags);
        List<LanguageOption> options = new ArrayList<>(tags.length);
        for (String tag : tags) {
            Locale locale = Locale.forLanguageTag(tag);
            String displayName = locale.getDisplayName(locale);
            options.add(new LanguageOption(tag, capitalize(displayName)));
        }
        return options;
    }

    @NonNull
    private static String capitalize(@NonNull String value) {
        if (value.isEmpty()) {
            return value;
        }
        // Locale.getDisplayName can return an all-lowercase string for some
        // target locales (e.g. certain ICU data for "id" yields "bahasa
        // indonesia"); capitalize defensively so the picker never shows a
        // lowercase-first-letter language name regardless of ICU version.
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    /** A single selectable language: its BCP-47 tag and human-readable name. */
    public static final class LanguageOption {
        private final String tag;
        private final String displayName;

        LanguageOption(@NonNull String tag, @NonNull String displayName) {
            this.tag = tag;
            this.displayName = displayName;
        }

        @NonNull
        public String getTag() {
            return tag;
        }

        @NonNull
        public String getDisplayName() {
            return displayName;
        }

        @Override
        public boolean equals(@Nullable Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof LanguageOption)) {
                return false;
            }
            return tag.equals(((LanguageOption) o).tag);
        }

        @Override
        public int hashCode() {
            return tag.hashCode();
        }
    }
}
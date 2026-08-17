package id.ditzzy.scalara.settings;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatDelegate;

/**
 * Converts between {@link AppSettings.ThemeMode} and
 * {@link AppCompatDelegate}'s {@code MODE_NIGHT_*} constants, and applies a
 * mode change.
 *
 * <p>Kept separate from {@link AppSettings} itself since this is pure
 * behavior (no state), and separate from {@code SettingsActivity} since
 * {@code ScalaraApplication} needs the same conversion at startup, before
 * any Activity exists.
 */
public final class ThemeUtils {

    private ThemeUtils() {
    }

    @AppCompatDelegate.NightMode
    public static int toNightMode(@NonNull AppSettings.ThemeMode mode) {
        switch (mode) {
            case LIGHT:
                return AppCompatDelegate.MODE_NIGHT_NO;
            case DARK:
                return AppCompatDelegate.MODE_NIGHT_YES;
            case FOLLOW_SYSTEM:
            default:
                return AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
        }
    }

    /**
     * Persists {@code mode} and applies it immediately via
     * {@link AppCompatDelegate#setDefaultNightMode}, which recreates every
     * active Activity to reflect the change — the same automatic-recreate
     * behavior {@link LocaleUtils#applyLocale} relies on for language
     * changes, so callers don't need to call {@code recreate()} themselves.
     */
    public static void applyThemeMode(@NonNull AppSettings settings, @NonNull AppSettings.ThemeMode mode) {
        settings.setThemeMode(mode);
        AppCompatDelegate.setDefaultNightMode(toNightMode(mode));
    }
}
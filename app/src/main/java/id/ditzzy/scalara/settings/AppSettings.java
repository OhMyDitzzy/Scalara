package id.ditzzy.scalara.settings;

import android.content.Context;
import android.content.SharedPreferences;

import id.ditzzy.scalara.resolution.PreviewRevertService;

/**
 * Central store for user-facing app settings: theme mode, in-app language,
 * whether the dangerous-resolution warning is shown, and the preview
 * timeout.
 *
 * <p>Deliberately separate from {@code AppPreferences}, which only holds
 * onboarding/setup state (disclaimer acceptance, permission method) — that
 * state gates first-run routing and is never surfaced as something the user
 * edits, while everything here is a user-adjustable preference exposed on
 * {@code SettingsActivity}. Keeping them apart means a future "reset
 * settings" action can clear this store without touching setup state, and
 * vice versa.
 */
public final class AppSettings {

    private static final String PREFS_NAME = "scalara_app_settings";

    private static final String KEY_THEME_MODE = "theme_mode";
    private static final String KEY_DANGEROUS_RESOLUTION_WARNING_ENABLED = "dangerous_resolution_warning_enabled";
    private static final String KEY_PREVIEW_TIMEOUT_MILLIS = "preview_timeout_millis";

    /** Minimum preview timeout a user can configure: long enough to be visible, short enough to stay safe. */
    public static final long MIN_PREVIEW_TIMEOUT_MILLIS = 3_000L;

    /** Maximum preview timeout a user can configure. */
    public static final long MAX_PREVIEW_TIMEOUT_MILLIS = 60_000L;

    /** In-app theme override. Mirrors the three options {@code SettingsActivity} presents to the user. */
    public enum ThemeMode {
        /** Follow the system's light/dark setting. Default. */
        FOLLOW_SYSTEM,
        LIGHT,
        DARK
    }

    private final SharedPreferences prefs;

    public AppSettings(Context context) {
        this.prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public ThemeMode getThemeMode() {
        String stored = prefs.getString(KEY_THEME_MODE, ThemeMode.FOLLOW_SYSTEM.name());
        try {
            return ThemeMode.valueOf(stored);
        } catch (IllegalArgumentException e) {
            return ThemeMode.FOLLOW_SYSTEM;
        }
    }

    public void setThemeMode(ThemeMode mode) {
        prefs.edit().putString(KEY_THEME_MODE, mode.name()).apply();
    }

    /**
     * Whether {@code ResolutionUtils.isResolutionDangerous} should gate
     * applying/previewing a resolution behind a confirmation dialog.
     * Defaults to {@code true}: the warning is a safety net most users
     * benefit from keeping on, so this is an opt-out rather than an
     * opt-in.
     */
    public boolean isDangerousResolutionWarningEnabled() {
        return prefs.getBoolean(KEY_DANGEROUS_RESOLUTION_WARNING_ENABLED, true);
    }

    public void setDangerousResolutionWarningEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_DANGEROUS_RESOLUTION_WARNING_ENABLED, enabled).apply();
    }

    /**
     * How long a "Try it out" preview stays applied before auto-reverting.
     * Defaults to {@link PreviewRevertService#DEFAULT_DURATION_MILLIS} so
     * this setting's default matches the app's pre-existing behavior rather
     * than introducing a silent change for users who never open Settings.
     */
    public long getPreviewTimeoutMillis() {
        long stored = prefs.getLong(KEY_PREVIEW_TIMEOUT_MILLIS, PreviewRevertService.DEFAULT_DURATION_MILLIS);
        if (stored < MIN_PREVIEW_TIMEOUT_MILLIS || stored > MAX_PREVIEW_TIMEOUT_MILLIS) {
            // Defensive: a value outside the range the UI allows (e.g. from
            // a future downgrade/rollback scenario) falls back to the
            // default rather than feeding an out-of-range duration into
            // PreviewRevertService.
            return PreviewRevertService.DEFAULT_DURATION_MILLIS;
        }
        return stored;
    }

    public void setPreviewTimeoutMillis(long millis) {
        prefs.edit().putLong(KEY_PREVIEW_TIMEOUT_MILLIS, millis).apply();
    }
}
package id.ditzzy.scalara.setup;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Central store for onboarding/setup state: whether the disclaimer has been
 * accepted, which permission method the user picked, and whether setup has
 * been completed at least once.
 *
 * <p>This is intentionally a thin wrapper over {@link SharedPreferences} —
 * there's no complex state to model, just a handful of flags that gate the
 * launch routing performed by {@code SplashActivity}.
 */
public final class AppPreferences {

    private static final String PREFS_NAME = "scalara_setup_prefs";

    private static final String KEY_DISCLAIMER_ACCEPTED = "disclaimer_accepted";
    private static final String KEY_SETUP_COMPLETED = "setup_completed";
    private static final String KEY_SETUP_METHOD = "setup_method";

    /** Which permission-granting method the user configured during setup. */
    public enum SetupMethod {
        /** No method configured yet. */
        NONE,
        /** Permission granted once via ADB; treated as permanent, no Shizuku binder is requested. */
        ADB,
        /** Permission managed live through the Shizuku user service. */
        SHIZUKU
    }

    private final SharedPreferences prefs;

    public AppPreferences(Context context) {
        this.prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public boolean isDisclaimerAccepted() {
        return prefs.getBoolean(KEY_DISCLAIMER_ACCEPTED, false);
    }

    public void setDisclaimerAccepted(boolean accepted) {
        prefs.edit().putBoolean(KEY_DISCLAIMER_ACCEPTED, accepted).apply();
    }

    public boolean isSetupCompleted() {
        return prefs.getBoolean(KEY_SETUP_COMPLETED, false);
    }

    public void setSetupCompleted(boolean completed) {
        prefs.edit().putBoolean(KEY_SETUP_COMPLETED, completed).apply();
    }

    public SetupMethod getSetupMethod() {
        String stored = prefs.getString(KEY_SETUP_METHOD, SetupMethod.NONE.name());
        try {
            return SetupMethod.valueOf(stored);
        } catch (IllegalArgumentException e) {
            return SetupMethod.NONE;
        }
    }

    public void setSetupMethod(SetupMethod method) {
        prefs.edit().putString(KEY_SETUP_METHOD, method.name()).apply();
    }

    /**
     * Clears setup completion and the chosen method so the user is routed
     * back into the wizard, without forcing them through the disclaimer
     * again. Used when the permission is found to have been revoked.
     */
    public void resetSetupState() {
        prefs.edit()
                .putBoolean(KEY_SETUP_COMPLETED, false)
                .putString(KEY_SETUP_METHOD, SetupMethod.NONE.name())
                .apply();
    }
}

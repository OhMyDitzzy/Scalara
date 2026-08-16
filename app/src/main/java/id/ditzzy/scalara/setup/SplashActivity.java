package id.ditzzy.scalara.setup;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.splashscreen.SplashScreen;

import id.ditzzy.scalara.MainActivity;

/**
 * Entry point activity. Installs the platform SplashScreen (backward
 * compatible down to API 23 via the core-splashscreen library) and, once the
 * splash has been shown, routes to exactly one of three destinations based
 * on stored state:
 *
 * <ol>
 *   <li>{@link DisclaimerActivity} — the user has never accepted the
 *       disclaimer for this install.</li>
 *   <li>{@link SetupActivity} — the disclaimer is accepted but permission
 *       setup hasn't been completed (or was reset, e.g. after a revoked
 *       permission was detected).</li>
 *   <li>{@link MainActivity} — both are done; the app can proceed straight
 *       through.</li>
 * </ol>
 *
 * <p>This activity never has its own UI beyond the system splash screen — it
 * always finishes immediately after starting the next activity, so it never
 * sits in the back stack.
 */
public class SplashActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Must be called before super.onCreate() per the SplashScreen API contract.
        SplashScreen.installSplashScreen(this);

        super.onCreate(savedInstanceState);

        route();
    }

    private void route() {
        AppPreferences preferences = new AppPreferences(this);

        Class<?> destination;
        if (!preferences.isDisclaimerAccepted()) {
            destination = DisclaimerActivity.class;
        } else if (!preferences.isSetupCompleted()) {
            destination = SetupActivity.class;
        } else {
            destination = MainActivity.class;
        }

        startActivity(new Intent(this, destination));
        finish();
    }
}

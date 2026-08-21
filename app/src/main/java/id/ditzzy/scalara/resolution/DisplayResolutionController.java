package id.ditzzy.scalara.resolution;

import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.graphics.Point;
import android.os.Build;
import android.provider.Settings;
import android.view.Display;

import java.lang.reflect.Method;

/**
 * Applies, clears, and reads the device's forced display size and density
 * through the hidden {@code IWindowManager} AIDL interface, reached via
 * reflection since it isn't part of the public Android SDK.
 *
 * <p>This is the same mechanism behind {@code adb shell wm size} and
 * {@code wm density}: {@link WindowManagerConstants.IWindowManager#SET_FORCED_DISPLAY_SIZE}
 * and friends. Every method here requires the caller to already hold
 * {@code WRITE_SECURE_SETTINGS} (see {@code SecureSettingsPermission}) or the
 * underlying {@code IWindowManager} call throws a {@link SecurityException}.
 */
@SuppressLint("PrivateApi")
public final class DisplayResolutionController {

    /**
     * Pseudo user id accepted by the density-for-user variants to mean "all
     * users" (mirrors {@code UserHandle.USER_CURRENT} as used by the
     * {@code wm density} shell command).
     */
    private static final int USER_ID = -3;

    /**
     * Secure-settings keys that gate access to hidden/greylisted APIs on
     * newer Android versions. Setting these to 1 (disabled) is required for
     * the reflection calls below to succeed at all on API levels that
     * otherwise block them.
     */
    private static final String[] GLOBAL_SETTINGS_BLACKLIST_KEYS = {
            "hidden_api_policy",
            "hidden_api_policy_pre_p_apps",
            "hidden_api_policy_p_apps"
    };

    private final Object iWindowManager;

    /**
     * Resolves {@code IWindowManager} via {@code WindowManagerGlobal} and
     * unblocks the hidden-API policy keys needed to call it.
     *
     * @throws ReflectiveOperationException if the hidden API surface this
     *                                      class depends on is unavailable
     *                                      on the current device/OS build
     */
    public DisplayResolutionController(ContentResolver contentResolver) throws ReflectiveOperationException {
        for (String key : GLOBAL_SETTINGS_BLACKLIST_KEYS) {
            Settings.Global.putInt(contentResolver, key, 1);
        }

        Class<?> windowManagerGlobalClass = Class.forName(WindowManagerConstants.WindowManagerGlobal.CLASS_NAME);
        Method getWindowManagerServiceMethod = windowManagerGlobalClass.getMethod(
                WindowManagerConstants.WindowManagerGlobal.GET_WINDOW_MANAGER_SERVICE
        );
        this.iWindowManager = getWindowManagerServiceMethod.invoke(null);
    }

    /**
     * Forces the display to render at {@code width}x{@code height} pixels,
     * independent of the physical panel resolution.
     */
    public void setResolution(int width, int height) throws ReflectiveOperationException {
        Class<?> iWindowManagerClass = Class.forName(WindowManagerConstants.IWindowManager.CLASS_NAME);
        Method setForcedDisplaySizeMethod = iWindowManagerClass.getMethod(
                WindowManagerConstants.IWindowManager.SET_FORCED_DISPLAY_SIZE,
                int.class, int.class, int.class
        );
        setForcedDisplaySizeMethod.invoke(iWindowManager, Display.DEFAULT_DISPLAY, width, height);
    }

    /**
     * Reads the display's real (physical, unforced) resolution as reported
     * by the system.
     */
    public Point getRealResolution() throws ReflectiveOperationException {
        Class<?> iWindowManagerClass = Class.forName(WindowManagerConstants.IWindowManager.CLASS_NAME);
        Method getInitialDisplaySizeMethod = iWindowManagerClass.getMethod(
                WindowManagerConstants.IWindowManager.GET_INITIAL_DISPLAY_SIZE,
                int.class, Point.class
        );
        Point point = new Point();
        getInitialDisplaySizeMethod.invoke(iWindowManager, Display.DEFAULT_DISPLAY, point);
        return point;
    }

    /** Clears any forced display size, restoring the physical resolution. */
    public void clearResolution() throws ReflectiveOperationException {
        Class<?> iWindowManagerClass = Class.forName(WindowManagerConstants.IWindowManager.CLASS_NAME);
        Method clearForcedDisplaySizeMethod = iWindowManagerClass.getMethod(
                WindowManagerConstants.IWindowManager.CLEAR_FORCED_DISPLAY_SIZE,
                int.class
        );
        clearForcedDisplaySizeMethod.invoke(iWindowManager, Display.DEFAULT_DISPLAY);
    }

    /**
     * Forces the display density to {@code dpi}. Uses the per-user variant
     * on API levels newer than N_MR1, matching what {@code wm density} does
     * internally on those versions.
     */
    public void setDisplayDensity(int dpi) throws ReflectiveOperationException {
        Class<?> iWindowManagerClass = Class.forName(WindowManagerConstants.IWindowManager.CLASS_NAME);

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.N_MR1) {
            Method setForcedDisplayDensityMethod = iWindowManagerClass.getMethod(
                    WindowManagerConstants.IWindowManager.SET_FORCED_DISPLAY_DENSITY,
                    int.class, int.class
            );
            setForcedDisplayDensityMethod.invoke(iWindowManager, Display.DEFAULT_DISPLAY, dpi);
        } else {
            Method setForcedDisplayDensityForUserMethod = iWindowManagerClass.getMethod(
                    WindowManagerConstants.IWindowManager.SET_FORCED_DISPLAY_DENSITY_FOR_USER,
                    int.class, int.class, int.class
            );
            setForcedDisplayDensityForUserMethod.invoke(iWindowManager, Display.DEFAULT_DISPLAY, dpi, USER_ID);
        }
    }

    /** Clears any forced density, restoring the physical/default density. */
    public void clearDisplayDensity() throws ReflectiveOperationException {
        Class<?> iWindowManagerClass = Class.forName(WindowManagerConstants.IWindowManager.CLASS_NAME);

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.N_MR1) {
            Method clearForcedDisplayDensityMethod = iWindowManagerClass.getMethod(
                    WindowManagerConstants.IWindowManager.CLEAR_FORCED_DISPLAY_DENSITY,
                    int.class
            );
            clearForcedDisplayDensityMethod.invoke(iWindowManager, Display.DEFAULT_DISPLAY);
        } else {
            Method clearForcedDisplayDensityForUserMethod = iWindowManagerClass.getMethod(
                    WindowManagerConstants.IWindowManager.CLEAR_FORCED_DISPLAY_DENSITY_FOR_USER,
                    int.class, int.class
            );
            clearForcedDisplayDensityForUserMethod.invoke(iWindowManager, Display.DEFAULT_DISPLAY, USER_ID);
        }
    }

    /** Reads the display's real (physical, unforced) density in DPI. */
    public int getRealDensity() throws ReflectiveOperationException {
        Class<?> iWindowManagerClass = Class.forName(WindowManagerConstants.IWindowManager.CLASS_NAME);
        Method getInitialDisplayDensityMethod = iWindowManagerClass.getMethod(
                WindowManagerConstants.IWindowManager.GET_INITIAL_DISPLAY_DENSITY,
                int.class
        );
        return (int) getInitialDisplayDensityMethod.invoke(iWindowManager, Display.DEFAULT_DISPLAY);
    }
}

package id.ditzzy.scalara.resolution;

/**
 * Class and method names for the hidden {@code IWindowManager} AIDL
 * interface, reached through reflection since it isn't part of the public
 * Android SDK.
 *
 * <p>These are the same calls behind {@code adb shell wm size} /
 * {@code wm density}, and require {@code WRITE_SECURE_SETTINGS} to succeed
 * from a third-party app.
 */
final class WindowManagerConstants {

    private WindowManagerConstants() {
    }

    static final class WindowManagerGlobal {
        static final String CLASS_NAME = "android.view.WindowManagerGlobal";
        static final String GET_WINDOW_MANAGER_SERVICE = "getWindowManagerService";

        private WindowManagerGlobal() {
        }
    }

    static final class IWindowManager {
        static final String CLASS_NAME = "android.view.IWindowManager";
        static final String SET_FORCED_DISPLAY_SIZE = "setForcedDisplaySize";
        static final String CLEAR_FORCED_DISPLAY_SIZE = "clearForcedDisplaySize";
        static final String SET_FORCED_DISPLAY_DENSITY = "setForcedDisplayDensity";
        static final String SET_FORCED_DISPLAY_DENSITY_FOR_USER = "setForcedDisplayDensityForUser";
        static final String CLEAR_FORCED_DISPLAY_DENSITY = "clearForcedDisplayDensity";
        static final String CLEAR_FORCED_DISPLAY_DENSITY_FOR_USER = "clearForcedDisplayDensityForUser";
        static final String GET_INITIAL_DISPLAY_SIZE = "getInitialDisplaySize";
        static final String GET_INITIAL_DISPLAY_DENSITY = "getInitialDisplayDensity";

        private IWindowManager() {
        }
    }
}

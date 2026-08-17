package id.ditzzy.scalara.resolution;

import android.content.Context;
import android.util.DisplayMetrics;
import android.view.WindowManager;

/**
 * Reads the device's real resolution through the standard, non-reflection
 * {@link DisplayMetrics} API, and flags resolution/density combinations that
 * differ too much from that baseline to warn the user before applying them.
 */
public final class ResolutionUtils {

    /**
     * A dimension (width, height, or DPI) more than this fraction away from
     * the device default is considered risky enough to warn about before
     * applying.
     */
    private static final double DANGEROUS_DEVIATION_THRESHOLD = 0.5;

    private ResolutionUtils() {
    }

    /**
     * Reads the device's current real resolution and density. Note this
     * reflects whatever is currently forced (if anything) — callers that
     * need the true physical default should prefer
     * {@link DisplayResolutionController#getRealResolution()} and
     * {@link DisplayResolutionController#getRealDensity()}, which query the
     * hidden {@code IWindowManager} API directly.
     */
    public static DeviceResolution getCurrentResolution(Context context) {
        WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        DisplayMetrics displayMetrics = new DisplayMetrics();
        //noinspection deprecation
        windowManager.getDefaultDisplay().getMetrics(displayMetrics);

        return new DeviceResolution(
                displayMetrics.widthPixels,
                displayMetrics.heightPixels,
                displayMetrics.densityDpi
        );
    }

    /**
     * True if any of width, height, or DPI in {@code candidate} differs from
     * {@code baseline} by more than {@link #DANGEROUS_DEVIATION_THRESHOLD}.
     */
    public static boolean isResolutionDangerous(DeviceResolution baseline, DeviceResolution candidate) {
        return deviatesTooMuch(candidate.getWidth(), baseline.getWidth())
                || deviatesTooMuch(candidate.getHeight(), baseline.getHeight())
                || deviatesTooMuch(candidate.getDpi(), baseline.getDpi());
    }

    private static boolean deviatesTooMuch(int value, int baseline) {
        if (baseline == 0) {
            return false;
        }
        double deviation = Math.abs((double) value / baseline - 1);
        return deviation > DANGEROUS_DEVIATION_THRESHOLD;
    }
}

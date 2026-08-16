package id.ditzzy.scalara.setup;

import android.content.pm.PackageManager;

import androidx.annotation.NonNull;

import id.ditzzy.scalara.app.InternalLogger;
import rikka.shizuku.Shizuku;

/**
 * Wraps the Shizuku API's binder lifecycle and permission-request flow.
 *
 * <p>Shizuku methods throw {@link IllegalStateException} unless the binder
 * is currently alive, so every listener registered here tracks that binder's
 * life for as long as this manager is attached. Callers must invoke
 * {@link #attach()} from {@code onCreate} and {@link #detach()} from
 * {@code onDestroy} of whichever activity owns this instance, mirroring the
 * lifecycle pattern from the official Shizuku integration guide.
 *
 * <p>This manager doesn't call {@code Sui.init()} itself and doesn't attempt
 * to launch or install the Shizuku app; the latter is left to the user via
 * the Play Store / GitHub, same as the official demo recommends. Not calling
 * {@code Sui.init()} explicitly doesn't mean Sui (the Magisk-module backend
 * some ROMs use instead of Shizuku's own ADB/root service) is unsupported —
 * {@code ShizukuProvider} has auto-initialized Sui since API version 12.1.0
 * of this library, so a device using Sui still works through the same
 * {@link Shizuku} calls used here without Scalara needing to special-case it.
 */
public final class ShizukuManager {

    private static final String TAG = "ShizukuManager";

    /** Request code passed to {@link Shizuku#requestPermission(int)}. */
    public static final int REQUEST_CODE_PERMISSION = 4102;

    /** Callback for binder + permission state changes relevant to setup. */
    public interface Listener {
        /** The Shizuku service is running and reachable. */
        void onBinderAvailable();

        /** The Shizuku service died, was stopped, or was never reachable. */
        void onBinderUnavailable();

        /** The user responded to a permission prompt triggered by {@link #requestPermission()}. */
        void onPermissionResult(boolean granted);
    }

    private final Listener listener;
    private boolean attached = false;

    private final Shizuku.OnBinderReceivedListener binderReceivedListener =
            this::handleBinderReceived;

    private final Shizuku.OnBinderDeadListener binderDeadListener =
            this::handleBinderDead;

    private final Shizuku.OnRequestPermissionResultListener permissionResultListener =
            this::handlePermissionResult;

    public ShizukuManager(@NonNull Listener listener) {
        this.listener = listener;
    }

    /** Registers all listeners. Call once from {@code onCreate}. */
    public void attach() {
        if (attached) {
            return;
        }
        attached = true;

        // Sticky: fires immediately with the current state if the binder is
        // already alive, in addition to firing on future (re)connections.
        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener);
        Shizuku.addBinderDeadListener(binderDeadListener);
        Shizuku.addRequestPermissionResultListener(permissionResultListener);
    }

    /** Unregisters all listeners. Call once from {@code onDestroy}. */
    public void detach() {
        if (!attached) {
            return;
        }
        attached = false;

        Shizuku.removeBinderReceivedListener(binderReceivedListener);
        Shizuku.removeBinderDeadListener(binderDeadListener);
        Shizuku.removeRequestPermissionResultListener(permissionResultListener);
    }

    /**
     * True if the Shizuku service is installed, running, and its binder is
     * currently reachable. Does not imply the permission itself is granted —
     * use {@link #isPermissionGranted()} for that.
     */
    public boolean isServiceRunning() {
        return Shizuku.pingBinder();
    }

    /**
     * True if this app currently holds the permission Shizuku manages
     * (equivalent to {@code WRITE_SECURE_SETTINGS} once granted through it).
     * Only meaningful while {@link #isServiceRunning()} is true.
     */
    public boolean isPermissionGranted() {
        if (!isServiceRunning()) {
            return false;
        }
        try {
            return Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
        } catch (IllegalStateException e) {
            // Binder died between the pingBinder() check above and this call.
            InternalLogger.w(TAG, "checkSelfPermission() raced binder death", e);
            return false;
        }
    }

    /**
     * True if the user previously denied the request with "don't ask again".
     * Only meaningful while {@link #isServiceRunning()} is true.
     */
    public boolean isPermissionPermanentlyDenied() {
        if (!isServiceRunning()) {
            return false;
        }
        try {
            return Shizuku.shouldShowRequestPermissionRationale();
        } catch (IllegalStateException e) {
            InternalLogger.w(TAG, "shouldShowRequestPermissionRationale() raced binder death", e);
            return false;
        }
    }

    /**
     * Triggers the Shizuku permission prompt. The result arrives
     * asynchronously via {@link Listener#onPermissionResult(boolean)}.
     * No-ops if the service isn't currently running.
     */
    public void requestPermission() {
        if (!isServiceRunning()) {
            InternalLogger.w(TAG, "requestPermission() called with no live binder");
            return;
        }
        try {
            Shizuku.requestPermission(REQUEST_CODE_PERMISSION);
        } catch (IllegalStateException e) {
            InternalLogger.w(TAG, "requestPermission() raced binder death", e);
        }
    }

    private void handleBinderReceived() {
        InternalLogger.i(TAG, "Shizuku binder received");
        listener.onBinderAvailable();
    }

    private void handleBinderDead() {
        InternalLogger.i(TAG, "Shizuku binder died");
        listener.onBinderUnavailable();
    }

    private void handlePermissionResult(int requestCode, int grantResult) {
        if (requestCode != REQUEST_CODE_PERMISSION) {
            return;
        }
        boolean granted = grantResult == PackageManager.PERMISSION_GRANTED;
        InternalLogger.i(TAG, "Shizuku permission result: " + granted);
        listener.onPermissionResult(granted);
    }
}

package id.ditzzy.scalara.setup;

import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.IBinder;

import androidx.annotation.NonNull;

import id.ditzzy.scalara.app.InternalLogger;
import rikka.shizuku.Shizuku;

public final class ShizukuManager {

    private static final String TAG = "ShizukuManager";
    public static final int REQUEST_CODE_PERMISSION = 4102;
    public interface Listener {
        void onBinderAvailable();
        void onBinderUnavailable();
        void onPermissionResult(boolean granted);
    }

    private final Listener listener;
    private boolean attached = false;
    private final Shizuku.OnBinderReceivedListener binderReceivedListener =
            this::handleBinderReceived;
    private final Shizuku.OnBinderDeadListener binderDeadListener = this::handleBinderDead;
    private final Shizuku.OnRequestPermissionResultListener permissionResultListener =
            this::handlePermissionResult;
    public ShizukuManager(@NonNull Listener listener) {
        this.listener = listener;
    }

    public void attach() {
        if (attached) {
            return;
        }

        attached = true;
        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener);
        Shizuku.addBinderDeadListener(binderDeadListener);
        Shizuku.addRequestPermissionResultListener(permissionResultListener);
    }

    public void detach() {
        if (!attached) {
            return;
        }

        attached = false;
        Shizuku.removeBinderReceivedListener(binderReceivedListener);
        Shizuku.removeBinderDeadListener(binderDeadListener);
        Shizuku.removeRequestPermissionResultListener(permissionResultListener);
    }

    public boolean isServiceRunning() {
        return Shizuku.pingBinder();
    }

    public boolean isPermissionGranted() {
        if (!isServiceRunning()) {
            return false;
        }

        try {
            return Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;

        } catch (IllegalStateException e) {
            InternalLogger.w(TAG, "checkSelfPermission() failed", e);

            return false;
        }
    }

    public boolean isPermissionPermanentlyDenied() {
        if (!isServiceRunning()) {
            return false;
        }

        try {
            return !Shizuku.shouldShowRequestPermissionRationale();

        } catch (IllegalStateException e) {
            InternalLogger.w(TAG, "shouldShowRequestPermissionRationale() failed", e);

            return false;
        }
    }

    public void requestPermission() {
        if (!isServiceRunning()) {
            InternalLogger.w(TAG, "requestPermission() called while Shizuku is not running");

            return;
        }

        try {
            Shizuku.requestPermission(REQUEST_CODE_PERMISSION);

        } catch (IllegalStateException e) {
            InternalLogger.w(TAG, "requestPermission() failed", e);
        }
    }

    public void grantSecureSettings(@NonNull Context context, @NonNull GrantCallback callback) {
        if (!isServiceRunning()) {
            callback.onResult(false, "Shizuku service is not running");

            return;
        }

        String packageName = context.getPackageName();
        Shizuku.UserServiceArgs args =
                new Shizuku.UserServiceArgs(
                        new ComponentName(packageName, MyUserService.class.getName()));

        args.daemon(false);
        args.processNameSuffix("privileged_service");
        args.version(1);

        ServiceConnection connection =
                new ServiceConnection() {

                    @Override
                    public void onServiceConnected(ComponentName name, IBinder service) {
                        InternalLogger.i(TAG, "UserService connected");
                        IUserService userService = IUserService.Stub.asInterface(service);

                        if (userService == null) {
                            callback.onResult(false, "IUserService is null");

                            unbind(args, this);
                            return;
                        }

                        try {
                            String result = userService.grantSecureSettings(packageName);
                            InternalLogger.i(TAG, "pm grant result:\n" + result);

                            boolean success = result != null && result.startsWith("exitCode=0");
                            callback.onResult(success, result);
                        } catch (Exception e) {
                            InternalLogger.e(TAG, "Error executing UserService", e);
                            callback.onResult(false, "Exception: " + e);
                        } finally {
                            // Don't call userService.destroy(). UserService will die after we unbind it.
                            unbind(args, this);
                        }
                    }

                    @Override
                    public void onServiceDisconnected(ComponentName name) {
                        InternalLogger.i(TAG, "UserService disconnected");
                    }
                };
        try {
            Shizuku.bindUserService(args, connection);
            InternalLogger.i(TAG, "Binding UserService...");
        } catch (Exception e) {
            InternalLogger.e(TAG, "Failed to bind UserService", e);
            callback.onResult(false, "Failed to bind UserService: " + e);
        }
    }

    private void unbind(Shizuku.UserServiceArgs args, ServiceConnection connection) {
        try {
            Shizuku.unbindUserService(args, connection, true);
            InternalLogger.i(TAG, "UserService unbound");
        } catch (Exception e) {
            InternalLogger.w(TAG, "Failed to unbind UserService", e);
        }
    }

    public interface GrantCallback {
        void onResult(boolean success, String result);
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

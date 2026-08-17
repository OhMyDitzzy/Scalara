package id.ditzzy.scalara.resolution;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.CountDownTimer;
import android.os.IBinder;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import id.ditzzy.scalara.MainActivity;
import id.ditzzy.scalara.R;
import id.ditzzy.scalara.app.InternalLogger;

/**
 * Runs the "Try it out" preview countdown as a foreground service, so the
 * forced resolution reliably reverts to the device default after the
 * preview duration even if {@code MainActivity} is backgrounded, rotated
 * away from, or killed by the system — all of which a plain in-Activity
 * {@link CountDownTimer} would not survive.
 *
 * <p>This service owns the entire preview lifecycle: applying the preview
 * resolution on start, counting down, and reverting either when the timer
 * completes or when asked to stop early (user taps "Revert now" from the
 * notification, or backs out of the preview screen). It does not persist
 * anything — if the process is killed outright (not just backgrounded) the
 * countdown and its notification die with it, same as any foreground
 * service; the {@link DisplayResolutionController#clearResolution()} /
 * {@link DisplayResolutionController#clearDisplayDensity()} calls this
 * service would have made on completion simply won't have run, leaving the
 * preview resolution applied until the user reopens Scalara. Given how
 * short the preview window is (default {@link #DEFAULT_DURATION_MILLIS}),
 * this is the same tradeoff Scalara already accepts elsewhere for
 * revoked-permission recovery: the user reopens the app and sorts it out
 * from there, rather than Scalara chasing every possible process-death edge
 * case.
 *
 * <p>State is exposed to observers (namely {@code MainViewModel}) via the
 * static {@link #remainingSecondsLiveData} rather than a bound-service
 * interface, since the only thing any UI needs from this service is "how
 * much time is left" — a full {@code Binder} interface would be more
 * machinery than the one value justifies.
 */
public class PreviewRevertService extends android.app.Service {

    private static final String TAG = "PreviewRevertService";

    private static final String CHANNEL_ID = "preview_revert_channel";
    private static final int NOTIFICATION_ID = 1001;

    private static final String ACTION_START = "id.ditzzy.scalara.action.START_PREVIEW";
    private static final String ACTION_REVERT_NOW = "id.ditzzy.scalara.action.REVERT_NOW";

    private static final String EXTRA_WIDTH = "extra_width";
    private static final String EXTRA_HEIGHT = "extra_height";
    private static final String EXTRA_DPI = "extra_dpi";
    private static final String EXTRA_DURATION_MILLIS = "extra_duration_millis";

    /** Default preview length: apply, wait, then automatically revert. */
    public static final long DEFAULT_DURATION_MILLIS = 10_000L;

    private static final long TICK_INTERVAL_MILLIS = 1_000L;

    /**
     * Seconds remaining in the active preview, or {@code null} when no
     * preview is running. Static and process-wide by design — there is only
     * ever one preview active at a time, and this lets {@code MainViewModel}
     * observe progress without binding to the service.
     */
    private static final MutableLiveData<Integer> remainingSecondsLiveData = new MutableLiveData<>(null);

    private DisplayResolutionController resolutionController;
    private CountDownTimer countDownTimer;
    private boolean revertAttempted = false;

    public static LiveData<Integer> getRemainingSecondsLiveData() {
        return remainingSecondsLiveData;
    }

    /** True if a preview countdown is currently active in this process. */
    public static boolean isPreviewActive() {
        return remainingSecondsLiveData.getValue() != null;
    }

    /**
     * Starts a preview: applies {@code width}x{@code height}@{@code dpi}
     * immediately, then reverts to the device default after
     * {@link #DEFAULT_DURATION_MILLIS}.
     */
    public static void start(Context context, int width, int height, int dpi) {
        start(context, width, height, dpi, DEFAULT_DURATION_MILLIS);
    }

    /**
     * Same as {@link #start(Context, int, int, int)}, but with an explicit
     * duration rather than {@link #DEFAULT_DURATION_MILLIS} — used by
     * {@code MainActivity} to honor the user's configured
     * {@code AppSettings.getPreviewTimeoutMillis()}, and by tests that need
     * a short, deterministic countdown.
     */
    public static void start(Context context, int width, int height, int dpi, long durationMillis) {
        Intent intent = new Intent(context, PreviewRevertService.class)
                .setAction(ACTION_START)
                .putExtra(EXTRA_WIDTH, width)
                .putExtra(EXTRA_HEIGHT, height)
                .putExtra(EXTRA_DPI, dpi)
                .putExtra(EXTRA_DURATION_MILLIS, durationMillis);
        ContextCompat.startForegroundService(context, intent);
    }

    /** Ends the active preview immediately and reverts to the device default, if one is running. */
    public static void revertNow(Context context) {
        Intent intent = new Intent(context, PreviewRevertService.class).setAction(ACTION_REVERT_NOW);
        ContextCompat.startForegroundService(context, intent);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;

        if (ACTION_REVERT_NOW.equals(action)) {
            revertAndStop();
            return START_NOT_STICKY;
        }

        if (ACTION_START.equals(action)) {
            int width = intent.getIntExtra(EXTRA_WIDTH, 0);
            int height = intent.getIntExtra(EXTRA_HEIGHT, 0);
            int dpi = intent.getIntExtra(EXTRA_DPI, 0);
            long durationMillis = intent.getLongExtra(EXTRA_DURATION_MILLIS, DEFAULT_DURATION_MILLIS);
            beginPreview(width, height, dpi, durationMillis);
            return START_NOT_STICKY;
        }

        // No recognized action (e.g. the system redelivering a stale
        // intent) and no preview already running: nothing to do.
        if (!isPreviewActive()) {
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    private void beginPreview(int width, int height, int dpi, long durationMillis) {
        revertAttempted = false;

        startForeground(NOTIFICATION_ID, buildNotification((int) (durationMillis / 1000)));

        try {
            resolutionController = new DisplayResolutionController(getContentResolver());
            resolutionController.setResolution(width, height);
            resolutionController.setDisplayDensity(dpi);
            InternalLogger.i(TAG, "Preview applied: " + width + "x" + height + "@" + dpi + "dpi");
        } catch (ReflectiveOperationException | RuntimeException e) {
            InternalLogger.e(TAG, "Failed to apply preview resolution", e);
            // Nothing was successfully forced, so there's nothing to revert
            // — just stop, no countdown needed.
            remainingSecondsLiveData.postValue(null);
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
            return;
        }

        long totalSeconds = durationMillis / 1000;
        remainingSecondsLiveData.postValue((int) totalSeconds);

        countDownTimer = new CountDownTimer(durationMillis, TICK_INTERVAL_MILLIS) {
            @Override
            public void onTick(long millisUntilFinished) {
                int secondsLeft = (int) Math.ceil(millisUntilFinished / 1000.0);
                remainingSecondsLiveData.postValue(secondsLeft);
                updateNotification(secondsLeft);
            }

            @Override
            public void onFinish() {
                revertAndStop();
            }
        };
        countDownTimer.start();
    }

    private void revertAndStop() {
        if (revertAttempted) {
            return;
        }
        revertAttempted = true;

        if (countDownTimer != null) {
            countDownTimer.cancel();
            countDownTimer = null;
        }

        try {
            if (resolutionController == null) {
                resolutionController = new DisplayResolutionController(getContentResolver());
            }
            resolutionController.clearResolution();
            resolutionController.clearDisplayDensity();
            InternalLogger.i(TAG, "Preview reverted to device default");
        } catch (ReflectiveOperationException | RuntimeException e) {
            InternalLogger.e(TAG, "Failed to revert preview resolution", e);
        }

        remainingSecondsLiveData.postValue(null);
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (countDownTimer != null) {
            countDownTimer.cancel();
            countDownTimer = null;
        }
        // Defensive: if the service is being torn down (e.g. task removed)
        // while a preview is still active and revert hasn't run yet, still
        // attempt it rather than leaving the preview resolution stuck.
        if (!revertAttempted) {
            revertAndStop();
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                getString(R.string.preview_notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
        );
        channel.setDescription(getString(R.string.preview_notification_channel_description));
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification(int secondsLeft) {
        Intent contentIntent = new Intent(this, MainActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent contentPendingIntent = PendingIntent.getActivity(
                this, 0, contentIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Intent revertIntent = new Intent(this, PreviewRevertService.class).setAction(ACTION_REVERT_NOW);
        PendingIntent revertPendingIntent = PendingIntent.getService(
                this, 0, revertIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_preview)
                .setContentTitle(getString(R.string.preview_notification_title))
                .setContentText(getString(R.string.preview_notification_body, secondsLeft))
                .setContentIntent(contentPendingIntent)
                .addAction(0, getString(R.string.preview_notification_action_revert_now), revertPendingIntent)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    private void updateNotification(int secondsLeft) {
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, buildNotification(secondsLeft));
        }
    }
}
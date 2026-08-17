package id.ditzzy.scalara.about;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import id.ditzzy.scalara.app.InternalLogger;

/**
 * Loads contributor avatars from their GitHub URL into an {@link ImageView},
 * with an in-memory cache so scrolling {@link AboutActivity}'s contributor
 * list back and forth doesn't re-download the same avatar repeatedly.
 *
 * <p>Written by hand rather than pulling in an image-loading dependency
 * (Coil, Glide): this app displays at most a handful of small avatars, on
 * one screen, in one session — a full-featured loader's disk caching,
 * request de-duplication, and transformation pipeline is more machinery
 * than that need justifies. See {@link ContributorsRepository}'s class doc
 * for the same reasoning applied to the network call itself.
 *
 * <p>Cache is in-memory only and process-lifetime: it does not persist to
 * disk, so a fresh process re-downloads every avatar once. Given how
 * infrequently the About screen is opened and how small these images are,
 * a disk cache's added complexity (eviction, staleness, concurrent-access
 * handling) isn't worth it here.
 */
public final class AvatarImageLoader {

    private static final String TAG = "AvatarImageLoader";
    private static final int CONNECT_TIMEOUT_MILLIS = 10_000;
    private static final int READ_TIMEOUT_MILLIS = 10_000;

    // Sized in kilobytes of decoded bitmap memory (see sizeOf below), not
    // entry count: a handful of small GitHub avatars (GitHub serves these
    // at modest resolutions already) comfortably fits well under this, so
    // this cache is not expected to evict anything during a normal visit to
    // the About screen.
    private static final int MAX_CACHE_SIZE_KB = 4 * 1024;

    private final LruCache<String, Bitmap> memoryCache = new LruCache<String, Bitmap>(MAX_CACHE_SIZE_KB) {
        @Override
        protected int sizeOf(@NonNull String key, @NonNull Bitmap bitmap) {
            return bitmap.getByteCount() / 1024;
        }
    };

    private final ExecutorService executor = Executors.newFixedThreadPool(4);
    private final Handler mainThreadHandler = new Handler(Looper.getMainLooper());

    /**
     * Loads {@code url} into {@code imageView}, or leaves it as-is on
     * failure. Safe to call repeatedly on a recycled {@code ImageView} (as
     * {@code AboutActivity}'s contributor list does while scrolling):
     * {@code imageView} is tagged with {@code url} before the async load
     * starts, and the result is only applied if that tag still matches
     * {@code url} once the load finishes — so a slower, stale request
     * finishing after the view has been rebound to a different contributor
     * can't clobber the correct, already-loaded image.
     */
    public void load(@NonNull String url, @NonNull ImageView imageView) {
        imageView.setTag(url);

        Bitmap cached = memoryCache.get(url);
        if (cached != null) {
            imageView.setImageBitmap(cached);
            return;
        }

        executor.submit(() -> {
            Bitmap bitmap = downloadBitmapBlocking(url);
            if (bitmap != null) {
                memoryCache.put(url, bitmap);
            }
            mainThreadHandler.post(() -> {
                if (url.equals(imageView.getTag()) && bitmap != null) {
                    imageView.setImageBitmap(bitmap);
                }
            });
        });
    }

    /** Call when the owning Activity is destroyed, so no queued download decodes into a dead screen. */
    public void shutdown() {
        executor.shutdownNow();
    }

    @Nullable
    private Bitmap downloadBitmapBlocking(@NonNull String url) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
            connection.setReadTimeout(READ_TIMEOUT_MILLIS);

            int status = connection.getResponseCode();
            if (status != HttpURLConnection.HTTP_OK) {
                InternalLogger.w(TAG, "Avatar fetch returned HTTP " + status + " for " + url);
                return null;
            }

            try (InputStream inputStream = connection.getInputStream()) {
                return BitmapFactory.decodeStream(inputStream);
            }
        } catch (IOException e) {
            InternalLogger.w(TAG, "Avatar fetch failed for " + url + ": " + e.getMessage());
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
}
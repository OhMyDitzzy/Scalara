package id.ditzzy.scalara.about;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import id.ditzzy.scalara.app.InternalLogger;

/**
 * Fetches Scalara's live contributor list from GitHub's REST API for
 * {@link AboutActivity} — names, avatars, and contribution counts, always
 * current as of whenever the About screen is opened rather than a list
 * baked into the app at build time.
 *
 * <p>Built on plain {@link HttpURLConnection} rather than a networking
 * dependency (OkHttp, Retrofit): this is the only network call anywhere in
 * the app (see the {@code INTERNET} permission's manifest comment), a
 * single unauthenticated GET with no retry/interceptor/caching needs an
 * HTTP client would meaningfully help with, so a dependency's ongoing
 * weight isn't worth taking on for it.
 */
public final class ContributorsRepository {

    private static final String TAG = "ContributorsRepository";

    // No API version pinned via the Accept header: GitHub's contributors
    // endpoint shape (login/avatar_url/html_url/contributions) has been
    // stable across API versions for years, and this app has no dependency
    // on any field precise enough to need pinning.
    private static final String CONTRIBUTORS_URL =
            "https://api.github.com/repos/OhMyDitzzy/Scalara/contributors";

    private static final int CONNECT_TIMEOUT_MILLIS = 10_000;
    private static final int READ_TIMEOUT_MILLIS = 10_000;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainThreadHandler = new Handler(Looper.getMainLooper());
    private final Gson gson = new Gson();

    public interface Callback {
        void onSuccess(@NonNull List<Contributor> contributors);

        void onFailure();
    }

    /**
     * Fetches the contributor list in the background and delivers the
     * result on the main thread via {@code callback}. Safe to call from
     * {@code onCreate}/a retry button click; each call runs independently
     * (no de-duplication of in-flight requests), which is fine given this
     * screen only ever has one fetch outstanding at a time in practice —
     * see {@link AboutActivity}'s retry handling.
     */
    public void fetchContributors(@NonNull Callback callback) {
        executor.submit(() -> {
            List<Contributor> result = fetchContributorsBlocking();
            mainThreadHandler.post(() -> {
                if (result != null) {
                    callback.onSuccess(result);
                } else {
                    callback.onFailure();
                }
            });
        });
    }

    /** Call when the owning Activity is destroyed, so no queued fetch delivers a callback into a dead screen. */
    public void shutdown() {
        executor.shutdownNow();
    }

    @Nullable
    private List<Contributor> fetchContributorsBlocking() {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(CONTRIBUTORS_URL).openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
            connection.setReadTimeout(READ_TIMEOUT_MILLIS);
            connection.setRequestProperty("Accept", "application/vnd.github+json");

            int status = connection.getResponseCode();
            if (status != HttpURLConnection.HTTP_OK) {
                // Covers both real errors (repo renamed/deleted, 404) and
                // GitHub's unauthenticated rate limit (403) — this
                // unauthenticated endpoint is capped at 60 requests/hour
                // per IP, shared across every Scalara install behind the
                // same network. Either way, there's nothing this method can
                // do about it beyond reporting failure; AboutActivity's
                // retry button is the user's recourse.
                InternalLogger.w(TAG, "Contributors fetch returned HTTP " + status);
                return null;
            }

            String body = readBody(connection.getInputStream());
            Type listType = new TypeToken<ArrayList<Contributor>>() {
            }.getType();
            List<Contributor> contributors = gson.fromJson(body, listType);
            return contributors != null ? contributors : new ArrayList<>();
        } catch (IOException e) {
            InternalLogger.w(TAG, "Contributors fetch failed: " + e.getMessage());
            return null;
        } catch (JsonSyntaxException e) {
            // GitHub's response shape changed, or this URL now serves
            // something other than the contributors endpoint's usual JSON
            // array — either way, not something retrying fixes.
            InternalLogger.e(TAG, "Contributors response was not valid JSON", e);
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    @NonNull
    private static String readBody(@NonNull InputStream inputStream) throws IOException {
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }
        }
        return builder.toString();
    }
}
package id.ditzzy.scalara.presets;

import android.content.ContentResolver;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * App-level export/import of the entire saved preset list as a single
 * encrypted {@code .scl} file, reached from {@code MainActivity}'s overflow
 * menu.
 *
 * <p>Distinct from the per-preset "Export" option on
 * {@code PresetOptionsSheet}: that one hands off a single preset (see
 * {@link #submitExportSingle}), while this class's {@link #submitExportAll}
 * covers the user's whole saved list — and only this class supports import,
 * since importing a single preset back into a list that already has it by
 * definition doesn't need a separate one-off flow the way a full-list
 * restore does.
 *
 * <p>File I/O goes through {@link ContentResolver} against a
 * {@link Uri} the caller obtained via the Storage Access Framework
 * ({@code ACTION_CREATE_DOCUMENT} / {@code ACTION_OPEN_DOCUMENT}) — this
 * needs no storage permission on any API level this app supports, since SAF
 * grants access to exactly the one document the user picked rather than
 * broad filesystem access.
 *
 * <p>Every operation here runs on a background thread and delivers its
 * result on the main thread via callback — never call {@link PresetCrypto}
 * or do this class's file I/O directly on the UI thread. {@link PresetCrypto}
 * deliberately runs PBKDF2 at 600,000 iterations (see its class doc), which
 * alone can take the better part of a second on modest hardware; combined
 * with SAF's own I/O latency (real for a local file, and worse yet for a
 * {@code Uri} backed by a cloud-sync provider), running either synchronously
 * on the main thread risks an ANR — which is exactly what every method here
 * is built to avoid.
 *
 * <p><b>Password array ownership:</b> every {@code submit*} method below
 * that takes a {@code password} takes ownership of that array and wipes it
 * (via {@code Arrays.fill(password, '\0')}) itself once the background work
 * finishes, success or failure alike — <em>not</em> the caller. This is a
 * deliberate change from a same-thread design, where a caller's
 * {@code finally}-block wipe immediately after the (then-synchronous) call
 * returned was safe. Once the actual work moved to a background thread,
 * that same caller-side wipe would instead race the background thread still
 * using the array for {@link PresetCrypto}'s key derivation — a caller
 * wiping it "immediately after" a {@code submit*} call now means
 * immediately after <em>queuing</em> the work, not after the work has
 * actually finished using it. Owning the wipe here, at the one place that
 * actually knows when the array's last real use has happened, is what keeps
 * that guarantee correct under async execution. Callers must not wipe the
 * array themselves.
 */
public final class ExportImportManager {

    private final ContentResolver contentResolver;
    private final PresetRepository presetRepository;
    private final Gson gson = new Gson();

    // Single-thread: every export/import call this class receives already
    // waits for user input (a password dialog, a SAF picker) immediately
    // before it, so there's never a real case of two of these needing to
    // run concurrently — a single background thread keeps this simple
    // without giving up any responsiveness a pool would have added.
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainThreadHandler = new Handler(Looper.getMainLooper());

    public ExportImportManager(@NonNull ContentResolver contentResolver, @NonNull PresetRepository presetRepository) {
        this.contentResolver = contentResolver;
        this.presetRepository = presetRepository;
    }

    /** Thrown when writing/reading the chosen document itself fails, independent of encryption. */
    public static final class FileAccessException extends Exception {
        public FileAccessException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /** Call when the owning screen is destroyed, so no queued background work delivers a callback into a dead screen. */
    public void shutdown() {
        executor.shutdownNow();
    }

    // ================================================================
    // Export
    // ================================================================

    /** Result callback for {@link #submitExportAll} and {@link #submitExportSingle}. */
    public interface ExportCallback {
        void onSuccess();

        /** {@code error} is either {@link FileAccessException} or {@link GeneralSecurityException}. */
        void onFailure(@NonNull Exception error);
    }

    /**
     * Encrypts every saved preset and writes the result to
     * {@code destination}, off the main thread. Takes ownership of
     * {@code password} — see this class's doc for why the caller must not
     * wipe it.
     */
    public void submitExportAll(@NonNull Uri destination, @NonNull char[] password, @NonNull ExportCallback callback) {
        submitWrite(destination, presetRepository.getAll(), password, callback);
    }

    /**
     * Encrypts a single preset (wrapped in a one-element list, so the file
     * format matches {@link #submitExportAll} and either kind of
     * {@code .scl} file can be imported the same way) and writes it to
     * {@code destination}, off the main thread. Takes ownership of
     * {@code password} — see this class's doc for why the caller must not
     * wipe it.
     */
    public void submitExportSingle(
            @NonNull Uri destination, @NonNull ResolutionPreset preset, @NonNull char[] password,
            @NonNull ExportCallback callback
    ) {
        List<ResolutionPreset> single = new ArrayList<>(1);
        single.add(preset);
        submitWrite(destination, single, password, callback);
    }

    private void submitWrite(
            @NonNull Uri destination, @NonNull List<ResolutionPreset> presets, @NonNull char[] password,
            @NonNull ExportCallback callback
    ) {
        executor.submit(() -> {
            try {
                writeEncrypted(destination, presets, password);
                mainThreadHandler.post(callback::onSuccess);
            } catch (FileAccessException | GeneralSecurityException e) {
                mainThreadHandler.post(() -> callback.onFailure(e));
            } finally {
                Arrays.fill(password, '\0');
            }
        });
    }

    private void writeEncrypted(@NonNull Uri destination, @NonNull List<ResolutionPreset> presets, @NonNull char[] password)
            throws FileAccessException, GeneralSecurityException {
        String json = gson.toJson(presets);
        byte[] fileBytes = PresetCrypto.encrypt(json, password);

        try (OutputStream out = contentResolver.openOutputStream(destination)) {
            if (out == null) {
                throw new FileAccessException("ContentResolver returned no output stream for destination", null);
            }
            out.write(fileBytes);
        } catch (IOException e) {
            throw new FileAccessException("Failed to write .scl file to destination", e);
        }
    }

    // ================================================================
    // Import
    // ================================================================

    /** How an import should combine with the presets already saved. */
    public enum ImportMode {
        /** Add every imported preset to the existing list, keeping what's already there. */
        MERGE,
        /** Discard the existing list entirely and replace it with the imported one. */
        REPLACE
    }

    /** Result callback for {@link #submitReadImportCandidate}. */
    public interface ReadImportCallback {
        void onSuccess(@NonNull List<ResolutionPreset> candidates);

        void onWrongPassword();

        void onInvalidFile();

        /** {@code error} is either {@link FileAccessException} or {@link GeneralSecurityException}. */
        void onFailure(@NonNull Exception error);
    }

    /**
     * Decrypts {@code source} with {@code password} off the main thread and
     * delivers the presets it contains, without touching
     * {@link PresetRepository} yet — callers are expected to show the user
     * what was found (count, names) before calling
     * {@link #submitApplyImport}, since {@link ImportMode#REPLACE} is
     * destructive and deserves a confirmation step first. Takes ownership
     * of {@code password} — see this class's doc for why the caller must
     * not wipe it.
     */
    public void submitReadImportCandidate(
            @NonNull Uri source, @NonNull char[] password, @NonNull ReadImportCallback callback
    ) {
        executor.submit(() -> {
            try {
                List<ResolutionPreset> candidates = readImportCandidate(source, password);
                mainThreadHandler.post(() -> callback.onSuccess(candidates));
            } catch (PresetCrypto.WrongPasswordException e) {
                mainThreadHandler.post(callback::onWrongPassword);
            } catch (PresetCrypto.InvalidFileException e) {
                mainThreadHandler.post(callback::onInvalidFile);
            } catch (FileAccessException | GeneralSecurityException e) {
                mainThreadHandler.post(() -> callback.onFailure(e));
            } finally {
                Arrays.fill(password, '\0');
            }
        });
    }

    @NonNull
    private List<ResolutionPreset> readImportCandidate(@NonNull Uri source, @NonNull char[] password)
            throws FileAccessException, PresetCrypto.InvalidFileException, PresetCrypto.WrongPasswordException,
            GeneralSecurityException {
        byte[] fileBytes = readAllBytes(source);
        String json = PresetCrypto.decrypt(fileBytes, password);

        Type listType = new TypeToken<ArrayList<ResolutionPreset>>() {
        }.getType();
        try {
            List<ResolutionPreset> presets = gson.fromJson(json, listType);
            return presets != null ? presets : new ArrayList<>();
        } catch (JsonSyntaxException e) {
            // The file decrypted successfully (so the password was right and
            // the GCM tag verified) but what came out isn't valid preset
            // JSON — most likely a .scl file from an incompatible future
            // export format. Reported as InvalidFileException, matching how
            // a corrupt/wrong-format file is reported before decryption even
            // starts, since from the user's perspective both mean "this
            // isn't a file I can import" rather than "wrong password".
            throw new PresetCrypto.InvalidFileException("Decrypted content is not a valid preset list");
        }
    }

    /** Result callback for {@link #submitApplyImport}. */
    public interface ApplyImportCallback {
        void onApplied();
    }

    /**
     * Commits previously-{@link #submitReadImportCandidate}-ed presets into
     * {@link PresetRepository} per {@code mode}, off the main thread. Split
     * from {@link #submitReadImportCandidate} so the caller's confirmation
     * step (and any "N presets found" summary shown in between) happens
     * against data that's already been decrypted and parsed, rather than
     * needing to re-decrypt after the user confirms.
     *
     * <p>{@link PresetRepository} itself is a thin {@code SharedPreferences}
     * wrapper (see its class doc) cheap enough not to need a background
     * thread on its own — this still runs through {@link #executor} so it's
     * naturally serialized after the {@link #submitReadImportCandidate} call
     * that produced {@code imported}, and so callers have one consistent
     * "background work, then a main-thread callback" shape for every
     * operation in this class rather than a special case for this one.
     *
     * <p>Callers are responsible for refreshing whatever preset list they
     * show on screen once {@code callback} fires — this method only
     * persists the change to {@link PresetRepository}, the same as
     * {@link PresetRepository#saveAll} itself; it has no way to reach a
     * screen's own state (e.g. {@code MainViewModel}'s {@code LiveData}) to
     * refresh it directly.
     */
    public void submitApplyImport(
            @NonNull List<ResolutionPreset> imported, @NonNull ImportMode mode, @NonNull ApplyImportCallback callback
    ) {
        executor.submit(() -> {
            applyImport(imported, mode);
            mainThreadHandler.post(callback::onApplied);
        });
    }

    private void applyImport(@NonNull List<ResolutionPreset> imported, @NonNull ImportMode mode) {
        if (mode == ImportMode.REPLACE) {
            presetRepository.saveAll(imported);
            return;
        }

        List<ResolutionPreset> merged = presetRepository.getAll();
        merged.addAll(imported);
        presetRepository.saveAll(merged);
    }

    @NonNull
    private byte[] readAllBytes(@NonNull Uri source) throws FileAccessException {
        try (InputStream in = contentResolver.openInputStream(source)) {
            if (in == null) {
                throw new FileAccessException("ContentResolver returned no input stream for source", null);
            }
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int bytesRead;
            while ((bytesRead = in.read(chunk)) != -1) {
                buffer.write(chunk, 0, bytesRead);
            }
            return buffer.toByteArray();
        } catch (IOException e) {
            throw new FileAccessException("Failed to read .scl file from source", e);
        }
    }
}

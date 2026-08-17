package id.ditzzy.scalara.main;

import android.app.Application;
import android.graphics.Point;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.ArrayList;
import java.util.List;

import id.ditzzy.scalara.R;
import id.ditzzy.scalara.app.InternalLogger;
import id.ditzzy.scalara.presets.PresetRepository;
import id.ditzzy.scalara.presets.ResolutionPreset;
import id.ditzzy.scalara.resolution.DeviceResolution;
import id.ditzzy.scalara.resolution.DisplayResolutionController;
import id.ditzzy.scalara.resolution.PreviewRevertService;
import id.ditzzy.scalara.resolution.ResolutionUtils;

/**
 * Backs {@code MainActivity}: owns the device's default resolution, the
 * saved preset list, and the "is a preview currently running" state
 * (mirrored from {@link PreviewRevertService}).
 *
 * <p>This ViewModel never touches {@code DisplayResolutionController} or
 * {@code PreviewRevertService} to actually change the resolution itself —
 * doing so here would tie a configuration-change-surviving object to a
 * specific in-flight system call, and a rotation mid-call would leak or
 * duplicate it. Instead, actions that need to apply a resolution raise an
 * {@link ApplyResolutionRequest} via {@link #applyRequestEvent}, which
 * {@code MainActivity} — as the {@code Context}-owning, lifecycle-bound
 * party — is responsible for actually carrying out.
 */
public class MainViewModel extends AndroidViewModel {

    private static final String TAG = "MainViewModel";

    private final PresetRepository presetRepository;

    private final MutableLiveData<DeviceResolution> defaultResolution = new MutableLiveData<>();
    private final MutableLiveData<List<ResolutionPreset>> presets = new MutableLiveData<>(new ArrayList<>());

    private final MediatorLiveData<Integer> previewSecondsRemaining = new MediatorLiveData<>();

    private final MutableLiveData<ConsumableEvent<ApplyResolutionRequest>> applyRequestEvent = new MutableLiveData<>();
    private final MutableLiveData<ConsumableEvent<Integer>> messageEvent = new MutableLiveData<>();

    public MainViewModel(@NonNull Application application) {
        super(application);
        this.presetRepository = new PresetRepository(application);

        previewSecondsRemaining.addSource(
                PreviewRevertService.getRemainingSecondsLiveData(),
                previewSecondsRemaining::setValue
        );

        loadDefaultResolution();
        reloadPresets();
    }

    // ================================================================
    // Exposed state
    // ================================================================

    /** The device's real (unforced) resolution and density, read once at startup. */
    public LiveData<DeviceResolution> getDefaultResolution() {
        return defaultResolution;
    }

    /** All saved presets, oldest first. */
    public LiveData<List<ResolutionPreset>> getPresets() {
        return presets;
    }

    /**
     * Seconds remaining in an active preview, or {@code null} when no
     * preview is running. Mirrors {@link PreviewRevertService}'s static
     * LiveData so the UI has one place to observe preview state regardless
     * of which screen started it.
     */
    public LiveData<Integer> getPreviewSecondsRemaining() {
        return previewSecondsRemaining;
    }

    /** Fires when a resolution change (permanent or preview) should actually be carried out. */
    public LiveData<ConsumableEvent<ApplyResolutionRequest>> getApplyRequestEvent() {
        return applyRequestEvent;
    }

    /** Fires with a string resource id for a one-off message (Snackbar/Toast) to show. */
    public LiveData<ConsumableEvent<Integer>> getMessageEvent() {
        return messageEvent;
    }

    // ================================================================
    // Default resolution card
    // ================================================================

    private void loadDefaultResolution() {
        try {
            DisplayResolutionController controller =
                    new DisplayResolutionController(getApplication().getContentResolver());
            Point real = controller.getRealResolution();
            int dpi = controller.getRealDensity();
            defaultResolution.setValue(new DeviceResolution(real.x, real.y, dpi));
        } catch (ReflectiveOperationException | RuntimeException e) {
            InternalLogger.e(TAG, "Failed to read device default resolution", e);
            // Fall back to whatever DisplayMetrics currently reports. If a
            // forced resolution is already active this won't be the true
            // physical default, but it keeps the card populated instead of
            // blank, and getRealResolution() is retried fresh next launch.
            defaultResolution.setValue(ResolutionUtils.getCurrentResolution(getApplication()));
        }
    }

    /** Re-applies the device's default resolution and density, clearing any forced values. */
    public void resetToDefault() {
        try {
            DisplayResolutionController controller =
                    new DisplayResolutionController(getApplication().getContentResolver());
            controller.clearResolution();
            controller.clearDisplayDensity();
            messageEvent.setValue(new ConsumableEvent<>(R.string.message_reset_success));
        } catch (ReflectiveOperationException | RuntimeException e) {
            InternalLogger.e(TAG, "Failed to reset resolution to default", e);
            messageEvent.setValue(new ConsumableEvent<>(R.string.message_reset_failed));
        }
    }

    // ================================================================
    // Preset list
    // ================================================================

    /**
     * Re-reads the saved preset list from {@link PresetRepository} and
     * republishes it via {@link #getPresets()}.
     *
     * <p>Called automatically by every mutation below that goes through
     * this ViewModel ({@link #savePreset}, {@link #savePresetAndApply},
     * {@link #deletePreset}). Exposed as {@code public} specifically for
     * {@code ExportImportSheet}: an import commits its result straight to
     * {@link PresetRepository} via {@code ExportImportManager}, bypassing
     * this ViewModel entirely (that manager has no reference to a
     * particular screen's ViewModel to update instead), so without an
     * explicit call here afterward this LiveData — and the list on screen —
     * would keep showing the pre-import data until the app process was
     * recreated and this ViewModel's constructor ran {@link #reloadPresets}
     * again from scratch.
     */
    public void reloadPresets() {
        presets.setValue(presetRepository.getAll());
    }

    /** Saves a new preset without applying it. */
    public void savePreset(String name, int width, int height, int dpi) {
        presetRepository.add(new ResolutionPreset(name, width, height, dpi));
        reloadPresets();
        messageEvent.setValue(new ConsumableEvent<>(R.string.message_preset_saved));
    }

    /** Saves a new preset and immediately applies it permanently. */
    public void savePresetAndApply(String name, int width, int height, int dpi) {
        presetRepository.add(new ResolutionPreset(name, width, height, dpi));
        reloadPresets();
        applyRequestEvent.setValue(new ConsumableEvent<>(
                new ApplyResolutionRequest(width, height, dpi, ApplyResolutionRequest.Mode.PERMANENT)
        ));
    }

    /** Starts a timed preview without saving anything. */
    public void previewOnly(int width, int height, int dpi) {
        applyRequestEvent.setValue(new ConsumableEvent<>(
                new ApplyResolutionRequest(width, height, dpi, ApplyResolutionRequest.Mode.PREVIEW)
        ));
    }

    /** Applies an already-saved preset permanently. */
    public void applyExistingPreset(ResolutionPreset preset) {
        applyRequestEvent.setValue(new ConsumableEvent<>(
                new ApplyResolutionRequest(preset.getWidth(), preset.getHeight(), preset.getDpi(),
                        ApplyResolutionRequest.Mode.PERMANENT)
        ));
    }

    /** Deletes a saved preset. */
    public void deletePreset(ResolutionPreset preset) {
        presetRepository.remove(preset.getId());
        reloadPresets();
        messageEvent.setValue(new ConsumableEvent<>(R.string.message_preset_deleted));
    }
}

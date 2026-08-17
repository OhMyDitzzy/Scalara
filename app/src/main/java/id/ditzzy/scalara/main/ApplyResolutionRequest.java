package id.ditzzy.scalara.main;

/**
 * Tells {@code MainActivity} to perform a resolution change: either apply it
 * permanently right away, or run it as a timed preview through
 * {@code PreviewRevertService}.
 *
 * <p>{@code MainViewModel} raises this as a {@link ConsumableEvent} rather
 * than performing the change itself, since actually calling
 * {@code DisplayResolutionController} / starting a service are
 * platform/Context-bound side effects that belong in the Activity, not the
 * ViewModel.
 */
public final class ApplyResolutionRequest {

    public enum Mode {
        /** Apply immediately and leave it applied. */
        PERMANENT,
        /** Apply immediately, then auto-revert after the preview duration. */
        PREVIEW
    }

    private final int width;
    private final int height;
    private final int dpi;
    private final Mode mode;

    public ApplyResolutionRequest(int width, int height, int dpi, Mode mode) {
        this.width = width;
        this.height = height;
        this.dpi = dpi;
        this.mode = mode;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public int getDpi() {
        return dpi;
    }

    public Mode getMode() {
        return mode;
    }
}

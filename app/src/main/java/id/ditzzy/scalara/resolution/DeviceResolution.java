package id.ditzzy.scalara.resolution;

/**
 * A width/height/DPI triple describing either the device's real (default)
 * resolution or a resolution the user wants to apply.
 */
public final class DeviceResolution {

    private final int width;
    private final int height;
    private final int dpi;

    public DeviceResolution(int width, int height, int dpi) {
        this.width = width;
        this.height = height;
        this.dpi = dpi;
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
}

package id.ditzzy.scalara.presets;

import java.util.UUID;

/**
 * A user-named width/height/DPI combination saved for later reuse.
 *
 * <p>Serialized as-is to JSON by {@link PresetRepository} (via Gson), so
 * field names and types here are part of the on-disk format — renaming a
 * field would silently drop existing presets on the next save/load cycle
 * rather than fail loudly, so avoid renaming without a migration path.
 */
public final class ResolutionPreset {

    private final String id;
    private String name;
    private int width;
    private int height;
    private int dpi;
    private final long createdAtMillis;

    public ResolutionPreset(String name, int width, int height, int dpi) {
        this(UUID.randomUUID().toString(), name, width, height, dpi, System.currentTimeMillis());
    }

    public ResolutionPreset(String id, String name, int width, int height, int dpi, long createdAtMillis) {
        this.id = id;
        this.name = name;
        this.width = width;
        this.height = height;
        this.dpi = dpi;
        this.createdAtMillis = createdAtMillis;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getWidth() {
        return width;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public int getHeight() {
        return height;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public int getDpi() {
        return dpi;
    }

    public void setDpi(int dpi) {
        this.dpi = dpi;
    }

    public long getCreatedAtMillis() {
        return createdAtMillis;
    }
}

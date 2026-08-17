package id.ditzzy.scalara.presets;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Persists the user's saved {@link ResolutionPreset} list as a single JSON
 * array in {@link SharedPreferences}.
 *
 * <p>Mirrors {@code AppPreferences}: a thin wrapper with no caching layer of
 * its own, since the preset list is expected to stay small (user-entered,
 * one at a time via a form) and every read/write here is already cheap
 * relative to a UI interaction.
 */
public final class PresetRepository {

    private static final String PREFS_NAME = "scalara_presets_prefs";
    private static final String KEY_PRESETS = "presets_json";

    private final SharedPreferences prefs;
    private final Gson gson = new Gson();

    public PresetRepository(Context context) {
        this.prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /** All saved presets, oldest first. Never null; empty list if none saved yet. */
    public List<ResolutionPreset> getAll() {
        String json = prefs.getString(KEY_PRESETS, null);
        if (json == null) {
            return new ArrayList<>();
        }

        Type listType = new TypeToken<ArrayList<ResolutionPreset>>() {
        }.getType();
        List<ResolutionPreset> presets = gson.fromJson(json, listType);
        return presets != null ? presets : new ArrayList<>();
    }

    /** Appends a new preset to the saved list. */
    public void add(ResolutionPreset preset) {
        List<ResolutionPreset> presets = getAll();
        presets.add(preset);
        saveAll(presets);
    }

    /** Removes the preset with the given id, if present. */
    public void remove(String presetId) {
        List<ResolutionPreset> presets = getAll();
        presets.removeIf(preset -> preset.getId().equals(presetId));
        saveAll(presets);
    }

    /** Replaces the entire saved preset list, e.g. after an import. */
    public void saveAll(List<ResolutionPreset> presets) {
        prefs.edit()
                .putString(KEY_PRESETS, gson.toJson(presets != null ? presets : Collections.emptyList()))
                .apply();
    }
}

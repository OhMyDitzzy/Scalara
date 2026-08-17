package id.ditzzy.scalara.main;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Objects;

import id.ditzzy.scalara.R;
import id.ditzzy.scalara.databinding.ItemResolutionPresetBinding;
import id.ditzzy.scalara.presets.ResolutionPreset;

/**
 * Renders the saved preset list. Each row shows the preset's name and its
 * width/height/DPI, with an overflow button that {@code MainActivity} wires
 * up to show the Apply/Delete/Export bottom sheet for that preset.
 */
public class PresetAdapter extends ListAdapter<ResolutionPreset, PresetAdapter.PresetViewHolder> {

    /** Invoked when the row itself (not the overflow button) is tapped. */
    public interface OnPresetClickListener {
        void onPresetClicked(@NonNull ResolutionPreset preset);
    }

    /** Invoked when a row's overflow (three-dot) button is tapped. */
    public interface OnPresetOverflowClickListener {
        void onOverflowClicked(@NonNull ResolutionPreset preset, @NonNull View anchor);
    }

    private static final DiffUtil.ItemCallback<ResolutionPreset> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<ResolutionPreset>() {
                @Override
                public boolean areItemsTheSame(@NonNull ResolutionPreset oldItem, @NonNull ResolutionPreset newItem) {
                    return oldItem.getId().equals(newItem.getId());
                }

                @Override
                public boolean areContentsTheSame(@NonNull ResolutionPreset oldItem, @NonNull ResolutionPreset newItem) {
                    return Objects.equals(oldItem.getName(), newItem.getName())
                            && oldItem.getWidth() == newItem.getWidth()
                            && oldItem.getHeight() == newItem.getHeight()
                            && oldItem.getDpi() == newItem.getDpi();
                }
            };

    private final OnPresetClickListener onPresetClickListener;
    private final OnPresetOverflowClickListener onOverflowClickListener;

    public PresetAdapter(
            @NonNull OnPresetClickListener onPresetClickListener,
            @NonNull OnPresetOverflowClickListener onOverflowClickListener
    ) {
        super(DIFF_CALLBACK);
        this.onPresetClickListener = onPresetClickListener;
        this.onOverflowClickListener = onOverflowClickListener;
    }

    @NonNull
    @Override
    public PresetViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemResolutionPresetBinding binding = ItemResolutionPresetBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false
        );
        return new PresetViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull PresetViewHolder holder, int position) {
        holder.bind(getItem(position), onPresetClickListener, onOverflowClickListener);
    }

    static final class PresetViewHolder extends RecyclerView.ViewHolder {

        private final ItemResolutionPresetBinding binding;

        PresetViewHolder(@NonNull ItemResolutionPresetBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(
                @NonNull ResolutionPreset preset,
                @NonNull OnPresetClickListener onPresetClickListener,
                @NonNull OnPresetOverflowClickListener onOverflowClickListener
        ) {
            binding.textPresetName.setText(preset.getName());
            binding.textPresetDetails.setText(
                    binding.getRoot().getContext().getString(
                            R.string.preset_details_format,
                            preset.getWidth(), preset.getHeight(), preset.getDpi()
                    )
            );

            binding.getRoot().setOnClickListener(v -> onPresetClickListener.onPresetClicked(preset));
            binding.buttonPresetOverflow.setOnClickListener(
                    v -> onOverflowClickListener.onOverflowClicked(preset, v)
            );
        }
    }
}
package id.ditzzy.scalara.about;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import id.ditzzy.scalara.R;
import id.ditzzy.scalara.app.InternalLogger;
import id.ditzzy.scalara.databinding.ItemContributorBinding;

/**
 * Renders {@link AboutActivity}'s live contributor list. A plain
 * {@link RecyclerView.Adapter} rather than {@code ListAdapter}/{@code DiffUtil}
 * — like {@code SettingsActivity}'s {@code LanguageOptionAdapter}, this list
 * is set once per successful fetch and never updates in place afterward
 * (a retry replaces it wholesale via {@link #submitList}), so there's no
 * ongoing diffing to do.
 */
public final class ContributorAdapter extends RecyclerView.Adapter<ContributorAdapter.ViewHolder> {

    private static final String TAG = "ContributorAdapter";

    private final List<Contributor> contributors = new ArrayList<>();
    private final AvatarImageLoader avatarImageLoader;

    public ContributorAdapter(@NonNull AvatarImageLoader avatarImageLoader) {
        this.avatarImageLoader = avatarImageLoader;
    }

    public void submitList(@NonNull List<Contributor> newContributors) {
        contributors.clear();
        contributors.addAll(newContributors);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemContributorBinding binding = ItemContributorBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false
        );
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Contributor contributor = contributors.get(position);
        Context context = holder.binding.getRoot().getContext();

        holder.binding.textContributorName.setText(contributor.getLogin());
        holder.binding.textContributorContributions.setText(
                context.getString(R.string.about_contributor_contributions_format, contributor.getContributions())
        );

        String avatarUrl = contributor.getAvatarUrl();
        if (avatarUrl != null) {
            avatarImageLoader.load(avatarUrl, holder.binding.imageContributorAvatar);
        }

        holder.binding.getRoot().setOnClickListener(v -> openProfile(context, contributor));
    }

    @Override
    public int getItemCount() {
        return contributors.size();
    }

    private void openProfile(@NonNull Context context, @NonNull Contributor contributor) {
        String profileUrl = contributor.getProfileUrl();
        if (profileUrl == null) {
            return;
        }
        try {
            context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(profileUrl)));
        } catch (ActivityNotFoundException e) {
            // No browser (or nothing else registered for http/https links)
            // is installed to handle this — an edge case not worth a
            // user-facing error for, since there's nothing actionable the
            // user could do about it from here.
            InternalLogger.w(TAG, "No activity found to open contributor profile: " + profileUrl);
        }
    }

    static final class ViewHolder extends RecyclerView.ViewHolder {
        final ItemContributorBinding binding;

        ViewHolder(@NonNull ItemContributorBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
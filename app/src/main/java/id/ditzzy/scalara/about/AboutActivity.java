package id.ditzzy.scalara.about;

import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import java.util.List;

import id.ditzzy.scalara.R;
import id.ditzzy.scalara.app.InternalLogger;
import id.ditzzy.scalara.databinding.ActivityAboutBinding;

/**
 * About screen reachable from {@code MainActivity}'s overflow menu: app
 * name/version/description, a link to the GitHub repository, and a live
 * contributor list.
 *
 * <p>The contributor list is fetched from GitHub at runtime (see
 * {@link ContributorsRepository}) rather than committed to the app as a
 * fixed list, so it always reflects who has actually contributed — no app
 * update is needed for a new contributor to appear here.
 */
public class AboutActivity extends AppCompatActivity {

    private static final String TAG = "AboutActivity";

    /** Kept in sync with app/build.gradle.kts's own repository URL comment near the version-derivation logic. */
    private static final String REPOSITORY_URL = "https://github.com/OhMyDitzzy/Scalara";

    private ActivityAboutBinding binding;
    private final ContributorsRepository contributorsRepository = new ContributorsRepository();
    private final AvatarImageLoader avatarImageLoader = new AvatarImageLoader();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityAboutBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.toolbar.setNavigationOnClickListener(v -> finish());
        binding.textAboutVersion.setText(getString(R.string.about_version, resolveVersionName()));

        binding.buttonViewRepository.setOnClickListener(v -> openRepository());

        setUpContributorsList();
        loadContributors();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Both hold their own background executor; shutting them down here
        // means a fetch/decode still in flight when the user backs out of
        // this screen doesn't run to completion (and try to touch views)
        // after there's nothing left to deliver its result to.
        contributorsRepository.shutdown();
        avatarImageLoader.shutdown();
        this.binding = null;
    }

    private String resolveVersionName() {
        try {
            // getPackageInfo(String, int) still works on every API level
            // this app supports. The API 33+ replacement (the
            // PackageInfoFlags overload) exists to support flag values that
            // don't fit in an int, which this call (flags=0) never needed.
            @SuppressWarnings("deprecation")
            PackageInfo packageInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            return packageInfo.versionName != null ? packageInfo.versionName : getString(R.string.about_version_unknown);
        } catch (PackageManager.NameNotFoundException e) {
            InternalLogger.w(TAG, "Could not resolve own package info for version display", e);
            return getString(R.string.about_version_unknown);
        }
    }

    private void openRepository() {
        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(REPOSITORY_URL)));
    }

    // ================================================================
    // Contributors
    // ================================================================

    private void setUpContributorsList() {
        binding.listContributors.setLayoutManager(new LinearLayoutManager(this));
        binding.listContributors.setAdapter(new ContributorAdapter(avatarImageLoader));
        binding.buttonRetryContributors.setOnClickListener(v -> loadContributors());
    }

    private void loadContributors() {
        showContributorsLoading();
        contributorsRepository.fetchContributors(new ContributorsRepository.Callback() {
            @Override
            public void onSuccess(List<Contributor> contributors) {
                if (binding == null) {
                    // Activity was destroyed between the fetch starting and
                    // this callback arriving; nothing left to update.
                    return;
                }
                if (contributors.isEmpty()) {
                    // Reached in practice only if the repository genuinely
                    // has no contributors recorded yet — shown as the same
                    // error state as a fetch failure, since "empty list" and
                    // "couldn't load the list" both mean there's nothing
                    // useful to show the user here, and a distinct
                    // near-impossible empty state isn't worth a separate
                    // string/layout branch.
                    showContributorsError();
                    return;
                }
                showContributorsLoaded(contributors);
            }

            @Override
            public void onFailure() {
                if (binding == null) {
                    return;
                }
                showContributorsError();
            }
        });
    }

    private void showContributorsLoading() {
        binding.layoutContributorsLoading.setVisibility(View.VISIBLE);
        binding.layoutContributorsError.setVisibility(View.GONE);
        binding.listContributors.setVisibility(View.GONE);
    }

    private void showContributorsError() {
        binding.layoutContributorsLoading.setVisibility(View.GONE);
        binding.layoutContributorsError.setVisibility(View.VISIBLE);
        binding.listContributors.setVisibility(View.GONE);
    }

    private void showContributorsLoaded(List<Contributor> contributors) {
        binding.layoutContributorsLoading.setVisibility(View.GONE);
        binding.layoutContributorsError.setVisibility(View.GONE);
        binding.listContributors.setVisibility(View.VISIBLE);
        ((ContributorAdapter) binding.listContributors.getAdapter()).submitList(contributors);
    }
}
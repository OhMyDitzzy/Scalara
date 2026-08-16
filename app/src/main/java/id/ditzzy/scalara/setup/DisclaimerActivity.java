package id.ditzzy.scalara.setup;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.widget.NestedScrollView;

import id.ditzzy.scalara.databinding.ActivityDisclaimerBinding;

/**
 * First-run consent screen. Shown exactly once per install, before the
 * permission setup wizard — gated by {@link AppPreferences#isDisclaimerAccepted()}
 * in {@link SplashActivity}.
 *
 * <p>The "I understand and agree" button starts disabled and only becomes
 * enabled once the user has scrolled the disclaimer text to the bottom, so
 * acceptance can't happen without at least passing over the whole notice.
 */
public class DisclaimerActivity extends AppCompatActivity {

    /**
     * Scroll slack, in pixels, to tolerate rounding/measurement differences
     * across devices when deciding whether the user reached the bottom.
     */
    private static final int SCROLL_BOTTOM_SLOP_PX = 24;

    private ActivityDisclaimerBinding binding;
    private boolean reachedBottom = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityDisclaimerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.buttonAccept.setOnClickListener(v -> onAccept());
        binding.buttonDecline.setOnClickListener(v -> onDecline());

        binding.scrollView.setOnScrollChangeListener(
                (NestedScrollView.OnScrollChangeListener) (v, scrollX, scrollY, oldScrollX, oldScrollY) ->
                        checkIfReachedBottom()
        );

        // Covers the case where the content is short enough to not need
        // scrolling at all (e.g. a large-font accessibility setting shrinks
        // the visible text, or a tall/tablet screen) — don't strand the
        // user with a permanently disabled button.
        binding.scrollView.post(this::checkIfReachedBottom);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        this.binding = null;
    }

    private void checkIfReachedBottom() {
        if (reachedBottom || binding == null) {
            return;
        }

        NestedScrollView scrollView = binding.scrollView;
        View content = scrollView.getChildAt(0);
        if (content == null) {
            return;
        }

        int scrollRange = content.getHeight() - scrollView.getHeight();
        boolean atBottom = scrollView.getScrollY() >= scrollRange - SCROLL_BOTTOM_SLOP_PX;

        if (atBottom) {
            reachedBottom = true;
            binding.buttonAccept.setEnabled(true);
            binding.scrollHint.setVisibility(View.GONE);
        }
    }

    private void onAccept() {
        new AppPreferences(this).setDisclaimerAccepted(true);
        startActivity(new Intent(this, SetupActivity.class));
        finish();
    }

    private void onDecline() {
        // No permission is granted, nothing was set up — closing the app is
        // the only coherent outcome of declining the disclaimer.
        finishAffinity();
    }
}

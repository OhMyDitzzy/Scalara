package com.ditzdev.scalara.activity;

import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.viewpager2.widget.ViewPager2;
import com.ditzdev.scalara.R;
import com.ditzdev.scalara.adapter.SetupOnBoardingAdapter;
import com.ditzdev.scalara.databinding.ActivitySetupBinding;
import com.ditzdev.scalara.utils.Perm;

public class SetupActivity extends AppCompatActivity {

    private ActivitySetupBinding binding;
    private SetupOnBoardingAdapter adapter;
    private ImageView[] indicators;
    private int totalPages = 3;
    private boolean hasPerm;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySetupBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        adapter = new SetupOnBoardingAdapter(this);
        binding.viewPager.setAdapter(adapter);
        hasPerm = Perm.checkSecureSettingsPerm(this);

        // Should be in phase 2, we lock the swipe from ViewPager2
        // However, if we have permission, then allow swipe.
        // Note that ViewPager2 will block all directions. However,
        // We just need to focus on the application permissions.
        // TODO: We have to find another way so that during phase 2,
        // We can swipe back, but not swipe next. Note That setCurrentItem parameter
        // smoothScroll can't solve this problem and causes a glitch effect.
        binding.viewPager.setUserInputEnabled(false);
        binding.viewPager.registerOnPageChangeCallback(
                new ViewPager2.OnPageChangeCallback() {
                    @Override
                    public void onPageSelected(int position) {
                        super.onPageSelected(position);
                        updateProgress(position);
                        updateButtons(position);
                    }
                });

        setupProgress();
        setupButtons();
    }

    private void setupProgress() {
        binding.progressIndicator.setMax(totalPages - 1);
        binding.progressIndicator.setProgress(0);
    }

    private void updateProgress(int position) {
        binding.progressIndicator.setProgress(position, true);
    }

    private void setupButtons() {
        binding.btnNext.setOnClickListener(
                v -> {
                    int currentItem = binding.viewPager.getCurrentItem();
                    if (currentItem < totalPages - 1) {
                        binding.viewPager.setCurrentItem(currentItem + 1, true);
                    } else {
                        // TODO: Add Logic to navigate to MainActivity
                        // Is it suitable to use sharedPrefs?
                        finish();
                    }
                });

        binding.btnBack.setOnClickListener(
                v -> {
                    int currentItem = binding.viewPager.getCurrentItem();
                    if (currentItem > 0) {
                        binding.viewPager.setCurrentItem(currentItem - 1, true);
                    }
                });

        updateButtons(0);
    }

    private void updateButtons(int position) {
        binding.btnNext.setEnabled(true);
        if (position == 0) {
            binding.btnBack.setVisibility(View.INVISIBLE);
        } else {
            binding.btnBack.setVisibility(View.VISIBLE);
            // During setup phase 2, the next button is automatically disabled.
            // However, if permission is detected to have been
            // granted, then we will activate it.
            if (position == 1) {
                binding.btnNext.setEnabled(hasPerm);
            }
        }

        if (position == totalPages - 1) {
            binding.btnNext.setIcon(ContextCompat.getDrawable(this, R.drawable.check));
            binding.btnBack.setVisibility(View.GONE);
        } else {
            binding.btnNext.setIcon(ContextCompat.getDrawable(this, R.drawable.arrow_forward));
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        binding = null;
    }

    @Override
    protected void onResume() {
        super.onResume();
        hasPerm = Perm.checkSecureSettingsPerm(this);

        int pos = binding.viewPager.getCurrentItem();
        updateButtons(pos);
    }
}

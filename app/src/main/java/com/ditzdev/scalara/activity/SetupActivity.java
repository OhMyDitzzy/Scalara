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

public class SetupActivity extends AppCompatActivity {

  private ActivitySetupBinding binding;
  private SetupOnBoardingAdapter adapter;
  private ImageView[] indicators;
  private int totalPages = 3;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    binding = ActivitySetupBinding.inflate(getLayoutInflater());
    setContentView(binding.getRoot());

    adapter = new SetupOnBoardingAdapter(this);

    binding.viewPager.setAdapter(adapter);

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
        binding.btnNext.setOnClickListener(v -> {
            int currentItem = binding.viewPager.getCurrentItem();
            if (currentItem < totalPages - 1) {
                binding.viewPager.setCurrentItem(currentItem + 1, true);
            } else {
                // TODO: Add Logic to navigate to MainActivity
                // Is it suitable to use sharedPrefs?
                finish();
            }
        });

        binding.btnBack.setOnClickListener(v -> {
            int currentItem = binding.viewPager.getCurrentItem();
            if (currentItem > 0) {
                binding.viewPager.setCurrentItem(currentItem - 1, true);
            }
        });

        updateButtons(0);
    }

    private void updateButtons(int position) {
        if (position == 0) {
            binding.btnBack.setVisibility(View.INVISIBLE);
        } else {
            binding.btnBack.setVisibility(View.VISIBLE);
        }

        if (position == totalPages - 1) {
            binding.btnNext.setIcon(ContextCompat.getDrawable(this, R.drawable.check));
            binding.btnBack.setVisibility(View.GONE);
        } else {
            binding.btnNext.setIcon(ContextCompat.getDrawable(this, R.drawable.arrow_forward));
        }
    }
}

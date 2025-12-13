package com.ditzdev.scalara.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.ditzdev.scalara.R;

public class StepThree extends Fragment {

    private ImageView ivIcon;
    private TextView tvTitle;
    private TextView tvDescription;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, 
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_step3, container, false);
        
        initViews(view);
        setupContent();
        
        return view;
    }

    private void initViews(View view) {
        ivIcon = view.findViewById(R.id.ivIcon);
        tvTitle = view.findViewById(R.id.tvTitle);
        tvDescription = view.findViewById(R.id.tvDescription);
    }

    private void setupContent() {
        // Di sini bisa custom logic untuk Setup 3
        // Misalnya finalisasi setup, save preferences, dll
        
        // Contoh: tampilkan summary dari setup sebelumnya
        // tvDescription.setText("Summary dari Setup 1 dan 2...");
        
        // Contoh: prepare untuk finish onboarding
        // prepareFinishOnboarding();
    }

    private void prepareFinishOnboarding() {
        // Save onboarding completion status
        // SharedPreferences prefs = requireActivity().getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
        // prefs.edit().putBoolean("onboarding_completed", true).apply();
    }
}

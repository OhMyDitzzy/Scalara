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

public class StepOne extends Fragment {

    private ImageView ivIcon;
    private TextView tvTitle;
    private TextView tvDescription;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, 
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_step1, container, false);
        
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
        // Di sini bisa custom logic untuk Setup 1
        // Misalnya load data, animasi, dll
        
        // Contoh: ubah icon programmatically
        // ivIcon.setImageResource(R.drawable.custom_icon);
        
        // Contoh: ubah text programmatically
        // tvTitle.setText("Custom Setup 1 Title");
        // tvDescription.setText("Custom description");
    }
}
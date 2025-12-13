package com.ditzdev.scalara.activity;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import com.ditzdev.scalara.databinding.ActivityMainBinding;

public class MainActivity extends AppCompatActivity {
    private ActivityMainBinding binding;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        binding.textView.setText("Hello, Basic Activity!");
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}
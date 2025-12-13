package com.ditzdev.scalara.adapter;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import com.ditzdev.scalara.fragment.StepOne;
import com.ditzdev.scalara.fragment.StepThree;
import com.ditzdev.scalara.fragment.StepTwo;
import java.util.ArrayList;
import java.util.List;

public class SetupOnBoardingAdapter extends FragmentStateAdapter {

    private List<Fragment> fragments;

    public SetupOnBoardingAdapter(@NonNull FragmentActivity fragmentActivity) {
        super(fragmentActivity);
        this.fragments = new ArrayList<>();
        setupFragments();
    }

    private void setupFragments() {
        fragments.add(new StepOne());
        fragments.add(new StepTwo());
        fragments.add(new StepThree());
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        return fragments.get(position);
    }

    @Override
    public int getItemCount() {
        return fragments.size();
    }
}
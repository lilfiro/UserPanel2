package com.example.A_Soft;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import java.util.ArrayList;
import java.util.List;

public class MyPagerAdapter extends FragmentStateAdapter {
    private final List<Fragment> fragments = new ArrayList<>();
    private final List<String> fragmentTitles = new ArrayList<>();
    private String ficheNo;

    public MyPagerAdapter(@NonNull FragmentActivity fragmentActivity) {
        super(fragmentActivity);
    }

    // Method to set the ficheNo
    public void setFicheNo(String ficheNo) {
        this.ficheNo = ficheNo;
    }

    // Add fragments and handle specific cases (e.g., Sevkiyat_QR)
    public void addFragment(Fragment fragment, String title) {
        if (fragment instanceof Sevkiyat_QR && ficheNo != null) {
            // Create a new instance of Sevkiyat_QR with ficheNo
            fragment = Sevkiyat_QR.newInstance(ficheNo);
        }
        fragments.add(fragment);
        fragmentTitles.add(title);
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

    // Method to retrieve the title of a specific fragment
    public CharSequence getPageTitle(int position) {
        return fragmentTitles.get(position);
    }
}

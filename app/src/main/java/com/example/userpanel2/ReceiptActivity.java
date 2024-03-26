package com.example.userpanel2;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentManager; // Import FragmentManager
import androidx.viewpager2.widget.ViewPager2;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

public class ReceiptActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.receipt_activity);

        TabLayout tabLayout = findViewById(R.id.tab_layout);
        ViewPager2 viewPager = findViewById(R.id.view_pager);

        // Create the adapter with getSupportFragmentManager()
        MyPagerAdapter adapter = new MyPagerAdapter(getSupportFragmentManager());

        // Add fragments to the adapter
        adapter.addFragment(new FirstFragment(), "First Fragment");
        adapter.addFragment(new SecondFragment(), "Second Fragment");
        adapter.addFragment(new ThirdFragment(), "Third Fragment");

        // Set the adapter to the ViewPager2
        viewPager.setAdapter(adapter);

        // Connect the TabLayout with the ViewPager2
        new TabLayoutMediator(tabLayout, viewPager,
                (tab, position) -> tab.setText(adapter.getPageTitle(position))
        ).attach();
    }
}

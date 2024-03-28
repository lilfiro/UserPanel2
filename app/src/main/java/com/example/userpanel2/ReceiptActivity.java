package com.example.userpanel2;

import androidx.fragment.app.FragmentActivity;
import android.os.Bundle;
import androidx.viewpager2.widget.ViewPager2;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

public class ReceiptActivity extends FragmentActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.receipt_activity);

        TabLayout tabLayout = findViewById(R.id.tab_layout);
        ViewPager2 viewPager = findViewById(R.id.view_pager);

        // Create the adapter with getSupportFragmentManager()
        MyPagerAdapter adapter = new MyPagerAdapter(this);


        // Add fragments to the adapter
        adapter.addFragment(new ReceiptGiris(), "Giriş");
        adapter.addFragment(new ReceiptIslem(), "İşlem");
        adapter.addFragment(new ReceiptOzet(), "Özet");

        // Set the adapter to the ViewPager2
        viewPager.setAdapter(adapter);

        // Connect the TabLayout with the ViewPager2
        new TabLayoutMediator(tabLayout, viewPager,
                (tab, position) -> tab.setText(adapter.getPageTitle(position))
        ).attach();
    }
}

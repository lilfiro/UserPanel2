package com.example.A_Soft;

import android.os.Bundle;

import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

public class SevkiyatInvoiceFragmentActivity extends androidx.fragment.app.FragmentActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.sevkiyat_invoices_details_main_activity);

        TabLayout tabLayout = findViewById(R.id.tab_layout_sevkiyat);
        ViewPager2 viewPager = findViewById(R.id.view_pager_sevkiyat);

        // Create the adapter with FragmentActivity
        MyPagerAdapter adapter = new MyPagerAdapter(this);

        // Get the FICHENO passed from SevkiyatActivity
        String ficheNo = getIntent().getStringExtra("FICHENO");
        Sevkiyat_QR Sevkiyat_QR = new Sevkiyat_QR(); // In case you need the second tab

        // Add the FICHENO as arguments to the SevkiyatOzet fragment
        SevkiyatOzet sevkiyatOzetFragment = new SevkiyatOzet();
        Bundle bundle = new Bundle();
        bundle.putString("FICHENO", ficheNo); // Add the FICHENO
        sevkiyatOzetFragment.setArguments(bundle);

        // Add fragments to the adapter
        adapter.addFragment(sevkiyatOzetFragment, "Gelen Faturalar");
        adapter.addFragment(Sevkiyat_QR, "Karekod Okutma");

        // Set the adapter to the ViewPager2
        viewPager.setAdapter(adapter);

        // Connect the TabLayout with the ViewPager2
        new TabLayoutMediator(tabLayout, viewPager,
                (tab, position) -> tab.setText(adapter.getPageTitle(position))
        ).attach();
    }

}

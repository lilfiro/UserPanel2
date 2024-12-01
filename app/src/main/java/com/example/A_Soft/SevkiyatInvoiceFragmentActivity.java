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

        // Get the FICHENO from the intent
        String ficheNo = getIntent().getStringExtra("FICHENO");

        // Create and configure the adapter
        MyPagerAdapter adapter = new MyPagerAdapter(this);
        adapter.setFicheNo(ficheNo);

        // Create fragments
        SevkiyatOzet sevkiyatOzetFragment = new SevkiyatOzet();
        Bundle bundle = new Bundle();
        bundle.putString("FICHENO", ficheNo);
        sevkiyatOzetFragment.setArguments(bundle);

        // Add fragments to adapter (Sevkiyat_QR will be created with ficheNo in adapter)
        adapter.addFragment(sevkiyatOzetFragment, "Sevkiyat Plan");
        adapter.addFragment(new Sevkiyat_QR(), "Karekod Okutma");

        // Set up ViewPager2
        viewPager.setAdapter(adapter);

        // Connect TabLayout with ViewPager2
        new TabLayoutMediator(tabLayout, viewPager,
                (tab, position) -> tab.setText(adapter.getPageTitle(position))
        ).attach();
    }
}
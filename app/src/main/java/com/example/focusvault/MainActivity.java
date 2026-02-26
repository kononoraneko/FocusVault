package com.example.focusvault;

import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.viewpager2.widget.ViewPager2;

import com.example.focusvault.ui.MainPagerAdapter;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        applyThemeMode();
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        BottomNavigationView bottomNavigationView = findViewById(R.id.bottom_navigation);
        ViewPager2 viewPager = findViewById(R.id.main_view_pager);
        viewPager.setAdapter(new MainPagerAdapter(this));

        bottomNavigationView.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.nav_today) {
                viewPager.setCurrentItem(0, true);
                return true;
            } else if (itemId == R.id.nav_notes) {
                viewPager.setCurrentItem(1, true);
                return true;
            } else if (itemId == R.id.nav_calendar) {
                viewPager.setCurrentItem(2, true);
                return true;
            } else if (itemId == R.id.nav_focus) {
                viewPager.setCurrentItem(3, true);
                return true;
            } else if (itemId == R.id.nav_profile) {
                viewPager.setCurrentItem(4, true);
                return true;
            }
            return false;
        });

        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                if (position == 0) {
                    bottomNavigationView.setSelectedItemId(R.id.nav_today);
                } else if (position == 1) {
                    bottomNavigationView.setSelectedItemId(R.id.nav_notes);
                } else if (position == 2) {
                    bottomNavigationView.setSelectedItemId(R.id.nav_calendar);
                } else if (position == 3) {
                    bottomNavigationView.setSelectedItemId(R.id.nav_focus);
                } else {
                    bottomNavigationView.setSelectedItemId(R.id.nav_profile);
                }
            }
        });

        if (savedInstanceState == null) {
            bottomNavigationView.setSelectedItemId(R.id.nav_today);
        }
    }

    private void applyThemeMode() {
        SharedPreferences preferences = getSharedPreferences("focusvault_prefs", MODE_PRIVATE);
        boolean isDark = preferences.getBoolean("dark_theme", false);
        AppCompatDelegate.setDefaultNightMode(isDark
                ? AppCompatDelegate.MODE_NIGHT_YES
                : AppCompatDelegate.MODE_NIGHT_NO);
    }
}

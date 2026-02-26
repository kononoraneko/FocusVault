package com.example.focusvault;

import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.fragment.app.Fragment;

import com.example.focusvault.ui.FocusFragment;
import com.example.focusvault.ui.NotesFragment;
import com.example.focusvault.ui.ProfileFragment;
import com.example.focusvault.ui.TodayFragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        applyThemeMode();
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        BottomNavigationView bottomNavigationView = findViewById(R.id.bottom_navigation);

        bottomNavigationView.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.nav_today) {
                loadFragment(new TodayFragment());
                return true;
            } else if (itemId == R.id.nav_notes) {
                loadFragment(new NotesFragment());
                return true;
            } else if (itemId == R.id.nav_focus) {
                loadFragment(new FocusFragment());
                return true;
            } else if (itemId == R.id.nav_profile) {
                loadFragment(new ProfileFragment());
                return true;
            }
            return false;
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

    private void loadFragment(Fragment fragment) {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commit();
    }
}

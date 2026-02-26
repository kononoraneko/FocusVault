package com.example.focusvault.ui;

import android.Manifest;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.NumberPicker;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;

import com.example.focusvault.R;
import com.example.focusvault.data.DatabaseHelper;
import com.example.focusvault.notifications.ReminderReceiver;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.util.Calendar;

public class ProfileFragment extends Fragment {

    private static final String PREFS = "focusvault_prefs";
    private static final String KEY_DARK_THEME = "dark_theme";
    private static final String KEY_NOTIFICATIONS = "notifications_enabled";
    private static final String KEY_WORK_MIN = "work_minutes";
    private static final String KEY_BREAK_MIN = "break_minutes";
    private static final String KEY_LONG_BREAK_MIN = "long_break_minutes";
    private static final String KEY_DAILY_GOAL = "daily_goal";
    private static final String KEY_NOTIFY_HOUR = "notify_hour";
    private static final String KEY_NOTIFY_MINUTE = "notify_minute";

    public ProfileFragment() {
        super(R.layout.fragment_profile);
    }

    @Override
    public void onViewCreated(@NonNull android.view.View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);

        MaterialSwitch themeSwitch = view.findViewById(R.id.switch_profile_theme);
        MaterialSwitch notificationsSwitch = view.findViewById(R.id.switch_notifications);
        NumberPicker workPicker = view.findViewById(R.id.picker_work_minutes);
        NumberPicker breakPicker = view.findViewById(R.id.picker_break_minutes);
        NumberPicker longBreakPicker = view.findViewById(R.id.picker_long_break_minutes);
        NumberPicker dailyGoalPicker = view.findViewById(R.id.picker_daily_goal);
        NumberPicker notifyHourPicker = view.findViewById(R.id.picker_notification_hour);
        NumberPicker notifyMinutePicker = view.findViewById(R.id.picker_notification_minute);
        Button saveTimerButton = view.findViewById(R.id.button_save_timer_settings);
        Button testNotificationButton = view.findViewById(R.id.button_test_notification);
        Button resetDataButton = view.findViewById(R.id.button_reset_all_data);

        setupPicker(workPicker, 10, 90, prefs.getInt(KEY_WORK_MIN, 25));
        setupPicker(breakPicker, 3, 30, prefs.getInt(KEY_BREAK_MIN, 5));
        setupPicker(longBreakPicker, 10, 60, prefs.getInt(KEY_LONG_BREAK_MIN, 15));
        setupPicker(dailyGoalPicker, 1, 12, prefs.getInt(KEY_DAILY_GOAL, 4));
        setupPicker(notifyHourPicker, 0, 23, prefs.getInt(KEY_NOTIFY_HOUR, 20));
        setupPicker(notifyMinutePicker, 0, 59, prefs.getInt(KEY_NOTIFY_MINUTE, 0));

        themeSwitch.setChecked(prefs.getBoolean(KEY_DARK_THEME, false));
        notificationsSwitch.setChecked(prefs.getBoolean(KEY_NOTIFICATIONS, false));

        themeSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean(KEY_DARK_THEME, isChecked).apply();
            AppCompatDelegate.setDefaultNightMode(isChecked
                    ? AppCompatDelegate.MODE_NIGHT_YES
                    : AppCompatDelegate.MODE_NIGHT_NO);
        });

        notificationsSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean(KEY_NOTIFICATIONS, isChecked).apply();
            if (isChecked) {
                ensureNotificationPermission();
                scheduleReminder(notifyHourPicker.getValue(), notifyMinutePicker.getValue());
            } else {
                cancelReminder();
            }
        });

        saveTimerButton.setOnClickListener(v -> new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(R.string.save_timer_settings)
                .setMessage(R.string.timer_settings_reset_confirm)
                .setPositiveButton(R.string.save, (dialog, which) -> {
                    prefs.edit()
                            .putInt(KEY_WORK_MIN, workPicker.getValue())
                            .putInt(KEY_BREAK_MIN, breakPicker.getValue())
                            .putInt(KEY_LONG_BREAK_MIN, longBreakPicker.getValue())
                            .putInt(KEY_DAILY_GOAL, dailyGoalPicker.getValue())
                            .putInt(KEY_NOTIFY_HOUR, notifyHourPicker.getValue())
                            .putInt(KEY_NOTIFY_MINUTE, notifyMinutePicker.getValue())
                            .apply();

                    if (notificationsSwitch.isChecked()) {
                        scheduleReminder(notifyHourPicker.getValue(), notifyMinutePicker.getValue());
                    }

                    Toast.makeText(requireContext(), R.string.timer_settings_saved, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(R.string.cancel, null)
                .show());


        testNotificationButton.setOnClickListener(v -> {
            ensureNotificationPermission();
            ReminderReceiver.showReminderNotification(requireContext());
            Toast.makeText(requireContext(), R.string.notification_test_sent, Toast.LENGTH_SHORT).show();
        });

        resetDataButton.setOnClickListener(v -> new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(R.string.reset_data)
                .setMessage(R.string.reset_data_confirm)
                .setPositiveButton(R.string.reset_data, (dialog, which) -> {
                    new DatabaseHelper(requireContext()).clearAllData();
                    Toast.makeText(requireContext(), R.string.data_reset_done, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(R.string.cancel, null)
                .show());
    }

    private void setupPicker(NumberPicker picker, int min, int max, int value) {
        picker.setMinValue(min);
        picker.setMaxValue(max);
        picker.setValue(value);
    }

    private void ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 501);
            }
        }
    }

    private void scheduleReminder(int hour, int minute) {
        Context context = requireContext();
        ReminderReceiver.createNotificationChannel(context);

        Intent intent = new Intent(context, ReminderReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                2001,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            return;
        }

        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, hour);
        calendar.set(Calendar.MINUTE, minute);
        calendar.set(Calendar.SECOND, 0);

        if (calendar.before(Calendar.getInstance())) {
            calendar.add(Calendar.DAY_OF_YEAR, 1);
        }

        alarmManager.setInexactRepeating(
                AlarmManager.RTC_WAKEUP,
                calendar.getTimeInMillis(),
                AlarmManager.INTERVAL_DAY,
                pendingIntent
        );
    }

    private void cancelReminder() {
        Context context = requireContext();
        Intent intent = new Intent(context, ReminderReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                2001,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager != null) {
            alarmManager.cancel(pendingIntent);
        }
    }
}

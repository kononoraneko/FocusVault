package com.example.focusvault.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.focusvault.R;
import com.example.focusvault.data.DatabaseHelper;
import com.example.focusvault.model.Task;
import com.google.android.material.progressindicator.CircularProgressIndicator;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

public class FocusFragment extends Fragment {

    private static final String PREFS = "focusvault_prefs";
    private static final String KEY_WORK_MIN = "work_minutes";
    private static final String KEY_BREAK_MIN = "break_minutes";
    private static final String KEY_LONG_BREAK_MIN = "long_break_minutes";
    private static final String KEY_FOCUS_HELP_HIDDEN = "focus_help_hidden";

    private TextView timerText;
    private TextView sessionsTodayText;
    private TextView selectedTaskText;
    private TextView timerSettingsInfoText;
    private Button startButton;
    private CircularProgressIndicator timerProgress;
    private CountDownTimer countDownTimer;
    private long timeLeftMillis;
    private int selectedDurationMin = 25;
    private int breakDurationMin = 5;
    private int longBreakDurationMin = 15;
    private boolean isRunning = false;
    private int selectedTaskId = -1;
    private String selectedTaskName = "";
    private String sessionStartTime;
    private DatabaseHelper databaseHelper;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_focus, container, false);

        databaseHelper = new DatabaseHelper(requireContext());
        timerText = view.findViewById(R.id.text_timer);
        selectedTaskText = view.findViewById(R.id.text_selected_task);
        sessionsTodayText = view.findViewById(R.id.text_focus_sessions_today);
        timerSettingsInfoText = view.findViewById(R.id.text_timer_settings_info);
        timerProgress = view.findViewById(R.id.progress_timer);
        startButton = view.findViewById(R.id.button_start);
        Button pauseButton = view.findViewById(R.id.button_pause);
        Button resetButton = view.findViewById(R.id.button_reset);

        View helpCard = view.findViewById(R.id.card_focus_help);
        TextView hideHelpButton = view.findViewById(R.id.button_hide_focus_help);
        setupHelpCard(helpCard, hideHelpButton);

        loadTimerSettings();
        timeLeftMillis = minutesToMillis(selectedDurationMin);

        updateTimerText();
        updateTimerProgress();
        updateSessionStats();
        updateSelectedTaskText();
        updateStartButtonText();

        startButton.setOnClickListener(v -> {
            if (!isRunning) {
                if (selectedTaskId == -1) {
                    showTaskSelectionAndStart();
                } else {
                    startTimer();
                }
            }
        });

        pauseButton.setOnClickListener(v -> pauseTimer());
        resetButton.setOnClickListener(v -> resetTimer());

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        loadTimerSettings();
        if (!isRunning) {
            timeLeftMillis = minutesToMillis(selectedDurationMin);
        }
        updateSessionStats();
        updateSelectedTaskText();
        updateTimerText();
        updateTimerProgress();
        updateStartButtonText();
    }

    private void setupHelpCard(View helpCard, TextView hideHelpButton) {
        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        boolean hidden = prefs.getBoolean(KEY_FOCUS_HELP_HIDDEN, false);
        helpCard.setVisibility(hidden ? View.GONE : View.VISIBLE);

        hideHelpButton.setOnClickListener(v -> {
            helpCard.setVisibility(View.GONE);
            prefs.edit().putBoolean(KEY_FOCUS_HELP_HIDDEN, true).apply();
        });
    }

    private void loadTimerSettings() {
        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        selectedDurationMin = prefs.getInt(KEY_WORK_MIN, 25);
        breakDurationMin = prefs.getInt(KEY_BREAK_MIN, 5);
        longBreakDurationMin = prefs.getInt(KEY_LONG_BREAK_MIN, 15);
        timerSettingsInfoText.setText(getString(R.string.focus_timer_settings_info, selectedDurationMin, breakDurationMin, longBreakDurationMin));
    }

    private void showTaskSelectionAndStart() {
        List<Task> tasks = databaseHelper.getTasksByDate(databaseHelper.getTodayDate());
        if (tasks.isEmpty()) {
            new AlertDialog.Builder(requireContext())
                    .setMessage(R.string.no_tasks_for_today)
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
            return;
        }

        String[] titles = new String[tasks.size()];
        for (int i = 0; i < tasks.size(); i++) {
            titles[i] = tasks.get(i).getTitle();
        }

        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.select_task)
                .setItems(titles, (dialog, which) -> {
                    selectedTaskId = tasks.get(which).getId();
                    selectedTaskName = tasks.get(which).getTitle();
                    updateSelectedTaskText();
                    startTimer();
                })
                .show();
    }

    private void startTimer() {
        sessionStartTime = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        countDownTimer = new CountDownTimer(timeLeftMillis, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                timeLeftMillis = millisUntilFinished;
                updateTimerText();
                updateTimerProgress();
                updateStartButtonText();
            }

            @Override
            public void onFinish() {
                isRunning = false;
                timeLeftMillis = 0;
                updateTimerText();
                updateTimerProgress();
                databaseHelper.insertPomodoroSession(sessionStartTime, selectedDurationMin, selectedTaskId);
                updateSessionStats();
                new AlertDialog.Builder(requireContext())
                        .setMessage(getString(R.string.pomodoro_completed_with_break, breakDurationMin, longBreakDurationMin))
                        .setPositiveButton(android.R.string.ok, null)
                        .show();
                resetTimer();
            }
        }.start();
        isRunning = true;
        updateStartButtonText();
    }

    private void pauseTimer() {
        if (countDownTimer != null && isRunning) {
            countDownTimer.cancel();
            isRunning = false;
            updateStartButtonText();
        }
    }

    private void resetTimer() {
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }
        timeLeftMillis = minutesToMillis(selectedDurationMin);
        isRunning = false;
        selectedTaskId = -1;
        selectedTaskName = "";
        updateTimerText();
        updateTimerProgress();
        updateSelectedTaskText();
        updateStartButtonText();
    }

    private void updateTimerText() {
        int minutes = (int) (timeLeftMillis / 1000) / 60;
        int seconds = (int) (timeLeftMillis / 1000) % 60;
        timerText.setText(String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds));
    }

    private void updateTimerProgress() {
        long totalMillis = minutesToMillis(selectedDurationMin);
        int progress = totalMillis == 0 ? 0 : (int) ((totalMillis - timeLeftMillis) * 100 / totalMillis);
        timerProgress.setProgressCompat(progress, true);
    }

    private void updateSessionStats() {
        int count = databaseHelper.getTodayPomodoroCount(databaseHelper.getTodayDate());
        sessionsTodayText.setText(getString(R.string.focus_sessions_today, count));
    }

    private void updateSelectedTaskText() {
        if (selectedTaskId == -1) {
            selectedTaskText.setText(getString(R.string.focus_selected_task_none));
        } else {
            selectedTaskText.setText(getString(R.string.focus_selected_task, selectedTaskName));
        }
    }

    private void updateStartButtonText() {
        if (isRunning) {
            startButton.setText(R.string.start_running);
            startButton.setEnabled(false);
        } else if (selectedTaskId != -1 && timeLeftMillis < minutesToMillis(selectedDurationMin)) {
            startButton.setText(R.string.start_resume);
            startButton.setEnabled(true);
        } else {
            startButton.setText(R.string.start);
            startButton.setEnabled(true);
        }
    }

    private long minutesToMillis(int minutes) {
        return minutes * 60L * 1000L;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }
    }
}

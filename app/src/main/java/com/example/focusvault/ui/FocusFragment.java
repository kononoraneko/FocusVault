package com.example.focusvault.ui;

import android.app.AlertDialog;
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

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

public class FocusFragment extends Fragment {

    private static final long DEFAULT_DURATION_MS = 25 * 60 * 1000;

    private TextView timerText;
    private CountDownTimer countDownTimer;
    private long timeLeftMillis = DEFAULT_DURATION_MS;
    private boolean isRunning = false;
    private int selectedTaskId = -1;
    private String sessionStartTime;
    private DatabaseHelper databaseHelper;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_focus, container, false);

        databaseHelper = new DatabaseHelper(requireContext());
        timerText = view.findViewById(R.id.text_timer);
        Button startButton = view.findViewById(R.id.button_start);
        Button pauseButton = view.findViewById(R.id.button_pause);
        Button resetButton = view.findViewById(R.id.button_reset);

        updateTimerText();

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
            }

            @Override
            public void onFinish() {
                isRunning = false;
                timeLeftMillis = 0;
                updateTimerText();
                databaseHelper.insertPomodoroSession(sessionStartTime, 25, selectedTaskId);
                new AlertDialog.Builder(requireContext())
                        .setMessage(R.string.pomodoro_completed)
                        .setPositiveButton(android.R.string.ok, null)
                        .show();
                resetTimer();
            }
        }.start();
        isRunning = true;
    }

    private void pauseTimer() {
        if (countDownTimer != null && isRunning) {
            countDownTimer.cancel();
            isRunning = false;
        }
    }

    private void resetTimer() {
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }
        timeLeftMillis = DEFAULT_DURATION_MS;
        isRunning = false;
        selectedTaskId = -1;
        updateTimerText();
    }

    private void updateTimerText() {
        int minutes = (int) (timeLeftMillis / 1000) / 60;
        int seconds = (int) (timeLeftMillis / 1000) % 60;
        timerText.setText(String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds));
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }
    }
}

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
import com.example.focusvault.notifications.FocusTimerReceiver;
import com.google.android.material.progressindicator.CircularProgressIndicator;

import java.util.List;
import java.util.Locale;

public class FocusFragment extends Fragment {

    private static final String PREFS = "focusvault_prefs";
    private static final String KEY_WORK_MIN = "work_minutes";
    private static final String KEY_BREAK_MIN = "break_minutes";
    private static final String KEY_LONG_BREAK_MIN = "long_break_minutes";
    private static final String KEY_FOCUS_HELP_HIDDEN = "focus_help_hidden";
    private static final String KEY_TIMER_LEFT_MS = "timer_left_ms";
    private static final String KEY_TIMER_RUNNING = "timer_running";
    private static final String KEY_SELECTED_TASK_ID = "selected_task_id";
    private static final String KEY_SELECTED_TASK_NAME = "selected_task_name";
    private static final String KEY_PHASE = "focus_phase";
    private static final String PHASE_WORK = "work";
    private static final String PHASE_BREAK = "break";

    private TextView timerText;
    private TextView sessionsTodayText;
    private TextView selectedTaskText;
    private TextView timerSettingsInfoText;
    private TextView timerPhaseText;
    private Button startButton;
    private Button pauseButton;
    private CircularProgressIndicator timerProgress;
    private CountDownTimer countDownTimer;
    private long timeLeftMillis;
    private int selectedDurationMin = 25;
    private int breakDurationMin = 5;
    private int longBreakDurationMin = 15;
    private boolean isRunning = false;
    private int selectedTaskId = -1;
    private String selectedTaskName = "";
    private String activePhase = PHASE_WORK;
    private long phaseEndAtMillis = 0L;
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
        timerPhaseText = view.findViewById(R.id.text_focus_phase);
        timerProgress = view.findViewById(R.id.progress_timer);
        startButton = view.findViewById(R.id.button_start);
        pauseButton = view.findViewById(R.id.button_pause);
        Button resetButton = view.findViewById(R.id.button_reset);

        View helpCard = view.findViewById(R.id.card_focus_help);
        TextView hideHelpButton = view.findViewById(R.id.button_hide_focus_help);
        setupHelpCard(helpCard, hideHelpButton);

        loadTimerSettings();
        restoreRuntimeState();

        updateTimerText();
        updateTimerProgress();
        updateSessionStats();
        updateSelectedTaskText();
        updateStartButtonText();
        updatePhaseText();

        startButton.setOnClickListener(v -> {
            if (!isRunning) {
                if (PHASE_WORK.equals(activePhase) && selectedTaskId == -1) {
                    showTaskSelectionAndStart();
                } else {
                    startTimer(activePhase, false);
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
        restoreRuntimeState();
        updateSessionStats();
        updateSelectedTaskText();
        updateTimerText();
        updateTimerProgress();
        updateStartButtonText();
        updatePhaseText();
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
        selectedDurationMin = sanitizeMinutes(prefs.getInt(KEY_WORK_MIN, 25));
        breakDurationMin = sanitizeMinutes(prefs.getInt(KEY_BREAK_MIN, 5));
        longBreakDurationMin = sanitizeMinutes(prefs.getInt(KEY_LONG_BREAK_MIN, 15));
        timerSettingsInfoText.setText(getString(R.string.focus_timer_settings_info, selectedDurationMin, breakDurationMin, longBreakDurationMin));
    }

    private void restoreRuntimeState() {
        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        selectedTaskId = prefs.getInt(KEY_SELECTED_TASK_ID, -1);
        selectedTaskName = prefs.getString(KEY_SELECTED_TASK_NAME, "");
        activePhase = prefs.getString(KEY_PHASE, PHASE_WORK);
        phaseEndAtMillis = prefs.getLong(FocusTimerReceiver.KEY_END_AT_MS, 0L);
        boolean persistedRunning = prefs.getBoolean(FocusTimerReceiver.KEY_RUNNING, false);

        if (persistedRunning && phaseEndAtMillis > System.currentTimeMillis()) {
            timeLeftMillis = phaseEndAtMillis - System.currentTimeMillis();
            if (!isRunning) {
                startTimer(activePhase, true);
            }
            return;
        }

        long savedLeft = prefs.getLong(KEY_TIMER_LEFT_MS, -1L);
        long phaseTotalMillis = getPhaseDurationMillis(activePhase);
        if (savedLeft > 0 && savedLeft <= phaseTotalMillis) {
            timeLeftMillis = savedLeft;
        } else {
            timeLeftMillis = phaseTotalMillis;
        }

        isRunning = prefs.getBoolean(KEY_TIMER_RUNNING, false) && timeLeftMillis > 0;
        if (isRunning) {
            startTimer(activePhase, true);
        }
    }

    private void persistRuntimeState() {
        if (!isAdded()) {
            return;
        }
        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        prefs.edit()
                .putLong(KEY_TIMER_LEFT_MS, timeLeftMillis)
                .putBoolean(KEY_TIMER_RUNNING, isRunning)
                .putInt(KEY_SELECTED_TASK_ID, selectedTaskId)
                .putString(KEY_SELECTED_TASK_NAME, selectedTaskName == null ? "" : selectedTaskName)
                .putString(KEY_PHASE, activePhase)
                .putLong(FocusTimerReceiver.KEY_END_AT_MS, phaseEndAtMillis)
                .putBoolean(FocusTimerReceiver.KEY_RUNNING, isRunning)
                .apply();
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
                    activePhase = PHASE_WORK;
                    startTimer(PHASE_WORK, false);
                })
                .show();
    }

    private void startTimer(String phase, boolean restoring) {
        if (!isAdded()) {
            return;
        }
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }

        activePhase = phase;
        long totalPhaseMillis = getPhaseDurationMillis(phase);
        if (!restoring) {
            if (timeLeftMillis <= 0 || timeLeftMillis > totalPhaseMillis) {
                timeLeftMillis = totalPhaseMillis;
            }
            phaseEndAtMillis = System.currentTimeMillis() + timeLeftMillis;
            if (PHASE_WORK.equals(phase)) {
                FocusTimerReceiver.markWorkSessionStarted(requireContext(), selectedTaskId);
            }
            FocusTimerReceiver.startPhaseAt(requireContext(), phase, phaseEndAtMillis);
        }

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
                if (!isAdded()) {
                    isRunning = false;
                    timeLeftMillis = 0;
                    return;
                }

                isRunning = false;
                timeLeftMillis = 0;
                phaseEndAtMillis = 0L;
                updateTimerText();
                updateTimerProgress();
                FocusTimerReceiver.cancelPhase(requireContext());

                if (PHASE_WORK.equals(activePhase)) {
                    FocusTimerReceiver.markWorkSessionRecorded(requireContext());
                    databaseHelper.insertPomodoroSession(
                            java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                            selectedDurationMin,
                            selectedTaskId
                    );
                    updateSessionStats();
                    showBreakStartDialog();
                } else {
                    new AlertDialog.Builder(requireContext())
                            .setMessage(R.string.focus_break_finished_text)
                            .setPositiveButton(android.R.string.ok, (dialog, which) -> switchToWorkMode())
                            .show();
                }
                updateStartButtonText();
            }
        }.start();

        isRunning = true;
        updateStartButtonText();
        updatePhaseText();
    }

    private void showBreakStartDialog() {
        if (!isAdded()) {
            return;
        }
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.focus_work_finished_title)
                .setMessage(getString(R.string.focus_start_break_confirm, breakDurationMin))
                .setNegativeButton(android.R.string.cancel, (dialog, which) -> {
                    switchToWorkMode();
                    updateStartButtonText();
                })
                .setPositiveButton(R.string.focus_start_break_action, (dialog, which) -> {
                    activePhase = PHASE_BREAK;
                    timeLeftMillis = minutesToMillis(breakDurationMin);
                    startTimer(PHASE_BREAK, false);
                })
                .show();
    }

    private void switchToWorkMode() {
        activePhase = PHASE_WORK;
        timeLeftMillis = minutesToMillis(selectedDurationMin);
        updateTimerText();
        updateTimerProgress();
        updatePhaseText();
    }

    private void pauseTimer() {
        if (countDownTimer != null && isRunning) {
            countDownTimer.cancel();
            isRunning = false;
            if (isAdded()) {
                FocusTimerReceiver.cancelPhase(requireContext());
            }
            updateStartButtonText();
        }
    }

    private void resetTimer() {
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }
        if (isAdded()) {
            FocusTimerReceiver.cancelPhase(requireContext());
        }
        switchToWorkMode();
        isRunning = false;
        selectedTaskId = -1;
        selectedTaskName = "";
        updateSelectedTaskText();
        updateStartButtonText();
    }

    private void updateTimerText() {
        int minutes = (int) (timeLeftMillis / 1000) / 60;
        int seconds = (int) (timeLeftMillis / 1000) % 60;
        timerText.setText(String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds));
    }

    private void updateTimerProgress() {
        long totalMillis = getPhaseDurationMillis(activePhase);
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
            pauseButton.setEnabled(true);
        } else if (timeLeftMillis < getPhaseDurationMillis(activePhase)) {
            startButton.setText(R.string.start_resume);
            startButton.setEnabled(true);
            pauseButton.setEnabled(false);
        } else {
            startButton.setText(PHASE_BREAK.equals(activePhase) ? R.string.focus_start_break_action : R.string.start);
            startButton.setEnabled(true);
            pauseButton.setEnabled(false);
        }
    }

    private void updatePhaseText() {
        timerPhaseText.setText(PHASE_BREAK.equals(activePhase) ? R.string.focus_phase_break : R.string.focus_phase_work);
    }

    private long getPhaseDurationMillis(String phase) {
        return PHASE_BREAK.equals(phase) ? minutesToMillis(breakDurationMin) : minutesToMillis(selectedDurationMin);
    }

    private long minutesToMillis(int minutes) {
        return sanitizeMinutes(minutes) * 60L * 1000L;
    }

    private int sanitizeMinutes(int minutes) {
        return Math.max(1, minutes);
    }

    @Override
    public void onPause() {
        super.onPause();
        persistRuntimeState();
    }

    @Override
    public void onDestroyView() {
        persistRuntimeState();
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }
        super.onDestroyView();
    }
}

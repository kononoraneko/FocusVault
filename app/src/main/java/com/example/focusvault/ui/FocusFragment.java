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

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class FocusFragment extends Fragment {

    private static final String PREFS = "focusvault_prefs";
    private static final String KEY_WORK_MIN = "work_minutes";
    private static final String KEY_BREAK_MIN = "break_minutes";
    private static final String KEY_LONG_BREAK_MIN = "long_break_minutes";
    private static final String KEY_FOCUS_HELP_HIDDEN = "focus_help_hidden";
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
    private DatabaseHelper databaseHelper;

    private int workDurationMin = 25;
    private int breakDurationMin = 5;
    private int longBreakDurationMin = 15;

    private String activePhase = PHASE_WORK;
    private long phaseEndAtMillis = 0L;
    private long timeLeftMillis = 25 * 60_000L;
    private boolean isRunning = false;

    private int selectedTaskId = -1;
    private String selectedTaskName = "";
    private String currentSessionStart = "";

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

        startButton.setOnClickListener(v -> onStartClicked());
        pauseButton.setOnClickListener(v -> pauseTimer());
        resetButton.setOnClickListener(v -> resetTimer());

        loadStateAndRefresh();
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        loadStateAndRefresh();
    }

    @Override
    public void onPause() {
        super.onPause();
        persistUiState();
    }

    @Override
    public void onDestroyView() {
        persistUiState();
        stopLocalTicker();
        super.onDestroyView();
    }

    private void onStartClicked() {
        if (isRunning) {
            return;
        }

        if (PHASE_WORK.equals(activePhase) && selectedTaskId == -1) {
            showTaskSelectionAndStart();
            return;
        }

        startCurrentPhase();
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
                    timeLeftMillis = phaseDurationMillis(PHASE_WORK);
                    startCurrentPhase();
                })
                .show();
    }

    private void startCurrentPhase() {
        if (!isAdded()) {
            return;
        }

        long total = phaseDurationMillis(activePhase);
        if (timeLeftMillis <= 0 || timeLeftMillis > total) {
            timeLeftMillis = total;
        }

        if (PHASE_WORK.equals(activePhase) && currentSessionStart.isEmpty()) {
            currentSessionStart = nowSqlDateTime();
            FocusTimerReceiver.markWorkSessionStarted(requireContext(), selectedTaskId, currentSessionStart);
        }

        phaseEndAtMillis = System.currentTimeMillis() + timeLeftMillis;
        FocusTimerReceiver.startPhaseAt(requireContext(), activePhase, phaseEndAtMillis);

        startLocalTicker(timeLeftMillis);
        isRunning = true;
        refreshUi();
    }

    private void pauseTimer() {
        if (!isRunning) {
            return;
        }

        stopLocalTicker();
        isRunning = false;
        long now = System.currentTimeMillis();
        timeLeftMillis = Math.max(0L, phaseEndAtMillis - now);

        if (isAdded()) {
            FocusTimerReceiver.cancelPhase(requireContext());
        }

        refreshUi();
    }

    private void resetTimer() {
        stopLocalTicker();
        isRunning = false;
        phaseEndAtMillis = 0L;
        activePhase = PHASE_WORK;
        timeLeftMillis = phaseDurationMillis(PHASE_WORK);
        currentSessionStart = "";
        selectedTaskId = -1;
        selectedTaskName = "";

        if (isAdded()) {
            FocusTimerReceiver.cancelPhase(requireContext());
        }

        refreshUi();
    }

    private void startLocalTicker(long durationMillis) {
        stopLocalTicker();

        countDownTimer = new CountDownTimer(durationMillis, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                timeLeftMillis = millisUntilFinished;
                refreshUi();
            }

            @Override
            public void onFinish() {
                isRunning = false;
                timeLeftMillis = 0L;
                phaseEndAtMillis = 0L;

                if (!isAdded()) {
                    return;
                }

                FocusTimerReceiver.cancelPhase(requireContext());

                if (PHASE_WORK.equals(activePhase)) {
                    FocusTimerReceiver.markWorkSessionRecorded(requireContext());
                    databaseHelper.insertPomodoroSession(
                            currentSessionStart.isEmpty() ? nowSqlDateTime() : currentSessionStart,
                            workDurationMin,
                            selectedTaskId
                    );
                    currentSessionStart = "";
                    updateSessionStats();
                    showBreakStartDialog();
                } else {
                    switchToWorkPhase();
                }

                refreshUi();
            }
        }.start();
    }

    private void stopLocalTicker() {
        if (countDownTimer != null) {
            countDownTimer.cancel();
            countDownTimer = null;
        }
    }

    private void showBreakStartDialog() {
        if (!isAdded()) {
            return;
        }

        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.focus_work_finished_title)
                .setMessage(getString(R.string.focus_start_break_confirm, breakDurationMin))
                .setNegativeButton(android.R.string.cancel, (dialog, which) -> switchToWorkPhase())
                .setPositiveButton(R.string.focus_start_break_action, (dialog, which) -> {
                    activePhase = PHASE_BREAK;
                    timeLeftMillis = phaseDurationMillis(PHASE_BREAK);
                    startCurrentPhase();
                })
                .show();
    }

    private void switchToWorkPhase() {
        activePhase = PHASE_WORK;
        timeLeftMillis = phaseDurationMillis(PHASE_WORK);
        currentSessionStart = "";
    }

    private void loadStateAndRefresh() {
        if (!isAdded()) {
            return;
        }

        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        workDurationMin = safeMinutes(prefs.getInt(KEY_WORK_MIN, 25));
        breakDurationMin = safeMinutes(prefs.getInt(KEY_BREAK_MIN, 5));
        longBreakDurationMin = safeMinutes(prefs.getInt(KEY_LONG_BREAK_MIN, 15));

        selectedTaskId = prefs.getInt(KEY_SELECTED_TASK_ID, -1);
        selectedTaskName = prefs.getString(KEY_SELECTED_TASK_NAME, "");

        activePhase = prefs.getString(KEY_PHASE, PHASE_WORK);
        if (!PHASE_BREAK.equals(activePhase)) {
            activePhase = PHASE_WORK;
        }

        phaseEndAtMillis = prefs.getLong(FocusTimerReceiver.KEY_END_AT_MS, 0L);
        boolean storedRunning = prefs.getBoolean(FocusTimerReceiver.KEY_RUNNING, false);
        currentSessionStart = prefs.getString(FocusTimerReceiver.KEY_SESSION_START_TIME, "");

        long defaultLeft = phaseDurationMillis(activePhase);
        long savedLeft = prefs.getLong("timer_left_ms", defaultLeft);

        if (storedRunning && phaseEndAtMillis > System.currentTimeMillis()) {
            timeLeftMillis = phaseEndAtMillis - System.currentTimeMillis();
            isRunning = true;
            startLocalTicker(timeLeftMillis);
        } else {
            isRunning = false;
            timeLeftMillis = (savedLeft > 0 && savedLeft <= defaultLeft) ? savedLeft : defaultLeft;
            stopLocalTicker();
        }

        refreshUi();
    }

    private void persistUiState() {
        if (!isAdded()) {
            return;
        }

        if (isRunning && phaseEndAtMillis > 0L) {
            timeLeftMillis = Math.max(0L, phaseEndAtMillis - System.currentTimeMillis());
        }

        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        prefs.edit()
                .putLong("timer_left_ms", timeLeftMillis)
                .putBoolean("timer_running", isRunning)
                .putInt(KEY_SELECTED_TASK_ID, selectedTaskId)
                .putString(KEY_SELECTED_TASK_NAME, selectedTaskName)
                .putString(KEY_PHASE, activePhase)
                .putLong(FocusTimerReceiver.KEY_END_AT_MS, phaseEndAtMillis)
                .putBoolean(FocusTimerReceiver.KEY_RUNNING, isRunning)
                .putString(FocusTimerReceiver.KEY_SESSION_START_TIME, currentSessionStart)
                .apply();
    }

    private void refreshUi() {
        timerSettingsInfoText.setText(getString(
                R.string.focus_timer_settings_info,
                workDurationMin,
                breakDurationMin,
                longBreakDurationMin
        ));

        timerPhaseText.setText(PHASE_BREAK.equals(activePhase)
                ? R.string.focus_phase_break
                : R.string.focus_phase_work);

        updateSelectedTaskText();
        updateSessionStats();
        updateTimerText();
        updateTimerProgress();

        if (isRunning) {
            startButton.setText(R.string.start_running);
            startButton.setEnabled(false);
            pauseButton.setEnabled(true);
        } else if (timeLeftMillis < phaseDurationMillis(activePhase)) {
            startButton.setText(R.string.start_resume);
            startButton.setEnabled(true);
            pauseButton.setEnabled(false);
        } else {
            startButton.setText(PHASE_BREAK.equals(activePhase)
                    ? R.string.focus_start_break_action
                    : R.string.start);
            startButton.setEnabled(true);
            pauseButton.setEnabled(false);
        }
    }

    private void updateSelectedTaskText() {
        if (selectedTaskId == -1) {
            selectedTaskText.setText(getString(R.string.focus_selected_task_none));
        } else {
            selectedTaskText.setText(getString(R.string.focus_selected_task, selectedTaskName));
        }
    }

    private void updateSessionStats() {
        int count = databaseHelper.getTodayPomodoroCount(databaseHelper.getTodayDate());
        sessionsTodayText.setText(getString(R.string.focus_sessions_today, count));
    }

    private void updateTimerText() {
        int minutes = (int) (timeLeftMillis / 1000) / 60;
        int seconds = (int) (timeLeftMillis / 1000) % 60;
        timerText.setText(String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds));
    }

    private void updateTimerProgress() {
        long totalMillis = phaseDurationMillis(activePhase);
        int progress = totalMillis == 0 ? 0 : (int) ((totalMillis - timeLeftMillis) * 100 / totalMillis);
        timerProgress.setProgressCompat(progress, true);
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

    private long phaseDurationMillis(String phase) {
        return (PHASE_BREAK.equals(phase) ? breakDurationMin : workDurationMin) * 60_000L;
    }

    private int safeMinutes(int minutes) {
        return Math.max(1, minutes);
    }

    private String nowSqlDateTime() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
    }
}

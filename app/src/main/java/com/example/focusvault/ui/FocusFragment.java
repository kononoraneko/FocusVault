package com.example.focusvault.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
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
import com.example.focusvault.notifications.ReminderReceiver;
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
    private static final String KEY_ACTIVE_BREAK_MIN = "focus_active_break_min";

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
    private int activeBreakDurationMin = 5;

    private String activePhase = PHASE_WORK;
    private long phaseEndAtMillis = 0L;
    private long timeLeftMillis = 25 * 60_000L;
    private boolean isRunning = false;

    private int selectedTaskId = -1;
    private String selectedTaskName = "";
    private String currentSessionStart = "";

    private SharedPreferences prefs;
    private final OnSharedPreferenceChangeListener timerPrefsListener = (sharedPreferences, key) -> {
        if (FocusTimerReceiver.KEY_RUNNING.equals(key)
                || FocusTimerReceiver.KEY_END_AT_MS.equals(key)
                || FocusTimerReceiver.KEY_TIMER_LEFT_MS.equals(key)
                || KEY_PHASE.equals(key)
                || KEY_ACTIVE_BREAK_MIN.equals(key)) {
            syncTimerStateFromPrefs();
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_focus, container, false);

        databaseHelper = new DatabaseHelper(requireContext());
        prefs = requireContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
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

        syncTimerStateFromPrefs();
        FocusTimerReceiver.refreshTimerNotification(requireContext());
        return view;
    }


    @Override
    public void onStart() {
        super.onStart();
        if (prefs != null) {
            prefs.registerOnSharedPreferenceChangeListener(timerPrefsListener);
        }
        syncTimerStateFromPrefs();
    }

    @Override
    public void onStop() {
        if (prefs != null) {
            prefs.unregisterOnSharedPreferenceChangeListener(timerPrefsListener);
        }
        super.onStop();
    }

    @Override
    public void onResume() {
        super.onResume();
        syncTimerStateFromPrefs();
        FocusTimerReceiver.refreshTimerNotification(requireContext());
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
                    activeBreakDurationMin = breakDurationMin;
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
            FocusTimerReceiver.pausePhase(requireContext());
        }

        syncTimerStateFromPrefs();
    }

    private void resetTimer() {
        stopLocalTicker();
        isRunning = false;
        phaseEndAtMillis = 0L;
        activePhase = PHASE_WORK;
        activeBreakDurationMin = breakDurationMin;
        timeLeftMillis = phaseDurationMillis(PHASE_WORK);
        currentSessionStart = "";
        selectedTaskId = -1;
        selectedTaskName = "";

        if (isAdded()) {
            FocusTimerReceiver.cancelPhase(requireContext());
        }

        syncTimerStateFromPrefs();
    }

    private void startLocalTicker(long durationMillis) {
        stopLocalTicker();

        countDownTimer = new CountDownTimer(durationMillis, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                timeLeftMillis = Math.max(0L, millisUntilFinished);
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
                    FocusTimerReceiver.showWorkFinishedNotification(requireContext());
                    currentSessionStart = "";
                    updateSessionStats();
                    showBreakStartDialog();
                } else {
                    ReminderReceiver.showCustomNotification(
                            requireContext(),
                            getString(R.string.focus_break_finished_title),
                            getString(R.string.focus_break_finished_text),
                            4003
                    );
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
                .setMessage(getString(R.string.focus_start_break_type_confirm, breakDurationMin, longBreakDurationMin))
                .setNegativeButton(android.R.string.cancel, (dialog, which) -> switchToWorkPhase())
                .setPositiveButton(R.string.focus_start_short_break_action, (dialog, which) -> startBreakWithDuration(breakDurationMin))
                .setNeutralButton(R.string.focus_start_long_break_action, (dialog, which) -> startBreakWithDuration(longBreakDurationMin))
                .show();
    }

    private void startBreakWithDuration(int breakMinutes) {
        if (!isAdded()) {
            return;
        }

        activePhase = PHASE_BREAK;
        activeBreakDurationMin = safeMinutes(breakMinutes);
        prefs.edit().putInt(KEY_ACTIVE_BREAK_MIN, activeBreakDurationMin).apply();
        timeLeftMillis = activeBreakDurationMin * 60_000L;
        phaseEndAtMillis = System.currentTimeMillis() + timeLeftMillis;
        FocusTimerReceiver.startPhaseAt(requireContext(), PHASE_BREAK, phaseEndAtMillis);
        stopLocalTicker();
        startLocalTicker(timeLeftMillis);
        isRunning = true;
        refreshUi();
    }

    private void switchToWorkPhase() {
        activePhase = PHASE_WORK;
        activeBreakDurationMin = breakDurationMin;
        timeLeftMillis = phaseDurationMillis(PHASE_WORK);
        currentSessionStart = "";
    }


    private void syncTimerStateFromPrefs() {
        if (!isAdded()) {
            return;
        }

        if (prefs == null) {
            prefs = requireContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        }

        workDurationMin = safeMinutes(prefs.getInt(KEY_WORK_MIN, 25));
        breakDurationMin = safeMinutes(prefs.getInt(KEY_BREAK_MIN, 5));
        longBreakDurationMin = safeMinutes(prefs.getInt(KEY_LONG_BREAK_MIN, 15));
        selectedTaskId = prefs.getInt(KEY_SELECTED_TASK_ID, -1);
        selectedTaskName = prefs.getString(KEY_SELECTED_TASK_NAME, "");
        currentSessionStart = prefs.getString(FocusTimerReceiver.KEY_SESSION_START_TIME, "");

        String phase = prefs.getString(KEY_PHASE, PHASE_WORK);
        if (!PHASE_BREAK.equals(phase)) {
            phase = PHASE_WORK;
        }

        activePhase = phase;
        activeBreakDurationMin = safeMinutes(prefs.getInt(KEY_ACTIVE_BREAK_MIN, breakDurationMin));

        long storedEndAt = prefs.getLong(FocusTimerReceiver.KEY_END_AT_MS, 0L);
        boolean storedRunning = prefs.getBoolean(FocusTimerReceiver.KEY_RUNNING, false);
        long defaultLeft = phaseDurationMillis(activePhase);
        long savedLeft = prefs.getLong(FocusTimerReceiver.KEY_TIMER_LEFT_MS, defaultLeft);

        if (storedRunning && storedEndAt > System.currentTimeMillis()) {
            long newLeft = Math.max(0L, storedEndAt - System.currentTimeMillis());
            boolean shouldRestartTicker = !isRunning || Math.abs(newLeft - timeLeftMillis) > 1200L;
            phaseEndAtMillis = storedEndAt;
            timeLeftMillis = newLeft;
            isRunning = true;
            if (shouldRestartTicker) {
                startLocalTicker(timeLeftMillis);
            }
        } else {
            if (isRunning) {
                stopLocalTicker();
            }
            isRunning = false;
            phaseEndAtMillis = 0L;
            timeLeftMillis = Math.max(0L, Math.min(savedLeft, defaultLeft));
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

        if (prefs == null) {
            prefs = requireContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        }
        prefs.edit()
                .putLong(FocusTimerReceiver.KEY_TIMER_LEFT_MS, Math.max(0L, timeLeftMillis))
                .putInt(KEY_ACTIVE_BREAK_MIN, activeBreakDurationMin)
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
            startButton.setText(R.string.start);
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
        long safeLeft = Math.max(0L, timeLeftMillis);
        int minutes = (int) (safeLeft / 1000) / 60;
        int seconds = (int) (safeLeft / 1000) % 60;
        timerText.setText(String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds));
    }

    private void updateTimerProgress() {
        long totalMillis = phaseDurationMillis(activePhase);
        int progress = totalMillis == 0 ? 0 : (int) ((totalMillis - Math.max(0L, timeLeftMillis)) * 100 / totalMillis);
        progress = Math.max(0, Math.min(100, progress));
        timerProgress.setProgressCompat(progress, true);
    }

    private void setupHelpCard(View helpCard, TextView hideHelpButton) {
        if (prefs == null) {
            prefs = requireContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        }
        boolean hidden = prefs.getBoolean(KEY_FOCUS_HELP_HIDDEN, false);
        helpCard.setVisibility(hidden ? View.GONE : View.VISIBLE);
        hideHelpButton.setOnClickListener(v -> {
            helpCard.setVisibility(View.GONE);
            prefs.edit().putBoolean(KEY_FOCUS_HELP_HIDDEN, true).apply();
        });
    }

    private long phaseDurationMillis(String phase) {
        return (PHASE_BREAK.equals(phase) ? activeBreakDurationMin : workDurationMin) * 60_000L;
    }

    private int safeMinutes(int minutes) {
        return Math.max(1, minutes);
    }

    private String nowSqlDateTime() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
    }
}

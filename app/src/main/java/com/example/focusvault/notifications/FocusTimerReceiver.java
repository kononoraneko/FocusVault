package com.example.focusvault.notifications;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import com.example.focusvault.R;
import com.example.focusvault.data.DatabaseHelper;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class FocusTimerReceiver extends BroadcastReceiver {

    public static final String ACTION_TIMER_FINISH = "com.example.focusvault.action.TIMER_FINISH";
    public static final String ACTION_START_BREAK = "com.example.focusvault.action.START_BREAK";
    public static final String ACTION_TIMER_PAUSE = "com.example.focusvault.action.TIMER_PAUSE";
    public static final String ACTION_TIMER_RESUME = "com.example.focusvault.action.TIMER_RESUME";
    public static final String ACTION_TIMER_STOP = "com.example.focusvault.action.TIMER_STOP";

    public static final String EXTRA_PHASE = "phase";
    public static final String PHASE_WORK = "work";
    public static final String PHASE_BREAK = "break";

    public static final String PREFS = "focusvault_prefs";
    public static final String KEY_PHASE = "focus_phase";
    public static final String KEY_END_AT_MS = "focus_end_at_ms";
    public static final String KEY_RUNNING = "focus_running";
    public static final String KEY_BREAK_MIN = "break_minutes";
    public static final String KEY_WORK_MIN = "work_minutes";
    public static final String KEY_SELECTED_TASK_ID = "selected_task_id";
    public static final String KEY_SESSION_START_TIME = "focus_session_start_time";
    public static final String KEY_WORK_RECORDED = "focus_work_recorded";
    public static final String KEY_TIMER_LEFT_MS = "timer_left_ms";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent != null ? intent.getAction() : null;
        if (ACTION_TIMER_FINISH.equals(action)) {
            String phase = intent != null ? intent.getStringExtra(EXTRA_PHASE) : null;
            handleTimerFinish(context, phase);
            return;
        }

        if (ACTION_START_BREAK.equals(action)) {
            int breakMinutes = sanitizeMinutes(
                    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_BREAK_MIN, 5)
            );
            startPhase(context, PHASE_BREAK, breakMinutes);
            ReminderReceiver.showCustomNotification(
                    context,
                    context.getString(R.string.focus_break_started_title),
                    context.getString(R.string.focus_break_started_text, breakMinutes),
                    4004
            );
            return;
        }

        if (ACTION_TIMER_PAUSE.equals(action)) {
            pausePhase(context);
            return;
        }

        if (ACTION_TIMER_RESUME.equals(action)) {
            resumePhase(context);
            return;
        }

        if (ACTION_TIMER_STOP.equals(action)) {
            cancelPhase(context);
        }
    }

    private void handleTimerFinish(Context context, String phase) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        prefs.edit()
                .putBoolean(KEY_RUNNING, false)
                .putLong(KEY_END_AT_MS, 0L)
                .putLong(KEY_TIMER_LEFT_MS, 0L)
                .apply();

        ReminderReceiver.cancelFocusTimerNotification(context);

        if (PHASE_BREAK.equals(phase)) {
            ReminderReceiver.showCustomNotification(
                    context,
                    context.getString(R.string.focus_break_finished_title),
                    context.getString(R.string.focus_break_finished_text),
                    4003
            );
            return;
        }

        recordCompletedWorkSession(context, prefs);

        Intent startBreakIntent = new Intent(context, FocusTimerReceiver.class);
        startBreakIntent.setAction(ACTION_START_BREAK);
        PendingIntent startBreakPendingIntent = PendingIntent.getBroadcast(
                context,
                5002,
                startBreakIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        ReminderReceiver.showActionNotification(
                context,
                context.getString(R.string.focus_work_finished_title),
                context.getString(R.string.focus_work_finished_text),
                4002,
                context.getString(R.string.focus_start_break_action),
                startBreakPendingIntent
        );
    }

    public static void startPhase(Context context, String phase, int minutes) {
        int safeMinutes = sanitizeMinutes(minutes);
        long endAt = System.currentTimeMillis() + safeMinutes * 60_000L;
        startPhaseAt(context, phase, endAt);
    }

    public static void startPhaseAt(Context context, String phase, long endAt) {
        long now = System.currentTimeMillis();
        long timeLeft = Math.max(0L, endAt - now);

        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        prefs.edit()
                .putString(KEY_PHASE, phase)
                .putLong(KEY_END_AT_MS, endAt)
                .putLong(KEY_TIMER_LEFT_MS, timeLeft)
                .putBoolean(KEY_RUNNING, true)
                .apply();

        scheduleFinishAlarm(context, phase, endAt);
        refreshTimerNotification(context);
    }

    public static void pausePhase(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        long endAt = prefs.getLong(KEY_END_AT_MS, 0L);
        long timeLeft = Math.max(0L, endAt - System.currentTimeMillis());

        prefs.edit()
                .putLong(KEY_TIMER_LEFT_MS, timeLeft)
                .putLong(KEY_END_AT_MS, 0L)
                .putBoolean(KEY_RUNNING, false)
                .apply();

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager != null) {
            alarmManager.cancel(buildFinishPendingIntent(context, PHASE_WORK));
            alarmManager.cancel(buildFinishPendingIntent(context, PHASE_BREAK));
        }

        refreshTimerNotification(context);
    }

    public static void resumePhase(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String phase = prefs.getString(KEY_PHASE, PHASE_WORK);
        if (!PHASE_BREAK.equals(phase)) {
            phase = PHASE_WORK;
        }

        long storedLeft = prefs.getLong(KEY_TIMER_LEFT_MS, phaseDurationMillis(prefs, phase));
        if (storedLeft <= 0L) {
            storedLeft = phaseDurationMillis(prefs, phase);
        }

        long endAt = System.currentTimeMillis() + storedLeft;
        prefs.edit()
                .putLong(KEY_END_AT_MS, endAt)
                .putLong(KEY_TIMER_LEFT_MS, storedLeft)
                .putBoolean(KEY_RUNNING, true)
                .apply();

        scheduleFinishAlarm(context, phase, endAt);
        refreshTimerNotification(context);
    }

    public static void cancelPhase(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String phase = prefs.getString(KEY_PHASE, PHASE_WORK);
        if (!PHASE_BREAK.equals(phase)) {
            phase = PHASE_WORK;
        }

        prefs.edit()
                .putString(KEY_PHASE, PHASE_WORK)
                .putBoolean(KEY_RUNNING, false)
                .putLong(KEY_END_AT_MS, 0L)
                .putLong(KEY_TIMER_LEFT_MS, phaseDurationMillis(prefs, phase))
                .apply();

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager != null) {
            alarmManager.cancel(buildFinishPendingIntent(context, PHASE_WORK));
            alarmManager.cancel(buildFinishPendingIntent(context, PHASE_BREAK));
        }

        ReminderReceiver.cancelFocusTimerNotification(context);
    }

    public static void refreshTimerNotification(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String phase = prefs.getString(KEY_PHASE, PHASE_WORK);
        if (!PHASE_BREAK.equals(phase)) {
            phase = PHASE_WORK;
        }

        boolean isRunning = prefs.getBoolean(KEY_RUNNING, false);
        long endAt = prefs.getLong(KEY_END_AT_MS, 0L);

        if (!isRunning && prefs.getLong(KEY_TIMER_LEFT_MS, 0L) <= 0L) {
            ReminderReceiver.cancelFocusTimerNotification(context);
            return;
        }

        PendingIntent primaryAction;
        String primaryTitle;
        if (isRunning) {
            primaryAction = buildControlPendingIntent(context, ACTION_TIMER_PAUSE, 5100);
            primaryTitle = context.getString(R.string.pause);
        } else {
            primaryAction = buildControlPendingIntent(context, ACTION_TIMER_RESUME, 5101);
            primaryTitle = context.getString(R.string.start_resume);
            endAt = 0L;
        }

        PendingIntent stopAction = buildControlPendingIntent(context, ACTION_TIMER_STOP, 5102);

        ReminderReceiver.showFocusTimerNotification(
                context,
                phase,
                endAt,
                isRunning,
                primaryAction,
                primaryTitle,
                stopAction,
                context.getString(R.string.reset)
        );
    }

    public static void markWorkSessionStarted(Context context, int taskId, String sessionStart) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        prefs.edit()
                .putInt(KEY_SELECTED_TASK_ID, taskId)
                .putString(KEY_SESSION_START_TIME, sessionStart == null ? nowSqlDateTime() : sessionStart)
                .putBoolean(KEY_WORK_RECORDED, false)
                .apply();
    }

    public static void markWorkSessionRecorded(Context context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_WORK_RECORDED, true)
                .apply();
    }

    private void recordCompletedWorkSession(Context context, SharedPreferences prefs) {
        if (prefs.getBoolean(KEY_WORK_RECORDED, false)) {
            return;
        }

        String startTime = prefs.getString(KEY_SESSION_START_TIME, null);
        if (startTime == null || startTime.isEmpty()) {
            startTime = nowSqlDateTime();
        }

        int taskId = prefs.getInt(KEY_SELECTED_TASK_ID, -1);
        int duration = sanitizeMinutes(prefs.getInt(KEY_WORK_MIN, 25));
        DatabaseHelper db = new DatabaseHelper(context);
        db.insertPomodoroSession(startTime, duration, taskId);
        markWorkSessionRecorded(context);
    }

    private static void scheduleFinishAlarm(Context context, String phase, long triggerAtMillis) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            return;
        }

        PendingIntent pendingIntent = buildFinishPendingIntent(context, phase);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
            }
        } catch (SecurityException ex) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
            } else {
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
            }
        }
    }

    private static PendingIntent buildFinishPendingIntent(Context context, String phase) {
        Intent intent = new Intent(context, FocusTimerReceiver.class);
        intent.setAction(ACTION_TIMER_FINISH);
        intent.putExtra(EXTRA_PHASE, phase);
        int requestCode = PHASE_BREAK.equals(phase) ? 5001 : 5000;
        return PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private static PendingIntent buildControlPendingIntent(Context context, String action, int requestCode) {
        Intent intent = new Intent(context, FocusTimerReceiver.class);
        intent.setAction(action);
        return PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private static long phaseDurationMillis(SharedPreferences prefs, String phase) {
        int minutes = PHASE_BREAK.equals(phase)
                ? sanitizeMinutes(prefs.getInt(KEY_BREAK_MIN, 5))
                : sanitizeMinutes(prefs.getInt(KEY_WORK_MIN, 25));
        return minutes * 60_000L;
    }

    private static int sanitizeMinutes(int minutes) {
        return Math.max(1, minutes);
    }

    private static String nowSqlDateTime() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
    }
}

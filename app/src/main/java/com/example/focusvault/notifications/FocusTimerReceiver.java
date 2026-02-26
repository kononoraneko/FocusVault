package com.example.focusvault.notifications;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import com.example.focusvault.R;

public class FocusTimerReceiver extends BroadcastReceiver {

    public static final String ACTION_TIMER_FINISH = "com.example.focusvault.action.TIMER_FINISH";
    public static final String ACTION_START_BREAK = "com.example.focusvault.action.START_BREAK";

    public static final String EXTRA_PHASE = "phase";
    public static final String PHASE_WORK = "work";
    public static final String PHASE_BREAK = "break";

    public static final String PREFS = "focusvault_prefs";
    public static final String KEY_PHASE = "focus_phase";
    public static final String KEY_END_AT_MS = "focus_end_at_ms";
    public static final String KEY_RUNNING = "focus_running";
    public static final String KEY_BREAK_MIN = "break_minutes";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent != null ? intent.getAction() : null;
        if (ACTION_TIMER_FINISH.equals(action)) {
            handleTimerFinish(context, intent.getStringExtra(EXTRA_PHASE));
            return;
        }

        if (ACTION_START_BREAK.equals(action)) {
            int breakMinutes = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .getInt(KEY_BREAK_MIN, 5);
            startPhase(context, PHASE_BREAK, breakMinutes);
            ReminderReceiver.showCustomNotification(
                    context,
                    context.getString(R.string.focus_break_started_title),
                    context.getString(R.string.focus_break_started_text, breakMinutes),
                    4004
            );
        }
    }

    private void handleTimerFinish(Context context, String phase) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        prefs.edit()
                .putBoolean(KEY_RUNNING, false)
                .putLong(KEY_END_AT_MS, 0L)
                .apply();

        if (PHASE_BREAK.equals(phase)) {
            ReminderReceiver.showCustomNotification(
                    context,
                    context.getString(R.string.focus_break_finished_title),
                    context.getString(R.string.focus_break_finished_text),
                    4003
            );
            return;
        }

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
        long endAt = System.currentTimeMillis() + minutes * 60_000L;
        startPhaseAt(context, phase, endAt);
    }

    public static void startPhaseAt(Context context, String phase, long endAt) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        prefs.edit()
                .putString(KEY_PHASE, phase)
                .putLong(KEY_END_AT_MS, endAt)
                .putBoolean(KEY_RUNNING, true)
                .apply();

        scheduleFinishAlarm(context, phase, endAt);
    }

    public static void cancelPhase(Context context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_RUNNING, false)
                .putLong(KEY_END_AT_MS, 0L)
                .apply();

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager != null) {
            alarmManager.cancel(buildFinishPendingIntent(context, PHASE_WORK));
            alarmManager.cancel(buildFinishPendingIntent(context, PHASE_BREAK));
        }
    }

    private static void scheduleFinishAlarm(Context context, String phase, long triggerAtMillis) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            return;
        }

        PendingIntent pendingIntent = buildFinishPendingIntent(context, phase);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
        } else {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
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
}

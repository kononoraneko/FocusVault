package com.example.focusvault.notifications;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.example.focusvault.MainActivity;
import com.example.focusvault.R;

public class ReminderReceiver extends BroadcastReceiver {

    public static final String CHANNEL_ID = "focusvault_reminders";
    public static final int FOCUS_TIMER_NOTIFICATION_ID = 4010;

    @Override
    public void onReceive(Context context, Intent intent) {
        showReminderNotification(context);
    }

    public static void showReminderNotification(Context context) {
        showCustomNotification(context,
                context.getString(R.string.reminder_title),
                context.getString(R.string.reminder_text),
                3001);
    }

    public static void showCustomNotification(Context context, String title, String text, int id) {
        showActionNotification(context, title, text, id, null, null);
    }

    public static void showActionNotification(
            Context context,
            String title,
            String text,
            int id,
            String actionTitle,
            PendingIntent actionIntent
    ) {
        createNotificationChannel(context);

        Intent openIntent = new Intent(context, MainActivity.class);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                1001,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(title)
                .setContentText(text)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setVibrate(new long[]{0, 350, 200, 350})
                .setAutoCancel(true);

        if (actionTitle != null && actionIntent != null) {
            builder.addAction(android.R.drawable.ic_media_play, actionTitle, actionIntent);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                return;
            }
        }

        NotificationManagerCompat.from(context).notify(id, builder.build());
    }

    public static void showFocusTimerNotification(
            Context context,
            String phase,
            long endAtMillis,
            boolean isRunning,
            PendingIntent primaryAction,
            String primaryActionTitle,
            PendingIntent secondaryAction,
            String secondaryActionTitle
    ) {
        createNotificationChannel(context);

        Intent openIntent = new Intent(context, MainActivity.class);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(
                context,
                1010,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        int phaseTitle = FocusTimerReceiver.PHASE_BREAK.equals(phase)
                ? R.string.focus_notification_break_title
                : R.string.focus_notification_work_title;
        int statusText = isRunning
                ? R.string.focus_notification_running
                : R.string.focus_notification_paused;

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(context.getString(phaseTitle))
                .setContentText(context.getString(statusText))
                .setContentIntent(contentIntent)
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_PROGRESS);

        if (isRunning && endAtMillis > System.currentTimeMillis()) {
            builder.setWhen(endAtMillis)
                    .setUsesChronometer(true)
                    .setChronometerCountDown(true);
        }

        if (primaryAction != null && primaryActionTitle != null) {
            builder.addAction(android.R.drawable.ic_media_pause, primaryActionTitle, primaryAction);
        }

        if (secondaryAction != null && secondaryActionTitle != null) {
            builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, secondaryActionTitle, secondaryAction);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                return;
            }
        }

        NotificationManagerCompat.from(context).notify(FOCUS_TIMER_NOTIFICATION_ID, builder.build());
    }

    public static void cancelFocusTimerNotification(Context context) {
        NotificationManagerCompat.from(context).cancel(FOCUS_TIMER_NOTIFICATION_ID);
    }

    public static void createNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.reminder_channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            channel.setDescription(context.getString(R.string.reminder_channel_desc));
            channel.enableVibration(true);
            channel.setVibrationPattern(new long[]{0, 350, 200, 350});
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }
}

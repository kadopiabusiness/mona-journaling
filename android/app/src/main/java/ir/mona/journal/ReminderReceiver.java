package ir.mona.journal;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;

public class ReminderReceiver extends BroadcastReceiver {
    /** wake-up: alarm ringtone that repeats until the notification is opened or dismissed */
    static final String CH_WAKE = "wake_alarm";
    /** other reminders: one sound on the alarm stream, so Do Not Disturb / bedtime mode does not mute them */
    static final String CH_REM = "reminder_alarm";
    static final int WAKE_ID = 1;

    @Override
    public void onReceive(Context c, Intent i) {
        String a = i.getAction();
        if (a != null && a.startsWith(ReminderScheduler.ACTION))
            show(c, i.getIntExtra("id", 0), i.getStringExtra("title"), i.getStringExtra("body"));
        // after a reminder, reboot, update or clock change: schedule the next ones
        ReminderScheduler.scheduleAll(c);
    }

    static void channels(NotificationManager nm) {
        nm.deleteNotificationChannel("reminders");
        AudioAttributes aa = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build();
        Uri alarm = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        if (alarm == null) alarm = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
        NotificationChannel w = new NotificationChannel(CH_WAKE, "آلارم بیداری", NotificationManager.IMPORTANCE_HIGH);
        w.setSound(alarm, aa);
        w.enableVibration(true);
        w.setVibrationPattern(new long[]{0, 800, 400, 800, 400, 800});
        w.setBypassDnd(true);
        nm.createNotificationChannel(w);
        NotificationChannel r = new NotificationChannel(CH_REM, "یادآورهای برنامه", NotificationManager.IMPORTANCE_HIGH);
        r.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION), aa);
        r.enableVibration(true);
        r.setBypassDnd(true);
        nm.createNotificationChannel(r);
    }

    static void show(Context c, int id, String title, String body) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        channels(nm);
        boolean wake = id == WAKE_ID;
        Intent open = new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(c, 1000 + id, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Builder b = new Notification.Builder(c, wake ? CH_WAKE : CH_REM)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle(title == null ? "ژورنال مونا" : title)
                .setContentText(body)
                .setStyle(new Notification.BigTextStyle().bigText(body))
                .setColor(0xFF7C3AED)
                .setCategory(Notification.CATEGORY_ALARM)
                .setContentIntent(pi)
                .setAutoCancel(true);
        if (wake) b.setFullScreenIntent(pi, true);
        Notification n = b.build();
        if (wake) n.flags |= Notification.FLAG_INSISTENT;
        try {
            nm.notify(id, n);
        } catch (SecurityException ignored) {
        }
    }
}

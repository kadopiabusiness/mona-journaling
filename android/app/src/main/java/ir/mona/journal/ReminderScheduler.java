package ir.mona.journal;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Calendar;

/** Schedules one exact alarm per enabled daily reminder; each alarm reschedules the next day when it fires. */
public final class ReminderScheduler {
    static final String PREFS = "reminders";
    static final String KEY = "list";
    static final String ACTION = "ir.mona.journal.REMIND.";
    private static final int MAX_ID = 60;

    private ReminderScheduler() {
    }

    static void scheduleAll(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        for (int id = 1; id <= MAX_ID; id++) {
            PendingIntent old = pending(c, id, null, null, PendingIntent.FLAG_NO_CREATE);
            if (old != null) {
                am.cancel(old);
                old.cancel();
            }
        }
        String s = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "");
        if (s == null || s.isEmpty()) return;
        try {
            JSONArray a = new JSONArray(s);
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                int id = o.optInt("id", 0);
                if (!o.optBoolean("on") || id < 1 || id > MAX_ID) continue;
                String[] t = o.optString("t", "").split(":");
                if (t.length < 2) continue;
                Calendar cal = Calendar.getInstance();
                cal.set(Calendar.HOUR_OF_DAY, Integer.parseInt(t[0].trim()));
                cal.set(Calendar.MINUTE, Integer.parseInt(t[1].trim()));
                cal.set(Calendar.SECOND, 0);
                cal.set(Calendar.MILLISECOND, 0);
                if (cal.getTimeInMillis() <= System.currentTimeMillis() + 1000) cal.add(Calendar.DAY_OF_YEAR, 1);
                PendingIntent p = pending(c, id, o.optString("title"), o.optString("body"), PendingIntent.FLAG_UPDATE_CURRENT);
                if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms())
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), p);
                else
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), p);
            }
        } catch (Exception ignored) {
        }
    }

    private static PendingIntent pending(Context c, int id, String title, String body, int flag) {
        Intent i = new Intent(c, ReminderReceiver.class).setAction(ACTION + id);
        if (title != null) {
            i.putExtra("id", id);
            i.putExtra("title", title);
            i.putExtra("body", body);
        }
        return PendingIntent.getBroadcast(c, id, i, flag | PendingIntent.FLAG_IMMUTABLE);
    }
}

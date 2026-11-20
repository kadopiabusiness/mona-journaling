package ir.mona.journal;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
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
                JSONArray days = o.optJSONArray("days");
                for (int k = 0; k < 7 && !onDay(days, cal); k++) cal.add(Calendar.DAY_OF_YEAR, 1);
                String body = o.optString("body");
                JSONArray quotes = o.optJSONArray("quotes");
                if (quotes != null && quotes.length() > 0) {
                    try {
                        LocalDate start = LocalDate.parse(o.optString("qstart", "2026-11-20"));
                        LocalDate day = LocalDate.of(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH));
                        long n = ChronoUnit.DAYS.between(start, day);
                        int len = quotes.length();
                        body = quotes.optString((int) (((n % len) + len) % len), body);
                    } catch (Exception ignored) {
                    }
                }
                PendingIntent p = pending(c, id, o.optString("title"), body, PendingIntent.FLAG_UPDATE_CURRENT);
                if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms())
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), p);
                else
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), p);
            }
        } catch (Exception ignored) {
        }
    }

    /** days uses Saturday=0 … Friday=6; empty or missing means every day. */
    private static boolean onDay(JSONArray days, Calendar cal) {
        if (days == null || days.length() == 0) return true;
        int dow = cal.get(Calendar.DAY_OF_WEEK);
        int idx = dow == Calendar.SATURDAY ? 0 : dow; // Sunday=1 … Friday=6
        for (int i = 0; i < days.length(); i++) if (days.optInt(i, -1) == idx) return true;
        return false;
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

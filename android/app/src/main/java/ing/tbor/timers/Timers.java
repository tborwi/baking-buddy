package ing.tbor.timers;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;

/**
 * The four timers: state, actions and alarms. One in-memory copy per process, shared by the
 * screen (TimersActivity) and the alarm/notification receiver, saved to SharedPreferences.
 *
 * "lastSet" = the duration the timer last started from (or was typed in); Reset goes back to it.
 * +1/-1 while running or paused do not change lastSet.
 */
public final class Timers {
    public static final int N = 4;
    public static final long MIN = 60_000L;
    public static final long MAX = 99 * MIN + 59_000L; // display is mm:ss
    // muted Nordic palette: terracotta, fjord blue, sage, ochre, clay rose, slate, teal, heather, birch, moss, lingonberry, charcoal
    public static final int[] PALETTE = {
        0xFFC07A5E, 0xFF7C93A6, 0xFF8A9A7E, 0xFFC9A15A, 0xFFC4918A, 0xFF5E6B73,
        0xFF5F8A8B, 0xFF9A8AA6, 0xFFB09F8C, 0xFF6E7F5A, 0xFFA9545A, 0xFF4A4845 };
    private static final int[] DEF_COLORS = { 0xFFC07A5E, 0xFF7C93A6, 0xFF8A9A7E, 0xFFC9A15A };
    static final String CHANNEL = "timer_done_v1";      // old channel with its own alarm sound: backup if RingService can't start
    static final String CHANNEL_RING = "timer_ring_v2"; // silent channel: RingService makes the noise (v0.6.0)
    public static final int SOUND_TONE = 0, SOUND_SPEAK = 1;

    public static final class T {
        public String name;
        public int color;
        public long lastSet;    // ms
        public long remaining;  // ms, valid when not running
        public long endAt;      // wall-clock ms when running, else 0
        public boolean paused;  // stopped part-way (Start resumes without touching lastSet)
        public boolean done;    // finished, alarm ringing until stopped
        public int sound;       // SOUND_TONE (play soundUri) or SOUND_SPEAK (say the name)
        public String soundUri; // null = the phone's alarm sound, "" = silent (vibrate only), else a picked sound
        public boolean running() { return endAt > 0; }
        public long left(long now) { return running() ? Math.max(0, endAt - now) : remaining; }
    }

    private static T[] ts;

    public static synchronized T[] get(Context c) {
        if (ts != null) return ts;
        SharedPreferences p = prefs(c);
        ts = new T[N];
        for (int i = 0; i < N; i++) {
            T t = new T();
            t.name = p.getString("n" + i, "Timer " + (i + 1));
            t.color = p.getInt("c" + i, DEF_COLORS[i]);
            boolean known = false; for (int pc : PALETTE) known |= pc == t.color;
            if (!known) t.color = DEF_COLORS[i]; // colors from the old bright palette
            t.lastSet = p.getLong("l" + i, 0);
            t.remaining = p.getLong("r" + i, 0);
            t.endAt = p.getLong("e" + i, 0);
            t.paused = p.getBoolean("p" + i, false);
            t.done = p.getBoolean("d" + i, false);
            t.sound = p.getInt("sm" + i, SOUND_TONE);
            t.soundUri = p.getString("su" + i, null);
            ts[i] = t;
        }
        return ts;
    }

    public static synchronized void save(Context c) {
        if (ts == null) return;
        SharedPreferences.Editor e = prefs(c).edit();
        for (int i = 0; i < N; i++) {
            T t = ts[i];
            e.putString("n" + i, t.name).putInt("c" + i, t.color).putLong("l" + i, t.lastSet)
             .putLong("r" + i, t.remaining).putLong("e" + i, t.endAt)
             .putBoolean("p" + i, t.paused).putBoolean("d" + i, t.done).putInt("sm" + i, t.sound);
            if (t.soundUri == null) e.remove("su" + i); else e.putString("su" + i, t.soundUri);
        }
        e.apply();
    }

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences("timers", Context.MODE_PRIVATE);
    }

    // ---------------------------------------------------------------- actions

    /** Start, or pause if running, or silence the alarm if done. */
    public static synchronized void startPause(Context c, int i) {
        T t = get(c)[i]; long now = System.currentTimeMillis();
        if (t.done) { stopAlarm(c, i); return; }
        if (t.running()) {
            t.remaining = Math.max(0, t.endAt - now); t.endAt = 0; t.paused = t.remaining > 0;
            cancelAlarm(c, i);
        } else {
            if (t.remaining <= 0) { if (t.lastSet <= 0) return; t.remaining = t.lastSet; t.paused = false; }
            if (!t.paused) t.lastSet = t.remaining;
            t.endAt = now + t.remaining; t.paused = false;
            schedule(c, i, t.endAt);
        }
        save(c);
    }

    /** Add (or take away) a minute. Adding to a ringing timer = snooze: it runs one more minute. */
    public static synchronized void addMinute(Context c, int i, int sign) {
        T t = get(c)[i]; long now = System.currentTimeMillis();
        if (t.done) {
            if (sign < 0) return;
            t.done = false; cancelNotification(c, i);
            t.endAt = now + MIN; t.remaining = 0; t.paused = false;
            schedule(c, i, t.endAt);
        } else if (t.running()) {
            long end = Math.min(t.endAt + sign * MIN, now + MAX);
            if (end <= now) { t.endAt = 0; t.remaining = 0; t.paused = false; cancelAlarm(c, i); }
            else { t.endAt = end; schedule(c, i, end); }
        } else {
            t.remaining = Math.min(MAX, Math.max(0, t.remaining + sign * MIN));
            if (t.remaining == 0) t.paused = false;
        }
        save(c);
    }

    /** Back to the last set time, stopped. */
    public static synchronized void reset(Context c, int i) {
        T t = get(c)[i];
        t.done = false; t.endAt = 0; t.paused = false; t.remaining = t.lastSet;
        cancelAlarm(c, i); cancelNotification(c, i);
        save(c);
    }

    /** Stop and zero (keeps the name, color and last set time). */
    public static synchronized void clear(Context c, int i) {
        T t = get(c)[i];
        t.done = false; t.endAt = 0; t.paused = false; t.remaining = 0;
        cancelAlarm(c, i); cancelNotification(c, i);
        save(c);
    }

    /** Typed-in time: becomes the new "last set" time. Keeps running if it was running. */
    public static synchronized void setTime(Context c, int i, long ms) {
        T t = get(c)[i]; ms = Math.min(ms, MAX);
        t.done = false; cancelNotification(c, i); t.lastSet = ms; t.paused = false;
        if (t.running() && ms > 0) { t.endAt = System.currentTimeMillis() + ms; schedule(c, i, t.endAt); }
        else { cancelAlarm(c, i); t.endAt = 0; t.remaining = ms; }
        save(c);
    }

    public static synchronized void setName(Context c, int i, String name) {
        get(c)[i].name = name; save(c);
    }

    public static synchronized void setColor(Context c, int i, int color) {
        get(c)[i].color = color; save(c);
    }

    /** End-of-timer sound: SOUND_TONE with a picked sound (null = phone's alarm sound, "" = silent), or SOUND_SPEAK. */
    public static synchronized void setSound(Context c, int i, int mode, String uri) {
        T t = get(c)[i]; t.sound = mode; t.soundUri = uri; save(c);
        if (t.done) RingService.update(c);
    }

    public static synchronized void stopAlarm(Context c, int i) {
        T t = get(c)[i];
        t.done = false; cancelNotification(c, i); save(c);
    }

    /** Called by the alarm and by the screen's tick; whichever comes first rings it. */
    public static synchronized void finishIfDue(Context c, int i) {
        T t = get(c)[i]; long now = System.currentTimeMillis();
        if (!t.running()) return;
        if (t.endAt > now + 1000) { schedule(c, i, t.endAt); return; } // stale alarm after +1 min
        t.endAt = 0; t.remaining = 0; t.paused = false; t.done = true;
        save(c);
        ring(c, i);
    }

    /** After a reboot alarms are gone: put them back (or ring ones that ended meanwhile). */
    public static synchronized void reschedule(Context c) {
        T[] all = get(c);
        for (int i = 0; i < N; i++) {
            if (all[i].running()) finishIfDue(c, i);
            else if (all[i].done) ring(c, i);
        }
    }

    // ---------------------------------------------------------------- alarms + notifications

    private static PendingIntent firePI(Context c, int i) {
        Intent in = new Intent(c, AlarmReceiver.class).setAction(AlarmReceiver.FIRE).putExtra("i", i);
        return PendingIntent.getBroadcast(c, i, in, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static PendingIntent openPI(Context c) {
        Intent in = new Intent(c, TimersActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return PendingIntent.getActivity(c, 0, in, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static void schedule(Context c, int i, long at) {
        AlarmManager am = c.getSystemService(AlarmManager.class);
        if (am.canScheduleExactAlarms()) am.setAlarmClock(new AlarmManager.AlarmClockInfo(at, openPI(c)), firePI(c, i));
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, firePI(c, i));
    }

    private static void cancelAlarm(Context c, int i) {
        c.getSystemService(AlarmManager.class).cancel(firePI(c, i));
    }

    static void ensureChannel(Context c) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        NotificationChannel ring = new NotificationChannel(CHANNEL_RING, "Timer ringing", NotificationManager.IMPORTANCE_HIGH);
        ring.setDescription("Shows a finished timer. The app plays its sound or speaks its name.");
        ring.setSound(null, null); ring.enableVibration(false);
        ring.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        nm.createNotificationChannel(ring);
        if (nm.getNotificationChannel(CHANNEL) != null) {
            NotificationChannel old = nm.getNotificationChannel(CHANNEL);
            old.setName("Timer done (backup sound)"); nm.createNotificationChannel(old);
            return;
        }
        NotificationChannel ch = new NotificationChannel(CHANNEL, "Timer done (backup sound)", NotificationManager.IMPORTANCE_HIGH);
        Uri sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        if (sound == null) sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        ch.setSound(sound, new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
        ch.enableVibration(true);
        ch.setVibrationPattern(new long[] { 0, 600, 400, 600, 400, 600 });
        ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        nm.createNotificationChannel(ch);
    }

    /** Shows the ringing timer and starts RingService for the noise; if that can't start, the old sounding channel rings. */
    private static void ring(Context c, int i) {
        ensureChannel(c);
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        nm.notify(1 + i, buildRing(c, i, CHANNEL_RING));
        if (!RingService.update(c)) {
            Notification n = buildRing(c, i, CHANNEL);
            n.flags |= Notification.FLAG_INSISTENT; // repeat the sound until stopped
            nm.notify(1 + i, n);
        }
    }

    static Notification buildRing(Context c, int i) { return buildRing(c, i, CHANNEL_RING); }

    private static Notification buildRing(Context c, int i, String channel) {
        T t = get(c)[i];
        Intent stop = new Intent(c, AlarmReceiver.class).setAction(AlarmReceiver.STOP).putExtra("i", i);
        PendingIntent stopPI = PendingIntent.getBroadcast(c, 100 + i, stop,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Intent again = new Intent(c, AlarmReceiver.class).setAction(AlarmReceiver.REPOST).putExtra("i", i);
        PendingIntent againPI = PendingIntent.getBroadcast(c, 300 + i, again,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Intent more = new Intent(c, AlarmReceiver.class).setAction(AlarmReceiver.PLUS1).putExtra("i", i);
        PendingIntent morePI = PendingIntent.getBroadcast(c, 200 + i, more,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(c, channel)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(t.name + " is done")
                .setContentText("Tap Stop to silence")
                .setColor(t.color).setColorized(true)
                .setCategory(Notification.CATEGORY_ALARM)
                .setOnlyAlertOnce(true) // RingService re-posts it as its own: no second pop-up
                .setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
                .setContentIntent(openPI(c))
                .setFullScreenIntent(openPI(c), true)
                .setOngoing(true)
                .setDeleteIntent(againPI) // swiped away = shown again: only Stop (or the app) silences it (v0.8.0)
                .addAction(new Notification.Action.Builder(null, "Stop", stopPI).build())
                .addAction(new Notification.Action.Builder(null, "+1 min", morePI).build())
                .build();
    }

    /** The ringing notification was swiped away: put it back while the timer still rings. */
    public static synchronized void repost(Context c, int i) {
        if (!get(c)[i].done) return;
        c.getSystemService(NotificationManager.class).notify(1 + i, buildRing(c, i, CHANNEL_RING));
        RingService.update(c);
    }

    private static void cancelNotification(Context c, int i) {
        c.getSystemService(NotificationManager.class).cancel(1 + i);
        RingService.update(c); // quiet (or switch to the next ringing timer)
    }
}

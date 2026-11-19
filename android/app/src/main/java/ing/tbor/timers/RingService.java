package ing.tbor.timers;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.VibrationAttributes;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;

import java.util.ArrayList;
import java.util.Locale;
import java.util.Objects;

/**
 * Makes the noise while a timer rings (v0.6.0): each timer either plays its own sound (picked with the system sound
 * picker; "silent" = vibrate only) or speaks its name, at alarm volume, until Stop is pressed (no time limit).
 * v0.8.0: one "announcement" = the alarm sound played once, then the ringing timers' names; then a quiet gap
 * (Settings > Alarm repeat, 1-60 s, all timers: prefs settings.gapSec) and it starts over.
 * Runs as a foreground service (type systemExempted: allowed for apps with USE_EXACT_ALARM that keep an alarm going),
 * whose notification is the ringing timer's own notification. The notification channel itself is silent.
 * Timers.java calls update() whenever a timer starts or stops ringing; with nothing ringing the service stops.
 */
public class RingService extends Service {
    private static final String TAG = "WalleTimers";
    private static RingService running;

    static final AudioAttributes ALARM = new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build();
    static final AudioAttributes ALARM_SPEECH = new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build();
    private static final int PLACEHOLDER_ID = 99;
    static final int GAP_MIN = 1, GAP_MAX = 60, GAP_DEF = 2; // seconds of quiet between announcements

    private final Handler h = new Handler(Looper.getMainLooper());
    private int fgId = -1;                 // notification id this service is in the foreground with
    private Uri toneUri;                  // sound to play each announcement (null = none)
    private MediaPlayer player; private Ringtone tone; // tone = fallback if MediaPlayer can't open the sound
    private TextToSpeech tts; private int ttsState; // 0 = not started / starting, 1 = ready, -1 = no voice
    private String speakText = "";
    private String cycleKey;              // what the running announcement loop plays; a change restarts it at once
    private int gen;                      // bumped on every restart so late callbacks from the old loop are ignored
    private boolean vibrating; private AudioFocusRequest focus; private PowerManager.WakeLock wake;

    /** Call after any timer starts or stops ringing. False = the service could not start (caller rings the old way). */
    static boolean update(Context c) {
        if (running != null) { running.refresh(); return true; }
        if (firstRinging(c) < 0) return true;
        try { c.startForegroundService(new Intent(c, RingService.class)); return true; }
        catch (RuntimeException e) { Log.w(TAG, "ring service not started", e); return false; }
    }

    private static int firstRinging(Context c) {
        Timers.T[] ts = Timers.get(c);
        for (int i = 0; i < Timers.N; i++) if (ts[i].done) return i;
        return -1;
    }

    @Override public IBinder onBind(Intent i) { return null; }

    @Override public void onCreate() { super.onCreate(); running = this; }

    @Override public int onStartCommand(Intent in, int flags, int id) {
        if (fgId < 0) {
            // must go foreground right away, even if the timer was stopped in the meantime (then we stop again at once)
            int first = firstRinging(this);
            try {
                if (first >= 0) goForeground(1 + first, Timers.buildRing(this, first));
                else goForeground(PLACEHOLDER_ID, new Notification.Builder(this, Timers.CHANNEL_RING)
                        .setSmallIcon(android.R.drawable.ic_lock_idle_alarm).setContentTitle("Timer").build());
            } catch (RuntimeException e) {
                Log.w(TAG, "startForeground failed", e);
                running = null; stopSelf(); return START_NOT_STICKY;
            }
        }
        refresh();
        return START_STICKY;
    }

    private void goForeground(int nid, Notification n) {
        if (Build.VERSION.SDK_INT >= 34) startForeground(nid, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED);
        else startForeground(nid, n);
        fgId = nid;
    }

    /** Match the noise to whichever timers are ringing now. */
    void refresh() {
        Timers.T[] ts = Timers.get(this);
        int first = -1; boolean anyTone = false; Uri toneUri = null;
        ArrayList<String> names = new ArrayList<>();
        for (int i = 0; i < Timers.N; i++) {
            Timers.T t = ts[i];
            if (!t.done) continue;
            if (first < 0) first = i;
            if (t.sound == Timers.SOUND_SPEAK) names.add(t.name);
            else if (!anyTone) { anyTone = true; toneUri = soundUri(this, t.soundUri); }
        }
        if (first < 0) { shutdown(); return; }

        // stay in the foreground with the timer that rang first; move on only when that one is stopped
        // (switching hands the old notification back to the system, which removes it)
        int keep = fgId > 0 && fgId != PLACEHOLDER_ID && ts[fgId - 1].done ? fgId : 1 + first;
        if (fgId != keep) {
            int old = fgId;
            try { goForeground(keep, Timers.buildRing(this, keep - 1)); } catch (RuntimeException e) { Log.w(TAG, "fg", e); }
            if (old > 0 && old != fgId) getSystemService(NotificationManager.class).cancel(old);
        }

        // speech: start the voice if needed; no voice on the phone = fall back to the alarm sound
        speakText = String.join(". ", names);
        if (!names.isEmpty() && ttsState == 0 && tts == null) startTts();
        if (!names.isEmpty() && ttsState < 0 && !anyTone) { anyTone = true; toneUri = soundUri(this, null); }
        this.toneUri = anyTone ? toneUri : null;

        // a timer started or stopped ringing (or the voice just came up): start a fresh announcement now
        String key = this.toneUri + "|" + speakText + "|" + ttsState;
        if (!Objects.equals(key, cycleKey)) { cycleKey = key; restartCycle(); }

        if (!vibrating) {
            Vibrator v = getSystemService(VibratorManager.class).getDefaultVibrator();
            v.vibrate(VibrationEffect.createWaveform(new long[] { 0, 600, 400, 600, 1600 }, 0),
                    VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM));
            vibrating = true;
        }
        if (focus == null) {
            focus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT).setAudioAttributes(ALARM).build();
            getSystemService(AudioManager.class).requestAudioFocus(focus);
        }
        if (wake == null) { // no time limit: rings until Stop (released in shutdown)
            wake = getSystemService(PowerManager.class).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "walle-timers:ring");
            wake.acquire();
        }
    }

    /** Stored sound -> playable uri: null = the phone's alarm sound (or notification sound if none), "" = silent. */
    static Uri soundUri(Context c, String stored) {
        if (stored != null) return stored.isEmpty() ? null : Uri.parse(stored);
        Uri u = RingtoneManager.getActualDefaultRingtoneUri(c, RingtoneManager.TYPE_ALARM);
        if (u == null) u = RingtoneManager.getActualDefaultRingtoneUri(c, RingtoneManager.TYPE_NOTIFICATION);
        if (u == null) u = RingtoneManager.getActualDefaultRingtoneUri(c, RingtoneManager.TYPE_RINGTONE);
        return u;
    }

    /** Seconds of quiet between announcements (Settings > Alarm repeat). */
    static int gapSec(Context c) {
        int g = c.getSharedPreferences("settings", MODE_PRIVATE).getInt("gapSec", GAP_DEF);
        return Math.max(GAP_MIN, Math.min(GAP_MAX, g));
    }

    // ---------------------------------------------------------------- the announcement loop

    private void restartCycle() {
        gen++;
        h.removeCallbacks(cycle);
        stopTone();
        if (tts != null && ttsState == 1) tts.stop();
        h.post(cycle);
    }

    /** One announcement: the sound once, then the names, then the gap. */
    private final Runnable cycle = () -> playTone(gen);

    private void playTone(int g) {
        if (g != gen) return;
        if (toneUri == null) { speak(g); return; }
        if (!startPlayer(g, toneUri)) {
            Uri d = soundUri(this, null);
            if (d == null || d.equals(toneUri) || !startPlayer(g, d)) startRingtoneOnce(g);
        }
    }

    private boolean startPlayer(int g, Uri u) {
        MediaPlayer mp = new MediaPlayer();
        try {
            mp.setAudioAttributes(ALARM);
            mp.setDataSource(this, u);
            mp.setOnCompletionListener(m -> { if (g == gen) { stopTone(); speak(g); } });
            mp.setOnErrorListener((m, what, extra) -> {
                Log.w(TAG, "sound error " + what + "/" + extra);
                if (g == gen) { stopTone(); speak(g); }
                return true;
            });
            mp.prepare();
            mp.setLooping(false);
            mp.start();
            player = mp;
            watchPlayer(g, mp);
            return true;
        } catch (Exception e) {
            Log.w(TAG, "can't play " + u, e);
            mp.release();
            return false;
        }
    }

    /**
     * Alarm sounds usually carry an ANDROID_LOOP tag, which makes MediaPlayer loop them forever on the alarm stream (no
     * completion callback). So watch the position: when it jumps back to the start (one pass done) or playback stops,
     * end the sound and move on. Unknown-length sounds are cut at 60 s.
     */
    private void watchPlayer(int g, MediaPlayer mp) {
        long until = System.currentTimeMillis() + 60_000;
        h.postDelayed(new Runnable() {
            int last = -1;
            @Override public void run() {
                if (g != gen || player != mp) return;
                int pos;
                try { pos = mp.isPlaying() ? mp.getCurrentPosition() : -1; } catch (IllegalStateException e) { pos = -1; }
                boolean done = pos < 0 || (last >= 0 && pos + 300 < last)
                        || (mp.getDuration() <= 0 && System.currentTimeMillis() > until);
                if (done) { stopTone(); speak(g); return; }
                last = pos;
                h.postDelayed(this, 100);
            }
        }, 100);
    }

    /** Last resort if MediaPlayer can't open the sound: Ringtone, watched until it stops (capped at 60 s). */
    private void startRingtoneOnce(int g) {
        Ringtone r = RingtoneManager.getRingtone(this, toneUri);
        if (r == null) { Log.w(TAG, "no playable sound for " + toneUri); speak(g); return; }
        r.setAudioAttributes(ALARM);
        r.setLooping(false);
        r.play();
        tone = r;
        long until = System.currentTimeMillis() + 60_000;
        h.postDelayed(new Runnable() {
            @Override public void run() {
                if (g != gen) return;
                if (tone != null && tone.isPlaying() && System.currentTimeMillis() < until) { h.postDelayed(this, 250); return; }
                stopTone(); speak(g);
            }
        }, 500);
    }

    private void stopTone() {
        if (player != null) { try { player.stop(); } catch (IllegalStateException ignored) {} player.release(); player = null; }
        if (tone != null) { tone.stop(); tone = null; }
    }

    private void speak(int g) {
        if (g != gen) return;
        if (tts == null || ttsState != 1 || speakText.isEmpty()) { waitGap(g); return; }
        Bundle b = new Bundle();
        b.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1f);
        if (tts.speak(speakText, TextToSpeech.QUEUE_FLUSH, b, "ring" + g) != TextToSpeech.SUCCESS) waitGap(g);
    }

    private void waitGap(int g) {
        if (g != gen) return;
        h.removeCallbacks(cycle);
        h.postDelayed(cycle, gapSec(this) * 1000L);
    }

    private void spoke(String id) {
        if (("ring" + gen).equals(id)) waitGap(gen);
    }

    private void startTts() {
        ttsState = 0;
        h.postDelayed(ttsTimeout, 6000);
        tts = new TextToSpeech(this, status -> h.post(() -> {
            if (tts == null) return;
            h.removeCallbacks(ttsTimeout);
            boolean ok = status == TextToSpeech.SUCCESS;
            if (ok && tts.setLanguage(Locale.getDefault()) < 0 && tts.setLanguage(Locale.US) < 0) ok = false;
            if (ok) {
                tts.setAudioAttributes(ALARM_SPEECH);
                tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    @Override public void onStart(String u) {}
                    @Override public void onDone(String u) { h.post(() -> spoke(u)); }
                    @Override public void onError(String u) { h.post(() -> spoke(u)); }
                });
            }
            ttsState = ok ? 1 : -1;
            if (!ok) Log.w(TAG, "text-to-speech unavailable (" + status + "): using the alarm sound");
            refresh();
        }));
    }

    private final Runnable ttsTimeout = () -> { if (ttsState == 0) { ttsState = -1; refresh(); } };

    private void shutdown() {
        h.removeCallbacksAndMessages(null);
        gen++; stopTone(); cycleKey = null; toneUri = null;
        if (tts != null) { tts.stop(); tts.shutdown(); tts = null; } ttsState = 0;
        if (vibrating) { getSystemService(VibratorManager.class).getDefaultVibrator().cancel(); vibrating = false; }
        if (focus != null) { getSystemService(AudioManager.class).abandonAudioFocusRequest(focus); focus = null; }
        if (wake != null) { if (wake.isHeld()) wake.release(); wake = null; }
        stopForeground(STOP_FOREGROUND_REMOVE); fgId = -1;
        running = null;
        stopSelf();
    }

    @Override public void onDestroy() {
        if (running == this) shutdown();
        super.onDestroy();
    }
}

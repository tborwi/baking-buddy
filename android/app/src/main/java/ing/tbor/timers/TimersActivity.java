package ing.tbor.timers;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.text.InputType;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.RelativeSizeSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;
import java.util.function.Consumer;

/**
 * Four timers in a 2x2 grid. Each quadrant, top to bottom:
 *   [ CLEAR ]  /  timer box (seven-segment mm:ss, MIN/SEC)  /  [ − ][ + ] (1 minute)
 *   [ ▶ NAME start / ■ NAME stop (2/3); long-press = name, color, sound ][ ↺ reset (1/3) ]   — Scandinavian style: linen, hairlines, muted colors.
 * Settings (gear): theme System/Light/Dark, and how much of each quadrant the timer box takes.
 * Behaviour lives in Timers.java; this file is only the screen.
 */
public class TimersActivity extends Activity {
    private static TimersActivity open;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final Quad[] quads = new Quad[Timers.N];
    private Pal pal;
    private static final int REQ_SOUND = 7;
    private Consumer<String> onSoundPicked;      // open Edit timer dialog waiting for the system sound picker
    private TextToSpeech voice; private int voiceState; private String voicePending; // "Hear it" preview

    static final int THEME_SYSTEM = 0, THEME_LIGHT = 1, THEME_DARK = 2;
    static final int SCALE_MIN = 25, SCALE_MAX = 65, SCALE_DEF = 45; // % of a quadrant's height for the timer box

    private static final class Quad {
        IconButton main; SegmentView seg; LinearLayout box, card; int shownColor = 0;
    }

    /** Scandinavian palette: warm linen / oat light theme, warm charcoal dark theme, hairlines not shadows. */
    private static final class Pal {
        final boolean dark; final int bg, surface, line, text, sub;
        Pal(boolean d) {
            dark = d;
            bg = d ? 0xFF1C1B19 : 0xFFF2EEE8;
            surface = d ? 0xFF252321 : 0xFFFBF9F6;
            line = d ? 0xFF3A3632 : 0xFFE2DCD3;
            text = d ? 0xFFFFFFFF : 0xFF3A3632; // dark: pure white
            sub = d ? 0xFFFFFFFF : 0xFF968D82;  // dark: labels (CLEAR, MIN/SEC, gear) white too
        }
        int accent(int c) { return dark ? blend(c, 0xFFFFFFFF, 0.10f) : blend(c, 0xFF2A2724, 0.10f); }
        int tint(int c) { return dark ? blend(c, surface, 0.84f) : blend(c, surface, 0.88f); }
        int ghost(int c) { return (accent(c) & 0x00FFFFFF) | (dark ? 0x1C000000 : 0x16000000); }
        int onAccent(int c) { return luminance(c) > 0.62 ? 0xFF2A2724 : 0xFFFBF9F6; }
    }

    private final Runnable tick = new Runnable() {
        @Override public void run() { refresh(); h.postDelayed(this, 250); }
    };

    static void refreshIfOpen() {
        TimersActivity a = open;
        if (a != null) a.runOnUiThread(a::refresh);
    }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Timers.ensureChannel(this);
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[] { Manifest.permission.POST_NOTIFICATIONS }, 1);
        build();
    }

    @Override public void onConfigurationChanged(Configuration c) {
        super.onConfigurationChanged(c);
        build(); // system dark mode or rotation changed
    }

    @Override protected void onResume() { super.onResume(); open = this; h.post(tick); }
    @Override protected void onPause() { super.onPause(); open = null; h.removeCallbacks(tick); }
    @Override protected void onDestroy() { if (voice != null) { voice.shutdown(); voice = null; } super.onDestroy(); }

    // ------------------------------------------------------------------ settings

    private SharedPreferences settings() { return getSharedPreferences("settings", MODE_PRIVATE); }
    private int themeSetting() { return settings().getInt("theme", THEME_SYSTEM); }
    private int scaleSetting() { return settings().getInt("scale", SCALE_DEF); }
    private boolean hideZeroSetting() { return settings().getBoolean("hideZero", false); }

    private boolean isDark() {
        int t = themeSetting();
        if (t == THEME_DARK) return true;
        if (t == THEME_LIGHT) return false;
        return (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
    }

    /** Context for views inside dialogs, so their text follows the dialog's light/dark theme. */
    private android.content.Context dctx() { return new android.view.ContextThemeWrapper(this, dialogTheme()); }

    private int dialogTheme() {
        return pal.dark ? android.R.style.Theme_Material_Dialog_Alert : android.R.style.Theme_Material_Light_Dialog_Alert;
    }

    private void openSettings() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(24), dp(8), dp(24), dp(8));
        l.addView(heading("Theme"));
        RadioGroup rg = new RadioGroup(dctx());
        String[] names = { "System", "Light", "Dark" };
        for (int i = 0; i < 3; i++) {
            RadioButton rb = new RadioButton(dctx());
            rb.setText(names[i]); rb.setId(100 + i); rb.setTextSize(16);
            rg.addView(rb);
        }
        rg.check(100 + themeSetting());
        rg.setOnCheckedChangeListener((g, id) -> { settings().edit().putInt("theme", id - 100).apply(); build(); });
        l.addView(rg);

        TextView sz = heading("");
        l.addView(sz);
        SeekBar sb = new SeekBar(dctx());
        sb.setMax(SCALE_MAX - SCALE_MIN);
        sb.setProgress(scaleSetting() - SCALE_MIN);
        Runnable label = () -> sz.setText("Timer size: " + (sb.getProgress() + SCALE_MIN) + "% (buttons get the rest)");
        label.run();
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean user) {
                label.run();
                if (user) { settings().edit().putInt("scale", p + SCALE_MIN).apply(); applyScale(); }
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });
        l.addView(sb);
        l.addView(heading("Display"));
        android.widget.Switch hz = new android.widget.Switch(dctx());
        hz.setText("Hide leading zero (5:00, not 05:00)"); hz.setTextSize(16);
        hz.setChecked(hideZeroSetting());
        hz.setPadding(0, dp(8), 0, dp(8));
        hz.setOnCheckedChangeListener((v, on) -> { settings().edit().putBoolean("hideZero", on).apply(); refresh(); });
        l.addView(hz);

        // v0.8.0: alarms repeat until Stop; this is the quiet gap between announcements, for all timers
        TextView gl = heading("");
        l.addView(gl);
        SeekBar gb = new SeekBar(dctx());
        gb.setMax(RingService.GAP_MAX - RingService.GAP_MIN);
        gb.setProgress(RingService.gapSec(this) - RingService.GAP_MIN);
        Runnable glabel = () -> {
            int sec = gb.getProgress() + RingService.GAP_MIN;
            gl.setText("Alarm repeat: wait " + (sec == 60 ? "1 minute" : sec + (sec == 1 ? " second" : " seconds"))
                    + " between alarms");
        };
        glabel.run();
        gb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean user) {
                glabel.run();
                if (user) settings().edit().putInt("gapSec", p + RingService.GAP_MIN).apply();
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });
        l.addView(gb);
        TextView gh = new TextView(dctx());
        gh.setText("Each alarm plays the sound (or says the name), waits this long, and repeats until you press Stop.");
        gh.setTextSize(13); gh.setAlpha(0.7f);
        l.addView(gh);
        android.widget.ScrollView sv = new android.widget.ScrollView(this);
        sv.addView(l);
        new AlertDialog.Builder(this, dialogTheme()).setTitle("Settings").setView(sv)
                .setPositiveButton("Done", null).show();
    }

    private TextView heading(String s) {
        TextView t = new TextView(dctx());
        t.setText(s); t.setTextSize(14); t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(0, dp(16), 0, dp(4));
        return t;
    }

    // ------------------------------------------------------------------ building the screen

    private void build() {
        pal = new Pal(isDark());
        getWindow().getDecorView().setBackgroundColor(pal.bg);
        WindowInsetsController wic = getWindow().getInsetsController();
        if (wic != null) {
            int light = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
            wic.setSystemBarsAppearance(pal.dark ? 0 : light, light);
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(pal.bg);
        root.setOnApplyWindowInsetsListener((v, ins) -> {
            android.graphics.Insets s = ins.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            v.setPadding(s.left + dp(8), s.top, s.right + dp(8), s.bottom + dp(8));
            return WindowInsets.CONSUMED;
        });

        // top bar: quiet letter-spaced title + gear
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(10), dp(4), 0, dp(4));
        TextView title = new TextView(this);
        title.setText("BAKING BUDDY"); title.setTextSize(15); title.setLetterSpacing(0.3f);
        title.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL)); title.setTextColor(pal.text);
        title.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView gear = new TextView(this);
        gear.setText("⚙"); gear.setTextSize(22); gear.setTextColor(pal.sub); gear.setGravity(Gravity.CENTER);
        gear.setContentDescription("Settings");
        gear.setLayoutParams(new LinearLayout.LayoutParams(dp(48), dp(48)));
        gear.setBackground(new RippleDrawable(ColorStateList.valueOf(0x22808080), null, oval(0xFFFFFFFF)));
        gear.setOnClickListener(v -> openSettings());
        bar.addView(title); bar.addView(gear);
        root.addView(bar);

        for (int row = 0; row < 2; row++) {
            LinearLayout r = new LinearLayout(this);
            r.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
            for (int col = 0; col < 2; col++) {
                int i = row * 2 + col;
                quads[i] = buildQuad(i);
                r.addView(quads[i].card);
            }
            root.addView(r);
        }
        setContentView(root);
        applyScale();
        refresh();
    }

    private Quad buildQuad(int i) {
        Quad q = new Quad();
        Timers.T t = Timers.get(this)[i];
        int acc = pal.accent(t.color);
        q.shownColor = t.color;

        q.card = new LinearLayout(this);
        q.card.setOrientation(LinearLayout.VERTICAL);
        q.card.setPadding(dp(10), dp(10), dp(10), dp(10));
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1);
        cp.setMargins(dp(5), dp(5), dp(5), dp(5));
        q.card.setLayoutParams(cp);
        q.card.setBackground(rounded(pal.surface, 6, 1, pal.line));

        // Clear: quiet word button
        IconButton clear = btn(IconButton.TEXT, pal.sub, false, 0, "Clear", v -> act(() -> Timers.clear(this, i)));
        clear.setText("CLEAR");
        q.card.addView(cell(clear, 0.2f));

        // timer box: just the digits, on a pale wash of the timer's color (name lives on the play/stop button)
        q.box = new LinearLayout(this);
        q.box.setOrientation(LinearLayout.VERTICAL);
        q.box.setPadding(dp(8), dp(8), dp(8), dp(6));
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1);
        bl.setMargins(0, dp(4), 0, dp(4));
        q.box.setLayoutParams(bl);
        q.box.setBackground(rounded(pal.tint(t.color), 4, 0, 0));
        q.seg = new SegmentView(this);
        q.seg.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        q.seg.setOnClickListener(v -> editTime(i));
        q.seg.setOnLongClickListener(v -> { numberPad(i); return true; });
        q.seg.setContentDescription("Set time");
        q.box.addView(q.seg);
        q.card.addView(q.box);

        // plain + / − (one minute each)
        LinearLayout pm = new LinearLayout(this);
        pm.addView(cell(btn(IconButton.MINUS, pal.text, false, 0, "Take away 1 minute",
                v -> act(() -> Timers.addMinute(this, i, -1))), 1));
        pm.addView(cell(btn(IconButton.PLUS, pal.text, false, 0, "Add 1 minute",
                v -> act(() -> Timers.addMinute(this, i, +1))), 1));
        q.card.addView(withWeight(pm, 0.42f));

        // ▶ / ■ (2/3, filled in the timer's color) + reset (1/3)
        LinearLayout bottom = new LinearLayout(this);
        q.main = btn(IconButton.PLAY, pal.onAccent(acc), true, acc, "Start",
                v -> act(() -> Timers.startPause(this, i)));
        q.main.setLongClickable(true);
        q.main.setOnLongClickListener(v -> { editTimer(i); return true; }); // name + color
        bottom.addView(cell(q.main, 2));
        bottom.addView(cell(btn(IconButton.RESET, pal.text, false, 0, "Reset to last set time",
                v -> act(() -> Timers.reset(this, i))), 1));
        q.card.addView(withWeight(bottom, 0.30f));
        return q;
    }

    /** Timer box gets scale%, the three button rows share the rest (22 : 44 : 34). */
    private void applyScale() {
        float s = scaleSetting() / 100f, rest = 1 - s;
        for (Quad q : quads) {
            if (q == null) continue;
            setWeight(q.card.getChildAt(0), rest * 0.22f);
            setWeight(q.box, s);
            setWeight(q.card.getChildAt(2), rest * 0.44f);
            setWeight(q.card.getChildAt(3), rest * 0.34f);
            q.card.requestLayout();
        }
    }

    private void act(Runnable r) { r.run(); refresh(); }

    private void refresh() {
        long now = System.currentTimeMillis();
        boolean hideZero = hideZeroSetting();
        Timers.T[] ts = Timers.get(this);
        for (int i = 0; i < Timers.N; i++) {
            Timers.T t = ts[i];
            if (t.running() && t.endAt <= now) Timers.finishIfDue(this, i);
            Quad q = quads[i];
            if (q == null) continue;
            if (q.shownColor != t.color) { build(); return; } // color changed: restyle everything
            q.main.setLabel(t.name.toUpperCase(java.util.Locale.getDefault()));
            q.seg.setColors(pal.accent(t.color), pal.ghost(t.color), pal.sub);
            q.seg.setHideLeadingZero(hideZero);
            if (t.done) q.seg.setDone((now / 500) % 2 == 0); else q.seg.setTime(t.left(now));
            // square = stop: pauses a running timer, silences a ringing one; triangle = start/resume
            boolean stop = t.done || t.running();
            q.main.setKind(stop ? IconButton.STOP : IconButton.PLAY);
            q.main.setContentDescription((t.done ? "Stop alarm" : t.running() ? "Stop" : t.paused ? "Resume" : "Start")
                    + " " + t.name + ". Long-press for name, color and sound");
        }
    }

    // ------------------------------------------------------------------ button views

    /** A weighted slot holding a button that fills it (height capped by the button). */
    private FrameLayout cell(View child, float weight) {
        FrameLayout f = new FrameLayout(this);
        f.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, weight));
        f.setPadding(dp(3), dp(3), dp(3), dp(3));
        child.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER));
        f.addView(child);
        return f;
    }

    /** A horizontal row of cells: weight goes on the width. */
    private View withWeight(LinearLayout row, float weight) {
        row.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, weight));
        for (int k = 0; k < row.getChildCount(); k++) {
            LinearLayout.LayoutParams p = (LinearLayout.LayoutParams) row.getChildAt(k).getLayoutParams();
            p.width = 0; p.height = ViewGroup.LayoutParams.MATCH_PARENT;
        }
        return row;
    }

    private static void setWeight(View v, float w) {
        LinearLayout.LayoutParams p = (LinearLayout.LayoutParams) v.getLayoutParams();
        p.weight = w; p.height = 0;
        v.setLayoutParams(p);
    }

    /** Flat button: hairline outline, or filled with the timer's color; small square-ish corners. */
    private IconButton btn(int kind, int fg, boolean filled, int fill, String desc, View.OnClickListener l) {
        IconButton b = new IconButton(this, kind, 68);
        b.setColor(fg);
        GradientDrawable shape = rounded(filled ? fill : pal.surface, 4, filled ? 0 : 1, pal.line);
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(pal.dark ? 0x22FFFFFF : 0x14000000), shape, null));
        b.setContentDescription(desc);
        b.setOnClickListener(l);
        return b;
    }

    // ------------------------------------------------------------------ dialogs

    /** Long-press on the play/stop button: name and color in one dialog. OK saves both, Cancel neither. */
    private void editTimer(int i) {
        Timers.T t = Timers.get(this)[i];
        int[] chosen = { t.color };
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(20), dp(8), dp(20), 0);
        EditText e = new EditText(dctx());
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        e.setSingleLine(true);
        e.setText(t.name); e.setSelectAllOnFocus(true);
        e.setHint("Name");
        l.addView(e);
        l.addView(heading("Color"));
        GridLayout g = new GridLayout(this);
        g.setColumnCount(4);
        View[] sw = new View[Timers.PALETTE.length];
        Runnable mark = () -> {
            for (int k = 0; k < sw.length; k++) {
                GradientDrawable o = oval(Timers.PALETTE[k]);
                if (Timers.PALETTE[k] == chosen[0]) o.setStroke(dp(4), pal.dark ? 0xFFFFFFFF : 0xFF3A3632);
                else o.setStroke(dp(1), pal.dark ? 0x66FFFFFF : 0x33000000);
                sw[k].setBackground(o);
            }
        };
        for (int k = 0; k < sw.length; k++) {
            int c = Timers.PALETTE[k];
            View v = new View(this);
            GridLayout.LayoutParams p = new GridLayout.LayoutParams();
            p.width = dp(48); p.height = dp(48); p.setMargins(dp(8), dp(8), dp(8), dp(8));
            v.setLayoutParams(p);
            v.setContentDescription("Color " + (k + 1));
            v.setOnClickListener(x -> { chosen[0] = c; mark.run(); });
            sw[k] = v;
            g.addView(v);
        }
        mark.run();
        l.addView(g);

        // sound at the end: a sound from the system picker (or silent), or the phone says the timer's name
        l.addView(heading("Sound when done"));
        int[] mode = { t.sound };
        String[] uri = { t.soundUri };
        RadioGroup rg = new RadioGroup(dctx());
        RadioButton tone = new RadioButton(dctx());
        tone.setId(200); tone.setText("Alarm sound"); tone.setTextSize(16);
        TextView toneName = link("");
        RadioButton say = new RadioButton(dctx());
        say.setId(201); say.setText("Speak the name"); say.setTextSize(16);
        TextView hear = link("▶  Hear it");
        rg.addView(tone); rg.addView(toneName); rg.addView(say); rg.addView(hear);
        Runnable showTone = () -> toneName.setText("♪  " + soundTitle(uri[0]) + "  ›");
        showTone.run();
        rg.check(t.sound == Timers.SOUND_SPEAK ? 201 : 200);
        rg.setOnCheckedChangeListener((gr, id) -> mode[0] = id == 201 ? Timers.SOUND_SPEAK : Timers.SOUND_TONE);
        toneName.setContentDescription("Choose the alarm sound");
        toneName.setOnClickListener(v -> {
            rg.check(200);
            pickSound(nameOr(e, i), uri[0], picked -> { uri[0] = picked; showTone.run(); });
        });
        hear.setContentDescription("Hear the name spoken");
        hear.setOnClickListener(v -> { rg.check(201); hear(nameOr(e, i)); });
        l.addView(rg);

        android.widget.ScrollView sv = new android.widget.ScrollView(this);
        sv.addView(l);
        new AlertDialog.Builder(this, dialogTheme()).setTitle("Edit timer").setView(sv)
                .setPositiveButton("OK", (d, w) -> {
                    Timers.setName(this, i, nameOr(e, i));
                    Timers.setColor(this, i, chosen[0]);
                    Timers.setSound(this, i, mode[0], uri[0]);
                    refresh();
                })
                .setNegativeButton("Cancel", null)
                .setOnDismissListener(d -> { onSoundPicked = null; if (voice != null && voiceState == 1) voice.stop(); })
                .show();
    }

    private static String nameOr(EditText e, int i) {
        String n = e.getText().toString().trim();
        return n.isEmpty() ? "Timer " + (i + 1) : n;
    }

    /** A tappable line under a radio button, lined up with the radio's text. */
    private TextView link(String s) {
        TextView v = new TextView(dctx());
        v.setText(s); v.setTextSize(15); v.setTextColor(pal.text); // white in dark mode
        v.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        v.setPadding(dp(32), dp(8), dp(8), dp(10));
        v.setBackground(new RippleDrawable(ColorStateList.valueOf(0x22808080), null, rounded(0xFFFFFFFF, 4, 0, 0)));
        return v;
    }

    /** Stored sound: null = phone's alarm sound, "" = silent, else a picked sound's uri. */
    private String soundTitle(String stored) {
        if (stored != null && stored.isEmpty()) return "Silent (vibrate only)";
        Uri u = stored == null ? Settings.System.DEFAULT_ALARM_ALERT_URI : Uri.parse(stored);
        try {
            Ringtone r = RingtoneManager.getRingtone(this, u);
            String n = r == null ? null : r.getTitle(this);
            if (n != null && !n.isEmpty()) return n;
        } catch (RuntimeException ignored) { }
        return stored == null ? "Phone's alarm sound" : "Picked sound";
    }

    /** The system sound picker (with Default and Silent). */
    private void pickSound(String name, String current, Consumer<String> done) {
        Uri existing = current == null ? Settings.System.DEFAULT_ALARM_ALERT_URI : current.isEmpty() ? null : Uri.parse(current);
        Intent in = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Sound for " + name)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, Settings.System.DEFAULT_ALARM_ALERT_URI)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, existing);
        onSoundPicked = done;
        try { startActivityForResult(in, REQ_SOUND); }
        catch (android.content.ActivityNotFoundException x) {
            onSoundPicked = null;
            Toast.makeText(this, "No sound picker on this phone", Toast.LENGTH_LONG).show();
        }
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != REQ_SOUND || res != RESULT_OK || data == null || onSoundPicked == null) return;
        Uri u = data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri.class);
        onSoundPicked.accept(u == null ? "" : u.equals(Settings.System.DEFAULT_ALARM_ALERT_URI) ? null : u.toString());
    }

    /** "Hear it": says the name once, at alarm volume, the way the alarm will. */
    private void hear(String text) {
        voicePending = text;
        if (voice != null) { sayPending(); return; }
        voiceState = 0;
        voice = new TextToSpeech(this, st -> runOnUiThread(() -> {
            if (voice == null) return;
            boolean ok = st == TextToSpeech.SUCCESS;
            if (ok && voice.setLanguage(Locale.getDefault()) < 0 && voice.setLanguage(Locale.US) < 0) ok = false;
            if (ok) voice.setAudioAttributes(RingService.ALARM_SPEECH);
            voiceState = ok ? 1 : -1;
            sayPending();
        }));
    }

    private void sayPending() {
        if (voiceState < 0) {
            Toast.makeText(this, "No text-to-speech voice on this phone: the alarm sound will play instead", Toast.LENGTH_LONG).show();
            voicePending = null;
        } else if (voiceState == 1 && voicePending != null) {
            voice.speak(voicePending, TextToSpeech.QUEUE_FLUSH, null, "preview");
            voicePending = null;
        }
    }

    private void editTime(int i) {
        Timers.T t = Timers.get(this)[i];
        long cur = t.left(System.currentTimeMillis());
        if (cur == 0) cur = t.lastSet;
        long secs = Math.min(Timers.MAX, cur) / 1000;
        NumberPicker mp = picker(0, 99, (int) (secs / 60));
        NumberPicker sp = picker(0, 59, (int) (secs % 60));
        LinearLayout l = new LinearLayout(this);
        l.setGravity(Gravity.CENTER);
        l.addView(mp); l.addView(label("min")); l.addView(sp); l.addView(label("sec"));
        new AlertDialog.Builder(this, dialogTheme()).setTitle("Set " + t.name).setView(l)
                .setPositiveButton("Set", (d, w) -> {
                    Timers.setTime(this, i, (mp.getValue() * 60L + sp.getValue()) * 1000L); refresh();
                })
                .setNegativeButton("Cancel", null).show();
    }

    /**
     * Long press on the digits: microwave-style number pad. Digits fill from the right (5,3,0 = 5:30; 90 = 1:30).
     * Set makes it the timer's new "last set" (sticky) time, which Reset returns to.
     */
    private void numberPad(int i) {
        Timers.T t = Timers.get(this)[i];
        int acc = pal.accent(t.color);
        StringBuilder typed = new StringBuilder();
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(16), dp(8), dp(16), dp(4));
        l.setBackgroundColor(pal.surface);

        SegmentView preview = new SegmentView(this);
        preview.setColors(acc, pal.ghost(t.color), pal.sub);
        preview.setHideLeadingZero(hideZeroSetting());
        preview.setTime(0);
        preview.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(110)));
        l.addView(preview);
        Runnable show = () -> preview.setTime(padMs(typed));

        GridLayout g = new GridLayout(this);
        g.setColumnCount(3);
        g.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        String[] keys = { "1", "2", "3", "4", "5", "6", "7", "8", "9", "C", "0", "⌫" };
        for (String k : keys) {
            IconButton b = btn(IconButton.TEXT, k.equals("C") || k.equals("⌫") ? pal.sub : pal.text, false, 0,
                    k.equals("C") ? "Clear entry" : k.equals("⌫") ? "Delete" : k, v -> {
                if (k.equals("C")) typed.setLength(0);
                else if (k.equals("⌫")) { if (typed.length() > 0) typed.setLength(typed.length() - 1); }
                else if (typed.length() < 4 && !(typed.length() == 0 && k.equals("0"))) typed.append(k);
                show.run();
            });
            b.setText(k);
            GridLayout.LayoutParams p = new GridLayout.LayoutParams(GridLayout.spec(GridLayout.UNDEFINED),
                    GridLayout.spec(GridLayout.UNDEFINED, 1f));
            p.width = 0; p.height = dp(60); p.setMargins(dp(4), dp(4), dp(4), dp(4));
            b.setLayoutParams(p);
            g.addView(b);
        }
        l.addView(g);
        new AlertDialog.Builder(this, dialogTheme()).setTitle(t.name).setView(l)
                .setPositiveButton("Set", (d, w) -> { Timers.setTime(this, i, padMs(typed)); refresh(); })
                .setNegativeButton("Cancel", null).show();
    }

    /** "530" -> 5:30, "90" -> 1:30 (seconds over 59 roll into minutes), capped at 99:59. */
    private static long padMs(CharSequence typed) {
        int v = typed.length() == 0 ? 0 : Integer.parseInt(typed.toString());
        return Math.min(Timers.MAX, ((v / 100) * 60L + v % 100) * 1000L);
    }

    private NumberPicker picker(int min, int max, int val) {
        NumberPicker p = new NumberPicker(dctx());
        p.setMinValue(min); p.setMaxValue(max); p.setValue(val);
        p.setFormatter(v -> String.format("%02d", v));
        return p;
    }

    private TextView label(String s) {
        TextView t = new TextView(dctx());
        t.setText(s); t.setPadding(dp(4), 0, dp(16), 0);
        return t;
    }

    // ------------------------------------------------------------------ helpers

    private GradientDrawable rounded(int fill, int radiusDp, int strokeDp, int stroke) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill); g.setCornerRadius(dp(radiusDp));
        if (strokeDp > 0) g.setStroke(dp(strokeDp), stroke);
        return g;
    }

    private static GradientDrawable oval(int fill) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL); g.setColor(fill);
        return g;
    }

    private static double luminance(int c) {
        return (0.299 * Color.red(c) + 0.587 * Color.green(c) + 0.114 * Color.blue(c)) / 255.0;
    }

    static int blend(int a, int b, float f) {
        int r = (int) (Color.red(a) * (1 - f) + Color.red(b) * f);
        int g = (int) (Color.green(a) * (1 - f) + Color.green(b) * f);
        int bl = (int) (Color.blue(a) * (1 - f) + Color.blue(b) * f);
        return Color.rgb(r, g, bl);
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}

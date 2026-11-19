package ing.tbor.timers;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

/** Seven-segment "mm:ss" readout with "min" / "sec" labels under the digit pairs. */
public class SegmentView extends View {
    // segment bits: a=top b=top-right c=bottom-right d=bottom e=bottom-left f=top-left g=middle
    private static final int BLANK = -1; // digit not drawn at all (no ghost segments)
    private static final int A = 1, B = 2, C = 4, D = 8, E = 16, F = 32, G = 64;
    private static final int[] DIGITS = {
        A|B|C|D|E|F, B|C, A|B|D|E|G, A|B|C|D|G, B|C|F|G, A|C|D|F|G, A|C|D|E|F|G, A|B|C, A|B|C|D|E|F|G, A|B|C|D|F|G };

    private final Paint lit = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ghost = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private int[] segs = { DIGITS[0], DIGITS[0], DIGITS[0], DIGITS[0] };
    private boolean dark; // blink-off frame of a ringing timer
    private boolean hideZero; // blank the first minute digit when it is 0

    public void setHideLeadingZero(boolean h) { if (h != hideZero) { hideZero = h; invalidate(); } }

    public SegmentView(Context c) {
        super(c);
        label.setTextAlign(Paint.Align.CENTER);
        label.setLetterSpacing(0.18f);
        label.setTypeface(android.graphics.Typeface.create("sans-serif-light", android.graphics.Typeface.NORMAL));
    }

    public void setColors(int litColor, int ghostColor, int labelColor) {
        if (lit.getColor() == litColor && ghost.getColor() == ghostColor && label.getColor() == labelColor) return;
        lit.setColor(litColor); ghost.setColor(ghostColor); label.setColor(labelColor); invalidate();
    }

    /** Shows mm:ss (capped at 99:59). */
    public void setTime(long ms) {
        long s = Math.min(99 * 60 + 59, (ms + 999) / 1000); // round up: 0:01 until it really ends
        long m = s / 60, sec = s % 60;
        set(new int[] { hideZero && m < 10 ? BLANK : DIGITS[(int) (m / 10)], DIGITS[(int) (m % 10)], DIGITS[(int) (sec / 10)], DIGITS[(int) (sec % 10)] }, false);
    }

    /** Shows "donE"; blinkOff dims the lit segments. */
    public void setDone(boolean blinkOff) {
        set(new int[] { B|C|D|E|G, C|D|E|G, C|E|G, A|D|E|F|G }, blinkOff);
    }

    private void set(int[] s, boolean off) {
        if (java.util.Arrays.equals(s, segs) && off == dark) return;
        segs = s; dark = off; invalidate();
    }

    @Override protected void onDraw(Canvas cv) {
        float aw = getWidth() - getPaddingLeft() - getPaddingRight();
        float ah = getHeight() - getPaddingTop() - getPaddingBottom();
        // layout in units of digit width W: 4 digits, 2 pair gaps (0.2W), colon (0.7W); height 1.8W + labels 0.4W
        float w = Math.min(aw / 5.1f, ah / 2.2f);
        if (w <= 0) return;
        // extra height (bigger "Timer size" setting, portrait quadrants) makes the digits taller, up to 3x their width
        float h = Math.max(1.8f * w, Math.min(3.0f * w, ah - 0.4f * w)), gap = 0.2f * w, colon = 0.7f * w;
        float total = 4 * w + 2 * gap + colon;
        float x0 = getPaddingLeft() + (aw - total) / 2;
        float y0 = getPaddingTop() + (ah - (h + 0.4f * w)) / 2;
        float t = Math.min(0.115f * w, 0.07f * h); // slim, upright segments

        cv.save();
                float[] xs = { x0, x0 + w + gap, x0 + 2 * w + gap + colon, x0 + 3 * w + 2 * gap + colon };
        for (int i = 0; i < 4; i++) digit(cv, xs[i], y0, w, h, t, segs[i]);
        float cx = x0 + 2 * w + gap + colon / 2;
        Paint cp = dark ? ghost : lit;
        cv.drawCircle(cx, y0 + h * 0.34f, t * 0.55f, cp);
        cv.drawCircle(cx, y0 + h * 0.66f, t * 0.55f, cp);
        cv.restore();

        label.setTextSize(0.24f * w);
        float ly = y0 + h + 0.36f * w;
        cv.drawText("MIN", (xs[0] + xs[1] + w) / 2, ly, label);
        cv.drawText("SEC", (xs[2] + xs[3] + w) / 2, ly, label);
    }

    private void digit(Canvas cv, float x, float y, float w, float h, float t, int on) {
        if (on == BLANK) return;
        float g = t * 0.18f, l = x + t / 2 + g, r = x + w - t / 2 - g;
        float top = y + t / 2, mid = y + h / 2, bot = y + h - t / 2;
        hseg(cv, l, r, top, t, (on & A) != 0);
        hseg(cv, l, r, mid, t, (on & G) != 0);
        hseg(cv, l, r, bot, t, (on & D) != 0);
        vseg(cv, x + t / 2, top + g, mid - g, t, (on & F) != 0);
        vseg(cv, x + w - t / 2, top + g, mid - g, t, (on & B) != 0);
        vseg(cv, x + t / 2, mid + g, bot - g, t, (on & E) != 0);
        vseg(cv, x + w - t / 2, mid + g, bot - g, t, (on & C) != 0);
    }

    private void hseg(Canvas cv, float x1, float x2, float y, float t, boolean on) {
        float k = t / 2;
        path.rewind();
        path.moveTo(x1, y); path.lineTo(x1 + k, y - k); path.lineTo(x2 - k, y - k);
        path.lineTo(x2, y); path.lineTo(x2 - k, y + k); path.lineTo(x1 + k, y + k); path.close();
        cv.drawPath(path, on && !dark ? lit : ghost);
    }

    private void vseg(Canvas cv, float x, float y1, float y2, float t, boolean on) {
        float k = t / 2;
        path.rewind();
        path.moveTo(x, y1); path.lineTo(x + k, y1 + k); path.lineTo(x + k, y2 - k);
        path.lineTo(x, y2); path.lineTo(x - k, y2 - k); path.lineTo(x - k, y1 + k); path.close();
        cv.drawPath(path, on && !dark ? lit : ghost);
    }
}

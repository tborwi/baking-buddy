package ing.tbor.timers;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;

/**
 * Flat, line-drawn button (Scandinavian style): plus, minus, play, stop square, reset arrow, or a word.
 * An icon button can also carry a label (the timer's name on the play/stop button): icon + label are centred as a group,
 * and the label shrinks, then ellipsizes, to fit.
 */
public class IconButton extends View {
    public static final int PLUS = 0, MINUS = 1, PLAY = 2, STOP = 3, RESET = 4, TEXT = 5;
    private int kind;
    private String text = "", label = "";
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint lp = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rect = new RectF();
    private final int maxH;

    public IconButton(Context c, int kind, int maxHeightDp) {
        super(c);
        this.kind = kind;
        maxH = Math.round(maxHeightDp * c.getResources().getDisplayMetrics().density);
        p.setStrokeCap(Paint.Cap.ROUND); p.setStrokeJoin(Paint.Join.ROUND);
        p.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        p.setTextAlign(Paint.Align.CENTER);
        p.setLetterSpacing(0.14f);
        lp.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        lp.setLetterSpacing(0.16f);
        setClickable(true); setFocusable(true);
    }

    public void setKind(int k) { if (k != kind) { kind = k; invalidate(); } }
    public void setText(String t) { text = t; invalidate(); }
    public void setLabel(String l) { if (!l.equals(label)) { label = l; invalidate(); } }
    public void setColor(int c) { p.setColor(c); lp.setColor(c); invalidate(); }

    @Override protected void onMeasure(int ws, int hs) {
        int w = MeasureSpec.getSize(ws), h = Math.min(MeasureSpec.getSize(hs), maxH);
        setMeasuredDimension(w, h);
    }

    @Override protected void onDraw(Canvas cv) {
        float w = getWidth(), h = getHeight(), cy = h / 2;
        float d = getResources().getDisplayMetrics().density;
        if (kind == TEXT) {
            p.setStyle(Paint.Style.FILL);
            p.setTextSize(Math.min(h * 0.36f, w / Math.max(4, text.length() * 0.75f)));
            Paint.FontMetrics fm = p.getFontMetrics();
            cv.drawText(text, w / 2, cy - (fm.ascent + fm.descent) / 2, p);
            return;
        }
        if (label.isEmpty()) { drawIcon(cv, w / 2, cy, Math.min(w, h) * 0.34f, d); return; }

        // icon + label, centred as a group
        float s = Math.min(w, h) * 0.30f, iconW = s, gap = s * 0.55f, pad = w * 0.08f;
        float avail = w - 2 * pad - iconW - gap;
        float ts = Math.min(h * 0.24f, 18 * d * getResources().getConfiguration().fontScale);
        lp.setTextSize(ts);
        while (lp.measureText(label) > avail && ts > 9 * d) { ts -= d; lp.setTextSize(ts); }
        String shown = label;
        if (lp.measureText(shown) > avail) {
            while (shown.length() > 1 && lp.measureText(shown + "…") > avail) shown = shown.substring(0, shown.length() - 1);
            shown = shown.trim() + "…";
        }
        float total = iconW + gap + lp.measureText(shown);
        float x0 = (w - total) / 2;
        drawIcon(cv, x0 + iconW / 2, cy, s, d);
        Paint.FontMetrics fm = lp.getFontMetrics();
        cv.drawText(shown, x0 + iconW + gap, cy - (fm.ascent + fm.descent) / 2, lp);
    }

    private void drawIcon(Canvas cv, float cx, float cy, float s, float d) {
        p.setStrokeWidth(Math.max(1.5f * d, s * 0.11f));
        switch (kind) {
            case PLUS:
                p.setStyle(Paint.Style.STROKE);
                cv.drawLine(cx - s / 2, cy, cx + s / 2, cy, p);
                cv.drawLine(cx, cy - s / 2, cx, cy + s / 2, p);
                break;
            case MINUS:
                p.setStyle(Paint.Style.STROKE);
                cv.drawLine(cx - s / 2, cy, cx + s / 2, cy, p);
                break;
            case PLAY: {
                p.setStyle(Paint.Style.FILL);
                float r = s * 0.55f;
                path.rewind();
                path.moveTo(cx - r * 0.75f, cy - r); path.lineTo(cx + r, cy); path.lineTo(cx - r * 0.75f, cy + r); path.close();
                cv.drawPath(path, p);
                break;
            }
            case STOP: {
                p.setStyle(Paint.Style.FILL);
                float r = s * 0.42f;
                rect.set(cx - r, cy - r, cx + r, cy + r);
                cv.drawRoundRect(rect, r * 0.15f, r * 0.15f, p);
                break;
            }
            case RESET: {
                p.setStyle(Paint.Style.STROKE);
                float r = s * 0.6f;
                rect.set(cx - r, cy - r, cx + r, cy + r);
                cv.drawArc(rect, -60, 290, false, p);
                // arrow head at the arc's start (top right)
                double a = Math.toRadians(-60);
                float ax = cx + (float) (r * Math.cos(a)), ay = cy + (float) (r * Math.sin(a));
                float k = r * 0.5f;
                cv.drawLine(ax, ay, ax - k, ay - k * 0.15f, p);
                cv.drawLine(ax, ay, ax + k * 0.1f, ay + k, p);
                break;
            }
            default:
        }
    }
}

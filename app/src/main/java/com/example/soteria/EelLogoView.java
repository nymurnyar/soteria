package com.example.soteria;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

import androidx.annotation.Nullable;

/**
 * The Soteria logo, drawn in code, where the "S" ribbon is an eel.
 *
 * 3 second story: logo pops in, a fishing hook drops down, the eel opens its mouth and bites,
 * gets yanked up and thrashes, breaks free, settles back into the S, and the sound bars pulse.
 *
 * All drawing happens in "art" coordinates that match the 1932px logo artwork, then gets scaled
 * to the view size.
 */
public class EelLogoView extends View {

    public interface Listener {
        void onFinished();
    }

    public static final long DURATION_MS = 3000;

    // Artwork space.
    private static final float ART = 1932f;
    private static final float CX = ART / 2f;

    // Logo colors.
    private static final int NAVY = 0xFF022759;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int HOOK = 0xFFD9E2EC;

    // Eel body.
    private static final int N = 96;                // samples along the spine
    private static final float HALF_WIDTH = 96f;    // half the body thickness
    private final float[] baseX = new float[N], baseY = new float[N];
    private final float[] tanX = new float[N], tanY = new float[N];
    private final float[] px = new float[N], py = new float[N];
    private float spineLength;

    private final Paint tilePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shieldPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint eelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint eelShadePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mouthPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint whitePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pupilPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hookPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bubblePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Path tilePath = new Path();
    private final Path shieldPath = new Path();
    private final Path bodyPath = new Path();
    private final Path mouthPath = new Path();
    private final Path hookPath = new Path();
    private final RectF rect = new RectF();

    // Sound bars from the logo: x centre and half height.
    private static final float[] BAR_X = {794, 842, 890, 938, 986, 1034, 1082};
    private static final float[] BAR_H = {48, 72, 108, 166, 108, 72, 48};
    private static final float BAR_Y = 948f;

    // Where the hook bend sits relative to the head tip before the bite.
    private static final float LUNGE = 70f;

    private float timeMs;          // current animation time
    private float breakTipX, breakTipY;
    private ValueAnimator animator;
    private Listener listener;

    public EelLogoView(Context context) {
        this(context, null);
    }

    public EelLogoView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        buildStaticShapes();
        buildSpine();

        tilePaint.setColor(NAVY);
        shieldPaint.setColor(WHITE);
        shieldPaint.setStyle(Paint.Style.STROKE);
        shieldPaint.setStrokeWidth(50f);
        shieldPaint.setStrokeJoin(Paint.Join.ROUND);

        // Pale cream on the left to leaf green on the right, like the logo ribbon.
        eelPaint.setShader(new LinearGradient(520, 0, 1360, 0,
                new int[]{0xFFF4F7EC, 0xFFC4D7A6, 0xFF8DB672, 0xFF66A055},
                new float[]{0f, 0.4f, 0.75f, 1f}, Shader.TileMode.CLAMP));
        // Deep green in the middle stroke, like the logo.
        eelShadePaint.setShader(new RadialGradient(930, 940, 430,
                new int[]{0xE0005F1E, 0x80247A35, 0x00247A35},
                new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));

        mouthPaint.setColor(NAVY);
        whitePaint.setColor(WHITE);
        pupilPaint.setColor(NAVY);

        linePaint.setColor(0xCCFFFFFF);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(8f);

        hookPaint.setColor(HOOK);
        hookPaint.setStyle(Paint.Style.STROKE);
        hookPaint.setStrokeWidth(24f);
        hookPaint.setStrokeCap(Paint.Cap.ROUND);
        hookPaint.setStrokeJoin(Paint.Join.ROUND);

        bubblePaint.setColor(0xB3FFFFFF);
        bubblePaint.setStyle(Paint.Style.STROKE);
        bubblePaint.setStrokeWidth(6f);

        breakTip();
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    /** Starts (or restarts) the 3 second animation. */
    public void play() {
        cancel();
        animator = ValueAnimator.ofFloat(0f, DURATION_MS);
        animator.setDuration(DURATION_MS);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(a -> {
            timeMs = (float) a.getAnimatedValue();
            invalidate();
        });
        animator.addListener(new AnimatorListenerAdapter() {
            private boolean cancelled;

            @Override
            public void onAnimationCancel(Animator animation) {
                cancelled = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (!cancelled && listener != null) listener.onFinished();
            }
        });
        animator.start();
    }

    public void cancel() {
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        cancel();
        super.onDetachedFromWindow();
    }

    // ---- Geometry ---------------------------------------------------------------------------

    private void buildStaticShapes() {
        rect.set(85, 122, 1847, 1776);
        tilePath.addRoundRect(rect, 92, 92, Path.Direction.CW);

        shieldPath.moveTo(938, 440);
        shieldPath.cubicTo(1080, 518, 1230, 556, 1366, 576);
        shieldPath.cubicTo(1405, 900, 1270, 1320, 938, 1462);
        shieldPath.cubicTo(606, 1320, 471, 900, 510, 576);
        shieldPath.cubicTo(646, 556, 796, 518, 938, 440);
        shieldPath.close();
    }

    /** The S, from the head (top right) to the tail (bottom left). */
    private void buildSpine() {
        Path spine = new Path();
        spine.moveTo(1300, 636);
        spine.lineTo(900, 636);
        spine.cubicTo(680, 636, 575, 740, 600, 845);
        spine.cubicTo(630, 935, 790, 950, 950, 955);
        spine.cubicTo(1150, 962, 1290, 1030, 1282, 1130);
        spine.cubicTo(1272, 1215, 1160, 1228, 1050, 1228);
        spine.lineTo(560, 1228);

        PathMeasure pm = new PathMeasure(spine, false);
        spineLength = pm.getLength();
        float[] pos = new float[2];
        float[] tan = new float[2];
        for (int i = 0; i < N; i++) {
            pm.getPosTan(spineLength * i / (N - 1), pos, tan);
            baseX[i] = pos[0];
            baseY[i] = pos[1];
            tanX[i] = tan[0];
            tanY[i] = tan[1];
        }
    }

    /** Body half-width at fraction s along the eel (0 = head, 1 = tail). */
    private static float halfWidth(float s) {
        if (s < 0.05f) return HALF_WIDTH * (0.72f + 0.28f * (s / 0.05f));
        if (s > 0.72f) return HALF_WIDTH * Math.max(0f, (1f - s) / 0.28f);
        return HALF_WIDTH;
    }

    // ---- Story ------------------------------------------------------------------------------

    private static float seg(float t, float a, float b) {
        return Math.max(0f, Math.min(1f, (t - a) / (b - a)));
    }

    private static float easeOut(float x) {
        float i = 1f - x;
        return 1f - i * i * i;
    }

    private static float easeInOut(float x) {
        return x < 0.5f ? 4f * x * x * x : 1f - (float) Math.pow(-2f * x + 2f, 3) / 2f;
    }

    private static final float BITE = 1150f;
    private static final float BREAK = 2050f;

    private float mouth(float t) {
        if (t < BITE) return easeOut(seg(t, 850, 1100));
        if (t < BREAK) return 1f - 0.7f * seg(t, BITE, BITE + 80);   // clamped on the hook
        if (t < BREAK + 150) return 0.3f + 0.7f * seg(t, BREAK, BREAK + 60); // spits it out
        return 1f - seg(t, BREAK + 150, BREAK + 320);
    }

    private float lunge(float t) {
        if (t < BREAK) return LUNGE * easeInOut(seg(t, 950, BITE));
        return LUNGE * (float) Math.exp(-(t - BREAK) / 150f);
    }

    /** How far the line has pulled the head up. */
    private float tug(float t) {
        if (t < BREAK) {
            float u = seg(t, 1250, BREAK);
            return 280f * easeInOut(Math.min(1f, u * 1.6f))
                    + 26f * (float) Math.sin(u * Math.PI * 9) * u;
        }
        float d = t - BREAK;
        float atBreak = 280f;
        return atBreak * (float) (Math.exp(-d / 170f) * Math.cos(d / 70f));
    }

    private float wiggle(float t) {
        if (t < BREAK) return 30f * seg(t, 1250, 1500) + 8f * seg(t, 900, BITE);
        return 34f * (float) Math.exp(-(t - BREAK) / 330f);
    }

    /** Fills px/py with the eel's spine at time t. */
    private void computeBody(float t) {
        float lunge = lunge(t);
        float tug = tug(t);
        float wig = wiggle(t);
        // Forward direction for the head = opposite of the spine tangent at the head.
        float fx = -tanX[0], fy = -tanY[0];
        for (int i = 0; i < N; i++) {
            float s = i / (float) (N - 1);
            float along = s * spineLength;
            float headPull = (float) Math.exp(-along / 340f);
            float wave = (float) Math.sin(2 * Math.PI * (s * 2.4f) - t / 55f)
                    * wig * (1f - 0.75f * headPull) * (0.5f + 0.5f * s);
            float nx = -tanY[i], ny = tanX[i];
            px[i] = baseX[i] + fx * lunge * headPull + nx * wave;
            py[i] = baseY[i] + fy * lunge * headPull - tug * headPull + ny * wave;
        }
    }

    /** Snout tip at the current px/py. */
    private float tipX() {
        return px[0] + headFx() * halfWidth(0) * 1.05f;
    }

    private float tipY() {
        return py[0] + headFy() * halfWidth(0) * 1.05f;
    }

    private float headFx() {
        float dx = px[0] - px[2], dy = py[0] - py[2];
        return dx / (float) Math.hypot(dx, dy);
    }

    private float headFy() {
        float dx = px[0] - px[2], dy = py[0] - py[2];
        return dy / (float) Math.hypot(dx, dy);
    }

    private void breakTip() {
        computeBody(BREAK - 1);
        breakTipX = tipX();
        breakTipY = tipY();
    }

    // ---- Drawing ----------------------------------------------------------------------------

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float t = timeMs;
        float w = getWidth(), h = getHeight();
        float scale = Math.min(w, h) / ART;

        canvas.save();
        canvas.translate((w - ART * scale) / 2f, (h - ART * scale) / 2f);
        canvas.scale(scale, scale);

        // Pop in with a little overshoot.
        float in = seg(t, 0, 420);
        float pop = 0.86f + 0.14f * (1f + 2.2f * (float) Math.pow(in - 1, 3) + 1.2f * (float) Math.pow(in - 1, 2));
        canvas.scale(pop, pop, CX, 950);

        canvas.drawPath(tilePath, tilePaint);
        canvas.save();
        canvas.clipPath(tilePath);

        canvas.drawPath(shieldPath, shieldPaint);

        computeBody(t);
        drawEel(canvas, t);
        drawBars(canvas, t);
        drawHook(canvas, t);
        drawBubbles(canvas, t);

        canvas.restore();
        canvas.restore();
    }

    private void drawEel(Canvas canvas, float t) {
        float fx = headFx(), fy = headFy();
        float hw0 = halfWidth(0);

        // Outline: upper side head->tail, lower side tail->head, rounded snout.
        bodyPath.reset();
        for (int i = 0; i < N; i++) {
            float s = i / (float) (N - 1);
            float hw = halfWidth(s);
            float nx = -tanY[i], ny = tanX[i];
            float x = px[i] + nx * hw, y = py[i] + ny * hw;
            if (i == 0) bodyPath.moveTo(x, y); else bodyPath.lineTo(x, y);
        }
        for (int i = N - 1; i >= 0; i--) {
            float s = i / (float) (N - 1);
            float hw = halfWidth(s);
            float nx = -tanY[i], ny = tanX[i];
            bodyPath.lineTo(px[i] - nx * hw, py[i] - ny * hw);
        }
        float nx0 = -tanY[0], ny0 = tanX[0];
        float lowX = px[0] - nx0 * hw0, lowY = py[0] - ny0 * hw0;
        float upX = px[0] + nx0 * hw0, upY = py[0] + ny0 * hw0;
        float k = hw0 * 1.4f;
        bodyPath.cubicTo(lowX + fx * k, lowY + fy * k, upX + fx * k, upY + fy * k, upX, upY);
        bodyPath.close();

        canvas.drawPath(bodyPath, eelPaint);
        canvas.drawPath(bodyPath, eelShadePaint);

        // Mouth: a navy wedge cut into the snout.
        float m = mouth(t);
        if (m > 0.02f) {
            float tipX = tipX(), tipY = tipY();
            float hingeX = px[0] - fx * hw0 * 0.15f, hingeY = py[0] - fy * hw0 * 0.15f;
            float open = hw0 * 1.05f * m;
            float jawX = tipX + fx * hw0 * 0.5f, jawY = tipY + fy * hw0 * 0.5f;
            mouthPath.reset();
            mouthPath.moveTo(hingeX, hingeY);
            mouthPath.lineTo(jawX + nx0 * open, jawY + ny0 * open);
            mouthPath.lineTo(jawX - nx0 * open, jawY - ny0 * open);
            mouthPath.close();
            canvas.drawPath(mouthPath, mouthPaint);
        }

        // Eye on the upper side of the head; it widens when hooked.
        float ex = px[2] + nx0 * hw0 * 0.42f - fx * 6f;
        float ey = py[2] + ny0 * hw0 * 0.42f - fy * 6f;
        boolean hooked = t >= BITE && t < BREAK;
        float eyeR = hooked ? 21f : 17f;
        canvas.drawCircle(ex, ey, eyeR, whitePaint);
        float look = hooked ? -6f : 3f;   // looks up at the hook while hooked
        canvas.drawCircle(ex + fx * 4f, ey + look, hooked ? 7f : 9f, pupilPaint);
    }

    private void drawBars(Canvas canvas, float t) {
        float alpha = seg(t, 120, 520);
        if (alpha <= 0f) return;
        whitePaint.setAlpha((int) (255 * alpha));
        float pulse = seg(t, 2300, 2950);
        for (int i = 0; i < BAR_X.length; i++) {
            float eq = 1f;
            if (pulse > 0f && pulse < 1f) {
                eq = 1f + 0.45f * (float) Math.sin(pulse * Math.PI * 4 + i * 1.3f)
                        * (float) Math.sin(pulse * Math.PI);
            }
            float hh = BAR_H[i] * eq * (0.4f + 0.6f * alpha);
            rect.set(BAR_X[i] - 12, BAR_Y - hh, BAR_X[i] + 12, BAR_Y + hh);
            canvas.drawRoundRect(rect, 12, 12, whitePaint);
        }
        whitePaint.setAlpha(255);
    }

    private void drawHook(Canvas canvas, float t) {
        if (t < 300) return;

        // Hook bend position (where the eel bites).
        float bx, by;
        float alpha = 1f;
        if (t < BITE) {
            computeTargetFromBase();
            float u = easeOut(seg(t, 300, 950));
            float bob = 14f * (float) Math.sin(seg(t, 950, BITE) * Math.PI * 2);
            bx = targetX;
            by = -440 + (targetY + 260) * u + bob;
        } else if (t < BREAK) {
            bx = tipX();
            by = tipY();
        } else {
            float d = (t - BREAK) / 1000f;
            bx = breakTipX + 120 * d;
            by = breakTipY - 2600 * d * d - 500 * d;   // flung away upward
            alpha = 1f - seg(t, BREAK + 250, BREAK + 500);
        }
        if (alpha <= 0f) return;

        // Hook: shank up from the bend, curl to a barb that points back into the mouth.
        float sx = bx + 78;              // shank x
        float topY = by - 230;
        hookPath.reset();
        hookPath.moveTo(sx, topY);
        hookPath.lineTo(sx, by - 50);
        hookPath.quadTo(sx, by + 37, bx, by + 37);
        hookPath.quadTo(bx - 78, by + 37, bx - 78, by - 44);
        hookPath.lineTo(bx - 48, by - 14);   // barb
        linePaint.setAlpha((int) (204 * alpha));
        hookPaint.setAlpha((int) (255 * alpha));
        canvas.drawLine(sx, 0, sx, topY - 10, linePaint);
        canvas.drawCircle(sx, topY - 6, 14, hookPaint);   // eye of the hook
        canvas.drawPath(hookPath, hookPaint);
    }

    private float targetX, targetY;

    /** Hook rest spot before the bite: just ahead of the resting snout. */
    private void computeTargetFromBase() {
        float fx = -tanX[0], fy = -tanY[0];
        targetX = baseX[0] + fx * (halfWidth(0) * 1.05f + LUNGE);
        targetY = baseY[0] + fy * (halfWidth(0) * 1.05f + LUNGE);
    }

    private void drawBubbles(Canvas canvas, float t) {
        float u = seg(t, BREAK + 40, BREAK + 800);
        if (u <= 0f || u >= 1f) return;
        bubblePaint.setAlpha((int) (180 * (1f - u)));
        float[] dx = {10, 52, 28};
        float[] r = {16, 11, 8};
        float[] delay = {0f, 0.15f, 0.3f};
        for (int i = 0; i < 3; i++) {
            float v = Math.max(0f, u - delay[i]) / (1f - delay[i]);
            if (v <= 0f) continue;
            float x = breakTipX + dx[i] + 10f * (float) Math.sin(v * 9 + i);
            float y = breakTipY - 40 - 260 * v;
            canvas.drawCircle(x, y, r[i], bubblePaint);
        }
    }
}

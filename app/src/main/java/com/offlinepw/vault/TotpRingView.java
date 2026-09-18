package com.offlinepw.vault;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/**
 * حلقه‌ی شمارش معکوس نوردیک برای TOTP — کمان ۳۰ ثانیه‌ای که با هر ثانیه کم می‌شود؛
 * در ۱۰ ثانیه‌ی آخر کمان قرمز هشدار می‌شود. بی‌نقص با invalidate از روی ساعت دیواری
 * کار می‌کند و نخ/تایمر جداگانه لازم ندارد (تیکر ۱ ثانیه‌ایِ ولت آن را ریفرش می‌کند).
 */
public class TotpRingView extends View {
    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bounds = new RectF();
    private float fraction = 1f;
    private boolean dark = true;

    public TotpRingView(Context context) {
        super(context);
        float strokeWidth = 3.5f * getResources().getDisplayMetrics().density;
        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setStrokeWidth(strokeWidth);
        arcPaint.setStyle(Paint.Style.STROKE);
        arcPaint.setStrokeWidth(strokeWidth);
        arcPaint.setStrokeCap(Paint.Cap.ROUND);
    }

    /** @param remainingFraction ۱ = تازه، ۰ = منقضی */
    public void setState(boolean isDark, float remainingFraction) {
        this.dark = isDark;
        this.fraction = Math.max(0f, Math.min(1f, remainingFraction));
        invalidate();
    }

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!isAttachedToWindow()) return;
            invalidate(); // fraction در onDraw از ساعت دیواری خوانده می‌شود
            postDelayed(this, 250L);
        }
    };

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        removeCallbacks(tick);
        postDelayed(tick, 250L);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        removeCallbacks(tick);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        // fraction همیشه از ساعت دیواری — بدون نیاز به بیرونی sync با کد
        fraction = (30000L - (System.currentTimeMillis() % 30000L)) / 30000f;
        float pad = 5 * getResources().getDisplayMetrics().density;
        bounds.set(pad, pad, getWidth() - pad, getHeight() - pad);

        trackPaint.setColor(Color.parseColor(dark ? "#3F3F46" : "#D4D4D8"));
        canvas.drawArc(bounds, 0, 360, false, trackPaint);

        int secondsLeft = Math.round(30 * fraction);
        arcPaint.setColor(secondsLeft <= 10
                ? Color.parseColor("#EF4444")   // هشدار قرمز در ۱۰ ثانیه آخر
                : Color.parseColor("#F59E0B")); // کهربایی نوردیک
        canvas.drawArc(bounds, -90, 360 * fraction, false, arcPaint);
    }
}

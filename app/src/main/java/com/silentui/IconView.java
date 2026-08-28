package com.silentui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

public class IconView extends View {

    private String icon = "dot";
    private int color = Color.BLACK;
    private int batteryLevel = -1;

    public IconView(Context context) {
        super(context);
        setBackgroundColor(Color.TRANSPARENT);
    }

    public void setIcon(String icon) {
        this.icon = icon;
        invalidate();
    }

    public void setIconColor(int color) {
        this.color = color;
        invalidate();
    }

    public void setBatteryLevel(int level) {
        this.batteryLevel = level;
        invalidate();
    }

    private float dp(float v) {
        return getResources().getDisplayMetrics().density * v;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        if (w <= 0 || h <= 0) return;

        float cx = w / 2f;
        float cy = h / 2f;
        float stroke = Math.max(1f, dp(1.4f));

        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(color);
        p.setStrokeWidth(stroke);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);

        if ("dot".equals(icon)) {
            p.setStyle(Paint.Style.FILL);
            canvas.drawCircle(cx, cy, Math.min(w, h) * 0.28f, p);
        } else if ("wifi".equals(icon)) {
            p.setStyle(Paint.Style.STROKE);
            RectF r1 = new RectF(w * 0.08f, h * 0.12f, w * 0.92f, h * 0.92f);
            RectF r2 = new RectF(w * 0.24f, h * 0.30f, w * 0.76f, h * 0.86f);
            RectF r3 = new RectF(w * 0.40f, h * 0.48f, w * 0.60f, h * 0.78f);
            canvas.drawArc(r1, 135, 270, false, p);
            canvas.drawArc(r2, 135, 270, false, p);
            canvas.drawArc(r3, 135, 270, false, p);
            p.setStyle(Paint.Style.FILL);
            canvas.drawCircle(cx, h * 0.82f, Math.min(w, h) * 0.06f, p);
        } else if ("bt".equals(icon)) {
            p.setStyle(Paint.Style.STROKE);
            Path path = new Path();
            path.moveTo(w * 0.60f, h * 0.16f);
            path.lineTo(w * 0.32f, h * 0.82f);
            path.lineTo(w * 0.72f, h * 0.50f);
            path.lineTo(w * 0.32f, h * 0.16f);
            path.lineTo(w * 0.60f, h * 0.82f);
            canvas.drawPath(path, p);
        } else if ("battery".equals(icon)) {
            p.setStyle(Paint.Style.STROKE);
            RectF body = new RectF(w * 0.04f, h * 0.22f, w * 0.86f, h * 0.78f);
            canvas.drawRect(body, p);
            p.setStyle(Paint.Style.FILL);
            canvas.drawRect(new RectF(w * 0.86f, h * 0.38f, w * 0.96f, h * 0.62f), p);
            if (batteryLevel >= 0) {
                int level = Math.max(0, Math.min(100, batteryLevel));
                float left = w * 0.09f;
                float top = h * 0.28f;
                float right = w * 0.81f;
                float bottom = h * 0.72f;
                float fillRight = left + (right - left) * level / 100f;
                canvas.drawRect(new RectF(left, top, fillRight, bottom), p);
            }
        } else if ("settings".equals(icon)) {
            p.setStyle(Paint.Style.STROKE);
            canvas.drawCircle(cx, cy, w * 0.22f, p);
            for (int i = 0; i < 8; i++) {
                double a = Math.PI * 2 * i / 8;
                float x1 = cx + (float) Math.cos(a) * w * 0.32f;
                float y1 = cy + (float) Math.sin(a) * w * 0.32f;
                float x2 = cx + (float) Math.cos(a) * w * 0.42f;
                float y2 = cy + (float) Math.sin(a) * w * 0.42f;
                canvas.drawLine(x1, y1, x2, y2, p);
            }
        } else if ("brightness".equals(icon)) {
            p.setStyle(Paint.Style.STROKE);
            canvas.drawCircle(cx, cy, w * 0.22f, p);
            for (int i = 0; i < 8; i++) {
                double a = Math.PI * 2 * i / 8;
                float x1 = cx + (float) Math.cos(a) * w * 0.30f;
                float y1 = cy + (float) Math.sin(a) * w * 0.30f;
                float x2 = cx + (float) Math.cos(a) * w * 0.42f;
                float y2 = cy + (float) Math.sin(a) * w * 0.42f;
                canvas.drawLine(x1, y1, x2, y2, p);
            }
        } else if ("bell".equals(icon)) {
            p.setStyle(Paint.Style.STROKE);
            Path path = new Path();
            path.moveTo(w * 0.25f, h * 0.78f);
            path.cubicTo(w * 0.25f, h * 0.35f, w * 0.40f, h * 0.22f, w * 0.50f, h * 0.22f);
            path.cubicTo(w * 0.60f, h * 0.22f, w * 0.75f, h * 0.35f, w * 0.75f, h * 0.78f);
            canvas.drawPath(path, p);
            canvas.drawLine(w * 0.20f, h * 0.78f, w * 0.80f, h * 0.78f, p);
            p.setStyle(Paint.Style.FILL);
            canvas.drawCircle(cx, h * 0.84f, Math.min(w, h) * 0.07f, p);
        } else if ("chevron_up".equals(icon)) {
            p.setStyle(Paint.Style.STROKE);
            canvas.drawLine(w * 0.22f, h * 0.62f, cx, h * 0.34f, p);
            canvas.drawLine(cx, h * 0.34f, w * 0.78f, h * 0.62f, p);
        } else if ("chevron_down".equals(icon)) {
            p.setStyle(Paint.Style.STROKE);
            canvas.drawLine(w * 0.22f, h * 0.38f, cx, h * 0.66f, p);
            canvas.drawLine(cx, h * 0.66f, w * 0.78f, h * 0.38f, p);
        } else if ("close".equals(icon)) {
            p.setStyle(Paint.Style.STROKE);
            canvas.drawLine(w * 0.25f, h * 0.25f, w * 0.75f, h * 0.75f, p);
            canvas.drawLine(w * 0.75f, h * 0.25f, w * 0.25f, h * 0.75f, p);
        } else if ("apps".equals(icon)) {
            p.setStyle(Paint.Style.FILL);
            float s = w * 0.34f;
            float gap = w * 0.10f;
            canvas.drawRect(new RectF(w * 0.14f, h * 0.14f, w * 0.14f + s, h * 0.14f + s), p);
            canvas.drawRect(new RectF(w * 0.14f + s + gap, h * 0.14f, w * 0.14f + s + gap + s, h * 0.14f + s), p);
            canvas.drawRect(new RectF(w * 0.14f, h * 0.14f + s + gap, w * 0.14f + s, h * 0.14f + s + gap + s), p);
            canvas.drawRect(new RectF(w * 0.14f + s + gap, h * 0.14f + s + gap, w * 0.14f + s + gap + s, h * 0.14f + s + gap + s), p);
        }
    }
}

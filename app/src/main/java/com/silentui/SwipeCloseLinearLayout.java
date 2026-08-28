package com.silentui;

import android.content.Context;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;

public class SwipeCloseLinearLayout extends LinearLayout {

    public interface OnSwipeUpListener {
        void onSwipeUp();
    }

    private OnSwipeUpListener listener;
    private View protectedView;
    private float downY;
    private boolean blockForScroll;
    private boolean intercepted;

    public SwipeCloseLinearLayout(Context context) {
        super(context);
    }

    public void setOnSwipeUpListener(OnSwipeUpListener l) {
        this.listener = l;
    }

    public void setProtectedView(View v) {
        this.protectedView = v;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downY = ev.getRawY();
                blockForScroll = protectedView instanceof ScrollView
                        && ((ScrollView) protectedView).getScrollY() > 0;
                intercepted = false;
                return false;
            case MotionEvent.ACTION_MOVE:
                if (!intercepted && !blockForScroll && downY - ev.getRawY() > dp(20)) {
                    intercepted = true;
                    if (listener != null) {
                        listener.onSwipeUp();
                    }
                    return true;
                }
                return false;
            default:
                return false;
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        return true;
    }

    private int dp(int v) {
        return (int) (getResources().getDisplayMetrics().density * v + 0.5f);
    }
}

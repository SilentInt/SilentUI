package com.silentui;

import android.content.Context;
import android.content.SharedPreferences;

public final class Prefs {
    public static final String NAME = "silentui_gesture";

    public static final String K_ENABLED = "enabled";
    public static final String K_AUTO_START = "auto_start";
    public static final String K_DEVICE = "device";
    public static final String K_EDGE_PCT = "edge_pct";
    public static final String K_SWIPE_PCT = "swipe_pct";
    public static final String K_HOLD_MS = "hold_ms";
    public static final String K_HOLD_SLOP_PCT = "hold_slop_pct";
    public static final String K_STRUCT = "struct";
    public static final String K_MAX_X = "max_x";
    public static final String K_MAX_Y = "max_y";

    public static final String G_LEFT_EDGE = "gesture_left_edge";
    public static final String G_RIGHT_EDGE = "gesture_right_edge";
    public static final String G_BOTTOM_UP = "gesture_bottom_up";
    public static final String G_BOTTOM_HOLD = "gesture_bottom_hold";

    public static final String DEF_LEFT = "key:4";       // BACK
    public static final String DEF_RIGHT = "key:4";      // BACK
    public static final String DEF_BOTTOM = "app:com.yota.launcher"; // 启动 Yota Paper
    public static final String DEF_BOTTOM_HOLD = "key:3";             // HOME

    private static SharedPreferences sp;

    private Prefs() {}

    public static void init(Context c) {
        sp = c.getApplicationContext().getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }

    private static SharedPreferences p() {
        return sp;
    }

    public static boolean enabled() {
        return p().getBoolean(K_ENABLED, false);
    }

    public static void setEnabled(boolean v) {
        p().edit().putBoolean(K_ENABLED, v).commit();
    }

    public static boolean autoStart() {
        return p().getBoolean(K_AUTO_START, false);
    }

    public static void setAutoStart(boolean v) {
        p().edit().putBoolean(K_AUTO_START, v).commit();
    }

    public static String device() {
        return p().getString(K_DEVICE, "");
    }

    public static void setDevice(String v) {
        p().edit().putString(K_DEVICE, v).commit();
    }

    public static int edgePct() {
        return clamp(p().getInt(K_EDGE_PCT, 6), 1, 40);
    }

    public static void setEdgePct(int v) {
        p().edit().putInt(K_EDGE_PCT, clamp(v, 1, 40)).commit();
    }

    public static int swipePct() {
        return clamp(p().getInt(K_SWIPE_PCT, 12), 2, 60);
    }

    public static void setSwipePct(int v) {
        p().edit().putInt(K_SWIPE_PCT, clamp(v, 2, 60)).commit();
    }

    public static int holdMs() {
        return p().getInt(K_HOLD_MS, 500);
    }

    public static void setHoldMs(int v) {
        p().edit().putInt(K_HOLD_MS, v).commit();
    }

    public static int holdSlopPct() {
        return clamp(p().getInt(K_HOLD_SLOP_PCT, 3), 1, 30);
    }

    public static void setHoldSlopPct(int v) {
        p().edit().putInt(K_HOLD_SLOP_PCT, clamp(v, 1, 30)).commit();
    }

    public static int structSize() {
        return p().getInt(K_STRUCT, 0);
    }

    public static int touchMaxX() {
        return p().getInt(K_MAX_X, 0);
    }

    public static int touchMaxY() {
        return p().getInt(K_MAX_Y, 0);
    }

    public static void setDeviceInfo(String node, int maxX, int maxY, int struct) {
        p().edit().putString(K_DEVICE, node == null ? "" : node)
                .putInt(K_MAX_X, maxX)
                .putInt(K_MAX_Y, maxY)
                .putInt(K_STRUCT, struct)
                .commit();
    }

    public static String action(String gestureKey) {
        String def;
        switch (gestureKey) {
            case G_LEFT_EDGE:
                def = DEF_LEFT;
                break;
            case G_RIGHT_EDGE:
                def = DEF_RIGHT;
                break;
            case G_BOTTOM_UP:
                def = DEF_BOTTOM;
                break;
            case G_BOTTOM_HOLD:
                def = DEF_BOTTOM_HOLD;
                break;
            default:
                def = "";
                break;
        }
        return p().getString(gestureKey, def);
    }

    public static void setAction(String gestureKey, String spec) {
        p().edit().putString(gestureKey, spec).commit();
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}

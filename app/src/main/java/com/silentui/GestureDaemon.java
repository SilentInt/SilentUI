package com.silentui;

import android.content.Intent;
import android.os.IBinder;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.KeyEvent;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SilentUI 系统守护进程（纯 Java，无 Android Context）。
 *
 * 以 root 身份经 app_process 启动，独立于设置 App 运行，因此不受
 * 系统“按 HOME 杀后台”策略影响。读取 /dev/input/eventN 原始触摸事件，
 * 识别边缘手势并注入按键。
 *
 * 用法: app_process /system/bin com.silentui.GestureDaemon [config] [log]
 */
public final class GestureDaemon {

    private static final String DEFAULT_CONFIG = "/data/local/tmp/SilentUI.conf";

    private static final int EV_SYN = 0x00;
    private static final int EV_KEY = 0x01;
    private static final int EV_ABS = 0x03;
    private static final int SYN_REPORT = 0x00;
    private static final int BTN_TOUCH = 0x14a;
    private static final int ABS_X = 0x00;
    private static final int ABS_Y = 0x01;
    private static final int ABS_MT_SLOT = 0x2f;
    private static final int ABS_MT_POSITION_X = 0x35;
    private static final int ABS_MT_POSITION_Y = 0x36;
    private static final int ABS_MT_TRACKING_ID = 0x39;

    private static final int C_NONE = 0;
    private static final int C_LEFT = 1;
    private static final int C_RIGHT = 2;
    private static final int C_BOTTOM = 3;

    private static final Pattern MAX_PAT = Pattern.compile("max\\s+(\\d+)");

    private static String configPath = DEFAULT_CONFIG;
    private static final Map<String, String> cfg = new HashMap<>();

    // touch state
    private static boolean typeB;
    private static int slot;
    private static final Map<Integer, long[]> slots = new HashMap<>();
    private static float ax, ay;
    private static boolean typeATouch;
    private static boolean touching;
    private static float startX, startY, curX, curY;
    private static int candidate;
    private static boolean armedLeft, armedRight, armedBottom, holdFired;
    private static long holdStart;
    private static float lastHoldX, lastHoldY;
    private static int txMax, tyMax;

    public static void main(String[] args) {
        if (args.length > 0) {
            configPath = args[0];
        }
        log("SilentUI daemon start, config=" + configPath);

        startStatusBarWatchdog();

        while (true) {
            try {
                reloadConfig();
                if (!enabled()) {
                    sleepQuietly(2000);
                    continue;
                }
                runEngine();
            } catch (Throwable t) {
                log("run error: " + t);
            }
            sleepQuietly(2000);
        }
    }

    /** 状态栏看门狗：独立线程，与手势主循环互不阻塞，root 常驻周期性授权并拉起状态栏服务。 */
    private static void startStatusBarWatchdog() {
        Thread t = new Thread(() -> {
            while (true) {
                if (ensureStatusBarService()) {
                    sleepQuietly(30000);
                } else {
                    sleepQuietly(5000);
                }
            }
        }, "status-bar-watchdog");
        t.setDaemon(true);
        t.start();
    }

    /** 先 root 授权，再拉起状态栏服务（授权并启动）。 */
    private static boolean ensureStatusBarService() {
        String sb;
        synchronized (cfg) {
            sb = cfg.get("status_bar");
        }
        if (!"1".equals(sb)) {
            return true;
        }
        grantStatusBarPerms();
        int rc = execResult("am startservice -n com.silentui/.StatusBarService --user 0");
        if (rc != 0) {
            log("status-bar start fail(" + rc + ")");
            return false;
        }
        return true;
    }

    /** 以 root 身份授予状态栏所需权限（悬浮窗 / 写入设置 / 通知读取 / 蓝牙 / 隐藏系统栏）。 */
    private static void grantStatusBarPerms() {
        String pkg = "com.silentui";
        String listener = pkg + "/" + pkg + ".NotificationListener";
        String cmd =
                "pm grant " + pkg + " android.permission.BLUETOOTH_CONNECT 2>/dev/null;"
                + "pm grant " + pkg + " android.permission.POST_NOTIFICATIONS 2>/dev/null;"
                + "appops set " + pkg + " SYSTEM_ALERT_WINDOW allow;"
                + "appops set " + pkg + " WRITE_SETTINGS allow;"
                + "settings put secure enabled_notification_listeners " + listener + ";"
                + "settings put global policy_control immersive.full=*";
        execResult(cmd);
    }

    // ------------------------------------------------------------------ config

    private static boolean enabled() {
        return "1".equals(cfg.get("enabled"));
    }

    private static void reloadConfig() {
        synchronized (cfg) {
            cfg.clear();
            try {
                File f = new File(configPath);
                if (!f.exists()) {
                    return;
                }
                BufferedReader r = new BufferedReader(new java.io.FileReader(f));
                String line;
                while ((line = r.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) {
                        continue;
                    }
                    int eq = line.indexOf('=');
                    if (eq > 0) {
                        cfg.put(line.substring(0, eq).trim(), line.substring(eq + 1).trim());
                    }
                }
                r.close();
            } catch (Throwable t) {
                log("config read error: " + t);
            }
        }
    }

    private static int intOf(String key, int def) {
        String v = cfg.get(key);
        if (v == null) {
            return def;
        }
        try {
            return Integer.parseInt(v.trim());
        } catch (Throwable ignored) {
            return def;
        }
    }

    private static boolean same(String a, String b) {
        if (a == null) {
            a = "";
        }
        if (b == null) {
            b = "";
        }
        return a.equals(b);
    }

    // ------------------------------------------------------------------ engine

    private static void runEngine() throws Exception {
        String device = cfg.get("device");
        int struct = intOf("struct", 0);
        int mx = intOf("max_x", 0);
        int my = intOf("max_y", 0);
        if (struct <= 0) {
            struct = detectStruct();
        }
        if (isEmpty(device) || mx <= 0 || my <= 0) {
            String[] d = discoverTouch();
            if (d != null) {
                if (isEmpty(device)) {
                    device = d[0];
                }
                if (mx <= 0) {
                    mx = parseInt(d[1], 0);
                }
                if (my <= 0) {
                    my = parseInt(d[2], 0);
                }
            }
        }
        if (isEmpty(device)) {
            log("no touch device found");
            return;
        }
        txMax = mx > 0 ? mx : 1448;
        tyMax = my > 0 ? my : 1072;

        InputStream in = new FileInputStream(device);
        byte[] buf = new byte[struct];
        long lastCheck = 0;
        try {
            while (true) {
                if (!readFully(in, buf)) {
                    break;
                }
                long now = System.currentTimeMillis();
                if (now - lastCheck > 2000) {
                    lastCheck = now;
                    String oldDevice = cfg.get("device");
                    int oldStruct = intOf("struct", struct);
                    int oldMx = intOf("max_x", mx);
                    int oldMy = intOf("max_y", my);
                    reloadConfig();
                    if (!enabled()) {
                        break;
                    }
                    String nd = cfg.get("device");
                    int ns = intOf("struct", struct);
                    int nmx = intOf("max_x", mx);
                    int nmy = intOf("max_y", my);
                    if (!same(nd, oldDevice) || ns != oldStruct || nmx != oldMx || nmy != oldMy) {
                        break;
                    }
                    if (nmx > 0) {
                        txMax = nmx;
                    }
                    if (nmy > 0) {
                        tyMax = nmy;
                    }
                }

                int type, code, value;
                if (struct == 24) {
                    type = u16(buf, 16);
                    code = u16(buf, 18);
                    value = s32(buf, 20);
                } else {
                    type = u16(buf, 8);
                    code = u16(buf, 10);
                    value = s32(buf, 12);
                }
                onEvent(type, code, value);
            }
        } finally {
            try {
                in.close();
            } catch (Throwable ignored) {
            }
        }
    }

    private static void onEvent(int type, int code, int value) {
        if (type == EV_ABS) {
            if (code == ABS_MT_SLOT) {
                typeB = true;
                slot = value;
            } else if (code == ABS_MT_TRACKING_ID) {
                typeB = true;
                long[] s = slots.get(slot);
                if (value == -1) {
                    slots.remove(slot);
                } else if (s == null) {
                    slots.put(slot, new long[]{value, 0, 0});
                } else {
                    s[0] = value;
                }
            } else if (code == ABS_MT_POSITION_X) {
                long[] s = slots.get(slot);
                if (s == null) {
                    slots.put(slot, new long[]{-1, value, 0});
                } else {
                    s[1] = value;
                }
            } else if (code == ABS_MT_POSITION_Y) {
                long[] s = slots.get(slot);
                if (s == null) {
                    slots.put(slot, new long[]{-1, 0, value});
                } else {
                    s[2] = value;
                }
            } else if (code == ABS_X) {
                ax = value;
            } else if (code == ABS_Y) {
                ay = value;
            }
        } else if (type == EV_KEY && code == BTN_TOUCH) {
            typeATouch = (value == 1);
        } else if (type == EV_SYN && code == SYN_REPORT) {
            onSample();
        }
    }

    private static void onSample() {
        long now = System.currentTimeMillis();
        if (typeB) {
            int primary = -1;
            int minSlot = Integer.MAX_VALUE;
            for (Map.Entry<Integer, long[]> e : slots.entrySet()) {
                if (e.getValue()[0] >= 0 && e.getKey() < minSlot) {
                    minSlot = e.getKey();
                    primary = e.getKey();
                }
            }
            if (primary >= 0) {
                long[] s = slots.get(primary);
                float x = s[1];
                float y = s[2];
                if (!touching) {
                    beginTouch(x, y, now);
                } else {
                    updateTouch(x, y, now);
                }
            } else if (touching) {
                endTouch(curX, curY);
            }
        } else {
            float x = ax;
            float y = ay;
            if (typeATouch) {
                if (!touching) {
                    beginTouch(x, y, now);
                } else {
                    updateTouch(x, y, now);
                }
            } else if (touching) {
                endTouch(curX, curY);
            }
        }
    }

    private static void beginTouch(float x, float y, long now) {
        touching = true;
        startX = curX = x;
        startY = curY = y;
        armedLeft = armedRight = armedBottom = false;
        holdFired = false;
        holdStart = 0;
        lastHoldX = x;
        lastHoldY = y;
        candidate = classify(x, y);
    }

    private static void updateTouch(float x, float y, long now) {
        curX = x;
        curY = y;
        float dx = x - startX;
        float dy = y - startY;
        int minDim = Math.min(txMax, tyMax);
        int swipeMinPx = minDim * intOf("swipe_pct", 12) / 100;

        if (candidate == C_LEFT) {
            if (dx >= swipeMinPx && dx > Math.abs(dy)) {
                armedLeft = true;
            }
        } else if (candidate == C_RIGHT) {
            if (-dx >= swipeMinPx && -dx > Math.abs(dy)) {
                armedRight = true;
            }
        } else if (candidate == C_BOTTOM) {
            float up = startY - y;
            if (up >= swipeMinPx && up > Math.abs(dx)) {
                if (!armedBottom) {
                    armedBottom = true;
                    holdStart = now;
                    lastHoldX = x;
                    lastHoldY = y;
                } else {
                    int holdSlopPx = minDim * intOf("hold_slop_pct", 3) / 100;
                    float moved = dist(x, y, lastHoldX, lastHoldY);
                    if (moved > holdSlopPx) {
                        holdStart = now;
                        lastHoldX = x;
                        lastHoldY = y;
                    } else if (!holdFired && (now - holdStart) >= intOf("hold_ms", 500)) {
                        holdFired = true;
                        log("trigger: bottom-up-hold");
                        execAction(cfg.get("bottom_hold"));
                    }
                }
            }
        }
    }

    private static void endTouch(float x, float y) {
        boolean left = armedLeft;
        boolean right = armedRight;
        boolean bottom = armedBottom && !holdFired;
        touching = false;
        armedLeft = armedRight = armedBottom = false;
        holdFired = false;
        candidate = C_NONE;

        if (left) {
            log("trigger: left-edge-in");
            execAction(cfg.get("left"));
        } else if (right) {
            log("trigger: right-edge-in");
            execAction(cfg.get("right"));
        } else if (bottom) {
            log("trigger: bottom-up");
            execAction(cfg.get("bottom"));
        }
    }

    private static int classify(float x, float y) {
        int minDim = Math.min(txMax, tyMax);
        int edgePx = minDim * intOf("edge_pct", 6) / 100;
        if (x <= edgePx) {
            return C_LEFT;
        }
        if (x >= txMax - edgePx) {
            return C_RIGHT;
        }
        if (y >= tyMax - edgePx) {
            return C_BOTTOM;
        }
        return C_NONE;
    }

    private static String zoneName(int c) {
        switch (c) {
            case C_LEFT:
                return "left";
            case C_RIGHT:
                return "right";
            case C_BOTTOM:
                return "bottom";
            default:
                return "mid";
        }
    }

    private static void execAction(String spec) {
        if (spec == null) {
            return;
        }
        String s = spec.trim();
        if (s.isEmpty() || "none".equals(s)) {
            return;
        }
        try {
            if ("settings".equals(s)) {
                if (!launchApp("com.silentui")) {
                    exec("am start -n com.silentui/.MainActivity -f 0x10000000");
                }
            } else if (s.startsWith("key:")) {
                int code = Integer.parseInt(s.substring(4).trim());
                if (!injectKey(code)) {
                    exec("input keyevent " + code);
                }
            } else if (s.startsWith("key-long:")) {
                int code = Integer.parseInt(s.substring(9).trim());
                if (!injectKeyLong(code)) {
                    exec("input keyevent --longpress " + code);
                }
            } else if (s.startsWith("app:")) {
                String pkg = s.substring(4).trim();
                if (!launchApp(pkg)) {
                    exec("monkey -p " + pkg + " -c android.intent.category.LAUNCHER 1");
                }
            } else if (s.startsWith("cmd:")) {
                String cmd = s.substring(4).trim();
                exec(cmd);
            } else {
                int code = Integer.parseInt(s);
                if (!injectKey(code)) {
                    exec("input keyevent " + code);
                }
            }
        } catch (Throwable t) {
            log("action fail: " + t);
        }
    }

    // ------------------------------------------------------------------ fast inject (binder, 免 app_process 拉起)

    private static Object sInputManager;
    private static Method sInjectMethod;
    private static boolean sBinderWarned;

    private static void binderWarn(Throwable t) {
        if (!sBinderWarned) {
            sBinderWarned = true;
            log("binder 快速注入不可用，回退慢速 shell: " + t);
        }
    }

    /** 通过 InputManager.injectInputEvent 注入按键，构造方式与 SystemUI 的 KeyButtonView 一致。 */
    private static boolean injectKey(int code) {
        try {
            Object im = inputManager();
            if (im == null) {
                return false;
            }
            long downAt = SystemClock.uptimeMillis();
            KeyEvent down = keyEvent(KeyEvent.ACTION_DOWN, code, downAt, downAt);
            KeyEvent up = keyEvent(KeyEvent.ACTION_UP, code, downAt, SystemClock.uptimeMillis());
            Object r1 = sInjectMethod.invoke(im, down, 0);
            Object r2 = sInjectMethod.invoke(im, up, 0);
            boolean ok = Boolean.TRUE.equals(r1) && Boolean.TRUE.equals(r2);
            if (!ok) {
                log("binder key " + code + " false r1=" + r1 + " r2=" + r2);
            }
            return ok;
        } catch (Throwable t) {
            binderWarn(t);
            return false;
        }
    }

    private static boolean injectKeyLong(int code) {
        try {
            Object im = inputManager();
            if (im == null) {
                return false;
            }
            long downAt = SystemClock.uptimeMillis();
            KeyEvent down = keyEvent(KeyEvent.ACTION_DOWN, code, downAt, downAt);
            Object r1 = sInjectMethod.invoke(im, down, 0);
            if (!Boolean.TRUE.equals(r1)) {
                log("binder key-long " + code + " down=false r1=" + r1);
                return false;
            }
            Thread.sleep(650);
            KeyEvent up = keyEvent(KeyEvent.ACTION_UP, code, downAt, SystemClock.uptimeMillis());
            Object r2 = sInjectMethod.invoke(im, up, 0);
            boolean ok = Boolean.TRUE.equals(r2);
            if (!ok) {
                log("binder key-long " + code + " up=false r2=" + r2);
            }
            return ok;
        } catch (Throwable t) {
            binderWarn(t);
            return false;
        }
    }

    /** 与 SystemUI KeyButtonView 相同的 KeyEvent 构造：FLAG_FROM_SYSTEM | FLAG_KEEP_TOUCH_MODE，source=KEYBOARD。 */
    private static KeyEvent keyEvent(int action, int code, long downTime, long eventTime) {
        return new KeyEvent(downTime, eventTime, action, code, 0, 0, -1, 0,
                KeyEvent.FLAG_FROM_SYSTEM | KeyEvent.FLAG_KEEP_TOUCH_MODE,
                InputDevice.SOURCE_KEYBOARD);
    }

    private static Object inputManager() throws Exception {
        if (sInputManager == null) {
            Class<?> cls = Class.forName("android.hardware.input.InputManager");
            sInputManager = cls.getMethod("getInstance").invoke(null);
            sInjectMethod = cls.getMethod("injectInputEvent", InputEvent.class, int.class);
        }
        return sInputManager;
    }

    /** 通过 ActivityManager binder 直接拉起应用，避免 spawn am/monkey。 */
    private static boolean launchApp(String pkg) {
        try {
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_LAUNCHER);
            intent.setPackage(pkg);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            Class<?> amn = Class.forName("android.app.ActivityManagerNative");
            Object am = amn.getMethod("getDefault").invoke(null);
            Method start = amn.getMethod("startActivityAsUser",
                    Class.forName("android.app.IApplicationThread"),
                    String.class, Intent.class, String.class,
                    IBinder.class, String.class, int.class, int.class,
                    Class.forName("android.app.ProfilerInfo"), android.os.Bundle.class, int.class);
            Object r = start.invoke(am, null, "com.silentui", intent, null, null, null, 0, 0, null, null, -2);
            return r instanceof Integer && ((Integer) r).intValue() == 0;
        } catch (Throwable t) {
            binderWarn(t);
            return false;
        }
    }

    // ------------------------------------------------------------------ discovery

    private static String[] discoverTouch() {
        String out = execCapture("getevent -p 2>/dev/null");
        if (out == null || out.trim().isEmpty()) {
            out = execCapture("/system/bin/getevent -p 2>/dev/null");
        }
        if (out == null) {
            return null;
        }

        List<String[]> list = new ArrayList<>();
        String curNode = null;
        String curName = "";
        boolean hasMtX = false, hasMtY = false, hasX = false, hasY = false;
        int maxMtX = 0, maxMtY = 0, maxX = 0, maxY = 0;

        for (String raw : out.split("\n")) {
            String line = raw.trim();
            if (line.startsWith("add device")) {
                if (curNode != null && (hasMtX || (hasX && hasY))) {
                    list.add(new String[]{curNode, curName,
                            hasMtX ? String.valueOf(maxMtX) : String.valueOf(maxX),
                            hasMtY ? String.valueOf(maxMtY) : String.valueOf(maxY),
                            hasMtX && hasMtY ? "1" : "0"});
                }
                int idx = line.indexOf('/');
                curNode = idx >= 0 ? line.substring(idx).trim() : null;
                curName = "";
                hasMtX = hasMtY = hasX = hasY = false;
                maxMtX = maxMtY = maxX = maxY = 0;
            } else if (line.startsWith("name:")) {
                curName = line.substring("name:".length()).replace("\"", "").trim();
            } else if (line.contains(": value")) {
                int sp = line.indexOf(':');
                String code = line.substring(0, sp).trim();
                int m = parseMax(line);
                if ("0035".equals(code)) {
                    hasMtX = true;
                    maxMtX = m;
                } else if ("0036".equals(code)) {
                    hasMtY = true;
                    maxMtY = m;
                } else if ("0000".equals(code)) {
                    hasX = true;
                    maxX = m;
                } else if ("0001".equals(code)) {
                    hasY = true;
                    maxY = m;
                }
            }
        }
        if (curNode != null && (hasMtX || (hasX && hasY))) {
            list.add(new String[]{curNode, curName,
                    hasMtX ? String.valueOf(maxMtX) : String.valueOf(maxX),
                    hasMtY ? String.valueOf(maxMtY) : String.valueOf(maxY),
                    hasMtX && hasMtY ? "1" : "0"});
        }

        if (list.isEmpty()) {
            return null;
        }
        for (String[] d : list) {
            if ("1".equals(d[4])) {
                return new String[]{d[0], d[2], d[3]};
            }
        }
        return new String[]{list.get(0)[0], list.get(0)[2], list.get(0)[3]};
    }

    private static int detectStruct() {
        String arch = execCapture("uname -m");
        if (arch != null && arch.contains("64")) {
            return 24;
        }
        return 16;
    }

    private static int parseMax(String line) {
        Matcher m = MAX_PAT.matcher(line);
        return m.find() ? parseInt(m.group(1), 0) : 0;
    }

    private static int parseInt(String s, int def) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Throwable ignored) {
            return def;
        }
    }

    private static boolean isEmpty(String s) {
        return s == null || s.trim().isEmpty();
    }

    // ------------------------------------------------------------------ io helpers

    private static boolean readFully(InputStream in, byte[] buf) throws Exception {
        int off = 0;
        while (off < buf.length) {
            int n = in.read(buf, off, buf.length - off);
            if (n < 0) {
                return false;
            }
            off += n;
        }
        return true;
    }

    private static int u16(byte[] b, int o) {
        return (b[o] & 0xff) | ((b[o + 1] & 0xff) << 8);
    }

    private static int s32(byte[] b, int o) {
        return (b[o] & 0xff)
                | ((b[o + 1] & 0xff) << 8)
                | ((b[o + 2] & 0xff) << 16)
                | (b[o + 3] << 24);
    }

    private static float dist(float x1, float y1, float x2, float y2) {
        float dx = x1 - x2;
        float dy = y1 - y2;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    private static void exec(String cmd) {
        try {
            ProcessBuilder pb = new ProcessBuilder("sh", "-c", cmd);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            p.getOutputStream().close();
            Thread t = new Thread(() -> {
                try {
                    InputStream i = p.getInputStream();
                    byte[] b = new byte[256];
                    while (i.read(b) != -1) {
                    }
                    int exit = p.waitFor();
                    if (exit != 0) {
                        log("cmd fail(" + exit + "): " + cmd);
                    }
                } catch (Throwable ignored) {
                }
            });
            t.setDaemon(true);
            t.start();
        } catch (Throwable t) {
            log("exec fail: " + t);
        }
    }

    private static String execCapture(String cmd) {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"sh", "-c", cmd});
            InputStream in = p.getInputStream();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] b = new byte[4096];
            int n;
            while ((n = in.read(b)) != -1) {
                out.write(b, 0, n);
            }
            in.close();
            p.waitFor();
            return out.toString("UTF-8");
        } catch (Throwable t) {
            return null;
        }
    }

    /** 同步执行 shell 命令并返回退出码（失败返回 -1）。 */
    private static int execResult(String cmd) {
        try {
            Process p = new ProcessBuilder("sh", "-c", cmd).redirectErrorStream(true).start();
            InputStream in = p.getInputStream();
            byte[] b = new byte[256];
            while (in.read(b) != -1) {
            }
            in.close();
            return p.waitFor();
        } catch (Throwable t) {
            return -1;
        }
    }

    private static void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
        }
    }

    private static void log(String msg) {
        System.out.println("[" + timeNow() + "] " + msg);
        System.out.flush();
    }

    private static String timeNow() {
        return new java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.US)
                .format(new java.util.Date());
    }
}

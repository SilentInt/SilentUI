package com.silentui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class GestureActivity extends Activity {

    private static final String[][] ACTIONS = {
            {"返回 (BACK)", "key:4"},
            {"Home (HOME)", "key:3"},
            {"菜单 (MENU)", "key:82"},
            {"音量+ (VOLUME_UP)", "key:24"},
            {"音量- (VOLUME_DOWN)", "key:25"},
            {"上一页 (PAGE_UP)", "key:92"},
            {"下一页 (PAGE_DOWN)", "key:93"},
            {"电源 (POWER)", "key:26"},
            {"截图 (SYSRQ)", "key:120"},
            {"最近任务 (需5.0+)", "key:187"},
            {"打开本应用设置", "settings"},
            {"无动作", "none"},
    };

    private static final String[][] GESTURES = {
            {"左缘内滑", Prefs.G_LEFT_EDGE},
            {"右缘内滑", Prefs.G_RIGHT_EDGE},
            {"底部上滑", Prefs.G_BOTTOM_UP},
            {"底部上滑并按住", Prefs.G_BOTTOM_HOLD},
    };

    private DaemonManager dm;
    private TextView statusText;
    private Button toggleButton;
    private TextView deviceText;
    private LinearLayout gestureContainer;
    private TextView logView;
    private ScrollView logScroll;
    private volatile boolean daemonRunning;

    private final Runnable logListener = new Runnable() {
        @Override
        public void run() {
            runOnUiThread(() -> {
                if (!isFinishing()) {
                    refreshLog();
                }
            });
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Prefs.init(this);
        dm = new DaemonManager(this);
        setContentView(buildUi());
        LogBus.addListener(logListener);
        refresh();
        refreshDaemonStatusAsync();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
        refreshDaemonStatusAsync();
    }

    @Override
    protected void onDestroy() {
        LogBus.removeListener(logListener);
        super.onDestroy();
    }

    // ------------------------------------------------------------------ UI

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);
        root.setPadding(dp(10), dp(8), dp(10), dp(8));

        TextView title = new TextView(this);
        title.setText("SilentUI 手势导航");
        title.setTextColor(Color.BLACK);
        title.setTextSize(18f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);

        TextView hint = new TextView(this);
        hint.setText("设置部分仅负责配置；手势识别由 root 系统守护进程独立完成，退出本应用后依然生效。");
        hint.setTextColor(Color.GRAY);
        hint.setTextSize(10f);
        hint.setPadding(0, dp(2), 0, dp(4));
        root.addView(hint);

        statusText = new TextView(this);
        statusText.setTextColor(Color.DKGRAY);
        statusText.setTextSize(11f);
        statusText.setTypeface(Typeface.MONOSPACE);
        root.addView(statusText);

        toggleButton = paperButton("", this::toggle);
        toggleButton.setTextSize(15f);
        root.addView(toggleButton, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(48)) {{
            topMargin = dp(6);
        }});

        LinearLayout devRow = new LinearLayout(this);
        devRow.setOrientation(LinearLayout.HORIZONTAL);
        devRow.setGravity(Gravity.CENTER_VERTICAL);
        devRow.setPadding(0, dp(6), 0, dp(2));
        deviceText = new TextView(this);
        deviceText.setTextColor(Color.DKGRAY);
        deviceText.setTextSize(10f);
        deviceText.setTypeface(Typeface.MONOSPACE);
        devRow.addView(deviceText, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        Button detect = paperButton("检测设备", this::detectDevice);
        devRow.addView(detect, new LinearLayout.LayoutParams(dp(96), dp(38)) {{
            setMarginStart(dp(6));
        }});
        root.addView(devRow);

        TextView gestureTitle = new TextView(this);
        gestureTitle.setText("手势 → 动作（点击修改）");
        gestureTitle.setTextColor(Color.BLACK);
        gestureTitle.setTextSize(13f);
        gestureTitle.setTypeface(Typeface.DEFAULT_BOLD);
        gestureTitle.setPadding(0, dp(8), 0, dp(2));
        root.addView(gestureTitle);

        gestureContainer = new LinearLayout(this);
        gestureContainer.setOrientation(LinearLayout.VERTICAL);
        root.addView(gestureContainer);

        Button params = paperButton("参数", this::showParamsDialog);
        root.addView(params, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(40)) {{
            topMargin = dp(6);
        }});

        TextView logTitle = new TextView(this);
        logTitle.setText("运行日志");
        logTitle.setTextColor(Color.BLACK);
        logTitle.setTextSize(12f);
        logTitle.setTypeface(Typeface.DEFAULT_BOLD);
        logTitle.setPadding(0, dp(8), 0, dp(2));
        root.addView(logTitle);

        logScroll = new ScrollView(this);
        logView = new TextView(this);
        logView.setTextColor(Color.DKGRAY);
        logView.setTextSize(9f);
        logView.setTypeface(Typeface.MONOSPACE);
        logScroll.addView(logView);
        root.addView(logScroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        return root;
    }

    private void renderGestures() {
        gestureContainer.removeAllViews();
        for (String[] g : GESTURES) {
            gestureContainer.addView(gestureRow(g[0], g[1]), cardLp());
        }
    }

    private View gestureRow(String name, final String key) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(10), dp(8), dp(10), dp(8));
        row.setBackground(cardBackground());
        row.setClickable(true);

        TextView nameView = new TextView(this);
        nameView.setText(name);
        nameView.setTextColor(Color.BLACK);
        nameView.setTextSize(13f);
        nameView.setTypeface(Typeface.DEFAULT_BOLD);
        row.addView(nameView, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView actionView = new TextView(this);
        actionView.setText(labelFor(Prefs.action(key)));
        actionView.setTextColor(Color.DKGRAY);
        actionView.setTextSize(11f);
        actionView.setGravity(Gravity.END);
        row.addView(actionView);

        row.setOnClickListener(v -> showActionDialog(key));
        return row;
    }

    private void showActionDialog(final String key) {
        String[] labels = new String[ACTIONS.length + 3];
        for (int i = 0; i < ACTIONS.length; i++) {
            labels[i] = ACTIONS[i][0];
        }
        labels[ACTIONS.length] = "启动应用…";
        labels[ACTIONS.length + 1] = "自定义按键码…";
        labels[ACTIONS.length + 2] = "自定义命令…";

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("选择动作")
                .setItems(labels, (d, which) -> {
                    if (which < ACTIONS.length) {
                        Prefs.setAction(key, ACTIONS[which][1]);
                        renderGestures();
                        pushConfig();
                    } else if (which == ACTIONS.length) {
                        showAppPicker(key);
                    } else if (which == ACTIONS.length + 1) {
                        showKeycodeDialog(key);
                    } else {
                        showCommandDialog(key);
                    }
                })
                .setNegativeButton("取消", null)
                .show();
        styleDialogButton(dialog.getButton(AlertDialog.BUTTON_NEGATIVE));
    }

    private void showKeycodeDialog(final String key) {
        final EditText et = new EditText(this);
        et.setInputType(InputType.TYPE_CLASS_NUMBER);
        et.setHint("输入 Android 键码，例如 24");
        new AlertDialog.Builder(this)
                .setTitle("自定义按键码")
                .setView(padDialog(et))
                .setPositiveButton("确定", (d, w) -> {
                    try {
                        int code = Integer.parseInt(et.getText().toString().trim());
                        Prefs.setAction(key, "key:" + code);
                        renderGestures();
                        pushConfig();
                    } catch (Throwable ignored) {
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showCommandDialog(final String key) {
        final EditText et = new EditText(this);
        et.setInputType(InputType.TYPE_CLASS_TEXT);
        et.setHint("例如 input swipe 100 100 300 100 200");
        new AlertDialog.Builder(this)
                .setTitle("自定义 shell 命令（root 执行）")
                .setView(padDialog(et))
                .setPositiveButton("确定", (d, w) -> {
                    String cmd = et.getText().toString().trim();
                    if (!cmd.isEmpty()) {
                        Prefs.setAction(key, "cmd:" + cmd);
                        renderGestures();
                        pushConfig();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showAppPicker(final String key) {
        final List<AppEntry> apps = loadApps();
        if (apps.isEmpty()) {
            LogBus.log("未找到可启动的应用");
            return;
        }
        String[] labels = new String[apps.size()];
        for (int i = 0; i < apps.size(); i++) {
            labels[i] = apps.get(i).label + "  (" + apps.get(i).pkg + ")";
        }
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("选择要启动的应用")
                .setItems(labels, (d, which) -> {
                    Prefs.setAction(key, "app:" + apps.get(which).pkg);
                    renderGestures();
                    pushConfig();
                })
                .setNegativeButton("取消", null)
                .show();
        styleDialogButton(dialog.getButton(AlertDialog.BUTTON_NEGATIVE));
    }

    private List<AppEntry> loadApps() {
        List<AppEntry> out = new ArrayList<>();
        PackageManager pm = getPackageManager();
        Intent main = new Intent(Intent.ACTION_MAIN);
        main.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> infos;
        try {
            infos = pm.queryIntentActivities(main, 0);
        } catch (Throwable t) {
            return out;
        }
        for (ResolveInfo ri : infos) {
            if (ri == null || ri.activityInfo == null) {
                continue;
            }
            String pkg = ri.activityInfo.packageName;
            String label = String.valueOf(ri.loadLabel(pm));
            out.add(new AppEntry(label, pkg));
        }
        Collections.sort(out, (a, b) -> a.label.compareToIgnoreCase(b.label));
        return out;
    }

    private void showParamsDialog() {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setBackgroundColor(Color.WHITE);
        content.setPadding(dp(14), dp(6), dp(14), dp(6));

        content.addView(divider());

        content.addView(stepperRow("边缘宽度 (%)", Prefs.edgePct(), 1, 40, 1,
                v -> { Prefs.setEdgePct(v); pushConfig(); }));
        content.addView(stepperRow("滑动距离 (%)", Prefs.swipePct(), 2, 60, 1,
                v -> { Prefs.setSwipePct(v); pushConfig(); }));
        content.addView(stepperRow("长按时长 (ms)", Prefs.holdMs(), 200, 2000, 50,
                v -> { Prefs.setHoldMs(v); pushConfig(); }));
        content.addView(stepperRow("长按容忍 (%)", Prefs.holdSlopPct(), 1, 30, 1,
                v -> { Prefs.setHoldSlopPct(v); pushConfig(); }));

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.WHITE);
        scroll.addView(content);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("参数")
                .setView(scroll)
                .setPositiveButton("完成", (d, w) -> pushConfig())
                .setNegativeButton("关闭", null)
                .show();
        styleDialogButton(dialog.getButton(AlertDialog.BUTTON_POSITIVE));
        styleDialogButton(dialog.getButton(AlertDialog.BUTTON_NEGATIVE));
    }

    /** 纸面步进器：名称 + 数值 + [−] [+]，替代深色 SeekBar。 */
    private View stepperRow(String name, int value, int min, int max, int step,
                            final IntListener listener) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(4), 0, dp(4));

        TextView nameView = new TextView(this);
        nameView.setText(name);
        nameView.setTextSize(12f);
        nameView.setTextColor(Color.BLACK);
        row.addView(nameView, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        final TextView valueView = new TextView(this);
        valueView.setText(String.valueOf(value));
        valueView.setTextSize(13f);
        valueView.setTypeface(Typeface.DEFAULT_BOLD);
        valueView.setTextColor(Color.BLACK);
        valueView.setGravity(Gravity.CENTER);
        valueView.setBackground(paperBg());
        row.addView(valueView, new LinearLayout.LayoutParams(dp(64), dp(38)) {{
            setMarginStart(dp(4));
            setMarginEnd(dp(4));
        }});

        final int[] cur = {value};
        Button minus = paperButton("−", null);
        Button plus = paperButton("+", null);
        minus.setOnClickListener(v -> {
            int nv = Math.max(min, cur[0] - step);
            cur[0] = nv;
            valueView.setText(String.valueOf(nv));
            listener.onChanged(nv);
        });
        plus.setOnClickListener(v -> {
            int nv = Math.min(max, cur[0] + step);
            cur[0] = nv;
            valueView.setText(String.valueOf(nv));
            listener.onChanged(nv);
        });
        row.addView(minus, new LinearLayout.LayoutParams(dp(40), dp(38)));
        row.addView(plus, new LinearLayout.LayoutParams(dp(40), dp(38)) {{
            setMarginStart(dp(4));
        }});
        return row;
    }

    private View divider() {
        View v = new View(this);
        v.setBackgroundColor(Color.rgb(0xcc, 0xcc, 0xcc));
        return v;
    }

    private void toggle() {
        if (Prefs.enabled()) {
            Prefs.setEnabled(false);
            dm.stop();
        } else {
            RootShell.ensureSession();
            Prefs.setEnabled(true);
            dm.start();
        }
        refresh();
        refreshDaemonStatusAsync();
    }

    private void detectDevice() {
        Thread t = new Thread(() -> {
            List<TouchDevice.Info> list = TouchDevice.discover();
            if (list.isEmpty()) {
                LogBus.log("未发现触摸设备，请确认已授予 root");
                return;
            }
            TouchDevice.Info info = TouchDevice.pickBest(list, null);
            Prefs.setDeviceInfo(info.node, info.maxX, info.maxY, TouchDevice.structSize());
            LogBus.log("检测到设备: " + info);
            runOnUiThread(this::refresh);
            pushConfig();
        }, "detect-device");
        t.setDaemon(true);
        t.start();
    }

    /** 配置变更后同步给守护进程（守护会在下一次触碰/每 2 秒重新读配置）。 */
    private void pushConfig() {
        if (Prefs.enabled()) {
            new Thread(() -> dm.writeConfig(), "push-config").start();
        }
    }

    // ------------------------------------------------------------------ refresh

    private void refresh() {
        boolean on = Prefs.enabled();
        toggleButton.setText(on ? "停用手势导航" : "启用手势导航");
        statusText.setText("守护：" + (daemonRunning ? "运行中" : "已停止")
                + "\n设备：" + (Prefs.device().isEmpty() ? "自动检测" : Prefs.device())
                + "   边缘=" + Prefs.edgePct() + "% 滑动=" + Prefs.swipePct() + "%");
        deviceText.setText(Prefs.device().isEmpty() ? "自动检测触摸设备" : Prefs.device());
        renderGestures();
        refreshLog();
    }

    private void refreshDaemonStatusAsync() {
        Thread t = new Thread(() -> {
            // 让刚写入的 pid 文件落盘，避免竞态显示“已停止”
            try {
                Thread.sleep(800);
            } catch (InterruptedException ignored) {
            }
            boolean running = dm.isRunning();
            daemonRunning = running;
            runOnUiThread(() -> {
                if (!isFinishing()) {
                    refresh();
                }
            });
        }, "status");
        t.setDaemon(true);
        t.start();
    }

    private void refreshLog() {
        StringBuilder sb = new StringBuilder();
        List<String> app = LogBus.snapshot();
        int from = Math.max(0, app.size() - 40);
        for (int i = from; i < app.size(); i++) {
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(app.get(i));
        }
        // 守护日志
        Thread t = new Thread(() -> {
            final String daemonLog = dm.readLog();
            runOnUiThread(() -> {
                if (isFinishing()) {
                    return;
                }
                StringBuilder sb2 = new StringBuilder();
                List<String> app2 = LogBus.snapshot();
                int f2 = Math.max(0, app2.size() - 40);
                for (int i = f2; i < app2.size(); i++) {
                    if (sb2.length() > 0) {
                        sb2.append('\n');
                    }
                    sb2.append(app2.get(i));
                }
                if (daemonLog != null && !daemonLog.trim().isEmpty()) {
                    if (sb2.length() > 0) {
                        sb2.append("\n── 守护日志 ──\n");
                    }
                    sb2.append(daemonLog);
                }
                logView.setText(sb2.toString());
                logScroll.post(() -> logScroll.fullScroll(View.FOCUS_DOWN));
            });
        }, "read-log");
        t.setDaemon(true);
        t.start();
    }

    // ------------------------------------------------------------------ helpers

    private String labelFor(String spec) {
        if (spec == null || spec.isEmpty()) {
            return "无动作";
        }
        for (String[] a : ACTIONS) {
            if (a[1].equals(spec)) {
                return a[0];
            }
        }
        if (spec.startsWith("key:")) {
            return "按键 " + spec.substring(4);
        }
        if (spec.startsWith("key-long:")) {
            return "长按按键 " + spec.substring(9);
        }
        if (spec.startsWith("app:")) {
            String pkg = spec.substring(4);
            try {
                ApplicationInfo ai = getPackageManager().getApplicationInfo(pkg, 0);
                return "应用 " + getPackageManager().getApplicationLabel(ai);
            } catch (Throwable t) {
                return "应用 " + pkg;
            }
        }
        if (spec.startsWith("cmd:")) {
            return "命令 " + spec.substring(4);
        }
        return spec;
    }

    private LinearLayout padDialog(View v) {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setBackgroundColor(Color.WHITE);
        wrap.setPadding(dp(20), dp(12), dp(20), dp(4));
        wrap.addView(v, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        return wrap;
    }

    private Button paperButton(String text, Runnable onClick) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(12f);
        b.setAllCaps(false);
        b.setTextColor(Color.BLACK);
        if (Build.VERSION.SDK_INT >= 21) {
            b.setStateListAnimator(null);
        }
        b.setBackground(paperBg());
        if (onClick != null) {
            b.setOnClickListener(v -> onClick.run());
        }
        return b;
    }

    private GradientDrawable paperBg() {
        return new GradientDrawable() {{
            setColor(Color.WHITE);
            setStroke(1, Color.rgb(0x33, 0x33, 0x33));
            setCornerRadius(dp(4));
        }};
    }

    private GradientDrawable cardBackground() {
        return new GradientDrawable() {{
            setColor(Color.WHITE);
            setStroke(1, Color.rgb(0x33, 0x33, 0x33));
            setCornerRadius(dp(6));
        }};
    }

    private LinearLayout.LayoutParams cardLp() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT) {{
            bottomMargin = dp(6);
        }};
    }

    private void styleDialogButton(Button b) {
        if (b == null) {
            return;
        }
        b.setTextColor(Color.BLACK);
        b.setAllCaps(false);
        if (Build.VERSION.SDK_INT >= 21) {
            b.setStateListAnimator(null);
        }
        b.setBackground(paperBg());
    }

    private static final class AppEntry {
        final String label;
        final String pkg;

        AppEntry(String label, String pkg) {
            this.label = label;
            this.pkg = pkg;
        }
    }

    private interface IntListener {
        void onChanged(int value);
    }

    private int dp(int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                getResources().getDisplayMetrics());
    }
}

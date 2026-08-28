package com.silentui;

import android.app.ActivityGroup;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends ActivityGroup {

    private static final String STATUS_PREFS = "cfg";
    private static final String STATUS_ENABLED = "enabled";

    private TextView gestureStatus;
    private TextView barStatus;
    private FrameLayout content;
    private final TextView[] tabs = new TextView[2];
    private final View[] panels = new View[2];
    private static boolean autoStarted;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Prefs.init(this);
        setContentView(buildRoot());
        showTab(0);
        if (!autoStarted) {
            autoStarted = true;
            autoGrantAndStart();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    // ---------------------------------------------------------------- layout

    private LinearLayout buildRoot() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);

        // header
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(14), dp(12), dp(14), dp(8));

        TextView title = new TextView(this);
        title.setText("SilentUI");
        title.setTextColor(Color.BLACK);
        title.setTextSize(20);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        header.addView(title);        gestureStatus = new TextView(this);
        gestureStatus.setTextColor(Color.DKGRAY);
        gestureStatus.setTextSize(12);
        gestureStatus.setTypeface(Typeface.MONOSPACE);
        header.addView(gestureStatus);

        barStatus = new TextView(this);
        barStatus.setTextColor(Color.DKGRAY);
        barStatus.setTextSize(12);
        barStatus.setTypeface(Typeface.MONOSPACE);
        header.addView(barStatus);

        root.addView(header);

        // content area
        content = new FrameLayout(this);
        content.setBackgroundColor(Color.WHITE);
        root.addView(content, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        // bottom navigation
        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setBackgroundColor(Color.parseColor("#F2F2F2"));
        nav.setMinimumHeight(dp(56));
        nav.setMinimumWidth(LinearLayout.LayoutParams.MATCH_PARENT);

        LinearLayout.LayoutParams navLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(56));
        navLp.topMargin = dp(1);
        root.addView(nav, navLp);

        LinearLayout.LayoutParams tabLp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.MATCH_PARENT, 1f);
        nav.addView(makeTab(0, "手势导航"), tabLp);
        nav.addView(makeTab(1, "状态栏"), tabLp);

        return root;
    }

    private TextView makeTab(final int idx, String label) {
        TextView t = new TextView(this);
        t.setText(label);
        t.setTextSize(15);
        t.setGravity(Gravity.CENTER);
        t.setClickable(true);
        t.setOnClickListener(v -> showTab(idx));
        tabs[idx] = t;
        return t;
    }

    // ---------------------------------------------------------------- tabs

    private void showTab(int idx) {
        View panel = panels[idx];
        if (panel == null) {
            panel = createPanel(idx);
            panels[idx] = panel;
            content.addView(panel, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT));
        }
        for (int i = 0; i < panels.length; i++) {
            if (panels[i] != null) {
                panels[i].setVisibility(i == idx ? View.VISIBLE : View.GONE);
            }
        }
        for (int i = 0; i < tabs.length; i++) {
            if (tabs[i] != null) {
                tabs[i].setTextColor(i == idx ? Color.parseColor("#1A73E8") : Color.GRAY);
                tabs[i].setTypeface(i == idx ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            }
        }
    }

    private View createPanel(int idx) {
        String id = idx == 0 ? "gesture" : "status";
        Class<?> cls = idx == 0 ? GestureActivity.class : StatusBarActivity.class;
        Window w = getLocalActivityManager().startActivity(id, new Intent(this, cls));
        return w.getDecorView();
    }

    // ---------------------------------------------------------------- auto

    /** 打开应用时：先检查权限，缺失则后台一键授权；权限完整→启动手势+状态栏，不完整→重启。 */
    private void autoGrantAndStart() {
        Thread t = new Thread(() -> {
            try {
                boolean ok = PermissionHelper.ensurePermissions(this);
                if (ok) {
                    RootShell.ensureSession();
                    Prefs.setEnabled(true);
                    getSharedPreferences(STATUS_PREFS, MODE_PRIVATE).edit()
                            .putBoolean(STATUS_ENABLED, true).apply();
                    new DaemonManager(this).start();
                    runOnUiThread(() -> {
                        Toast.makeText(this, "已授权并启动", Toast.LENGTH_LONG).show();
                        refresh();
                    });
                } else if (RootShell.available()) {
                    runOnUiThread(() -> Toast.makeText(this,
                            "权限不完整，即将重启以完成授权", Toast.LENGTH_LONG).show());
                    try {
                        Thread.sleep(1800);
                    } catch (InterruptedException ignored) {
                    }
                    RootShell.send("reboot");
                } else {
                    runOnUiThread(() -> Toast.makeText(this,
                            "未获取到 root，无法自动授权", Toast.LENGTH_LONG).show());
                }
            } catch (Throwable ignored) {
            }
        }, "startup");
        t.setDaemon(true);
        t.start();
    }

    private void refresh() {
        boolean gestureOn = Prefs.enabled();
        SharedPreferences sp = getSharedPreferences(STATUS_PREFS, MODE_PRIVATE);
        boolean barOn = sp.getBoolean(STATUS_ENABLED, false);
        gestureStatus.setText("手势导航：" + (gestureOn ? "已启用" : "未启用"));
        barStatus.setText("自定义状态栏：" + (barOn ? "运行中" : "未启动"));
    }

    private int dp(int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                getResources().getDisplayMetrics());
    }
}

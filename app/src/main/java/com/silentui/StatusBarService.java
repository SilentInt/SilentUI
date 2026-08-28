package com.silentui;

import android.app.Notification;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.service.notification.StatusBarNotification;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class StatusBarService extends android.app.Service {

    public static final String ACTION_REFRESH = "com.silentui.REFRESH";
    public static StatusBarService instance;

    private static final int BAR_HEIGHT = 60;

    private WindowManager wm;
    private View statusBarView;
    private View panelView;

    private IconView statusBell;
    private IconView trayWifi;
    private IconView trayBt;
    private IconView trayBattery;
    private TextView leftText;
    private TextView clockText;
    private TextView batteryText;
    private TextView wifiNameText;

    private LinearLayout notifContainer;
    private SeekBar brightnessBar;

    private SharedPreferences prefs;
    private WifiManager wifiManager;
    private BluetoothAdapter bluetoothAdapter;
    private Handler handler = new Handler(Looper.getMainLooper());
    private Runnable ticker;
    private BroadcastReceiver receiver;
    private Runnable hideBarTask;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        prefs = getSharedPreferences("cfg", MODE_PRIVATE);
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        wifiManager = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        PermissionHelper.hideSystemStatusBar();
        registerReceivers();
        buildStatusBar();
        updateClock();
        updateTray();
        updateLeft();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_REFRESH.equals(intent.getAction())) {
            buildStatusBar();
            if (panelView != null) {
                closePanel();
                openPanel();
            }
        }
        updateClock();
        updateTray();
        updateLeft();
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        instance = null;
        removeView(statusBarView);
        removeView(panelView);
        if (hideBarTask != null) {
            handler.removeCallbacks(hideBarTask);
        }
        applyOverscan(false);
        PermissionHelper.restoreSystemStatusBar();
        if (receiver != null) {
            try {
                unregisterReceiver(receiver);
            } catch (Exception ignored) {
            }
            receiver = null;
        }
        if (ticker != null) {
            handler.removeCallbacks(ticker);
        }
    }

    private void applyOverscan(final boolean on) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String cmd = on ? "wm overscan '0,60,0,0'" : "wm overscan '0,0,0,0'";
                    Process p = new ProcessBuilder("su", "-c", cmd)
                            .redirectErrorStream(true).start();
                    p.waitFor();
                } catch (Exception ignored) {
                }
            }
        }).start();
    }

    // ---------------- status bar ----------------

    private void buildStatusBar() {
        removeView(statusBarView);

        FrameLayout outer = new FrameLayout(this);
        outer.setBackgroundColor(Color.WHITE);

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(Color.WHITE);
        bar.setPadding(dp(14), 0, dp(14), 0);

        // left
        LinearLayout leftBox = new LinearLayout(this);
        leftBox.setOrientation(LinearLayout.HORIZONTAL);
        leftBox.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);

        statusBell = new IconView(this);
        statusBell.setIcon("bell");
        statusBell.setIconColor(Color.BLACK);
        statusBell.setVisibility(View.GONE);
        leftBox.addView(statusBell, new LinearLayout.LayoutParams(dp(18), dp(18)));

        leftText = new TextView(this);
        leftText.setTextColor(Color.BLACK);
        leftText.setTextSize(15);
        leftText.setSingleLine(true);
        LinearLayout.LayoutParams ltp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        ltp.leftMargin = dp(5);
        leftBox.addView(leftText, ltp);

        bar.addView(leftBox, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.MATCH_PARENT, 1f));
        bar.addView(vDivider(), new LinearLayout.LayoutParams(1,
                LinearLayout.LayoutParams.MATCH_PARENT));

        // center
        clockText = new TextView(this);
        clockText.setTextColor(Color.BLACK);
        clockText.setTextSize(18);
        clockText.setSingleLine(true);
        clockText.setGravity(Gravity.CENTER);
        bar.addView(clockText, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.MATCH_PARENT, 1f));
        bar.addView(vDivider(), new LinearLayout.LayoutParams(1,
                LinearLayout.LayoutParams.MATCH_PARENT));

        // right
        LinearLayout rightBox = new LinearLayout(this);
        rightBox.setOrientation(LinearLayout.HORIZONTAL);
        rightBox.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);

        trayWifi = new IconView(this);
        trayWifi.setIcon("wifi");
        trayBt = new IconView(this);
        trayBt.setIcon("bt");
        trayBattery = new IconView(this);
        trayBattery.setIcon("battery");
        batteryText = new TextView(this);
        batteryText.setTextColor(Color.BLACK);
        batteryText.setTextSize(14);

        wifiNameText = new TextView(this);
        wifiNameText.setTextColor(Color.BLACK);
        wifiNameText.setTextSize(13);
        wifiNameText.setSingleLine(true);
        wifiNameText.setVisibility(View.GONE);

        rightBox.addView(trayWifi, iconLp());
        LinearLayout.LayoutParams wntp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        wntp.leftMargin = dp(2);
        wntp.rightMargin = dp(2);
        rightBox.addView(wifiNameText, wntp);
        rightBox.addView(trayBt, iconLp());
        rightBox.addView(trayBattery, iconLp());
        LinearLayout.LayoutParams btp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        btp.leftMargin = dp(2);
        rightBox.addView(batteryText, btp);

        bar.addView(rightBox, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.MATCH_PARENT, 1f));

        outer.addView(bar, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        outer.setOnTouchListener(statusTouch());

        WindowManager.LayoutParams p = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                BAR_HEIGHT,
                getOverlayType(),
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                        | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                android.graphics.PixelFormat.TRANSLUCENT);
        p.gravity = Gravity.TOP | Gravity.LEFT;
        p.x = 0;
        p.y = isTouchMode() ? 0 : -BAR_HEIGHT;

        // 触摸显示模式：保持原有大小，仅用透明度隐藏/显示；固定位置模式始终不透明。
        outer.setAlpha(isTouchMode() ? 0f : 1f);

        try {
            wm.addView(outer, p);
            statusBarView = outer;
        } catch (Exception e) {
            Toast.makeText(this, "状态栏添加失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }

        applyOverscan(!isTouchMode());
    }

    private boolean isTouchMode() {
        return "touch".equals(prefs.getString("bar_mode", "touch"));
    }

    private void showBarTemporarily() {
        if (statusBarView != null) {
            statusBarView.setAlpha(1f);
        }
        scheduleHideBar();
    }

    private void scheduleHideBar() {
        handler.removeCallbacks(hideBarTask);
        if (!isTouchMode()) {
            return;
        }
        long delay = Math.max(1000L, prefs.getLong("bar_hide_ms", 2000L));
        hideBarTask = new Runnable() {
            @Override
            public void run() {
                if (statusBarView != null) {
                    statusBarView.setAlpha(0f);
                }
            }
        };
        handler.postDelayed(hideBarTask, delay);
    }

    private View.OnTouchListener statusTouch() {
        return new View.OnTouchListener() {
            float downX, downY, lastX;
            boolean left;
            boolean acted;
            boolean bright;

            @Override
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downX = e.getRawX();
                        downY = e.getRawY();
                        lastX = e.getRawX();
                        left = e.getX() < v.getWidth() / 2f;
                        acted = false;
                        bright = false;
                        showBarTemporarily();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float dx = e.getRawX() - downX;
                        float dy = e.getRawY() - downY;
                        if (prefs.getBoolean("swipe_brightness", false)
                                && Math.abs(dx) > dp(18)
                                && Math.abs(dx) > Math.abs(dy)) {
                            bright = true;
                            float delta = e.getRawX() - lastX;
                            lastX = e.getRawX();
                            if (Math.abs(delta) >= dp(2)) {
                                adjustBrightnessDelta(delta, v.getWidth());
                            }
                            return true;
                        }
                        if (!acted && dy > dp(16)) {
                            acted = true;
                            runSwipeAction(left);
                        } else if (!acted && -dy > dp(16)) {
                            acted = true;
                            closePanel();
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        Log.d("CSB", "status UP acted=" + acted + " bright=" + bright + " panelOpen=" + (panelView != null));
                        if (!acted && !bright) {
                            togglePanel();
                        }
                        return true;
                }
                return true;
            }
        };
    }

    private void adjustBrightnessDelta(float deltaPx, int width) {
        try {
            if (!Settings.System.canWrite(this)) return;
            int cur = Settings.System.getInt(getContentResolver(),
                    Settings.System.SCREEN_BRIGHTNESS, 128);
            if (width <= 0) width = 1080;
            int step = Math.round(deltaPx / (float) width * 255f);
            int next = Math.max(1, Math.min(255, cur + step));
            setBrightness(next);
            if (brightnessBar != null) {
                brightnessBar.setProgress(next);
            }
        } catch (Exception ignored) {
        }
    }

    private void runSwipeAction(boolean left) {
        String key = prefs.getString(left ? "swipe_left" : "swipe_right",
                left ? "panel" : "settings");
        if ("panel".equals(key)) {
            openPanel();
        } else if ("settings".equals(key)) {
            launchSettings();
        } else if ("wifi".equals(key)) {
            launchWifiSettings();
        } else if ("bt".equals(key)) {
            launchBtSettings();
        } else if (key.startsWith("custom:")) {
            launchCustomActivity(key.substring(7));
        }
    }

    private void togglePanel() {
        Log.d("CSB", "togglePanel panelOpen=" + (panelView != null));
        if (panelView != null) {
            closePanel();
        } else {
            openPanel();
        }
    }

    private LinearLayout.LayoutParams iconLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(18), dp(18));
        lp.leftMargin = dp(3);
        lp.rightMargin = dp(3);
        return lp;
    }

    private View vDivider() {
        View v = new View(this);
        v.setBackgroundColor(Color.parseColor("#40000000"));
        return v;
    }

    private void updateLeft() {
        if (leftText == null) return;
        String name = prefs.getString("name", "");
        int n = 0;
        if (NotificationListener.instance != null) {
            n = NotificationListener.instance.getActive().size();
        }

        boolean showNotif = prefs.getBoolean("show_notifications", true);
        if (statusBell != null) {
            statusBell.setVisibility(showNotif && n > 0 ? View.VISIBLE : View.GONE);
        }

        StringBuilder sb = new StringBuilder();
        if (name.length() > 0) sb.append(name);
        if (showNotif && n > 0) {
            if (sb.length() > 0) sb.append(" ");
            sb.append(n);
        }
        leftText.setText(sb.toString());
    }

    private void updateTray() {
        if (trayWifi == null) return;

        boolean showWifi = prefs.getBoolean("show_wifi", true);
        boolean showBt = prefs.getBoolean("show_bt", true);
        boolean showBattery = prefs.getBoolean("show_battery", true);

        boolean wifiOn = wifiManager != null && wifiManager.isWifiEnabled();
        boolean btOn = btEnabled();
        int level = getBatteryLevel();

        trayWifi.setVisibility(showWifi ? View.VISIBLE : View.GONE);
        trayWifi.setIconColor(wifiOn ? Color.BLACK : Color.LTGRAY);

        String ssid = wifiName();
        if (wifiNameText != null) {
            wifiNameText.setVisibility(showWifi && ssid != null ? View.VISIBLE : View.GONE);
            wifiNameText.setText(ssid != null ? ssid : "");
        }

        trayBt.setVisibility(showBt ? View.VISIBLE : View.GONE);
        trayBt.setIconColor(btOn ? Color.BLACK : Color.LTGRAY);

        trayBattery.setVisibility(showBattery ? View.VISIBLE : View.GONE);
        trayBattery.setBatteryLevel(level);
        trayBattery.setIconColor(Color.BLACK);

        batteryText.setVisibility(showBattery ? View.VISIBLE : View.GONE);
        batteryText.setText(level >= 0 ? level + "%" : "");
    }

    private void updateClock() {
        if (clockText != null) {
            SimpleDateFormat fmt = new SimpleDateFormat("HH:mm", Locale.getDefault());
            clockText.setText(fmt.format(new Date()));
        }
        if (ticker == null) {
            ticker = new Runnable() {
                @Override
                public void run() {
                    updateClock();
                    handler.postDelayed(this, 30000);
                }
            };
            handler.postDelayed(ticker, 30000);
        }
    }

    // ---------------- panel ----------------

    private void openPanel() {
        if (panelView != null) return;
        buildPanel();
    }

    private void closePanel() {
        removeView(panelView);
        panelView = null;
    }

    private void buildPanel() {
        removeView(panelView);

        SwipeCloseLinearLayout panel = new SwipeCloseLinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackground(panelBackground());
        panel.setPadding(dp(10), dp(10), dp(10), dp(6));
        panel.setOnSwipeUpListener(new SwipeCloseLinearLayout.OnSwipeUpListener() {
            @Override public void onSwipeUp() {
                closePanel();
            }
        });

        // title row
        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(this);
        title.setText("快捷面板");
        title.setTextColor(Color.BLACK);
        title.setTextSize(16);
        titleRow.addView(title, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        LinearLayout closeBox = new LinearLayout(this);
        closeBox.setOrientation(LinearLayout.HORIZONTAL);
        closeBox.setGravity(Gravity.CENTER_VERTICAL);
        IconView closeIcon = new IconView(this);
        closeIcon.setIcon("chevron_down");
        closeIcon.setIconColor(Color.BLACK);
        closeBox.addView(closeIcon, new LinearLayout.LayoutParams(dp(18), dp(18)));
        TextView closeLabel = new TextView(this);
        closeLabel.setText("收起");
        closeLabel.setTextColor(Color.BLACK);
        closeLabel.setTextSize(13);
        closeBox.addView(closeLabel);
        closeBox.setOnTouchListener(collapseTouch());
        titleRow.addView(closeBox);
        panel.addView(titleRow);

        panel.addView(dividerRow());

        // quick settings tiles: 3 per row, partial last row expands to full width
        final List<Tile> tiles = loadTiles();
        LinearLayout tilesBox = new LinearLayout(this);
        tilesBox.setOrientation(LinearLayout.VERTICAL);
        if (tiles.isEmpty()) {
            TextView none = new TextView(this);
            none.setText("未启用快捷开关");
            none.setTextColor(Color.DKGRAY);
            none.setTextSize(13);
            tilesBox.addView(none);
        } else {
            int perRow = 3;
            for (int i = 0; i < tiles.size(); i += perRow) {
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                int end = Math.min(i + perRow, tiles.size());
                for (int j = i; j < end; j++) {
                    row.addView(makeTile(tiles.get(j)), tileLp());
                }
                tilesBox.addView(row);
            }
        }
        panel.addView(tilesBox);

        panel.addView(dividerRow());

        // backlight: small switch button + slider in the same row
        LinearLayout blRow = new LinearLayout(this);
        blRow.setOrientation(LinearLayout.HORIZONTAL);
        blRow.setGravity(Gravity.CENTER_VERTICAL);

        if (prefs.getBoolean("tile_brightness", true)) {
            Button blBtn = new Button(this);
            blBtn.setText("背光开关");
            blBtn.setTextSize(12);
            blBtn.setAllCaps(false);
            blBtn.setTextColor(Color.BLACK);
            blBtn.setMinimumHeight(dp(36));
            blBtn.setPadding(dp(8), 0, dp(8), 0);
            blBtn.setBackground(roundedRect(Color.WHITE, dp(6), Color.BLACK, 1));
            blBtn.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    toggleBrightness();
                }
            });
            blRow.addView(blBtn);
        }

        brightnessBar = new SeekBar(this);
        brightnessBar.setMax(255);
        int cur = Settings.System.getInt(getContentResolver(),
                Settings.System.SCREEN_BRIGHTNESS, 128);
        brightnessBar.setProgress(cur);
        brightnessBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                if (fromUser) setBrightness(progress);
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {
            }
            @Override public void onStopTrackingTouch(SeekBar sb) {
            }
        });
        blRow.addView(brightnessBar, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        panel.addView(blRow);

        panel.addView(dividerRow());

        // notifications header
        LinearLayout notifHeader = new LinearLayout(this);
        notifHeader.setOrientation(LinearLayout.HORIZONTAL);
        notifHeader.setGravity(Gravity.CENTER_VERTICAL);
        TextView nt = new TextView(this);
        nt.setText("通知");
        nt.setTextColor(Color.BLACK);
        nt.setTextSize(15);
        notifHeader.addView(nt, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        Button clearBtn = makeSmallButton("清除全部");
        clearBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (NotificationListener.instance != null) {
                    NotificationListener.instance.clearAll();
                }
                populateNotifications();
                updateLeft();
            }
        });
        notifHeader.addView(clearBtn);
        panel.addView(notifHeader);

        // notifications list
        ScrollView scroll = new ScrollView(this);
        notifContainer = new LinearLayout(this);
        notifContainer.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(notifContainer);
        panel.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        panel.setProtectedView(scroll);

        // bottom collapse indicator
        LinearLayout indicator = new LinearLayout(this);
        indicator.setOrientation(LinearLayout.HORIZONTAL);
        indicator.setGravity(Gravity.CENTER);
        indicator.setPadding(0, dp(4), 0, dp(4));
        IconView chev = new IconView(this);
        chev.setIcon("chevron_up");
        chev.setIconColor(Color.BLACK);
        indicator.addView(chev, new LinearLayout.LayoutParams(dp(18), dp(18)));
        TextView indText = new TextView(this);
        indText.setText("上滑收起");
        indText.setTextColor(Color.BLACK);
        indText.setTextSize(13);
        indicator.addView(indText);
        indicator.setOnTouchListener(collapseTouch());
        panel.addView(indicator);

        WindowManager.LayoutParams p = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                getOverlayType(),
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                        | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                android.graphics.PixelFormat.TRANSLUCENT);
        p.gravity = Gravity.TOP | Gravity.LEFT;
        p.x = 0;
        p.y = isTouchMode() ? BAR_HEIGHT : 0;

        try {
            wm.addView(panel, p);
            panelView = panel;
        } catch (Exception e) {
            Toast.makeText(this, "面板添加失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }

        populateNotifications();
    }

    private View.OnTouchListener collapseTouch() {
        return new View.OnTouchListener() {
            float downY;
            boolean closed;

            @Override
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downY = e.getRawY();
                        closed = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        if (!closed && downY - e.getRawY() > dp(20)) {
                            closed = true;
                            closePanel();
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        closePanel();
                        return true;
                }
                return true;
            }
        };
    }

    private View makeTile(final Tile tile) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(4), dp(8), dp(4), dp(8));
        box.setBackground(roundedRect(Color.WHITE, dp(12), Color.BLACK, 1));

        if (tile.id.startsWith("custom:")) {
            ImageView appIconView = new ImageView(this);
            String comp = tile.id.substring(7);
            String pkg = comp.contains("/") ? comp.split("/", 2)[0] : comp;
            Drawable d = appIcon(pkg);
            if (d != null) {
                d.setBounds(0, 0, dp(28), dp(28));
                appIconView.setImageDrawable(d);
            }
            box.addView(appIconView, new LinearLayout.LayoutParams(dp(28), dp(28)));
        } else {
            IconView icon = new IconView(this);
            icon.setIcon(tile.icon);
            icon.setIconColor(Color.BLACK);
            box.addView(icon, new LinearLayout.LayoutParams(dp(28), dp(28)));
        }

        TextView label = new TextView(this);
        label.setText(tile.label);
        label.setTextColor(Color.BLACK);
        label.setTextSize(12);
        label.setGravity(Gravity.CENTER);
        label.setSingleLine(true);
        box.addView(label);

        box.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                runTile(tile);
            }
        });
        box.setOnLongClickListener(new View.OnLongClickListener() {
            @Override public boolean onLongClick(View v) {
                runTileLong(tile);
                return true;
            }
        });
        return box;
    }

    private LinearLayout.LayoutParams tileLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMargins(dp(3), dp(3), dp(3), dp(3));
        return lp;
    }

    private View dividerRow() {
        View v = new View(this);
        v.setBackgroundColor(Color.parseColor("#30000000"));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1);
        lp.topMargin = dp(6);
        lp.bottomMargin = dp(6);
        v.setLayoutParams(lp);
        return v;
    }

    private void runTile(Tile tile) {
        if ("wifi".equals(tile.id)) {
            toggleWifi();
        } else if ("bt".equals(tile.id)) {
            toggleBluetooth();
        } else if ("settings".equals(tile.id)) {
            launchSettings();
        } else if (tile.id.startsWith("custom:")) {
            launchCustomActivity(tile.id.substring(7));
        }
    }

    private void runTileLong(Tile tile) {
        if ("wifi".equals(tile.id)) {
            launchWifiSettings();
        } else if ("bt".equals(tile.id)) {
            launchBtSettings();
        } else if ("settings".equals(tile.id)) {
            launchSettings();
        } else if ("brightness".equals(tile.id)) {
            launchSettings();
        }
    }

    private void toggleWifi() {
        try {
            if (wifiManager != null) {
                wifiManager.setWifiEnabled(!wifiManager.isWifiEnabled());
            }
        } catch (Exception e) {
            Toast.makeText(this, "切换 WiFi 失败", Toast.LENGTH_SHORT).show();
        }
        updateTray();
    }

    private void toggleBluetooth() {
        try {
            if (bluetoothAdapter != null) {
                if (bluetoothAdapter.isEnabled()) {
                    bluetoothAdapter.disable();
                } else {
                    bluetoothAdapter.enable();
                }
            }
        } catch (Exception e) {
            Toast.makeText(this, "切换蓝牙失败", Toast.LENGTH_SHORT).show();
        }
        updateTray();
    }

    private void toggleBrightness() {
        int cur = Settings.System.getInt(getContentResolver(),
                Settings.System.SCREEN_BRIGHTNESS, 128);
        int next = cur > 0 ? 0 : 128;
        setBrightness(next);
        if (brightnessBar != null) {
            brightnessBar.setProgress(next);
        }
    }

    private void launchSettings() {
        Intent i = new Intent(Settings.ACTION_SETTINGS);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(i);
        } catch (Exception ignored) {
        }
        closePanel();
    }

    private void launchWifiSettings() {
        Intent i = new Intent(Settings.ACTION_WIFI_SETTINGS);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(i);
        } catch (Exception ignored) {
        }
        closePanel();
    }

    private void launchBtSettings() {
        Intent i = new Intent(Settings.ACTION_BLUETOOTH_SETTINGS);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(i);
        } catch (Exception ignored) {
        }
        closePanel();
    }

    private void launchCustomActivity(String comp) {
        if (comp.length() == 0) {
            Toast.makeText(this, "未配置应用", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            Intent i;
            if (comp.contains("/")) {
                String[] parts = comp.split("/", 2);
                String pkg = parts[0];
                String cls = parts[1].startsWith(".")
                        ? pkg + parts[1] : parts[1];
                i = new Intent();
                i.setClassName(pkg, cls);
            } else {
                i = getPackageManager().getLaunchIntentForPackage(comp);
            }
            if (i == null) {
                Toast.makeText(this, "找不到应用", Toast.LENGTH_SHORT).show();
                return;
            }
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Exception e) {
            Toast.makeText(this, "启动失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
        closePanel();
    }

    private void refreshPanelIfOpen() {
        if (panelView != null) {
            closePanel();
            openPanel();
        }
    }

    private void setBrightness(int v) {
        try {
            if (!Settings.System.canWrite(this)) {
                Toast.makeText(this, "请先授予写入设置权限", Toast.LENGTH_SHORT).show();
                return;
            }
            Settings.System.putInt(getContentResolver(),
                    Settings.System.SCREEN_BRIGHTNESS_MODE,
                    Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
            Settings.System.putInt(getContentResolver(),
                    Settings.System.SCREEN_BRIGHTNESS, v);
        } catch (Exception e) {
            Toast.makeText(this, "背光设置失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void populateNotifications() {
        if (notifContainer == null) return;
        notifContainer.removeAllViews();

        NotificationListener nl = NotificationListener.instance;
        if (nl == null || !nl.isConnected()) {
            TextView hint = new TextView(this);
            hint.setText("未启用通知读取权限");
            hint.setTextColor(Color.DKGRAY);
            hint.setTextSize(14);
            hint.setPadding(0, dp(10), 0, dp(10));
            notifContainer.addView(hint);
            return;
        }

        List<StatusBarNotification> list = nl.getActive();
        if (list.isEmpty()) {
            TextView hint = new TextView(this);
            hint.setText("暂无通知");
            hint.setTextColor(Color.DKGRAY);
            hint.setTextSize(14);
            hint.setPadding(0, dp(10), 0, dp(10));
            notifContainer.addView(hint);
            return;
        }

        for (StatusBarNotification sbn : list) {
            notifContainer.addView(makeNotificationRow(sbn));
        }
    }

    private View makeNotificationRow(final StatusBarNotification sbn) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.HORIZONTAL);
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setPadding(dp(10), dp(8), dp(10), dp(8));
        item.setBackground(roundedRect(Color.WHITE, dp(8), Color.LTGRAY, 1));

        ImageView iconView = new ImageView(this);
        Drawable icon = appIcon(sbn.getPackageName());
        if (icon != null) {
            icon.setBounds(0, 0, dp(36), dp(36));
            iconView.setImageDrawable(icon);
        }
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(36), dp(36));
        ilp.rightMargin = dp(10);
        item.addView(iconView, ilp);

        LinearLayout textBox = new LinearLayout(this);
        textBox.setOrientation(LinearLayout.VERTICAL);

        TextView appName = new TextView(this);
        appName.setText(appLabel(sbn.getPackageName()));
        appName.setTextColor(Color.BLACK);
        appName.setTextSize(14);
        appName.setTypeface(Typeface.DEFAULT_BOLD);
        appName.setSingleLine(true);
        textBox.addView(appName);

        TextView content = new TextView(this);
        String title = "";
        String text = "";
        try {
            if (sbn.getNotification() != null && sbn.getNotification().extras != null) {
                CharSequence ct = sbn.getNotification().extras.getCharSequence(Notification.EXTRA_TITLE);
                CharSequence cx = sbn.getNotification().extras.getCharSequence(Notification.EXTRA_TEXT);
                if (ct != null) title = ct.toString();
                if (cx != null) text = cx.toString();
            }
        } catch (Exception ignored) {
        }
        String line = title;
        if (text.length() > 0) line = line.length() > 0 ? line + "  " + text : text;
        content.setText(line);
        content.setTextColor(Color.DKGRAY);
        content.setTextSize(13);
        content.setSingleLine(true);
        textBox.addView(content);

        item.addView(textBox, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        item.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                try {
                    PendingIntent pi = sbn.getNotification() != null
                            ? sbn.getNotification().contentIntent : null;
                    if (pi != null) {
                        pi.send();
                    } else {
                        Toast.makeText(StatusBarService.this, "该通知无跳转", Toast.LENGTH_SHORT).show();
                    }
                } catch (Exception e) {
                    Toast.makeText(StatusBarService.this, "打开失败", Toast.LENGTH_SHORT).show();
                }
                closePanel();
            }
        });

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(4);
        lp.bottomMargin = dp(4);
        item.setLayoutParams(lp);
        return item;
    }

    private Drawable appIcon(String pkg) {
        try {
            return getPackageManager().getApplicationIcon(pkg);
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    private String appLabel(String pkg) {
        try {
            ApplicationInfo ai = getPackageManager().getApplicationInfo(pkg, 0);
            CharSequence label = getPackageManager().getApplicationLabel(ai);
            return label != null ? label.toString() : pkg;
        } catch (PackageManager.NameNotFoundException e) {
            return pkg;
        }
    }

    private List<Tile> loadTiles() {
        List<Tile> tiles = new ArrayList<Tile>();
        String ssid = wifiName();
        String btName = btDeviceName();
        if (prefs.getBoolean("tile_wifi", true)) {
            tiles.add(new Tile("wifi", ssid != null ? ssid : "WiFi", "wifi"));
        }
        if (prefs.getBoolean("tile_bt", true)) {
            tiles.add(new Tile("bt", btName != null ? btName : "蓝牙", "bt"));
        }
        if (prefs.getBoolean("tile_settings", true)) {
            tiles.add(new Tile("settings", "设置", "settings"));
        }

        Set<String> customs = prefs.getStringSet("custom_activities", new HashSet<String>());
        Set<String> hidden = prefs.getStringSet("custom_quick_hidden", new HashSet<String>());
        for (String c : customs) {
            if (hidden.contains(c)) continue;
            String pkg = c.contains("/") ? c.split("/", 2)[0] : c;
            tiles.add(new Tile("custom:" + c, appLabel(pkg), "apps"));
        }
        return tiles;
    }

    // ---------------- receivers / state ----------------

    private void registerReceivers() {
        receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent != null && Intent.ACTION_CLOSE_SYSTEM_DIALOGS.equals(intent.getAction())) {
                    closePanel();
                    return;
                }
                updateClock();
                updateTray();
            }
        };
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_TIME_TICK);
        f.addAction(Intent.ACTION_BATTERY_CHANGED);
        f.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        f.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        f.addAction(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
        registerReceiver(receiver, f);
    }

    private int getBatteryLevel() {
        try {
            IntentFilter f = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            Intent b = registerReceiver(null, f);
            if (b == null) return -1;
            int level = b.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = b.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
            if (level < 0 || scale <= 0) return -1;
            return level * 100 / scale;
        } catch (Exception e) {
            return -1;
        }
    }

    private String wifiName() {
        try {
            if (wifiManager != null) {
                String s = wifiManager.getConnectionInfo().getSSID();
                if (s != null) {
                    s = s.replace("\"", "").trim();
                    if (s.length() > 0 && !"<unknown ssid>".equals(s)) {
                        return s;
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private boolean btEnabled() {
        try {
            return bluetoothAdapter != null && bluetoothAdapter.isEnabled();
        } catch (Exception e) {
            return false;
        }
    }
    private String btDeviceName() {
        try {
            if (bluetoothAdapter != null && bluetoothAdapter.isEnabled()) {
                Set<BluetoothDevice> bonded = bluetoothAdapter.getBondedDevices();
                if (bonded != null) {
                    for (BluetoothDevice d : bonded) {
                        if (btDeviceConnected(d)) {
                            String n = d.getName();
                            if (n != null && n.length() > 0) return n;
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private boolean btDeviceConnected(BluetoothDevice d) {
        try {
            java.lang.reflect.Method m = d.getClass().getMethod("isConnected");
            Object r = m.invoke(d);
            return r instanceof Boolean && ((Boolean) r).booleanValue();
        } catch (Exception e) {
            return false;
        }
    }

    public void onNotificationsChanged() {
        updateLeft();
        if (panelView != null) {
            populateNotifications();
        }
    }

    // ---------------- helpers ----------------

    private int getOverlayType() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_SYSTEM_ALERT;
    }

    private GradientDrawable roundedRect(int bg, float radiusDp, int strokeColor, int strokePx) {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(bg);
        gd.setCornerRadius(radiusDp);
        if (strokeColor != 0) {
            gd.setStroke(strokePx, strokeColor);
        }
        return gd;
    }

    private GradientDrawable panelBackground() {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(Color.WHITE);
        float r = dp(16);
        gd.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        gd.setStroke(1, Color.BLACK);
        return gd;
    }

    private Button makeSmallButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(13);
        b.setAllCaps(false);
        b.setTextColor(Color.BLACK);
        b.setMinimumHeight(dp(40));
        b.setBackground(roundedRect(Color.WHITE, dp(6), Color.BLACK, 1));
        return b;
    }

    private void removeView(View v) {
        if (v != null) {
            try {
                wm.removeView(v);
            } catch (Exception ignored) {
            }
        }
    }

    private int dp(int v) {
        return (int) (getResources().getDisplayMetrics().density * v + 0.5f);
    }

    private static class Tile {
        String id;
        String label;
        String icon;

        Tile(String id, String label, String icon) {
            this.id = id;
            this.label = label;
            this.icon = icon;
        }
    }
}

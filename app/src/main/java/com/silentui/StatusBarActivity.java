package com.silentui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class StatusBarActivity extends Activity {

    private static final String PREFS = "cfg";
    private static final String KEY_NAME = "name";
    private static final String KEY_CLOCK = "show_clock";
    private static final String KEY_WIFI = "show_wifi";
    private static final String KEY_BT = "show_bt";
    private static final String KEY_BATTERY = "show_battery";
    private static final String KEY_NOTIF = "show_notifications";
    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_TILE_WIFI = "tile_wifi";
    private static final String KEY_TILE_BT = "tile_bt";
    private static final String KEY_TILE_SETTINGS = "tile_settings";
    private static final String KEY_TILE_BRIGHTNESS = "tile_brightness";
    private static final String KEY_CUSTOM_ACTIVITIES = "custom_activities";
    private static final String KEY_CUSTOM_QUICK_HIDDEN = "custom_quick_hidden";
    private static final String KEY_SWIPE_LEFT = "swipe_left";
    private static final String KEY_SWIPE_RIGHT = "swipe_right";
    private static final String KEY_SWIPE_BRIGHTNESS = "swipe_brightness";
    private static final String KEY_BAR_MODE = "bar_mode";

    private static final String[] SWIPE_BASE_LABELS = {
            "打开快捷面板", "打开系统设置", "打开 WiFi 设置", "打开蓝牙设置", "无动作"
    };
    private static final String[] SWIPE_BASE_VALUES = {
            "panel", "settings", "wifi", "bt", "none"
    };
    private String[] swipeValues = new String[0];
    private String[] swipeLabels = new String[0];

    private SharedPreferences prefs;
    private EditText nameEdit;
    private CheckBox clockBox, wifiBox, btBox, batteryBox, notifBox;
    private CheckBox tileWifiBox, tileBtBox, tileSettingsBox, tileBrightnessBox;
    private CheckBox swipeBrightnessBox;
    private Button leftSwipeBtn, rightSwipeBtn;
    private String currentLeftSwipe = "panel";
    private String currentRightSwipe = "settings";
    private LinearLayout customList;
    private TextView status;
    private boolean loading = false;
    private Handler applyHandler = new Handler();
    private Runnable applyRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);
        root.setPadding(dp(12), dp(14), dp(12), dp(20));
        root.setFocusableInTouchMode(true);

        TextView title = new TextView(this);
        title.setText("自定义状态栏");
        title.setTextColor(Color.BLACK);
        title.setTextSize(22);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);

        status = new TextView(this);
        status.setTextColor(Color.DKGRAY);
        status.setTextSize(13);
        status.setPadding(0, dp(6), 0, dp(8));
        root.addView(status);

        TextView tip = new TextView(this);
        tip.setText("修改后立即保存并应用（无需手动保存）");
        tip.setTextColor(Color.GRAY);
        tip.setTextSize(12);
        tip.setPadding(0, 0, 0, dp(8));
        root.addView(tip);

        // ---- card 1: status bar content ----
        LinearLayout c1 = card();
        addCardTitle(c1, "状态栏显示内容");
        nameEdit = new EditText(this);
        nameEdit.setHint("左侧自定义名称（留空则不显示）");
        nameEdit.setTextColor(Color.BLACK);
        nameEdit.setHintTextColor(Color.GRAY);
        nameEdit.setSingleLine(true);
        nameEdit.setBackground(roundedStroke(Color.WHITE, dp(8), Color.BLACK, 1));
        nameEdit.setPadding(dp(10), dp(6), dp(10), dp(6));
        nameEdit.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
            }
            @Override public void afterTextChanged(Editable s) {
                if (!loading) scheduleApply();
            }
        });
        c1.addView(nameEdit, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        GridLayout g1 = grid(2);
        clockBox = makeCheck("显示时间");
        wifiBox = makeCheck("WiFi 图标");
        btBox = makeCheck("蓝牙图标");
        batteryBox = makeCheck("电量图标");
        notifBox = makeCheck("通知数量");
        g1.addView(clockBox, gridLp());
        g1.addView(wifiBox, gridLp());
        g1.addView(btBox, gridLp());
        g1.addView(batteryBox, gridLp());
        g1.addView(notifBox, gridLp());
        c1.addView(g1);
        root.addView(c1);

        // ---- card 2: swipe actions ----
        LinearLayout c2 = card();
        addCardTitle(c2, "状态栏左/右下滑动作");
        TextView swipeHint = new TextView(this);
        swipeHint.setText("点击状态栏为展开/收起；左、右下滑可分别指定动作");
        swipeHint.setTextColor(Color.GRAY);
        swipeHint.setTextSize(12);
        c2.addView(swipeHint);
        leftSwipeBtn = makePickerButton();
        rightSwipeBtn = makePickerButton();
        leftSwipeBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                showSwipePicker(true);
            }
        });
        rightSwipeBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                showSwipePicker(false);
            }
        });
        c2.addView(makePickerRow("左侧下滑", leftSwipeBtn));
        c2.addView(makePickerRow("右侧下滑", rightSwipeBtn));
        swipeBrightnessBox = makeCheck("左右滑动状态栏调整背光");
        c2.addView(swipeBrightnessBox);
        root.addView(c2);

        // ---- card 3: quick panel switches ----
        LinearLayout c3 = card();
        addCardTitle(c3, "快捷面板开关");
        TextView tileHint = new TextView(this);
        tileHint.setText("WiFi/蓝牙：点击切换，长按打开对应设置页");
        tileHint.setTextColor(Color.GRAY);
        tileHint.setTextSize(12);
        c3.addView(tileHint);
        GridLayout g3 = grid(2);
        tileWifiBox = makeCheck("WiFi 开关");
        tileBtBox = makeCheck("蓝牙开关");
        tileSettingsBox = makeCheck("设置入口");
        tileBrightnessBox = makeCheck("背光开关");
        g3.addView(tileWifiBox, gridLp());
        g3.addView(tileBtBox, gridLp());
        g3.addView(tileSettingsBox, gridLp());
        g3.addView(tileBrightnessBox, gridLp());
        c3.addView(g3);
        root.addView(c3);

        // ---- card 4: custom entries ----
        LinearLayout c4 = card();
        addCardTitle(c4, "自定义应用入口");
        customList = new LinearLayout(this);
        customList.setOrientation(LinearLayout.VERTICAL);
        c4.addView(customList);

        GridLayout g4 = grid(2);
        Button addApp = makeButton("添加应用入口");
        addApp.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                pickLauncherApp();
            }
        });
        Button addAct = makeButton("添加具体活动");
        addAct.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                pickAppForActivity();
            }
        });
        g4.addView(addApp, gridLp());
        g4.addView(addAct, gridLp());
        c4.addView(g4);
        root.addView(c4);

        // ---- card 5: run control ----
        LinearLayout c5 = card();
        addCardTitle(c5, "运行控制");
        GridLayout g5 = grid(2);
        g5.addView(makeButton("启动状态栏"), gridLp());
        g5.addView(makeButton("停止状态栏"), gridLp());
        Button startBtn = (Button) g5.getChildAt(0);
        Button stopBtn = (Button) g5.getChildAt(1);
        startBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                savePrefs();
                prefs.edit().putBoolean(KEY_ENABLED, true).apply();
                startService(new Intent(StatusBarActivity.this, StatusBarService.class));
                new DaemonManager(StatusBarActivity.this).writeConfig();
                setStatus("状态栏已启动");
            }
        });
        stopBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                prefs.edit().putBoolean(KEY_ENABLED, false).apply();
                stopService(new Intent(StatusBarActivity.this, StatusBarService.class));
                new DaemonManager(StatusBarActivity.this).writeConfig();
                setStatus("状态栏已停止");
            }
        });
        c5.addView(g5);

        // ---- card 5b: display mode ----
        LinearLayout c5b = card();
        addCardTitle(c5b, "状态栏显示模式");
        TextView modeHint = new TextView(this);
        modeHint.setText("固定位置：常驻顶部并设置 WM 留白；触摸显示：保持原大小，触摸/下拉时显示，超时自动变透明，保持 WM 全屏");
        modeHint.setTextColor(Color.GRAY);
        modeHint.setTextSize(12);
        c5b.addView(modeHint);
        Button fixedModeBtn = makeButton("固定位置");
        Button touchModeBtn = makeButton("触摸显示");
        fixedModeBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                setBarMode("fixed");
            }
        });
        touchModeBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                setBarMode("touch");
            }
        });
        GridLayout gm = grid(2);
        gm.addView(fixedModeBtn, gridLp());
        gm.addView(touchModeBtn, gridLp());
        c5b.addView(gm);

        LinearLayout hideRow = new LinearLayout(this);
        hideRow.setOrientation(LinearLayout.VERTICAL);
        TextView hideLabel = new TextView(this);
        hideLabel.setTextColor(Color.DKGRAY);
        hideLabel.setTextSize(14);
        hideLabel.setText("自动隐藏延迟（触摸显示模式）");
        hideRow.addView(hideLabel);
        final TextView hideDelayText = new TextView(this);
        hideDelayText.setTextColor(Color.BLACK);
        hideDelayText.setTextSize(15);
        int curSec = Math.max(1, prefs.getInt("bar_hide_sec", 2));
        hideDelayText.setText(curSec + " 秒");
        hideRow.addView(hideDelayText);
        SeekBar hideDelaySeek = new SeekBar(this);
        hideDelaySeek.setMax(9);
        hideDelaySeek.setProgress(curSec - 1);
        hideDelaySeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int p, boolean fromUser) {
                if (fromUser) {
                    int sec = p + 1;
                    hideDelayText.setText(sec + " 秒");
                    prefs.edit().putInt("bar_hide_sec", sec)
                            .putLong("bar_hide_ms", sec * 1000L).apply();
                    if (prefs.getBoolean(KEY_ENABLED, false)) {
                        Intent i = new Intent(StatusBarActivity.this, StatusBarService.class);
                        i.setAction(StatusBarService.ACTION_REFRESH);
                        startService(i);
                    }
                }
            }
            @Override public void onStartTrackingTouch(SeekBar sb) { }
            @Override public void onStopTrackingTouch(SeekBar sb) { }
        });
        hideRow.addView(hideDelaySeek);
        c5b.addView(hideRow);
        root.addView(c5b);

        // ---- card 6: permissions ----
        LinearLayout c6 = card();
        addCardTitle(c6, "权限");
        GridLayout g6 = grid(2);
        Button ovBtn = makeButton("悬浮窗权限");
        Button wsBtn = makeButton("写入设置权限");
        Button nlBtn = makeButton("通知读取权限");
        ovBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName())));
            }
        });
        wsBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivity(new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS,
                        Uri.parse("package:" + getPackageName())));
            }
        });
        nlBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
            }
        });
        g6.addView(ovBtn, gridLp());
        g6.addView(wsBtn, gridLp());
        g6.addView(nlBtn, gridLp());
        c6.addView(g6);
        root.addView(c6);

        loadPrefs();

        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        setContentView(scroll);
        root.requestFocus();
    }

    private void runRoot(final String cmd) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Process p = new ProcessBuilder("su", "-c", cmd)
                            .redirectErrorStream(true).start();
                    p.waitFor();
                } catch (Exception ignored) {
                }
            }
        }).start();
    }

    // ---------------- UI helpers ----------------

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(12), dp(10), dp(12), dp(12));
        card.setBackground(roundedStroke(Color.WHITE, dp(12), Color.BLACK, 1));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(8);
        lp.bottomMargin = dp(8);
        card.setLayoutParams(lp);
        return card;
    }

    private void addCardTitle(LinearLayout card, String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(Color.BLACK);
        tv.setTextSize(16);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setPadding(0, 0, 0, dp(6));
        card.addView(tv);
    }

    private GradientDrawable roundedStroke(int bg, float radiusDp, int strokeColor, int strokePx) {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(bg);
        gd.setCornerRadius(radiusDp);
        gd.setStroke(strokePx, strokeColor);
        return gd;
    }

    private GridLayout grid(int columns) {
        GridLayout g = new GridLayout(this);
        g.setColumnCount(columns);
        g.setPadding(0, dp(2), 0, 0);
        return g;
    }

    private GridLayout.LayoutParams gridLp() {
        GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
        lp.width = 0;
        lp.height = GridLayout.LayoutParams.WRAP_CONTENT;
        lp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        lp.setMargins(dp(3), dp(3), dp(3), dp(3));
        return lp;
    }

    private CheckBox makeCheck(String text) {
        CheckBox c = new CheckBox(this);
        c.setText(text);
        c.setTextColor(Color.BLACK);
        c.setTextSize(14);
        c.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton b, boolean checked) {
                if (!loading) applyConfigNow();
            }
        });
        return c;
    }

    private Button makeButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(13);
        b.setAllCaps(false);
        b.setTextColor(Color.BLACK);
        b.setMinimumHeight(dp(42));
        b.setPadding(dp(6), 0, dp(6), 0);
        b.setBackground(roundedStroke(Color.WHITE, dp(8), Color.BLACK, 1));
        return b;
    }

    private Button makePickerButton() {
        Button b = makeButton("请选择");
        b.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        b.setMinimumHeight(dp(42));
        return b;
    }

    private LinearLayout makePickerRow(String label, Button picker) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(4), 0, dp(4));
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextColor(Color.BLACK);
        tv.setTextSize(14);
        row.addView(tv, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(picker, new LinearLayout.LayoutParams(dp(200),
                LinearLayout.LayoutParams.WRAP_CONTENT));
        return row;
    }

    // ---------------- custom entry pickers ----------------

    private void pickLauncherApp() {
        Intent main = new Intent(Intent.ACTION_MAIN, null);
        main.addCategory(Intent.CATEGORY_LAUNCHER);
        final List<ResolveInfo> apps = getPackageManager().queryIntentActivities(main, 0);
        Collections.sort(apps, new Comparator<ResolveInfo>() {
            @Override public int compare(ResolveInfo a, ResolveInfo b) {
                return String.valueOf(a.loadLabel(getPackageManager()))
                        .compareTo(String.valueOf(b.loadLabel(getPackageManager())));
            }
        });

        final String[] labels = new String[apps.size()];
        final String[] comps = new String[apps.size()];
        for (int i = 0; i < apps.size(); i++) {
            ResolveInfo ri = apps.get(i);
            labels[i] = String.valueOf(ri.loadLabel(getPackageManager()));
            comps[i] = ri.activityInfo.packageName + "/" + ri.activityInfo.name;
        }
        new AlertDialog.Builder(this)
                .setTitle("选择应用")
                .setItems(labels, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        addCustom(comps[which]);
                    }
                })
                .show();
    }

    private void pickAppForActivity() {
        Intent main = new Intent(Intent.ACTION_MAIN, null);
        main.addCategory(Intent.CATEGORY_LAUNCHER);
        final List<ResolveInfo> apps = getPackageManager().queryIntentActivities(main, 0);
        Collections.sort(apps, new Comparator<ResolveInfo>() {
            @Override public int compare(ResolveInfo a, ResolveInfo b) {
                return String.valueOf(a.loadLabel(getPackageManager()))
                        .compareTo(String.valueOf(b.loadLabel(getPackageManager())));
            }
        });

        final String[] labels = new String[apps.size()];
        final String[] pkgs = new String[apps.size()];
        for (int i = 0; i < apps.size(); i++) {
            labels[i] = String.valueOf(apps.get(i).loadLabel(getPackageManager()));
            pkgs[i] = apps.get(i).activityInfo.packageName;
        }
        new AlertDialog.Builder(this)
                .setTitle("选择应用")
                .setItems(labels, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        pickActivityInPackage(pkgs[which]);
                    }
                })
                .show();
    }

    private void pickActivityInPackage(String pkg) {
        try {
            PackageInfo pi = getPackageManager().getPackageInfo(pkg, PackageManager.GET_ACTIVITIES);
            if (pi.activities == null || pi.activities.length == 0) {
                Toast.makeText(this, "该应用没有可用的活动", Toast.LENGTH_SHORT).show();
                return;
            }
            final List<ActivityInfo> acts = new ArrayList<ActivityInfo>();
            for (ActivityInfo ai : pi.activities) {
                if (ai.name != null) acts.add(ai);
            }
            Collections.sort(acts, new Comparator<ActivityInfo>() {
                @Override public int compare(ActivityInfo a, ActivityInfo b) {
                    return String.valueOf(a.loadLabel(getPackageManager()))
                            .compareTo(String.valueOf(b.loadLabel(getPackageManager())));
                }
            });
            final String[] labels = new String[acts.size()];
            final String[] comps = new String[acts.size()];
            for (int i = 0; i < acts.size(); i++) {
                String actLabel = String.valueOf(acts.get(i).loadLabel(getPackageManager()));
                comps[i] = pkg + "/" + acts.get(i).name;
                labels[i] = actLabel + "  ·  " + comps[i];
            }
            new AlertDialog.Builder(this)
                    .setTitle("选择活动")
                    .setItems(labels, new DialogInterface.OnClickListener() {
                        @Override public void onClick(DialogInterface dialog, int which) {
                            addCustom(comps[which]);
                        }
                    })
                    .show();
        } catch (PackageManager.NameNotFoundException e) {
            Toast.makeText(this, "找不到该应用", Toast.LENGTH_SHORT).show();
        }
    }

    private void addCustom(String component) {
        Set<String> set = new HashSet<String>(
                prefs.getStringSet(KEY_CUSTOM_ACTIVITIES, new HashSet<String>()));
        set.add(component);
        Set<String> hidden = new HashSet<String>(
                prefs.getStringSet(KEY_CUSTOM_QUICK_HIDDEN, new HashSet<String>()));
        hidden.remove(component);
        prefs.edit().putStringSet(KEY_CUSTOM_ACTIVITIES, set)
                .putStringSet(KEY_CUSTOM_QUICK_HIDDEN, hidden).apply();
        rebuildCustomList();
        rebuildSwipeOptions();
        applyConfigNow();
    }

    private void removeCustom(final String component) {
        Set<String> set = new HashSet<String>(
                prefs.getStringSet(KEY_CUSTOM_ACTIVITIES, new HashSet<String>()));
        set.remove(component);
        Set<String> hidden = new HashSet<String>(
                prefs.getStringSet(KEY_CUSTOM_QUICK_HIDDEN, new HashSet<String>()));
        hidden.remove(component);
        prefs.edit().putStringSet(KEY_CUSTOM_ACTIVITIES, set)
                .putStringSet(KEY_CUSTOM_QUICK_HIDDEN, hidden).apply();
        rebuildCustomList();
        rebuildSwipeOptions();
        applyConfigNow();
    }

    private void rebuildCustomList() {
        customList.removeAllViews();
        Set<String> set = prefs.getStringSet(KEY_CUSTOM_ACTIVITIES, new HashSet<String>());
        if (set.isEmpty()) {
            TextView none = new TextView(this);
            none.setText("（未添加自定义入口）");
            none.setTextColor(Color.GRAY);
            none.setTextSize(13);
            none.setPadding(0, dp(4), 0, dp(4));
            customList.addView(none);
            return;
        }

        List<String> sorted = new ArrayList<String>(set);
        Collections.sort(sorted);
        final Set<String> hidden = new HashSet<String>(
                prefs.getStringSet(KEY_CUSTOM_QUICK_HIDDEN, new HashSet<String>()));
        for (final String comp : sorted) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(6), dp(5), dp(6), dp(5));
            row.setBackground(roundedStroke(Color.WHITE, dp(8), Color.LTGRAY, 1));
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            rlp.setMargins(0, dp(3), 0, dp(3));
            row.setLayoutParams(rlp);

            String pkg = comp.contains("/") ? comp.split("/", 2)[0] : comp;
            ImageView icon = new ImageView(this);
            Drawable d = appIcon(pkg);
            if (d != null) {
                d.setBounds(0, 0, dp(28), dp(28));
                icon.setImageDrawable(d);
            }
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(28), dp(28));
            ilp.rightMargin = dp(6);
            row.addView(icon, ilp);

            LinearLayout nameCol = new LinearLayout(this);
            nameCol.setOrientation(LinearLayout.VERTICAL);
            TextView label = new TextView(this);
            label.setText(appLabel(pkg));
            label.setTextColor(Color.BLACK);
            label.setTextSize(13);
            label.setSingleLine(true);
            nameCol.addView(label);

            TextView actName = new TextView(this);
            actName.setText(comp);
            actName.setTextColor(Color.GRAY);
            actName.setTextSize(11);
            actName.setSingleLine(true);
            nameCol.addView(actName);

            row.addView(nameCol, new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            CheckBox showBox = new CheckBox(this);
            showBox.setText("快捷面板");
            showBox.setTextColor(Color.BLACK);
            showBox.setTextSize(12);
            showBox.setChecked(!hidden.contains(comp));
            showBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override public void onCheckedChanged(CompoundButton b, boolean checked) {
                    Set<String> h = new HashSet<String>(
                            prefs.getStringSet(KEY_CUSTOM_QUICK_HIDDEN, new HashSet<String>()));
                    if (checked) {
                        h.remove(comp);
                    } else {
                        h.add(comp);
                    }
                    prefs.edit().putStringSet(KEY_CUSTOM_QUICK_HIDDEN, h).apply();
                    applyConfigNow();
                }
            });
            row.addView(showBox);

            Button del = makeButton("删除");
            del.setMinimumHeight(dp(36));
            del.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    removeCustom(comp);
                }
            });
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            dlp.leftMargin = dp(6);
            del.setLayoutParams(dlp);
            row.addView(del);
            customList.addView(row);
        }
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

    // ---------------- swipe options ----------------

    private void rebuildSwipeOptions() {
        List<String> vals = new ArrayList<String>();
        List<String> labels = new ArrayList<String>();
        for (String v : SWIPE_BASE_VALUES) vals.add(v);
        for (String l : SWIPE_BASE_LABELS) labels.add(l);

        Set<String> customs = prefs.getStringSet(KEY_CUSTOM_ACTIVITIES, new HashSet<String>());
        List<String> sorted = new ArrayList<String>(customs);
        Collections.sort(sorted);
        for (String comp : sorted) {
            String pkg = comp.contains("/") ? comp.split("/", 2)[0] : comp;
            vals.add("custom:" + comp);
            labels.add(appLabel(pkg) + "  ·  " + comp);
        }

        swipeValues = vals.toArray(new String[0]);
        swipeLabels = labels.toArray(new String[0]);

        currentLeftSwipe = prefs.getString(KEY_SWIPE_LEFT, "panel");
        currentRightSwipe = prefs.getString(KEY_SWIPE_RIGHT, "settings");
        if (leftSwipeBtn != null) leftSwipeBtn.setText(labelForValue(currentLeftSwipe));
        if (rightSwipeBtn != null) rightSwipeBtn.setText(labelForValue(currentRightSwipe));
    }

    private String labelForValue(String value) {
        for (int i = 0; i < swipeValues.length; i++) {
            if (swipeValues[i].equals(value)) return swipeLabels[i];
        }
        return "请选择";
    }

    private void showSwipePicker(final boolean left) {
        final String[] vals = swipeValues;
        final String[] labels = swipeLabels;
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setTitle(left ? "左侧下滑动作" : "右侧下滑动作");
        b.setItems(labels, new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface dialog, int which) {
                if (left) {
                    currentLeftSwipe = vals[which];
                    leftSwipeBtn.setText(labels[which]);
                } else {
                    currentRightSwipe = vals[which];
                    rightSwipeBtn.setText(labels[which]);
                }
                applyConfigNow();
            }
        });
        AlertDialog d = b.create();
        android.view.Window w = d.getWindow();
        if (w != null) {
            w.setGravity(Gravity.BOTTOM);
            w.setLayout(WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.WRAP_CONTENT);
        }
        d.show();
    }

    // ---------------- prefs ----------------

    private void setBarMode(String mode) {
        prefs.edit().putString(KEY_BAR_MODE, mode).apply();
        Toast.makeText(this,
                "fixed".equals(mode) ? "已切换为固定位置模式（WM 留白）" : "已切换为触摸显示模式（WM 全屏）",
                Toast.LENGTH_SHORT).show();
        if (prefs.getBoolean(KEY_ENABLED, false)) {
            Intent i = new Intent(StatusBarActivity.this, StatusBarService.class);
            i.setAction(StatusBarService.ACTION_REFRESH);
            startService(i);
        }
    }

    private void applyConfigNow() {
        savePrefs();
        if (prefs.getBoolean(KEY_ENABLED, false)) {
            Intent i = new Intent(StatusBarActivity.this, StatusBarService.class);
            i.setAction(StatusBarService.ACTION_REFRESH);
            startService(i);
        }
    }

    private void scheduleApply() {
        if (applyRunnable == null) {
            applyRunnable = new Runnable() {
                @Override public void run() {
                    applyConfigNow();
                }
            };
        }
        applyHandler.removeCallbacks(applyRunnable);
        applyHandler.postDelayed(applyRunnable, 400);
    }

    private void savePrefs() {
        prefs.edit()
                .putString(KEY_NAME, nameEdit.getText().toString().trim())
                .putBoolean(KEY_CLOCK, clockBox.isChecked())
                .putBoolean(KEY_WIFI, wifiBox.isChecked())
                .putBoolean(KEY_BT, btBox.isChecked())
                .putBoolean(KEY_BATTERY, batteryBox.isChecked())
                .putBoolean(KEY_NOTIF, notifBox.isChecked())
                .putBoolean(KEY_TILE_WIFI, tileWifiBox.isChecked())
                .putBoolean(KEY_TILE_BT, tileBtBox.isChecked())
                .putBoolean(KEY_TILE_SETTINGS, tileSettingsBox.isChecked())
                .putBoolean(KEY_TILE_BRIGHTNESS, tileBrightnessBox.isChecked())
                .putString(KEY_SWIPE_LEFT, currentLeftSwipe)
                .putString(KEY_SWIPE_RIGHT, currentRightSwipe)
                .putBoolean(KEY_SWIPE_BRIGHTNESS, swipeBrightnessBox.isChecked())
                .apply();
    }

    private void loadPrefs() {
        loading = true;
        nameEdit.setText(prefs.getString(KEY_NAME, ""));
        clockBox.setChecked(prefs.getBoolean(KEY_CLOCK, true));
        wifiBox.setChecked(prefs.getBoolean(KEY_WIFI, true));
        btBox.setChecked(prefs.getBoolean(KEY_BT, true));
        batteryBox.setChecked(prefs.getBoolean(KEY_BATTERY, true));
        notifBox.setChecked(prefs.getBoolean(KEY_NOTIF, true));

        tileWifiBox.setChecked(prefs.getBoolean(KEY_TILE_WIFI, true));
        tileBtBox.setChecked(prefs.getBoolean(KEY_TILE_BT, true));
        tileSettingsBox.setChecked(prefs.getBoolean(KEY_TILE_SETTINGS, true));
        tileBrightnessBox.setChecked(prefs.getBoolean(KEY_TILE_BRIGHTNESS, true));
        swipeBrightnessBox.setChecked(prefs.getBoolean(KEY_SWIPE_BRIGHTNESS, false));

        rebuildSwipeOptions();
        rebuildCustomList();
        setStatus(prefs.getBoolean(KEY_ENABLED, false) ? "状态栏运行中" : "状态栏未启动");
        loading = false;
    }

    private void setStatus(String s) {
        if (status != null) status.setText(s);
    }

    private int dp(int v) {
        return (int) (getResources().getDisplayMetrics().density * v + 0.5f);
    }
}

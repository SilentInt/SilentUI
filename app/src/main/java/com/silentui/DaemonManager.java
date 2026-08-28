package com.silentui;

import android.content.Context;
import android.content.Intent;
import java.io.File;
import java.io.FileOutputStream;

/**
 * 管理系统守护进程的安装 / 启动 / 停止 / 状态 / 日志。
 * 守护进程 = app_process 加载本 APK 中的 GestureDaemon，以 root 独立运行。
 */
public final class DaemonManager {

    public static final String PACKAGE = "com.silentui";
    public static final String DAEMON_CLASS = "com.silentui.GestureDaemon";
    public static final String CONFIG_PATH = "/data/local/tmp/SilentUI.conf";
    public static final String LOG_PATH = "/data/local/tmp/SilentUId.log";
    public static final String PID_PATH = "/data/local/tmp/SilentUId.pid";
    public static final String BOOT_SCRIPT = "/data/adb/service.d/SilentUI.sh";

    private final Context ctx;

    public DaemonManager(Context ctx) {
        this.ctx = ctx.getApplicationContext();
    }

    /** 写配置到 /data/local/tmp/SilentUI.conf（先写 App 私有目录，再 root cp）。 */
    public void writeConfig() {
        File local = writeLocal("SilentUI.conf", buildConfig());
        if (local != null) {
            RootShell.send("cp " + q(local.getAbsolutePath()) + " " + q(CONFIG_PATH)
                    + " && chmod 644 " + q(CONFIG_PATH));
        }
    }

    /** 安装开机自启脚本（Magisk service.d）。 */
    public void installBootScript() {
        File local = writeLocal("SilentUI.sh", buildBootScript());
        if (local != null) {
            RootShell.send("mkdir -p /data/adb/service.d && cp " + q(local.getAbsolutePath())
                    + " " + q(BOOT_SCRIPT) + " && chmod 755 " + q(BOOT_SCRIPT));
        }
    }

    /** 立即启动守护进程与状态栏服务（先停旧实例避免重复，并写入开机自启脚本）。 */
    public void start() {
        writeConfig();
        stop();
        String apk = ctx.getPackageCodePath();
        String cmd = "CLASSPATH=" + q(apk) + " /system/bin/app_process /system/bin "
                + DAEMON_CLASS + " " + q(CONFIG_PATH)
                + " >> " + q(LOG_PATH) + " 2>&1 & echo $! > " + q(PID_PATH);
        RootShell.send(cmd);
        installBootScript();
        startStatusBar();
        LogBus.log("已请求启动守护进程与状态栏");
    }

    /** 立即启动状态栏服务（应用进程内直接 startService，无需 root）。 */
    private void startStatusBar() {
        try {
            Intent i = new Intent(ctx, StatusBarService.class);
            ctx.startService(i);
        } catch (Throwable t) {
            LogBus.log("启动状态栏服务失败: " + t);
        }
    }

    /** 停止守护进程。 */
    public void stop() {
        RootShell.send("kill $(cat " + q(PID_PATH) + ") 2>/dev/null; rm -f " + q(PID_PATH)
                + "; pkill -f " + DAEMON_CLASS + " 2>/dev/null");
        LogBus.log("已请求停止守护进程");
    }

    /** 守护进程是否在运行（按 pid 文件探测）。 */
    public boolean isRunning() {
        String out = RootShell.exec("kill -0 $(cat " + q(PID_PATH) + ") 2>/dev/null && echo RUNNING || echo DEAD", 3000);
        return out != null && out.contains("RUNNING");
    }

    /** 读守护日志（最近 200 行）。 */
    public String readLog() {
        String out = RootShell.exec("tail -n 200 " + q(LOG_PATH) + " 2>/dev/null", 4000);
        return out == null ? "" : out;
    }

    private String buildConfig() {
        StringBuilder sb = new StringBuilder();
        sb.append("enabled=").append(Prefs.enabled() ? 1 : 0).append('\n');
        sb.append("device=").append(Prefs.device() == null ? "" : Prefs.device()).append('\n');
        sb.append("struct=").append(Prefs.structSize()).append('\n');
        sb.append("max_x=").append(Prefs.touchMaxX()).append('\n');
        sb.append("max_y=").append(Prefs.touchMaxY()).append('\n');
        sb.append("edge_pct=").append(Prefs.edgePct()).append('\n');
        sb.append("swipe_pct=").append(Prefs.swipePct()).append('\n');
        sb.append("hold_ms=").append(Prefs.holdMs()).append('\n');
        sb.append("hold_slop_pct=").append(Prefs.holdSlopPct()).append('\n');
        sb.append("left=").append(Prefs.action(Prefs.G_LEFT_EDGE)).append('\n');
        sb.append("right=").append(Prefs.action(Prefs.G_RIGHT_EDGE)).append('\n');
        sb.append("bottom=").append(Prefs.action(Prefs.G_BOTTOM_UP)).append('\n');
        sb.append("bottom_hold=").append(Prefs.action(Prefs.G_BOTTOM_HOLD)).append('\n');
        sb.append("status_bar=").append(statusBarEnabled() ? 1 : 0).append('\n');
        return sb.toString();
    }

    private boolean statusBarEnabled() {
        return ctx.getSharedPreferences("cfg", Context.MODE_PRIVATE)
                .getBoolean("enabled", false);
    }

    private String buildBootScript() {
        // 用文件系统 glob 找 APK，避免开机早期 pm(包管理服务) 尚未就绪导致失败。
        // 不在脚本里 sleep；daemon 起来后由看门狗立即授权并拉起状态栏，未就绪则持续重试。
        return "#!/system/bin/sh\n"
                + "# SilentUI daemon (Magisk service.d)\n"
                + "(\n"
                + "  APK=$(ls /data/app/" + PACKAGE + "-*/base.apk 2>/dev/null | tail -1)\n"
                + "  [ -z \"$APK\" ] && exit 0\n"
                + "  kill $(cat " + PID_PATH + " 2>/dev/null) 2>/dev/null\n"
                + "  CLASSPATH=\"$APK\" nohup /system/bin/app_process /system/bin " + DAEMON_CLASS + " " + CONFIG_PATH
                + " >> " + LOG_PATH + " 2>&1 &\n"
                + "  echo $! > " + PID_PATH + "\n"
                + ") &\n";
    }

    private File writeLocal(String name, String content) {
        try {
            File dir = ctx.getFilesDir();
            File f = new File(dir, name);
            FileOutputStream os = new FileOutputStream(f);
            os.write(content.getBytes("UTF-8"));
            os.close();
            return f;
        } catch (Throwable t) {
            LogBus.log("写本地文件失败: " + t);
            return null;
        }
    }

    private static String q(String s) {
        return "'" + s.replace("'", "'\\''") + "'";
    }
}

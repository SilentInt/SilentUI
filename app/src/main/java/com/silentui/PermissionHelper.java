package com.silentui;

import android.content.Context;

/**
 * 有 root 时的一键授权与系统默认状态栏隐藏。
 */
public final class PermissionHelper {

    private PermissionHelper() {}

    /** 用 root 授予 SilentUI 所需的运行时权限和 appops（悬浮窗 / 写入设置 / 通知读取 / 蓝牙）。 */
    public static boolean rootGrantAll(Context ctx) {
        String pkg = ctx.getPackageName();
        String listener = pkg + "/" + pkg + ".NotificationListener";
        String cmds =
                "pm grant " + pkg + " android.permission.BLUETOOTH_CONNECT 2>/dev/null\n"
                + "pm grant " + pkg + " android.permission.POST_NOTIFICATIONS 2>/dev/null\n"
                + "appops set " + pkg + " SYSTEM_ALERT_WINDOW allow\n"
                + "appops set " + pkg + " WRITE_SETTINGS allow\n"
                + "settings put secure enabled_notification_listeners " + listener + "\n"
                + "echo ROOT_GRANT_DONE";
        String out = RootShell.exec(cmds, 10000);
        return out != null && out.contains("ROOT_GRANT_DONE");
    }

    /** 检查 SilentUI 所需权限是否已全部就绪。 */
    public static boolean permissionsComplete(Context ctx) {
        String pkg = ctx.getPackageName();
        String cmds =
                "appops get " + pkg + " SYSTEM_ALERT_WINDOW | grep -q allow"
                + " && appops get " + pkg + " WRITE_SETTINGS | grep -q allow"
                + " && settings get secure enabled_notification_listeners | grep -q " + pkg
                + " && echo PERM_COMPLETE";
        String out = RootShell.exec(cmds, 8000);
        return out != null && out.contains("PERM_COMPLETE");
    }

    /** 先检查，缺失则 root 一键授权，再复查；返回权限是否已完整。 */
    public static boolean ensurePermissions(Context ctx) {
        if (permissionsComplete(ctx)) {
            return true;
        }
        rootGrantAll(ctx);
        return permissionsComplete(ctx);
    }

    /** 隐藏系统默认状态栏与导航栏（policy_control immersive.full=*）。 */
    public static void hideSystemStatusBar() {
        RootShell.send("settings put global policy_control immersive.full=*");
    }

    /** 恢复系统默认状态栏与导航栏。 */
    public static void restoreSystemStatusBar() {
        RootShell.send("settings delete global policy_control");
    }
}

package com.quietweibo.app;

import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.util.Log;

/**
 * 桌面图标显隐控制。
 *
 * <p>桌面入口由 activity-alias {@code LauncherEntry} 承载；隐藏图标只禁用 alias，
 * MainActivity 仍保留 {@code de.robv.android.xposed.category.MODULE_SETTINGS}
 * 入口，因此隐藏后仍可从 LSPosed 管理器打开本页恢复。
 *
 * <p>系统组件状态是唯一真相源；本地 prefs 只在读取组件状态失败时兜底。
 */
public final class LauncherIcon {

    private static final String TAG = "QuietWeibo";
    private static final String PREFS = "launcher_icon";
    private static final String KEY_HIDDEN = "hidden";

    /** 与 AndroidManifest.xml 中 activity-alias 的 name 一致（namespace 前缀）。 */
    static final String ALIAS_CLASS = "com.quietweibo.app.LauncherEntry";

    private LauncherIcon() {
    }

    static ComponentName alias(Context context) {
        return new ComponentName(context.getPackageName(), ALIAS_CLASS);
    }

    /** 组件状态 → 是否隐藏；null 表示未知。 */
    static Boolean isHiddenState(int state) {
        switch (state) {
            case PackageManager.COMPONENT_ENABLED_STATE_DISABLED:
            case PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER:
            case PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED:
                return Boolean.TRUE;
            case PackageManager.COMPONENT_ENABLED_STATE_ENABLED:
            case PackageManager.COMPONENT_ENABLED_STATE_DEFAULT:
                return Boolean.FALSE;
            default:
                return null;
        }
    }

    public static boolean isHidden(Context context) {
        try {
            int state = context.getPackageManager().getComponentEnabledSetting(alias(context));
            Boolean hidden = isHiddenState(state);
            if (hidden != null) {
                if (prefs(context).getBoolean(KEY_HIDDEN, false) != hidden) {
                    prefs(context).edit().putBoolean(KEY_HIDDEN, hidden).apply();
                }
                return hidden;
            }
        } catch (Throwable t) {
            Log.w(TAG, "read launcher alias state failed", t);
        }
        return prefs(context).getBoolean(KEY_HIDDEN, false);
    }

    /**
     * 切换桌面图标显隐。
     *
     * @return false 表示系统拒绝写入，调用方应回滚 UI
     */
    public static boolean setHidden(Context context, boolean hidden) {
        int state = hidden
                ? PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                : PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
        boolean ok;
        try {
            context.getPackageManager().setComponentEnabledSetting(
                    alias(context), state, PackageManager.DONT_KILL_APP);
            ok = true;
        } catch (Throwable t) {
            Log.w(TAG, "set launcher alias state failed", t);
            ok = false;
        }
        if (ok) {
            prefs(context).edit().putBoolean(KEY_HIDDEN, hidden).apply();
        }
        return ok;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}

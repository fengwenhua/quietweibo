package com.quietweibo.app;

import android.app.Activity;
import android.util.Log;

import com.quietweibo.app.hooks.Hooks;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.libxposed.api.XposedModule;

/**
 * 延迟安装 hook。
 *
 * <p>微博 16.10.3 的 base.apk 没有整体加固：onPackageReady 时应用 ClassLoader
 * 已经能加载 {@code com.sina.weibo.*} 的全部 DEX，正常情况下立即安装成功。
 *
 * <p>保留延迟安装是为了兼容后续版本可能出现的壳/插件化：
 * <ul>
 *   <li>先用 onPackageReady 给的 ClassLoader 立即试一次；</li>
 *   <li>再用 100ms 轮询 {@code ActivityThread.mActivities} 中当前 Activity 的
 *       ClassLoader 和 TCCL，最多 30 秒；</li>
 *   <li>用 WeiboApplication / SplashActivity / RefreshService 做探针，三者任一可加载
 *       即认为真实 DEX 就绪；</li>
 *   <li>只安装一次，失败回滚标记，下一轮轮询可重试。</li>
 * </ul>
 */
final class DeferredInstall {

    private static final String TAG = "QuietWeibo";

    private static final String[] PROBES = {
            "com.sina.weibo.WeiboApplication",
            "com.sina.weibo.SplashActivity",
            "com.sina.weibo.mobileads.controller.RefreshService",
    };

    private static final AtomicBoolean sArmed = new AtomicBoolean();
    private static final AtomicBoolean sInstalled = new AtomicBoolean();

    private DeferredInstall() {
    }

    static void start(XposedModule module, ClassLoader cl) {
        if (!sArmed.compareAndSet(false, true)) {
            return;
        }
        module.log(Log.INFO, TAG, "deferred install armed, loader=" + cl);
        startPolling(module, cl);
        // 立即试一次：未加固的微博在 onPackageReady 就能解析到全部业务类。
        tryInstall(module, cl, "package-ready");
    }

    private static void startPolling(XposedModule module, ClassLoader baseCl) {
        Thread thread = new Thread(() -> {
            for (int i = 0; i < 300 && !sInstalled.get(); i++) {
                tryInstall(module, baseCl, "poll-base");
                if (!sInstalled.get()) {
                    tryInstall(module, activityLoader(), "poll-activity");
                }
                if (!sInstalled.get()) {
                    tryInstall(module, Thread.currentThread().getContextClassLoader(),
                            "poll-tccl");
                }
                if (sInstalled.get()) {
                    return;
                }
                try {
                    Thread.sleep(100L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
            if (!sInstalled.get()) {
                module.log(Log.WARN, TAG, "deferred install timeout: payload classes never visible");
            }
        }, "QuietWeibo-deferred");
        thread.setDaemon(true);
        thread.start();
    }

    private static void tryInstall(XposedModule module, ClassLoader cl, String source) {
        if (cl == null || sInstalled.get()) {
            return;
        }
        Class<?> probe = probe(cl);
        if (probe == null) {
            return;
        }
        ClassLoader installCl = probe.getClassLoader() != null ? probe.getClassLoader() : cl;
        if (!sInstalled.compareAndSet(false, true)) {
            return;
        }
        module.log(Log.INFO, TAG, "payload visible via " + probe.getName()
                + " (" + source + "), installing hooks with " + installCl);
        try {
            Hooks.installAll(module, installCl, true);
            module.log(Log.INFO, TAG, "hook installation finished");
        } catch (Throwable t) {
            // 回滚，下一轮轮询可重试。
            sInstalled.set(false);
            module.log(Log.ERROR, TAG, "hook installation failed, will retry", t);
        }
    }

    private static Class<?> probe(ClassLoader cl) {
        for (String name : PROBES) {
            try {
                return Class.forName(name, false, cl);
            } catch (Throwable ignored) {
                // 继续试下一个探针
            }
        }
        return null;
    }

    /** 从 ActivityThread.mActivities 拿当前 Activity 的 ClassLoader。 */
    private static ClassLoader activityLoader() {
        try {
            Class<?> at = Class.forName("android.app.ActivityThread");
            Object thread = at.getMethod("currentActivityThread").invoke(null);
            Field field = at.getDeclaredField("mActivities");
            field.setAccessible(true);
            Object raw = field.get(thread);
            if (!(raw instanceof Map)) {
                return null;
            }
            for (Object record : ((Map<?, ?>) raw).values()) {
                if (record == null) {
                    continue;
                }
                Field af = record.getClass().getDeclaredField("activity");
                af.setAccessible(true);
                Object activity = af.get(record);
                if (activity instanceof Activity) {
                    ClassLoader cl = ((Activity) activity).getClassLoader();
                    if (cl != null) {
                        return cl;
                    }
                }
            }
        } catch (Throwable ignored) {
            // 页面还没创建时正常失败，下一轮再试。
        }
        return null;
    }
}

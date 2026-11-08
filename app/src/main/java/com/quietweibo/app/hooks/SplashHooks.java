package com.quietweibo.app.hooks;

import android.app.Activity;

import com.quietweibo.app.Feature;
import com.quietweibo.app.HookState;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/**
 * AD-01 冷启动开屏广告 / AD-02 热启动（回到前台）开屏广告。
 *
 * <p>微博 16.10.3 的开屏链路（classes17/16/8）：
 * <ul>
 *   <li>{@code com.sina.weibo.startup.actor.a.b}（BizSplashActor）在 {@code j()}
 *       里注册广告位并调用 {@code b(activity)} 创建 {@code FlashAd}，随后
 *       {@code loadAdWithLoadManager}；</li>
 *   <li>广告加载成功/失败都会回调 {@code AdListener.onReceiveAd} /
 *       {@code onFailedToReceiveAd}，两个回调最终都调 {@code BizSplashActor.r()}
 *       真正进入主页；</li>
 *   <li>热启动由 {@code com.sina.weibo.utils.cd.a(boolean, String)} 触发，
 *       内部通过 {@code loadAndShowAd} 直接展示实时广告。</li>
 * </ul>
 *
 * <p><b>关键：不能直接跳过 BizSplashActor#b。</b>不创建 FlashAd 就没有任何
 * 广告回调，启动流程会永远等不到 {@code r()}，表现为一直卡在启动页
 * （真机已验证）。正确做法是让 FlashAd 正常创建，在
 * {@code loadAdWithLoadManager} 里不发请求、直接回调
 * {@code onFailedToReceiveAd(NO_FILL)}，让 BizSplashActor 走官方失败分支。
 */
final class SplashHooks {

    private static final String FLASH_AD = "com.sina.weibo.mobileads.view.FlashAd";
    private static final String SPLASH_UTILS = "com.sina.weibo.utils.cd";
    private static final String AD_ERROR = "com.sina.weibo.mobileads.model.AdRequest$ErrorCode";

    private SplashHooks() {
    }

    static void installColdSplash(XposedModule module, ClassLoader cl) {
        Feature f = Feature.SPLASH;
        try {
            Class<?> flashAd = HookKit.findClass(module, f, cl, FLASH_AD);
            if (flashAd == null) {
                return;
            }
            hookFlashClass(module, f, cl, flashAd);
            HookState.note(f, "冷启动开屏：loadAdWithLoadManager 失败回调 + loadAndShowAd 跳过");
        } catch (Throwable t) {
            HookKit.error(module, f.id + " install aborted", t);
            HookState.note(f, "安装异常");
        }
    }

    /** 对任意 FlashAd 类（含跨版本重命名后的类）安装开屏拦截。 */
    static void hookFlashClass(XposedModule module, Feature f, ClassLoader cl, Class<?> flashAd) {
        // 核心：不求广告，主动回调失败，让启动流程继续。
        Method load = HookKit.findByNameAndArity(flashAd, "loadAdWithLoadManager", 1);
        HookKit.install(module, f, "FlashAd#loadAdWithLoadManager", load,
                failSplashLoad(module, f, cl, flashAd));

        // 其它直接 loadAndShowAd 的开屏路径（热启动实时广告等）直接不展示。
        Method loadAndShow = HookKit.findByNameAndArity(flashAd, "loadAndShowAd", 2);
        HookKit.install(module, f, "FlashAd#loadAndShowAd", loadAndShow,
                HookKit.skipVoid(module, f, "FlashAd#loadAndShowAd skipped"));
    }

    /**
     * 不发广告请求，直接回调 FlashAd 的 AdListener.onFailedToReceiveAd。
     * 监听器缺失或回调失败时退回原逻辑，避免把启动页卡死。
     */
    private static XposedInterface.Hooker failSplashLoad(XposedModule module, Feature f,
                                                         ClassLoader cl, Class<?> flashAd) {
        return chain -> {
            try {
                Object ad = chain.getThisObject();
                if (notifyFailed(ad, cl)) {
                    HookKit.hit(module, f, "FlashAd#loadAdWithLoadManager blocked, onFailedToReceiveAd");
                    return false;
                }
                HookKit.warn(module, f.id + " listener unavailable, proceed real load");
            } catch (Throwable t) {
                HookKit.error(module, f.id + " fail-fast load error", t);
            }
            return chain.proceed();
        };
    }

    private static boolean notifyFailed(Object ad, ClassLoader cl) {
        if (ad == null) {
            return false;
        }
        try {
            Method getListener = ad.getClass().getMethod("getListener");
            Object listener = getListener.invoke(ad);
            if (listener == null) {
                return false;
            }
            Object error = errorCode(cl);
            for (Method m : listener.getClass().getMethods()) {
                if (!"onFailedToReceiveAd".equals(m.getName())
                        || m.getParameterTypes().length != 2) {
                    continue;
                }
                m.setAccessible(true);
                m.invoke(listener, ad, error);
                return true;
            }
        } catch (Throwable ignored) {
            // 交给调用方退回原逻辑。
        }
        return false;
    }

    private static Object errorCode(ClassLoader cl) {
        try {
            Class<?> errorClass = Class.forName(AD_ERROR, false, cl);
            Field field = errorClass.getField("NO_FILL");
            return field.get(null);
        } catch (Throwable t) {
            return null;
        }
    }

    static void installHotSplash(XposedModule module, ClassLoader cl) {
        Feature f = Feature.HOT_SPLASH;
        try {
            Class<?> cls = HookKit.findClass(module, f, cl, SPLASH_UTILS);
            if (cls == null) {
                return;
            }
            // 回到前台时的开屏判定与实时广告直接展示。
            Method hot = HookKit.findMethodQuiet(cls, "a", boolean.class, String.class);
            HookKit.install(module, f, "cd#a(boolean,String)", hot,
                    HookKit.skipVoid(module, f, "cd#a(boolean,String) skipped"));

            // 后台预加载两个开屏广告位。
            Method preloadAll = HookKit.findMethodQuiet(cls, "b", android.content.Context.class);
            HookKit.install(module, f, "cd#b(Context)", preloadAll,
                    HookKit.skipVoid(module, f, "cd#b(Context) skipped"));

            Method register = HookKit.findMethodQuiet(cls, "c", android.content.Context.class);
            HookKit.install(module, f, "cd#c(Context)", register,
                    HookKit.skipVoid(module, f, "cd#c(Context) skipped"));

            HookState.note(f, "热启动开屏：cd#a(boolean,String) + cd#b/c(Context)");
        } catch (Throwable t) {
            HookKit.error(module, f.id + " install aborted", t);
            HookState.note(f, "安装异常");
        }
    }
}

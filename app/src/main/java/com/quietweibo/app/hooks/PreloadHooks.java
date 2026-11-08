package com.quietweibo.app.hooks;

import com.quietweibo.app.Feature;
import com.quietweibo.app.HookState;

import java.lang.reflect.Method;

import io.github.libxposed.api.XposedModule;

/**
 * AD-13 广告预加载 / 广告策略刷新断源。
 *
 * <p>{@code com.sina.weibo.mobileads.controller.RefreshService#startFetchData()}
 * 是所有广告配置/物料拉取的统一出口：{@code reload()}、
 * {@code reloadAutoCheck()}、{@code registerAd()} 最终都会走到这里。
 * 跳过它只会让广告配置保持旧值，不影响正文、图片、视频等业务网络请求。
 */
final class PreloadHooks {

    private PreloadHooks() {
    }

    static void install(XposedModule module, ClassLoader cl) {
        Feature f = Feature.AD_PRELOAD;
        try {
            Class<?> cls = HookKit.findClass(module, f, cl,
                    "com.sina.weibo.mobileads.controller.RefreshService");
            if (cls == null) {
                return;
            }
            Method start = HookKit.findMethodQuiet(cls, "startFetchData");
            HookKit.install(module, f, "RefreshService#startFetchData", start,
                    HookKit.skipVoid(module, f, "RefreshService#startFetchData skipped"));
            HookState.note(f, "广告预加载：RefreshService#startFetchData 不执行");
        } catch (Throwable t) {
            HookKit.error(module, f.id + " install aborted", t);
            HookState.note(f, "安装异常");
        }
    }
}

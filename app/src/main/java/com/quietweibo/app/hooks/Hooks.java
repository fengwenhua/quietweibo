package com.quietweibo.app.hooks;

import android.util.Log;

import com.quietweibo.app.Config;
import com.quietweibo.app.Feature;
import com.quietweibo.app.HookState;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Set;

import io.github.libxposed.api.XposedModule;

/**
 * 统一在 onPackageReady（已确认包名 com.sina.weibo）之后调用。
 * 单个功能的失败不得影响其余功能，也不得让模块加载失败。
 */
public final class Hooks {

    private static final String TAG = "QuietWeibo";

    /** 单个 Feature 的安装动作；module/cl/config/feature 统一由 installOne 传入。 */
    @FunctionalInterface
    interface Installer {
        void install(XposedModule module, ClassLoader cl, Config config, Feature feature);
    }

    /** Feature -> 安装器。blocked Feature 不在表内。 */
    static final EnumMap<Feature, Installer> INSTALLERS = createInstallers();

    private Hooks() {
    }

    private static EnumMap<Feature, Installer> createInstallers() {
        EnumMap<Feature, Installer> map = new EnumMap<>(Feature.class);

        map.put(Feature.SPLASH,
                (module, cl, config, f) -> SplashHooks.installColdSplash(module, cl));
        map.put(Feature.HOT_SPLASH,
                (module, cl, config, f) -> SplashHooks.installHotSplash(module, cl));
        map.put(Feature.FEED_AD,
                (module, cl, config, f) -> {
                    FeedAdHooks.install(module, cl);
                    CommentHooks.install(module, f, cl);
                    CardListHooks.install(module, f, cl);
                    DetailFlowHooks.install(module, f, cl);
                    AdapterHooks.install(module, f, cl);
                });
        map.put(Feature.HOME_TOP_AD,
                (module, cl, config, f) -> ServiceHooks.installHomeTop(module, cl));
        map.put(Feature.PULL_PAUSE_AD,
                (module, cl, config, f) -> ServiceHooks.installPullPause(module, cl));
        map.put(Feature.OVERLAY_AD,
                (module, cl, config, f) -> ServiceHooks.installOverlay(module, cl));
        map.put(Feature.DISCOVER_AD,
                (module, cl, config, f) -> {
                    ServiceHooks.installDiscover(module, cl);
                    CardListHooks.install(module, f, cl);
                });
        map.put(Feature.SEARCH_AD,
                (module, cl, config, f) -> ServiceHooks.installSearch(module, cl));
        map.put(Feature.EXIT_AD,
                (module, cl, config, f) -> ServiceHooks.installExit(module, cl));
        map.put(Feature.REORDER_AD,
                (module, cl, config, f) -> ServiceHooks.installReorder(module, cl));
        map.put(Feature.FLOAT_AD,
                (module, cl, config, f) -> FloatAdHooks.install(module, cl));
        map.put(Feature.SHAKE_AD,
                (module, cl, config, f) -> SensorHooks.install(module, cl));
        map.put(Feature.AD_PRELOAD,
                (module, cl, config, f) -> PreloadHooks.install(module, cl));
        map.put(Feature.DYNAMIC,
                (module, cl, config, f) -> DynamicHooks.install(module, f, cl));

        return map;
    }

    /** 注册表键快照，供单测断言「非 blocked 全覆盖、blocked 不安装」。 */
    static Set<Feature> installerKeys() {
        EnumSet<Feature> keys = EnumSet.noneOf(Feature.class);
        keys.addAll(INSTALLERS.keySet());
        return Collections.unmodifiableSet(keys);
    }

    /**
     * @param mainProcess 当前是否为主进程（进程名 == 包名）。诊断回传只在主进程启动，
     *                    避免子进程未装全 hook 时把功能误报为失败。
     */
    public static void installAll(XposedModule module, ClassLoader cl, boolean mainProcess) {
        Config config;
        try {
            config = Config.load(module);
        } catch (Throwable t) {
            module.log(Log.ERROR, TAG, "config load failed", t);
            return;
        }
        if (!config.anyEnabled()) {
            module.log(Log.INFO, TAG, "all features disabled, nothing to hook");
            return;
        }
        if (mainProcess) {
            Report.start();
        } else {
            module.log(Log.INFO, TAG, "non-main process, status report disabled");
        }
        for (Feature f : Feature.values()) {
            if (f.blocked()) {
                module.log(Log.INFO, TAG, f.id + " blocked: " + f.blockedReason);
                continue;
            }
            if (!config.enabled(f)) {
                module.log(Log.INFO, TAG, f.id + " disabled");
                continue;
            }
            HookState.enable(f);
            installOne(module, cl, f, config);
        }
    }

    private static void installOne(XposedModule module, ClassLoader cl, Feature f, Config config) {
        try {
            Installer installer = INSTALLERS.get(f);
            if (installer == null) {
                module.log(Log.ERROR, TAG, f.id + " installer missing");
                HookState.markNotFound(f, "未注册安装器");
                return;
            }
            installer.install(module, cl, config, f);
        } catch (Throwable t) {
            module.log(Log.ERROR, TAG, f.id + " install threw", t);
            HookState.markNotFound(f, "安装异常");
        } finally {
            // 零成功挂载 + 至少一条 skip：整个功能不可用，升级为 critical，
            // 让异常横幅能反映版本不兼容。
            if (HookState.labels(f).isEmpty() && HookState.skipReason(f) != null) {
                HookState.markNotFoundCritical(f, "无可用挂载点");
            }
        }
    }
}

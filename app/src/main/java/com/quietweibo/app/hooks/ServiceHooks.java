package com.quietweibo.app.hooks;

import com.quietweibo.app.Feature;
import com.quietweibo.app.HookState;

import java.lang.reflect.Method;

import io.github.libxposed.api.XposedModule;

/**
 * 广告服务断源。
 *
 * <p>微博把各种广告位做成 streamservice 的“页面服务”，由页面 Presenter 在
 * {@code onInit/onInitView} 时初始化。这些类名都带 Ad，职责单一；只要跳过它们的
 * 生命周期入口，服务就不会注册监听、不会插入广告 ViewModel、也不会发广告请求：
 *
 * <ul>
 *   <li>AD-04 首页顶导广告：{@code AdChannelService}</li>
 *   <li>AD-05 下拉悬停广告：{@code AdPullPauseOperatorServiceV2} /
 *       {@code DiscoverAdPullPauseOperatorServiceV2}</li>
 *   <li>AD-06 大视窗/浮层/弹窗：{@code HomeAdTopVisionService}、
 *       {@code StreamAdInsertService}、{@code HomeZoomAdDialogService}、
 *       {@code AdVisionFloatAnimateService}、{@code HomeZoomAdAnimServise}</li>
 *   <li>AD-07 发现页广告：{@code DiscoverFlowAdService} + 发现页广告弹窗
 *       {@code com.sina.weibo.biz.discover.d#show()}</li>
 *   <li>AD-08 搜索/榜单广告：{@code SearchAdService} + {@code RankAdService}</li>
 *   <li>AD-09 退屏/切页广告：{@code AdFeedExitWindowService} /
 *       {@code AdFeedStreamService}</li>
 *   <li>AD-10 广告重排/插卡：{@code AdReOrderService}</li>
 * </ul>
 *
 * <p>跳过 onInit 不会影响页面其余服务：这些类不负责正文、分页或评论。
 */
final class ServiceHooks {

    private static final String PRESENTER =
            "com.sina.weibo.streamservice.constract.IPagePresenter";

    /** 静态 Hook 已覆盖的类，动态扫描时跳过，避免重复挂载。 */
    private static final java.util.Set<String> STATIC_CLASSES =
            new java.util.HashSet<>(java.util.Arrays.asList(
                    "com.sina.weibo.feedv2.home.service.ad.AdChannelService",
                    "com.sina.weibo.feedv2.stream.service.ad.AdPullPauseOperatorServiceV2",
                    "com.sina.weibo.feedv2.stream.service.ad.DiscoverAdPullPauseOperatorServiceV2",
                    "com.sina.weibo.feedv2.home.service.hometopvision.HomeAdTopVisionService",
                    "com.sina.weibo.feedv2.stream.service.streamtopvision.StreamAdInsertService",
                    "com.sina.weibo.feedv2.stream.service.ad.homgdialog.HomeZoomAdDialogService",
                    "com.sina.weibo.feedv2.stream.service.ad.AdVisionFloatAnimateService",
                    "com.sina.weibo.feedv2.stream.service.ad.anim.HomeZoomAdAnimServise",
                    "com.sina.weibo.feedv2.discover2.service.DiscoverFlowAdService",
                    "com.sina.weibo.page.searchv2.container.service.advertise.SearchAdService",
                    "com.sina.weibo.page.searchv2.container.service.advertise.DiscoverAdService",
                    "com.sina.weibo.search.rank.channel.service.ad.RankAdService",
                    "com.sina.weibo.feedv2.biz.service.AdFeedExitWindowService",
                    "com.sina.weibo.feedv2.biz.service.AdFeedStreamService",
                    "com.sina.weibo.feedv2.flow.services.adreorder.AdReOrderService",
                    "com.sina.weibo.feed.detailrefactor.service.adreorder.DetailAdReOrderService",
                    "com.sina.weibo.page.profilev2.container.service.ProfileAdDialogService"
            ));

    static boolean isStaticallyHandled(String className) {
        return STATIC_CLASSES.contains(className);
    }

    private ServiceHooks() {
    }

    static void installHomeTop(XposedModule module, ClassLoader cl) {
        Feature f = Feature.HOME_TOP_AD;
        disableLifecycle(module, f, cl,
                "com.sina.weibo.feedv2.home.service.ad.AdChannelService", true);
    }

    static void installPullPause(XposedModule module, ClassLoader cl) {
        Feature f = Feature.PULL_PAUSE_AD;
        disableLifecycle(module, f, cl,
                "com.sina.weibo.feedv2.stream.service.ad.AdPullPauseOperatorServiceV2", true);
        disableLifecycle(module, f, cl,
                "com.sina.weibo.feedv2.stream.service.ad.DiscoverAdPullPauseOperatorServiceV2", false);
    }

    static void installOverlay(XposedModule module, ClassLoader cl) {
        Feature f = Feature.OVERLAY_AD;
        disableLifecycle(module, f, cl,
                "com.sina.weibo.feedv2.home.service.hometopvision.HomeAdTopVisionService", true);
        disableLifecycle(module, f, cl,
                "com.sina.weibo.feedv2.stream.service.streamtopvision.StreamAdInsertService", true);
        disableLifecycle(module, f, cl,
                "com.sina.weibo.feedv2.stream.service.ad.homgdialog.HomeZoomAdDialogService", false);
        disableLifecycle(module, f, cl,
                "com.sina.weibo.feedv2.stream.service.ad.AdVisionFloatAnimateService", false);
        disableLifecycle(module, f, cl,
                "com.sina.weibo.feedv2.stream.service.ad.anim.HomeZoomAdAnimServise", false);
        // 个人页广告弹窗
        disableLifecycle(module, f, cl,
                "com.sina.weibo.page.profilev2.container.service.ProfileAdDialogService", false);
        // 超话页广告弹窗（page.ad 弹窗工厂的入口之一）
        disableMethod(module, f, cl, "com.sina.weibo.supergroup.page.l",
                "checkAndShowAdAsync", 1, false);
        // 图片型广告 Alert（CardListFragment 使用）
        disableMethod(module, f, cl, "com.sina.weibo.page.ad.c", "a", 1, false);
    }

    static void installDiscover(XposedModule module, ClassLoader cl) {
        Feature f = Feature.DISCOVER_AD;
        disableLifecycle(module, f, cl,
                "com.sina.weibo.feedv2.discover2.service.DiscoverFlowAdService", true);
        // 发现页搜索广告
        disableLifecycle(module, f, cl,
                "com.sina.weibo.page.searchv2.container.service.advertise.DiscoverAdService", false);
        // 发现页弹窗广告（DiscoverAdDialogV2）：skip 展示入口。
        disableShow(module, f, cl, "com.sina.weibo.biz.discover.d", "show", false);
    }

    static void installSearch(XposedModule module, ClassLoader cl) {
        Feature f = Feature.SEARCH_AD;
        disableLifecycle(module, f, cl,
                "com.sina.weibo.page.searchv2.container.service.advertise.SearchAdService", true);
        disableSafeLifecycle(module, f, cl,
                "com.sina.weibo.search.rank.channel.service.ad.RankAdService", false);
    }

    static void installExit(XposedModule module, ClassLoader cl) {
        Feature f = Feature.EXIT_AD;
        disableLifecycle(module, f, cl,
                "com.sina.weibo.feedv2.biz.service.AdFeedExitWindowService", true);
        disableLifecycle(module, f, cl,
                "com.sina.weibo.feedv2.biz.service.AdFeedStreamService", false);
    }

    static void installReorder(XposedModule module, ClassLoader cl) {
        Feature f = Feature.REORDER_AD;
        disableLifecycle(module, f, cl,
                "com.sina.weibo.feedv2.flow.services.adreorder.AdReOrderService", true);
        // 正文页的广告重排（正文底部/评论区之间的广告位）。
        disableLifecycle(module, f, cl,
                "com.sina.weibo.feed.detailrefactor.service.adreorder.DetailAdReOrderService", false);
    }

    /** 标准生命周期：onInit/onInitView(IPagePresenter)。 */
    private static void disableLifecycle(XposedModule module, Feature f, ClassLoader cl,
                                         String className, boolean critical) {
        Class<?> cls = critical
                ? HookKit.findClass(module, f, cl, className)
                : HookKit.findClassOptional(module, f, cl, className);
        if (cls == null) {
            return;
        }
        Class<?> presenter = classOrNull(cl, PRESENTER);
        boolean any = false;
        if (presenter != null) {
            any |= skip(module, f, cls, "onInit", presenter, className + "#onInit");
            any |= skip(module, f, cls, "onInitView", presenter, className + "#onInitView");
        }
        if (!any) {
            HookKit.warn(module, f.id + " skip: no lifecycle method in " + className);
            HookState.markNotFound(f, "生命周期未找到 " + className);
        }
    }

    /** 榜单广告用的是 SearchBaseLifecycleService 的 onInitSafe/onInitViewSafe。 */
    private static void disableSafeLifecycle(XposedModule module, Feature f, ClassLoader cl,
                                             String className, boolean critical) {
        Class<?> cls = critical
                ? HookKit.findClass(module, f, cl, className)
                : HookKit.findClassOptional(module, f, cl, className);
        if (cls == null) {
            return;
        }
        Class<?> presenter = classOrNull(cl, PRESENTER);
        boolean any = false;
        if (presenter != null) {
            any |= skip(module, f, cls, "onInitSafe", presenter, className + "#onInitSafe");
            any |= skip(module, f, cls, "onInitViewSafe", presenter, className + "#onInitViewSafe");
        }
        if (!any) {
            HookKit.warn(module, f.id + " skip: no Safe lifecycle in " + className);
            HookState.markNotFound(f, "生命周期未找到 " + className);
        }
    }

    /** 跳过指定名字/参数个数的展示入口。 */
    private static void disableMethod(XposedModule module, Feature f, ClassLoader cl,
                                      String className, String method, int arity,
                                      boolean critical) {
        Class<?> cls = critical
                ? HookKit.findClass(module, f, cl, className)
                : HookKit.findClassOptional(module, f, cl, className);
        if (cls == null) {
            return;
        }
        Method m = HookKit.findByNameAndArity(cls, method, arity);
        HookKit.install(module, f, className + "#" + method, m,
                HookKit.skipVoid(module, f, className + "#" + method + " skipped"));
    }

    /** 直接跳过无参/固定名字的展示入口。 */
    private static void disableShow(XposedModule module, Feature f, ClassLoader cl,
                                    String className, String method, boolean critical) {
        Class<?> cls = critical
                ? HookKit.findClass(module, f, cl, className)
                : HookKit.findClassOptional(module, f, cl, className);
        if (cls == null) {
            return;
        }
        Method m = HookKit.findMethodQuiet(cls, method);
        HookKit.install(module, f, className + "#" + method, m,
                HookKit.skipVoid(module, f, className + "#" + method + " skipped"));
    }

    private static boolean skip(XposedModule module, Feature f, Class<?> cls, String name,
                                Class<?> presenter, String label) {
        Method m = HookKit.findMethodQuiet(cls, name, presenter);
        if (m == null) {
            return false;
        }
        return HookKit.install(module, f, label, m,
                HookKit.skipVoid(module, f, label + " skipped"));
    }

    private static Class<?> classOrNull(ClassLoader cl, String name) {
        try {
            return Class.forName(name, false, cl);
        } catch (Throwable t) {
            return null;
        }
    }
}

package com.quietweibo.app.hooks;

import android.view.View;

import com.quietweibo.app.Feature;
import com.quietweibo.app.HookState;

import java.lang.reflect.Method;
import java.util.List;

import io.github.libxposed.api.XposedModule;

/**
 * 适配器/卡片 View 兜底：在 ViewModel 转成 RecyclerView item 前再筛一次。
 *
 * <p>发现页热搜、消息动态、搜索结果等页面并不总是走 BaseAdapterWrapper；
 * 但只要卡片最终由 {@code BaseCardView#setCardInfo(PageCardInfo)} 绑定，
 * 就可以在这里统一过滤：先让 AdFilter 原地移除嵌套广告条目，再对整体广告卡
 * 设置 GONE，避免露出空白广告位。
 */
final class AdapterHooks {

    private static final String WRAPPER =
            "com.sina.weibo.streamservice.adapter.BaseAdapterWrapper";
    private static final String PAGE_CARD = "com.sina.weibo.card.model.PageCardInfo";
    private static final String BASE_CARD = "com.sina.weibo.card.view.BaseCardView";

    private AdapterHooks() {
    }

    static void install(XposedModule module, Feature f, ClassLoader cl) {
        try {
            FeedAdHooks.AdFilter filter = FeedAdHooks.AdFilter.create(module, f, cl);
            installAdapterFilter(module, f, cl, filter);
            installCardViewFilter(module, f, cl, filter);
            HookState.note(f, "适配器/卡片兜底：过滤广告 ViewModel 与广告卡");
        } catch (Throwable t) {
            HookKit.error(module, f.id + " adapter hooks aborted", t);
            HookState.note(f, "安装异常");
        }
    }

    private static void installAdapterFilter(XposedModule module, Feature f, ClassLoader cl,
                                             FeedAdHooks.AdFilter filter) {
        Class<?> wrapper = HookKit.findClassOptional(module, f, cl, WRAPPER);
        if (wrapper == null) {
            return;
        }
        Method collect = null;
        for (Method m : wrapper.getDeclaredMethods()) {
            if ("collectAdapterDatas".equals(m.getName())
                    && m.getParameterTypes().length == 2
                    && List.class.isAssignableFrom(m.getParameterTypes()[0])) {
                collect = m;
                break;
            }
        }
        HookKit.install(module, f, "BaseAdapterWrapper#collectAdapterDatas", collect, chain -> {
            try {
                Object vm = chain.getArg(1);
                if (vm != null) {
                    Method getData = vm.getClass().getMethod("getData");
                    getData.setAccessible(true);
                    Object data = getData.invoke(vm);
                    if (data != null && filter.isAd(data)) {
                        HookKit.hit(module, f, "adapter ad vm skipped "
                                + data.getClass().getName());
                        return null;
                    }
                }
            } catch (Throwable ignored) {
                // ViewModel 没有 getData / 读取失败按正常 item 放行。
            }
            return chain.proceed();
        });
    }

    /**
     * BaseCardView#setCardInfo 是所有卡片绑定的公共入口。发现页热搜卡、
     * 热搜下方轮播卡、消息动态卡、搜索结果卡都可能绕过 adapter，因此在这里
     * 再过滤一次；命中整体广告卡时隐藏 View，避免留下空白。
     */
    private static void installCardViewFilter(XposedModule module, Feature f, ClassLoader cl,
                                              FeedAdHooks.AdFilter filter) {
        Class<?> pageCard = classOrNull(cl, PAGE_CARD);
        Class<?> baseCard = HookKit.findClassOptional(module, f, cl, BASE_CARD);
        if (pageCard == null || baseCard == null) {
            return;
        }
        Method setCardInfo = HookKit.findMethodQuiet(baseCard, "setCardInfo", pageCard);
        HookKit.install(module, f, "BaseCardView#setCardInfo", setCardInfo, chain -> {
            boolean ad = false;
            try {
                Object card = chain.getArg(0);
                ad = card != null && filter.isAd(card);
            } catch (Throwable t) {
                HookKit.error(module, f.id + " card filter failed", t);
            }
            Object result = chain.proceed();
            if (ad) {
                try {
                    Object self = chain.getThisObject();
                    if (self instanceof View) {
                        ((View) self).setVisibility(View.GONE);
                    }
                    HookKit.hit(module, f, "ad card hidden "
                            + chain.getArg(0).getClass().getName());
                } catch (Throwable ignored) {
                }
            }
            return result;
        });
    }

    private static Class<?> classOrNull(ClassLoader cl, String name) {
        try {
            return Class.forName(name, false, cl);
        } catch (Throwable t) {
            return null;
        }
    }
}

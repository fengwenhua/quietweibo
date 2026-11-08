package com.quietweibo.app.hooks;

import com.quietweibo.app.Feature;
import com.quietweibo.app.HookState;

import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.libxposed.api.XposedModule;

/**
 * 正文页流数据过滤。
 *
 * <p>正文页的流数据由 {@code DetailPageFlowFetchService} /
 * {@code InteractPageFlowFetchService} 返回，随后才被 Presenter 转成 ViewModel。
 * 只在 {@code getItems()} 里过滤时，某些路径已经先读过 {@code items} 字段，
 * 会出现“数据删了但 ViewHolder 还在”的情况（正文底部广告就是这样）。
 *
 * <p>因此在 fetch 返回值上再过滤一次 {@code items} 字段，保证 ViewModel 生成前
 * 广告条目已经不存在。只处理带 items 字段的数据对象，其它数据原样返回。
 */
final class DetailFlowHooks {

    private static final String INTERACT_FETCH =
            "com.sina.weibo.feed.interact.flow.service.data.InteractPageFlowFetchService";
    private static final String DETAIL_FETCH =
            "com.sina.weibo.feed.detailrefactor.flow.core.DetailPageFlowFetchService";

    private static final AtomicBoolean sInstalled = new AtomicBoolean();

    private DetailFlowHooks() {
    }

    static void install(XposedModule module, Feature f, ClassLoader cl) {
        if (!sInstalled.compareAndSet(false, true)) {
            return;
        }
        try {
            FeedAdHooks.AdFilter filter = FeedAdHooks.AdFilter.create(module, f, cl);
            boolean any = false;
            Class<?> base = classOrNull(cl, INTERACT_FETCH);
            if (base != null) {
                any |= installByName(base, new String[]{"c", "d", "e", "f", "g", "h"},
                        module, f, filter, "InteractFetch");
            }
            Class<?> detail = classOrNull(cl, DETAIL_FETCH);
            if (detail != null) {
                any |= installByName(detail, new String[]{"a", "b", "c", "d", "e", "g", "h"},
                        module, f, filter, "DetailFetch");
            }
            if (any) {
                HookState.note(f, "正文页流数据：fetch 返回值二次过滤");
            }
        } catch (Throwable t) {
            sInstalled.set(false);
            HookKit.error(module, f.id + " detail flow hooks aborted", t);
            HookState.note(f, "安装异常");
        }
    }

    private static boolean installByName(Class<?> cls, String[] names, XposedModule module,
                                         Feature f, FeedAdHooks.AdFilter filter, String prefix) {
        boolean any = false;
        for (Method m : cls.getDeclaredMethods()) {
            if (m.getParameterTypes().length != 1) {
                continue;
            }
            boolean match = false;
            for (String name : names) {
                if (name.equals(m.getName())) {
                    match = true;
                    break;
                }
            }
            if (!match) {
                continue;
            }
            m.setAccessible(true);
            any |= install(module, f, filter, m,
                    prefix + "#" + m.getName() + "(" + m.getParameterTypes()[0].getSimpleName() + ")");
        }
        return any;
    }

    private static boolean install(XposedModule module, Feature f, FeedAdHooks.AdFilter filter,
                                   Method method, String label) {
        return HookKit.install(module, f, label, method, chain -> {
            Object result = chain.proceed();
            try {
                List<?> list = filter.itemsOf(result);
                if (list != null) {
                    List<?> filtered = filter.apply(list);
                    if (filtered != list) {
                        filter.setItems(result, filtered);
                    }
                    if (filter.lastRemoved > 0) {
                        HookKit.hit(module, f, label + " removed=" + filter.lastRemoved
                                + " last=" + filter.lastRemovedClass);
                    }
                }
            } catch (Throwable t) {
                HookKit.error(module, f.id + " " + label + " filter failed", t);
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

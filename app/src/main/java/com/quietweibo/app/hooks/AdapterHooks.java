package com.quietweibo.app.hooks;

import com.quietweibo.app.Feature;
import com.quietweibo.app.HookState;

import java.lang.reflect.Method;
import java.util.List;

import io.github.libxposed.api.XposedModule;

/**
 * 适配器兜底：在 ViewModel 转成 RecyclerView item 前再筛一次。
 *
 * <p>有些页面在数据层过滤之后仍然会把广告 ViewModel 放进 adapter（例如正文页
 * 底部的 WBox/穿山甲广告卡、发现页的通栏广告）。{@code BaseAdapterWrapper#collectAdapterDatas}
 * 是最终收集 adapter item 的入口，在这里按 {@code ViewModel#getData()} 判断广告，
 * 命中就不加入最终列表。
 */
final class AdapterHooks {

    private static final String WRAPPER =
            "com.sina.weibo.streamservice.adapter.BaseAdapterWrapper";

    private AdapterHooks() {
    }

    static void install(XposedModule module, Feature f, ClassLoader cl) {
        try {
            Class<?> wrapper = HookKit.findClassOptional(module, f, cl, WRAPPER);
            if (wrapper == null) {
                return;
            }
            FeedAdHooks.AdFilter filter = FeedAdHooks.AdFilter.create(module, f, cl);
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
            HookState.note(f, "适配器兜底：过滤广告 ViewModel");
        } catch (Throwable t) {
            HookKit.error(module, f.id + " adapter hooks aborted", t);
            HookState.note(f, "安装异常");
        }
    }
}

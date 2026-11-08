package com.quietweibo.app.hooks;

import android.app.Activity;

import com.quietweibo.app.Feature;
import com.quietweibo.app.HookState;

import java.lang.reflect.Method;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/**
 * AD-11 视频/直播悬浮广告。
 *
 * <p>微博的悬浮广告由 {@code com.sina.weibo.floatvideo.AdVisionFloatManager}
 * 管理，实际 View 由 {@code com.sina.weibo.biz.AdVisionFloatHandler#getView} 创建。
 * {@code FloatingWindowManagerImp} 对 {@code getView} 返回 null 有显式判断
 * （“拿不到 View 就不挂悬浮窗”），因此返回 null 是框架自带的降级路径。
 *
 * <p>另外 {@code com.sina.weibo.extlibui.floatview.FloatViewFactory} 用
 * type=1 注册了 5 种 AdFloatInfo 视图；其它 type（音频、收藏、商品等）不受影响。
 */
final class FloatAdHooks {

    /**
     * 微博 16.10.3 的 DEX 类名是混淆后的 {@code com.sina.weibo.biz.e}；
     * jadx 通过 Kotlin Metadata 把它显示成 AdVisionFloatHandler。两个名字都试，
     * 兼容未来恢复原名或再次混淆。
     */
    private static final String[] HANDLER_NAMES = {
            "com.sina.weibo.biz.e",
            "com.sina.weibo.biz.AdVisionFloatHandler",
    };
    private static final String[] MANAGER_NAMES = {
            "com.sina.weibo.floatvideo.b",
            "com.sina.weibo.floatvideo.AdVisionFloatManager",
    };
    private static final String FACTORY = "com.sina.weibo.extlibui.floatview.FloatViewFactory";
    private static final String FLOAT_INFO =
            "com.sina.weibo.modules.story.interfaces.FloatDataInfo";

    /** FloatViewFactory 里广告浮窗的类型是 1。 */
    private static final int TYPE_AD = 1;

    private FloatAdHooks() {
    }

    static void install(XposedModule module, ClassLoader cl) {
        Feature f = Feature.FLOAT_AD;
        try {
            Class<?> handler = findFirst(module, f, cl, HANDLER_NAMES);
            if (handler != null) {
                Method getView = HookKit.findMethodQuiet(handler, "a",
                        Activity.class, int.class, int.class);
                HookKit.install(module, f, "AdVisionFloatHandler#a", getView,
                        HookKit.constValue(module, f, "AdVisionFloatHandler#a -> null", null));
            }

            Class<?> factory = HookKit.findClassOptional(module, f, cl, FACTORY);
            Class<?> floatInfo = classOrNull(cl, FLOAT_INFO);
            if (factory != null && floatInfo != null) {
                Method getView = HookKit.findMethodQuiet(factory, "getFloatView",
                        android.content.Context.class, floatInfo);
                HookKit.install(module, f, "FloatViewFactory#getFloatView", getView,
                        rejectAdType(module, f));

                Method getModel = HookKit.findMethodQuiet(factory, "getFloatViewModel",
                        android.content.Context.class, floatInfo);
                HookKit.install(module, f, "FloatViewFactory#getFloatViewModel", getModel,
                        rejectAdType(module, f));
            }

            Class<?> manager = findOptionalFirst(module, f, cl, MANAGER_NAMES);
            if (manager != null) {
                Method show = HookKit.findMethodQuiet(manager, "j");
                HookKit.install(module, f, "AdVisionFloatManager#j", show,
                        HookKit.skipVoid(module, f, "AdVisionFloatManager#j skipped"));
            }
            HookState.note(f, "悬浮广告：AdVisionFloatHandler#getView + FloatViewFactory(type=1)");
        } catch (Throwable t) {
            HookKit.error(module, f.id + " install aborted", t);
            HookState.note(f, "安装异常");
        }
    }

    /**
     * FloatDataInfo#getType() == 1 时返回 null（不创建广告浮窗），其余类型 proceed。
     * 读取失败按非广告处理，绝不影响音频/收藏/商品浮窗。
     */
    private static XposedInterface.Hooker rejectAdType(XposedModule module, Feature f) {
        return chain -> {
            try {
                Object info = chain.getArg(1);
                if (info != null) {
                    Method getType = info.getClass().getMethod("getType");
                    Object type = getType.invoke(info);
                    if (type instanceof Integer && ((Integer) type).intValue() == TYPE_AD) {
                        HookKit.hit(module, f, "ad FloatView type=1 blocked");
                        return null;
                    }
                }
            } catch (Throwable ignored) {
                // 读不到类型就放行。
            }
            return chain.proceed();
        };
    }

    private static Class<?> findFirst(XposedModule module, Feature f, ClassLoader cl,
                                      String[] names) {
        Class<?> cls = findOptionalFirst(module, f, cl, names);
        if (cls == null) {
            HookKit.warn(module, f.id + " skip: class not found " + String.join("/", names));
            HookState.markNotFoundCritical(f, "未找到悬浮广告 Handler");
        }
        return cls;
    }

    private static Class<?> findOptionalFirst(XposedModule module, Feature f, ClassLoader cl,
                                              String[] names) {
        for (String name : names) {
            try {
                return Class.forName(name, false, cl);
            } catch (Throwable ignored) {
                // 试下一个候选名
            }
        }
        return null;
    }

    private static Class<?> classOrNull(ClassLoader cl, String name) {
        try {
            return Class.forName(name, false, cl);
        } catch (Throwable t) {
            return null;
        }
    }
}

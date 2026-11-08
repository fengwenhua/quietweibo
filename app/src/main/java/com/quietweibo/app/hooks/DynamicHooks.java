package com.quietweibo.app.hooks;

import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.util.Log;

import com.quietweibo.app.Feature;
import com.quietweibo.app.HookState;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import io.github.libxposed.api.XposedModule;

/**
 * 跨版本动态兜底（AD-14）。
 *
 * <p>微博更新后类名/包名/混淆名可能变化，但 APK 的 DEX 一定可读。安装时用
 * {@link DexInventory} 只解析 DEX 类名，然后按结构特征动态补挂载点：
 *
 * <ul>
 *   <li>广告服务：类名/包名含 ad/advert/promotion/splash，且声明
 *       {@code onInit/onInitView/...Safe(IPagePresenter)} 的类，统一跳过生命周期；</li>
 *   <li>广告传感器：mobileads 包或类名含 sensor/shake/twist 的
 *       {@link SensorEventListener}，hook {@code onSensorChanged} 为空操作；</li>
 *   <li>开屏：类名含 FlashAd/splash 或 mobileads，且声明
 *       {@code loadAdWithLoadManager(...)} 的类，复用 AD-01 的失败回调逻辑。</li>
 * </ul>
 *
 * <p>扫描只读类名，不执行目标代码；单个类失败不影响其它类。
 */
final class DynamicHooks {

    private static final String PRESENTER =
            "com.sina.weibo.streamservice.constract.IPagePresenter";
    private static final Set<String> STATIC_SENSORS =
            ConcurrentHashMap.newKeySet();

    private DynamicHooks() {
    }

    static void install(XposedModule module, Feature f, ClassLoader cl) {
        List<String> all;
        try {
            all = DexInventory.classNames(cl);
        } catch (Throwable t) {
            HookKit.error(module, f.id + " dex inventory failed", t);
            HookState.markNotFound(f, "DEX 类名扫描失败");
            return;
        }
        module.log(Log.INFO, HookKit.TAG, f.id + " dex classes=" + all.size());
        if (all.isEmpty()) {
            HookState.markNotFound(f, "DEX 类名扫描为空");
            return;
        }
        STATIC_SENSORS.add("com.sina.weibo.mobileads.util.f");
        STATIC_SENSORS.add("com.sina.weibo.mobileads.util.e");
        STATIC_SENSORS.add("com.sina.weibo.mobileads.util.g");

        int services = installAdServices(module, f, cl, all);
        int sensors = installSensors(module, f, cl, all);
        int splash = installSplash(module, f, cl, all);
        HookState.note(f, "动态兜底：广告服务 " + services + " 个，传感器 "
                + sensors + " 个，开屏类 " + splash + " 个");
    }

    private static int installAdServices(XposedModule module, Feature f, ClassLoader cl,
                                         List<String> all) {
        Class<?> presenter = classOrNull(cl, PRESENTER);
        int count = 0;
        List<String> matched = new java.util.ArrayList<>();
        for (String name : all) {
            if (!looksLikeAdService(name) || ServiceHooks.isStaticallyHandled(name)) {
                continue;
            }
            Class<?> cls = classOrNull(cl, name);
            if (cls == null) {
                continue;
            }
            boolean any = false;
            try {
                for (Method m : cls.getDeclaredMethods()) {
                    if (m.getParameterTypes().length != 1
                            || m.getReturnType() != void.class) {
                        continue;
                    }
                    String method = m.getName();
                    if (!"onInit".equals(method) && !"onInitView".equals(method)
                            && !"onInitSafe".equals(method) && !"onInitViewSafe".equals(method)) {
                        continue;
                    }
                    Class<?> param = m.getParameterTypes()[0];
                    if (presenter != null && !presenter.isAssignableFrom(param)) {
                        continue;
                    }
                    if (presenter == null && !param.getName().contains("Presenter")) {
                        continue;
                    }
                    any |= HookKit.install(module, f, "DynamicService#" + simpleName(name)
                                    + "#" + method, m,
                            HookKit.skipVoid(module, f, simpleName(name) + "#" + method + " skipped"));
                }
            } catch (Throwable t) {
                HookKit.warn(module, f.id + " service scan failed " + name + ": " + t);
            }
            if (any) {
                count++;
                matched.add(name);
            }
        }
        if (!matched.isEmpty()) {
            module.log(Log.INFO, HookKit.TAG, f.id + " ad services: " + matched);
        }
        return count;
    }

    private static int installSensors(XposedModule module, Feature f, ClassLoader cl,
                                      List<String> all) {
        int count = 0;
        for (String name : all) {
            if (!looksLikeSensor(name) || STATIC_SENSORS.contains(name)) {
                continue;
            }
            Class<?> cls = classOrNull(cl, name);
            if (cls == null || !SensorEventListener.class.isAssignableFrom(cls)) {
                continue;
            }
            Method changed = HookKit.findMethodQuiet(cls, "onSensorChanged", SensorEvent.class);
            if (HookKit.install(module, f, "DynamicSensor#" + simpleName(name), changed,
                    HookKit.skipVoid(module, f, simpleName(name) + "#onSensorChanged skipped"))) {
                count++;
            }
        }
        return count;
    }

    private static int installSplash(XposedModule module, Feature f, ClassLoader cl,
                                     List<String> all) {
        int count = 0;
        for (String name : all) {
            if (!looksLikeSplash(name)
                    || "com.sina.weibo.mobileads.view.FlashAd".equals(name)) {
                continue;
            }
            Class<?> cls = classOrNull(cl, name);
            if (cls == null) {
                continue;
            }
            Method load = null;
            try {
                for (Method m : cls.getDeclaredMethods()) {
                    if ("loadAdWithLoadManager".equals(m.getName())
                            && m.getParameterTypes().length == 1) {
                        load = m;
                        break;
                    }
                }
            } catch (Throwable ignored) {
            }
            if (load == null) {
                continue;
            }
            // 只对 FlashAd 同构类安装：必须有 getListener()，否则回调失败无法推进启动。
            Method listener = HookKit.findMethodQuiet(cls, "getListener");
            if (listener == null) {
                continue;
            }
            SplashHooks.hookFlashClass(module, f, cl, cls);
            count++;
        }
        return count;
    }

    private static boolean looksLikeAdService(String name) {
        return AdNameMatcher.looksLikeAdService(name);
    }

    private static boolean looksLikeSensor(String name) {
        return AdNameMatcher.looksLikeSensor(name);
    }

    private static boolean looksLikeSplash(String name) {
        return AdNameMatcher.looksLikeSplash(name);
    }

    private static String simpleName(String name) {
        int i = name.lastIndexOf('.');
        return i >= 0 ? name.substring(i + 1) : name;
    }

    private static Class<?> classOrNull(ClassLoader cl, String name) {
        try {
            return Class.forName(name, false, cl);
        } catch (Throwable t) {
            return null;
        }
    }
}

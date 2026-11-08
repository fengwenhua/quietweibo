package com.quietweibo.app.hooks;

import com.quietweibo.app.Feature;
import com.quietweibo.app.HookState;

import java.lang.reflect.Method;

import io.github.libxposed.api.XposedModule;

/**
 * AD-12 摇一摇 / 扭一扭跳转。
 *
 * <p>微博 16.10.3 的广告传感器都在 {@code com.sina.weibo.mobileads.util} 里，
 * 三个类只服务广告（日志例如「首刷广告重新设置加速度」）：
 * <ul>
 *   <li>{@code f} AdShakeSensorManager：注册入口 {@code f()}；</li>
 *   <li>{@code e} AdShakePlusSensorManager：注册入口 {@code h()}；</li>
 *   <li>{@code g} AdTwistSensorManager：注册入口 {@code a()}。</li>
 * </ul>
 * 跳过注册后，广告点击区域不再响应晃动/扭转，正常页面传感器不受影响。
 */
final class SensorHooks {

    private SensorHooks() {
    }

    static void install(XposedModule module, ClassLoader cl) {
        Feature f = Feature.SHAKE_AD;
        try {
            skipRegistration(module, f, cl,
                    "com.sina.weibo.mobileads.util.f", "f",
                    "AdShakeSensorManager#f");
            skipRegistration(module, f, cl,
                    "com.sina.weibo.mobileads.util.e", "h",
                    "AdShakePlusSensorManager#h");
            skipRegistration(module, f, cl,
                    "com.sina.weibo.mobileads.util.g", "a",
                    "AdTwistSensorManager#a");
            HookState.note(f, "摇一摇：三个广告传感器注册入口均跳过");
        } catch (Throwable t) {
            HookKit.error(module, f.id + " install aborted", t);
            HookState.note(f, "安装异常");
        }
    }

    private static void skipRegistration(XposedModule module, Feature f, ClassLoader cl,
                                         String className, String method, String label) {
        Class<?> cls = HookKit.findClassOptional(module, f, cl, className);
        if (cls == null) {
            return;
        }
        Method m = HookKit.findMethodQuiet(cls, method);
        HookKit.install(module, f, label, m,
                HookKit.skipVoid(module, f, label + " skipped"));
    }
}

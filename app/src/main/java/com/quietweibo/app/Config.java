package com.quietweibo.app;

import android.content.SharedPreferences;
import android.util.Log;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/**
 * 开关读取：有 PROP_CAP_REMOTE 才读 getRemotePreferences("config")，
 * 否则（含能力探测/读取任一步抛异常）回退到 Feature 的编译期内置默认值。
 * 必须在 onModuleLoaded 之后调用，之前任何 API 都会抛 IllegalStateException。
 */
public final class Config {

    public static final String GROUP = "config";

    private static final String TAG = "QuietWeibo";

    private final Map<Feature, Boolean> mEnabled = new EnumMap<>(Feature.class);

    private Config() {
        for (Feature f : Feature.values()) {
            mEnabled.put(f, f.defaultEnabled);
        }
    }

    /** 纯 JVM 可测入口：overrides 里缺的键用 Feature 编译期默认值，未知键忽略。 */
    static Config fromOverrides(Map<String, Boolean> overrides) {
        Config config = new Config();
        if (overrides != null) {
            for (Feature f : Feature.values()) {
                Boolean v = overrides.get(f.key);
                if (v != null) {
                    config.mEnabled.put(f, v);
                }
            }
        }
        return config;
    }

    /**
     * remote 开关读取器。读取失败（含返回 null）由
     * {@link #load(boolean, RemoteReader)} 统一吞掉并回退默认值。
     */
    @FunctionalInterface
    interface RemoteReader {
        Map<String, Boolean> read() throws Throwable;
    }

    /**
     * 纯 JVM 可测入口：remote=false 或读取失败时全默认；读取成功时缺键用默认值。
     * 任何异常都不向外抛，避免一次 remote 读取失败让全部 hook 不安装。
     */
    static Config load(boolean remote, RemoteReader reader) {
        if (!remote) {
            return new Config();
        }
        Map<String, Boolean> overrides;
        try {
            overrides = reader == null ? null : reader.read();
        } catch (Throwable t) {
            return new Config();
        }
        return fromOverrides(overrides);
    }

    public static Config load(XposedModule module) {
        boolean remote;
        try {
            remote = (module.getFrameworkProperties() & XposedInterface.PROP_CAP_REMOTE) != 0;
        } catch (Throwable t) {
            module.log(Log.WARN, TAG, "framework properties unavailable: use built-in defaults", t);
            return new Config();
        }
        if (!remote) {
            module.log(Log.WARN, TAG, "no PROP_CAP_REMOTE: use built-in defaults");
            return new Config();
        }
        try {
            SharedPreferences sp = module.getRemotePreferences(GROUP);
            if (sp == null) {
                throw new IllegalStateException("remote prefs is null");
            }
            Map<String, Boolean> overrides = new HashMap<>();
            for (Feature f : Feature.values()) {
                overrides.put(f.key, sp.getBoolean(f.key, f.defaultEnabled));
            }
            return fromOverrides(overrides);
        } catch (Throwable t) {
            module.log(Log.WARN, TAG, "remote prefs unavailable: use built-in defaults", t);
            return new Config();
        }
    }

    public boolean enabled(Feature f) {
        return Boolean.TRUE.equals(mEnabled.get(f));
    }

    public boolean anyEnabled() {
        for (Map.Entry<Feature, Boolean> e : mEnabled.entrySet()) {
            if (Boolean.TRUE.equals(e.getValue()) && !e.getKey().blocked()) {
                return true;
            }
        }
        return false;
    }
}

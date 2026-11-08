package com.quietweibo.app;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 目标应用版本门禁与兼容性清单。
 *
 * <p>“已测试兼容”与“允许运行”是两件事：
 * <ul>
 *   <li>已测试版本：做过静态锚点体检与真机/日志验证，设置页显示“已测试”；</li>
 *   <li>其它 16.10+ / 17+ 版本：仍 fail-open 运行，由 AD-14 动态兜底，
 *       设置页高亮提示“未测试”。</li>
 * </ul>
 *
 * <p>清单按版本从新到旧排列，关于页直接按此顺序展示。
 */
public final class VersionGate {

    /** 最新已测试版本：微博 16.11.0（versionCode 8250）。 */
    public static final String SUPPORTED_VERSION = "16.11.0";

    /** 最新已测试版本的 versionCode。 */
    public static final long SUPPORTED_VERSION_CODE = 8250L;

    /** 已测试兼容版本，必须从新到旧。 */
    private static final TestedVersion[] TESTED = {
            new TestedVersion("16.11.0", 8250L),
            new TestedVersion("16.10.3", 8234L),
    };

    private VersionGate() {
    }

    public static final class TestedVersion {
        public final String name;
        public final long versionCode;

        TestedVersion(String name, long versionCode) {
            this.name = name;
            this.versionCode = versionCode;
        }
    }

    /** 已测试兼容版本，按从新到旧排列。 */
    public static List<TestedVersion> testedVersions() {
        List<TestedVersion> out = new ArrayList<>(TESTED.length);
        Collections.addAll(out, TESTED);
        return Collections.unmodifiableList(out);
    }

    /** 最新已测试版本名。 */
    public static String latestTestedVersion() {
        return TESTED[0].name;
    }

    /** 该版本是否在“已测试兼容”清单中。 */
    public static boolean isTested(String versionName) {
        if (versionName == null) {
            return false;
        }
        for (TestedVersion version : TESTED) {
            if (version.name.equals(versionName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 是否允许运行。已测试版本直接放行；16.10+ / 17+ 也放行（尽力兼容），
     * 未知版本由设置页“未测试”高亮和诊断页提示，不阻断 Hook。
     */
    public static boolean isSupported(String versionName) {
        if (isTested(versionName)) {
            return true;
        }
        if (versionName == null) {
            return false;
        }
        try {
            String[] parts = versionName.split("\\.");
            int major = Integer.parseInt(parts[0]);
            int minor = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
            return major > 16 || (major == 16 && minor >= 10);
        } catch (Throwable ignored) {
            return false;
        }
    }
}

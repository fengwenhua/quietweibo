package com.quietweibo.app;

/**
 * 目标应用版本门禁。
 *
 * <p>只声明“适配版本”这一件事：未知版本不阻断 hook（fail-open），
 * 由异常横幅提示；hook 内部仍按 Feature 开关执行。
 */
public final class VersionGate {

    /** 微博 16.10.3（versionCode 8234）：静态锚点按此版本 DEX 定位。 */
    public static final String SUPPORTED_VERSION = "16.10.3";

    /** 对应的 versionCode。 */
    public static final long SUPPORTED_VERSION_CODE = 8234L;

    private VersionGate() {
    }

    public static boolean isSupported(String versionName) {
        if (SUPPORTED_VERSION.equals(versionName)) {
            return true;
        }
        if (versionName == null) {
            return false;
        }
        // 跨版本策略：16.10+ 与 17+ 视为“尽力兼容”，静态锚点缺失时按
        // skip/fail-open 处理，并由 AD-14 动态扫描补挂载点。
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

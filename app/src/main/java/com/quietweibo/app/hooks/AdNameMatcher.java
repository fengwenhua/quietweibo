package com.quietweibo.app.hooks;

import java.util.Locale;

/**
 * 广告类名判定：按“包名段 + CamelCase 单词”匹配，避免子串误伤。
 *
 * <p>典型误伤：{@code HomeDidLoadService} / {@code PushPreloadService} 的
 * {@code ...loadService} 里包含小写子串 {@code adservice}。因此这里不再做
 * 小写子串匹配，而是要求：
 *
 * <ul>
 *   <li>包名中存在完整的 {@code ad}/{@code ads} 段，或以 advert/promotion/
 *       mobileads/splashad 开头的段；或</li>
 *   <li>类名中存在独立的 {@code Ad}/{@code Ads}/{@code AD} 词（后接大写字母或结尾），
 *       或包含 {@code Advert}/{@code Promotion}/{@code SplashAd}。</li>
 * </ul>
 *
 * <p>纯字符串逻辑，无 Android 依赖，可在 JVM 单测里直接覆盖。
 */
final class AdNameMatcher {

    private AdNameMatcher() {
    }

    static boolean looksLikeAdService(String name) {
        if (!isAppClass(name)) {
            return false;
        }
        return adPackage(name.toLowerCase(Locale.ROOT)) || adToken(simpleName(name));
    }

    static boolean looksLikeSensor(String name) {
        if (!isAppClass(name)) {
            return false;
        }
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.contains("mobileads") || lower.contains("sensor")
                || lower.contains("shake") || lower.contains("twist");
    }

    static boolean looksLikeSplash(String name) {
        if (!isAppClass(name)) {
            return false;
        }
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.contains("flashad") || lower.contains("splash")
                || lower.contains("mobileads");
    }

    private static boolean isAppClass(String name) {
        return name != null
                && !name.contains("$")
                && name.toLowerCase(Locale.ROOT).startsWith("com.sina.weibo");
    }

    private static boolean adPackage(String lower) {
        int dot = lower.lastIndexOf('.');
        if (dot <= 0) {
            return false;
        }
        for (String seg : lower.substring(0, dot).split("\\.")) {
            if (seg.equals("ad") || seg.equals("ads") || seg.equals("adx")
                    || seg.startsWith("advert") || seg.startsWith("adnet")
                    || seg.contains("promotion")
                    || seg.contains("mobileads") || seg.contains("splashad")) {
                return true;
            }
        }
        return false;
    }

    private static boolean adToken(String simple) {
        if (simple.contains("Advert") || simple.contains("Promotion")
                || simple.contains("SplashAd")) {
            return true;
        }
        for (int i = 0; i + 1 < simple.length(); i++) {
            if (simple.charAt(i) != 'A'
                    || (simple.charAt(i + 1) != 'd' && simple.charAt(i + 1) != 'D')) {
                continue;
            }
            int after = i + 2;
            if (after == simple.length()
                    || Character.isUpperCase(simple.charAt(after))) {
                return true;
            }
            // AdsXxx / ADSxxx 同样按广告词处理
            if ((simple.charAt(after) == 's' || simple.charAt(after) == 'S')
                    && (after + 1 == simple.length()
                    || Character.isUpperCase(simple.charAt(after + 1)))) {
                return true;
            }
        }
        return false;
    }

    private static String simpleName(String name) {
        int i = name.lastIndexOf('.');
        return i >= 0 ? name.substring(i + 1) : name;
    }
}

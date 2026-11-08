package com.quietweibo.app;

/**
 * 功能清单。key 同时是模块 App 内 remote prefs 的键，也是微博进程
 * getRemotePreferences("config") 的键，两处必须一致。
 *
 * <p>id 是统一功能编号（AD + 两位序号），只用于文档、日志前缀、诊断页与
 * CHANGELOG；remote key、类名不随编号变化。
 *
 * <p>blockedReason 非空表示本版本不提供实现：保留编号与条目、默认关闭、
 * 不注册 hook、不在设置页渲染。
 *
 * <p>默认值：广告净化默认开启。所有功能都只 Hook 微博自己的业务类，
 * 不碰广告 SDK 内部，也不伪造会员状态。
 */
public enum Feature {

    SPLASH("AD-01", "ad_splash",
            R.string.f_ad_splash_title, R.string.f_ad_splash_summary,
            true, Group.AD, null),
    HOT_SPLASH("AD-02", "ad_hot_splash",
            R.string.f_ad_hot_splash_title, R.string.f_ad_hot_splash_summary,
            true, Group.AD, null),
    FEED_AD("AD-03", "ad_feed",
            R.string.f_ad_feed_title, R.string.f_ad_feed_summary,
            true, Group.AD, null),
    HOME_TOP_AD("AD-04", "ad_home_top",
            R.string.f_ad_home_top_title, R.string.f_ad_home_top_summary,
            true, Group.AD, null),
    PULL_PAUSE_AD("AD-05", "ad_pull_pause",
            R.string.f_ad_pull_pause_title, R.string.f_ad_pull_pause_summary,
            true, Group.AD, null),
    OVERLAY_AD("AD-06", "ad_overlay",
            R.string.f_ad_overlay_title, R.string.f_ad_overlay_summary,
            true, Group.AD, null),
    DISCOVER_AD("AD-07", "ad_discover",
            R.string.f_ad_discover_title, R.string.f_ad_discover_summary,
            true, Group.AD, null),
    SEARCH_AD("AD-08", "ad_search",
            R.string.f_ad_search_title, R.string.f_ad_search_summary,
            true, Group.AD, null),
    EXIT_AD("AD-09", "ad_exit",
            R.string.f_ad_exit_title, R.string.f_ad_exit_summary,
            true, Group.AD, null),
    REORDER_AD("AD-10", "ad_reorder",
            R.string.f_ad_reorder_title, R.string.f_ad_reorder_summary,
            true, Group.AD, null),
    FLOAT_AD("AD-11", "ad_float",
            R.string.f_ad_float_title, R.string.f_ad_float_summary,
            true, Group.AD, null),
    SHAKE_AD("AD-12", "ad_shake",
            R.string.f_ad_shake_title, R.string.f_ad_shake_summary,
            true, Group.AD, null),
    AD_PRELOAD("AD-13", "ad_preload",
            R.string.f_ad_preload_title, R.string.f_ad_preload_summary,
            true, Group.AD, null),
    DYNAMIC("AD-14", "ad_dynamic",
            R.string.f_ad_dynamic_title, R.string.f_ad_dynamic_summary,
            true, Group.AD, null);

    public final String id;
    public final String key;
    public final int titleRes;
    public final int summaryRes;
    /** 编译期内置默认值：无 PROP_CAP_REMOTE 时使用。 */
    public final boolean defaultEnabled;
    public final Group group;
    /** 非空 = 本版本不注册任何 hook。 */
    public final String blockedReason;

    Feature(String id, String key, int titleRes, int summaryRes,
            boolean defaultEnabled, Group group, String blockedReason) {
        this.id = id;
        this.key = key;
        this.titleRes = titleRes;
        this.summaryRes = summaryRes;
        this.defaultEnabled = defaultEnabled;
        this.group = group;
        this.blockedReason = blockedReason;
    }

    public boolean blocked() {
        return blockedReason != null;
    }

    public enum Group {
        AD, UI, PV, EN, EX
    }
}

package com.quietweibo.app.hooks;

import com.quietweibo.app.Feature;
import com.quietweibo.app.HookState;

import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.libxposed.api.XposedModule;

/**
 * 卡片列表广告。
 *
 * <p>微博的“发现/热点”页、榜单页、个人页卡片流等并不走 {@code BaseFlowListData}，
 * 而是 {@code com.sina.weibo.models.CardList} 或 {@code CardSearch}：
 * <ul>
 *   <li>{@code getCardList()} → {@code List<PageCardInfo>}：卡片广告在这里；</li>
 *   <li>{@code getCardMblogStatusList()} → {@code List<Status>}：卡片里的广告微博；</li>
 *   <li>{@code getHotwordList()} → {@code List<SearchHotword>}：
 *       “猜你想搜/大家正在搜”和热搜里的推广词。</li>
 * </ul>
 *
 * <p>热搜推广词的判定不只看 {@code promotion} 字符串：服务端还会用
 * {@code note_tag.text == "商"}（商业热搜）或 {@code note == "商"} 做标记，
 * 这类词没有 promotion、但同样是广告。三种标记命中任意一种就移除。
 *
 * <p>AD-03 与 AD-07 都会尝试安装，进程内只装一次；先启用的功能负责安装。
 */
final class CardListHooks {

    private static final String CARD_LIST = "com.sina.weibo.models.CardList";
    private static final String CARD_SEARCH = "com.sina.weibo.card.model.CardSearch";
    private static final String HOTWORD = "com.sina.weibo.models.SearchHotword";
    private static final String DISCOVER_SEARCH =
            "com.sina.weibo.page.discoverv2.DiscoverSearchServiceV2";

    /** 带 {@code getHotwordList()} 的载体类；更新后若改名，只需在这里补候选。 */
    private static final String[] HOTWORD_HOLDERS = {CARD_LIST, CARD_SEARCH};

    /** note/note_tag 里表示商业推广的短标签。 */
    private static final String[] AD_TAGS = {"商", "广告", "推广", "赞助"};

    private static final AtomicBoolean sInstalled = new AtomicBoolean();

    private CardListHooks() {
    }

    static void install(XposedModule module, Feature f, ClassLoader cl) {
        if (sInstalled.get()) {
            return;
        }
        synchronized (CardListHooks.class) {
            if (sInstalled.get()) {
                return;
            }
            boolean any = false;
            FeedAdHooks.AdFilter filter = FeedAdHooks.AdFilter.create(module, f, cl);
            Class<?> cardList = HookKit.findClassOptional(module, f, cl, CARD_LIST);
            if (cardList != null) {
                any |= installFilter(module, f, cardList, "getCardList", filter);
                any |= installFilter(module, f, cardList, "getCardMblogStatusList", filter);
            }
            for (String holder : HOTWORD_HOLDERS) {
                Class<?> cls = HookKit.findClassOptional(module, f, cl, holder);
                if (cls != null) {
                    any |= installHotwordFilter(module, f, cl, cls);
                }
            }
            Class<?> discoverSearch = HookKit.findClassOptional(module, f, cl, DISCOVER_SEARCH);
            if (discoverSearch != null) {
                any |= installDiscoverHotwordFilters(module, f, cl, discoverSearch);
            }
            if (any) {
                sInstalled.set(true);
                HookState.note(f, "卡片列表：CardList/CardSearch/发现页热搜推广词过滤");
            }
        }
    }

    private static boolean installFilter(XposedModule module, Feature f, Class<?> cardList,
                                         String methodName, FeedAdHooks.AdFilter filter) {
        Method method = HookKit.findMethodQuiet(cardList, methodName);
        return HookKit.install(module, f, "CardList#" + methodName, method, chain -> {
            Object result = chain.proceed();
            if (!(result instanceof List)) {
                return result;
            }
            try {
                List<?> filtered = filter.apply((List<?>) result);
                if (filter.lastRemoved > 0) {
                    HookKit.hit(module, f, "CardList#" + methodName + " removed="
                            + filter.lastRemoved + " last=" + filter.lastRemovedClass);
                }
                return filtered;
            } catch (Throwable t) {
                HookKit.error(module, f.id + " CardList filter failed", t);
                return result;
            }
        });
    }

    private static boolean installHotwordFilter(XposedModule module, Feature f, ClassLoader cl,
                                                Class<?> holder) {
        Class<?> hotword;
        Method getPromotion;
        Method getNoteTag;
        Method getTagText;
        Method getNote;
        try {
            hotword = Class.forName(HOTWORD, false, cl);
            getPromotion = hotword.getMethod("getPromotion");
            getPromotion.setAccessible(true);
            getNote = methodOrNull(hotword, "getNote");
            getNoteTag = methodOrNull(hotword, "getNoteTag");
            getTagText = getNoteTag == null ? null
                    : methodOrNull(getNoteTag.getReturnType(), "getText");
        } catch (Throwable t) {
            HookKit.warn(module, f.id + " skip: SearchHotword markers unavailable");
            HookState.markNotFound(f, "热搜词标记不可用");
            return false;
        }
        Method method = HookKit.findMethodQuiet(holder, "getHotwordList");
        String label = holder.getSimpleName() + "#getHotwordList";
        return HookKit.install(module, f, label, method, chain -> {
            Object result = chain.proceed();
            if (!(result instanceof List)) {
                return result;
            }
            try {
                List<?> list = (List<?>) result;
                int removed = 0;
                for (int i = list.size() - 1; i >= 0; i--) {
                    Object item = list.get(i);
                    if (item == null || !hotword.isInstance(item)) {
                        continue;
                    }
                    if (isPromotedHotword(item, getPromotion, getNote, getNoteTag, getTagText)) {
                        list.remove(i);
                        removed++;
                    }
                }
                if (removed > 0) {
                    HookKit.hit(module, f, label + " removed=" + removed);
                }
                return list;
            } catch (Throwable t) {
                HookKit.error(module, f.id + " hotword filter failed", t);
                return result;
            }
        });
    }

    /**
     * 发现页热搜列表不走 CardList/CardSearch，而是由 DiscoverSearchServiceV2
     * 直接接收 {@code List<SearchHotword>}。在它更新 UI 前原地过滤。
     */
    private static boolean installDiscoverHotwordFilters(XposedModule module, Feature f,
                                                         ClassLoader cl, Class<?> service) {
        Class<?> hotword;
        Method getPromotion;
        Method getNote;
        Method getNoteTag;
        Method getTagText;
        try {
            hotword = Class.forName(HOTWORD, false, cl);
            getPromotion = hotword.getMethod("getPromotion");
            getPromotion.setAccessible(true);
            getNote = methodOrNull(hotword, "getNote");
            getNoteTag = methodOrNull(hotword, "getNoteTag");
            getTagText = getNoteTag == null ? null
                    : methodOrNull(getNoteTag.getReturnType(), "getText");
        } catch (Throwable t) {
            HookKit.warn(module, f.id + " skip: DiscoverSearchHotword markers unavailable");
            return false;
        }
        boolean any = false;
        for (String name : new String[]{"a", "b", "c"}) {
            Method method = HookKit.findMethodQuiet(service, name, List.class);
            if (method == null) {
                continue;
            }
            String label = "DiscoverSearch#" + name + "(List)";
            any |= HookKit.install(module, f, label, method, chain -> {
                try {
                    Object arg = chain.getArg(0);
                    if (arg instanceof List) {
                        List<?> list = (List<?>) arg;
                        int removed = 0;
                        for (int i = list.size() - 1; i >= 0; i--) {
                            Object item = list.get(i);
                            if (item != null && hotword.isInstance(item)
                                    && isPromotedHotword(item, getPromotion, getNote,
                                    getNoteTag, getTagText)) {
                                list.remove(i);
                                removed++;
                            }
                        }
                        if (removed > 0) {
                            HookKit.hit(module, f, label + " removed=" + removed);
                        }
                    }
                } catch (Throwable t) {
                    HookKit.error(module, f.id + " discover hotword filter failed", t);
                }
                return chain.proceed();
            });
        }
        return any;
    }

    /** 推广标记：promotion 非空，或 note/note_tag 打了“商/广告/推广/赞助”标签。 */
    private static boolean isPromotedHotword(Object item, Method getPromotion, Method getNote,
                                             Method getNoteTag, Method getTagText) {
        try {
            Object promotion = getPromotion.invoke(item);
            if (promotion instanceof String && !((String) promotion).isEmpty()) {
                return true;
            }
            if (getNote != null) {
                if (isAdTag(getNote.invoke(item))) {
                    return true;
                }
            }
            if (getNoteTag != null && getTagText != null) {
                Object tag = getNoteTag.invoke(item);
                if (tag != null && isAdTag(getTagText.invoke(tag))) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static boolean isAdTag(Object value) {
        if (!(value instanceof String)) {
            return false;
        }
        String text = ((String) value).trim();
        if (text.isEmpty()) {
            return false;
        }
        for (String tag : AD_TAGS) {
            if (text.equals(tag)) {
                return true;
            }
        }
        return false;
    }

    private static Method methodOrNull(Class<?> cls, String name) {
        try {
            Method m = cls.getMethod(name);
            m.setAccessible(true);
            return m;
        } catch (Throwable t) {
            return null;
        }
    }
}

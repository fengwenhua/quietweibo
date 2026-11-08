package com.quietweibo.app.hooks;

import com.quietweibo.app.Feature;
import com.quietweibo.app.HookState;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import io.github.libxposed.api.XposedModule;

/**
 * AD-03 信息流广告。
 *
 * <p>微博的流式页面（首页、推荐、发现、关注、个人页、正文页等）统一使用
 * {@code com.sina.weibo.flow.model.a}（BaseFlowListData）保存
 * {@code List<IFlowItem> items}。微博的广告微博是 {@code Status}，卡片广告是
 * {@code PageCardInfo}；两者都实现了 IFlowItem，直接混在 items 里。
 *
 * <p>广告标记（classes7/14）：
 * <ul>
 *   <li>{@code Status#isAd()} → {@code mblogtype == 1}；</li>
 *   <li>{@code Status#getPromotion()} 非空且 {@code Promotion#isInvalid() == false}
 *       → 服务端下发的推广单（注意 getPromotion() 对每个微博都非 null）；</li>
 *   <li>{@code Status#getAdObject() != null} → 广告物料对象；</li>
 *   <li>{@code Status.fromTopVision == true} → 大视窗广告插入位；</li>
 *   <li>{@code PageCardInfo#isAd()} → {@code is_ad_card == 1}。</li>
 * </ul>
 *
 * <p>在 {@code getItems()} 返回前把广告条目从列表里移除，比逐个隐藏 ViewHolder
 * 更稳定：不会留下空白卡槽，也不会破坏分页游标。列表不可修改时返回一个过滤后的
 * 副本作为兜底。
 */
final class FeedAdHooks {

    private static final String FLOW_LIST =
            "com.sina.weibo.flow.model.a";
    private static final String STATUS = "com.sina.weibo.models.Status";
    private static final String PAGE_CARD = "com.sina.weibo.card.model.PageCardInfo";
    private static final String FLOW_PARSER =
            "com.sina.weibo.models.gson.typeadapter.FeedFlowListTypeAdapter";
    private static final String JSON_READER = "com.google.gson.stream.JsonReader";

    /**
     * 服务端/卡片工厂里已知的广告卡片。部分广告卡片的 is_ad_card 为 0，
     * 只能按类名兜底。
     */
    private static final java.util.Set<String> AD_CARD_CLASSES =
            new java.util.HashSet<>(java.util.Arrays.asList(
                    "com.sina.weibo.card.CardCommonMediaAdInfo",
                    "com.sina.weibo.card.model.CardAdFoldInfo",
                    "com.sina.weibo.card.model.CardAdTitle",
                    "com.sina.weibo.card.model.WBXAdCardInfo",
                    "com.sina.weibo.card.model.CardFeedMoreFrameInfo",
                    "com.sina.weibo.card.model.CardVideoPromoter",
                    "com.sina.weibo.card.model.CardBrandHeader",
                    "com.sina.weibo.card.model.CardBrandBanner",
                    "com.sina.weibo.card.model.CardBrandActivity",
                    "com.sina.weibo.card.model.CardBrandTask"
            ));

    private FeedAdHooks() {
    }

    static void install(XposedModule module, ClassLoader cl) {
        Feature f = Feature.FEED_AD;
        try {
            AdFilter filter = AdFilter.create(module, f, cl);
            java.util.Set<Method> flowLists = findFlowListGetItems(cl);
            if (flowLists.isEmpty()) {
                HookKit.warn(module, f.id + " skip: no flow list getItems found");
                HookState.markNotFoundCritical(f, "未找到流列表 getItems");
                return;
            }
            for (Method getItems : flowLists) {
                installFlowListFilter(module, f, getItems, filter);
            }
            installParserHook(module, f, cl, filter);
            HookState.note(f, "信息流广告：流列表 getItems " + flowLists.size()
                    + " 处 + FeedFlowListTypeAdapter 过滤");
        } catch (Throwable t) {
            HookKit.error(module, f.id + " install aborted", t);
            HookState.note(f, "安装异常");
        }
    }

    /**
     * 跨版本定位流列表出口：类名 {@code com.sina.weibo.flow.model.a} 是混淆名，
     * 更新后可能是同包下的另一个短名；稳定的结构是“flow.model 包 + 无参
     * getItems() 返回 List”。扫描只读 DEX 类名 + 反射签名，命中多处时全部挂上，
     * 同一方法按 Method 去重。
     */
    private static java.util.Set<Method> findFlowListGetItems(ClassLoader cl) {
        java.util.Set<Method> out = new java.util.LinkedHashSet<>();
        Method known = getItems(classOrNull(cl, FLOW_LIST));
        if (known != null) {
            // 已知锚点存在时只用它：动态候选里可能有子类/同简名类，重复挂载会被框架拒绝。
            out.add(known);
            return out;
        }
        try {
            for (String name : DexInventory.classNames(cl)) {
                if (!name.startsWith("com.sina.weibo.flow.model.") || name.contains("$")) {
                    continue;
                }
                Method m = getItems(classOrNull(cl, name));
                if (m != null) {
                    out.add(m);
                }
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    private static Method getItems(Class<?> cls) {
        if (cls == null) {
            return null;
        }
        try {
            Method m = cls.getMethod("getItems");
            if (m.getParameterTypes().length == 0
                    && List.class.isAssignableFrom(m.getReturnType())) {
                m.setAccessible(true);
                return m;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static void installFlowListFilter(XposedModule module, Feature f, Method getItems,
                                              AdFilter filter) {
        String label = "FlowList#" + getItems.getDeclaringClass().getName() + "#getItems";
        HookKit.install(module, f, label, getItems, chain -> {
            Object result;
            try {
                result = chain.proceed();
            } catch (Throwable t) {
                HookKit.error(module, f.id + " getItems proceed failed", t);
                return chain.proceed();
            }
            if (!(result instanceof List)) {
                return result;
            }
            try {
                List<?> filtered = filter.apply((List<?>) result);
                if (filtered != result) {
                    HookKit.hit(module, f, "feed ad items removed (copy)=" + filter.lastRemovedClass);
                } else if (filter.lastRemoved > 0) {
                    HookKit.hit(module, f, "feed ad items removed=" + filter.lastRemoved
                            + " last=" + filter.lastRemovedClass);
                }
                return filtered;
            } catch (Throwable t) {
                HookKit.error(module, f.id + " filter failed", t);
                return result;
            }
        });
    }

    /**
     * 解析落库时再过滤一次 {@code items} 字段：有些页面直接读
     * {@code BaseFlowListData.items} 而不经过 getItems()，双保险。
     */
    private static void installParserHook(XposedModule module, Feature f, ClassLoader cl,
                                          AdFilter filter) {
        Class<?> reader = classOrNull(cl, JSON_READER);
        if (reader == null) {
            return;
        }
        java.util.Set<Method> parsers = findFlowParsers(cl, reader);
        for (Method doRead : parsers) {
            String label = "FlowParser#"
                    + doRead.getDeclaringClass().getSimpleName() + "#doRead";
            HookKit.install(module, f, label, doRead, chain -> {
                Object result = chain.proceed();
                try {
                    List<?> list = filter.itemsOf(result);
                    if (list != null) {
                        List<?> filtered = filter.apply(list);
                        if (filtered != list) {
                            filter.setItems(result, filtered);
                        }
                        if (filter.lastRemoved > 0) {
                            HookKit.hit(module, f, "feed ad items removed at parse="
                                    + filter.lastRemoved + " last=" + filter.lastRemovedClass);
                        }
                    }
                } catch (Throwable t) {
                    HookKit.error(module, f.id + " parse filter failed", t);
                }
                return result;
            });
        }
    }

    /**
     * 解析入口同样按结构找：{@code models.gson.typeadapter} 包内声明
     * {@code doRead(JsonReader)}、且类名带 Flow/Feed 的类型适配器。即使
     * {@code FeedFlowListTypeAdapter} 改名，同包的新适配器仍会被挂上。
     */
    private static java.util.Set<Method> findFlowParsers(ClassLoader cl, Class<?> reader) {
        java.util.Set<Method> out = new java.util.LinkedHashSet<>();
        addParser(out, classOrNull(cl, FLOW_PARSER), reader);
        try {
            for (String name : DexInventory.classNames(cl)) {
                if (!name.startsWith("com.sina.weibo.models.gson.typeadapter.")
                        || name.contains("$")) {
                    continue;
                }
                String simple = name.substring(name.lastIndexOf('.') + 1);
                if (!simple.contains("Flow") && !simple.contains("Feed")) {
                    continue;
                }
                addParser(out, classOrNull(cl, name), reader);
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    private static void addParser(java.util.Set<Method> out, Class<?> parser, Class<?> reader) {
        if (parser == null) {
            return;
        }
        Method m = HookKit.findMethodQuiet(parser, "doRead", reader);
        if (m != null) {
            m.setAccessible(true);
            out.add(m);
        }
    }

    private static Class<?> classOrNull(ClassLoader cl, String name) {
        try {
            return Class.forName(name, false, cl);
        } catch (Throwable t) {
            return null;
        }
    }

    /** 反射缓存微博的广告标记读取器；失败的功能按“非广告”放行。 */
    static final class AdFilter {

        /**
         * 跨版本兜底：广告模型的类名/方法名可能被混淆，但 Gson 的
         * {@code @SerializedName} 必须保留，否则服务端 JSON 解析不了。
         * 这里按序列化字段名识别广告，微博更新后只要字段语义不变就能命中。
         */
        private static final Set<String> AD_SERIALIZED_KEYS = new HashSet<>(Arrays.asList(
                "is_ad", "is_ad_card", "adid", "adType", "adtype", "ad_tag", "ad_scene",
                "ad_promotion", "ad_object", "ad_video", "ad_videoinfo", "ad_corner_mark",
                "ad_actionlog", "ad_actionlogs", "ad_click_actionlog", "promotion"
        ));

        private static final Map<Class<?>, List<Field>> AD_FIELD_CACHE =
                new ConcurrentHashMap<>();
        private static final Map<Class<?>, List<Method>> AD_METHOD_CACHE =
                new ConcurrentHashMap<>();

        private static final Set<String> AD_METHOD_NAMES = new HashSet<>(Arrays.asList(
                "isAd", "getAdTag", "getAdType", "getAdScene", "getAdPromotion",
                "getAdVideoInfo", "getAdCornerMarkData", "getAdObject", "getAdInfo"
        ));

        private static final String TREND = "com.sina.weibo.feed.detailrefactor.flow.h.b";
        private static final String COMMENT_FLOW =
                "com.sina.weibo.feed.detailrefactor.flow.comment.b";
        private static final String CARD_GROUP = "com.sina.weibo.card.model.CardGroup";
        private static final String CARD_GRID = "com.sina.weibo.card.model.CardGrid";
        private static final String CARD_GRID_ITEM = "com.sina.weibo.card.model.CardGridItem";
        private static final String CARD_MARQUEE = "com.sina.weibo.card.model.CardMarqueeAlpha";
        private static final String CARD285 = "com.sina.weibo.card.model.Card285Info";
        private static final String CARD_SPLICE = "com.sina.weibo.card.model.CardSpliceMultiple";
        private static final String CARD_SPLICE_ITEM =
                "com.sina.weibo.card.model.CardSpliceMultipleItem";

        private final Class<?> statusClass;
        private final Class<?> pageCardClass;
        private final Method statusIsAd;
        private final Method statusGetPromotion;
        private final Method promotionIsInvalid;
        private final Method statusGetAdObject;
        private final Field statusFromTopVision;
        private final Field itemsField;
        private final Method pageCardIsAd;

        /** 评论趋势卡（flow.h.b）：data.adType 是服务端广告标记。 */
        private final Class<?> trendClass;
        private final Method trendGetData;
        private final Method trendAdType;
        private final Method trendGetExposeData;
        /** 评论项（flow.comment.b）包着 JsonComment。 */
        private final Class<?> commentFlowClass;
        private final Method commentFlowGetData;
        private final Method jsonCommentExtraDesc;
        private final Field jsonCommentStatus;
        /** CardGroup：卡片组，广告大卡可能以组里的子卡形式出现。 */
        private final Class<?> cardGroupClass;
        private final Method cardGroupGetCards;
        /** CardGrid：发现页热搜下方的通栏广告可能是 Grid 里的 promotion item。 */
        private final Class<?> cardGridClass;
        private final Method cardGridGetItems;
        private final Method cardGridGetUnfoldItems;
        private final Method gridItemGetPromotion;
        private final Method gridItemGetType;
        private final Method gridItemGetLeftText;
        private final Method gridItemGetLeftTagImg;
        private final Method cardGridGetPosid;
        /** CardMarqueeAlpha：发现页热搜下方的轮播/通栏广告。 */
        private final Class<?> marqueeClass;
        private final Method marqueeGetAdTag;
        private final Method marqueeGetAdScene;
        private final Method marqueeGetItems;
        /** Card285：发现页两卡轮播广告，广告标记在 cover/video 的 ad_tag。 */
        private final Class<?> card285Class;
        private final Method card285GetImage;
        private final Method card285GetVideo;
        private final Method card285GetStatus;
        private final Method coverGetAdTag;
        private final Method videoGetAdTag;
        /** CardSpliceMultiple：发现页两卡拼图广告。 */
        private final Class<?> spliceClass;
        private final Method spliceGetAdTag;
        private final Method spliceGetAdScene;
        private final Method spliceGetSubItems;
        private final Class<?> spliceItemClass;
        private final Method spliceItemGetPromotion;
        private final Method spliceItemGetAdVideo;
        private final Method spliceItemGetCornerMark;

        /** 本次过滤移除的条数，仅用于日志。 */
        int lastRemoved;
        /** 最近一次移除的类名，便于诊断是哪种广告卡。 */
        String lastRemovedClass;

        AdFilter(Class<?> statusClass, Class<?> pageCardClass,
                 Method statusIsAd, Method statusGetPromotion, Method promotionIsInvalid,
                 Method statusGetAdObject, Field statusFromTopVision,
                 Field itemsField, Method pageCardIsAd,
                 Class<?> trendClass, Method trendGetData, Method trendAdType,
                 Method trendGetExposeData,
                 Class<?> commentFlowClass, Method commentFlowGetData,
                 Method jsonCommentExtraDesc, Field jsonCommentStatus,
                 Class<?> cardGroupClass, Method cardGroupGetCards,
                 Class<?> cardGridClass, Method cardGridGetItems,
                 Method cardGridGetUnfoldItems, Method gridItemGetPromotion,
                 Method gridItemGetType, Method gridItemGetLeftText,
                 Method gridItemGetLeftTagImg, Method cardGridGetPosid,
                 Class<?> marqueeClass, Method marqueeGetAdTag, Method marqueeGetAdScene,
                 Method marqueeGetItems,
                 Class<?> card285Class, Method card285GetImage, Method card285GetVideo,
                 Method card285GetStatus, Method coverGetAdTag, Method videoGetAdTag,
                 Class<?> spliceClass, Method spliceGetAdTag, Method spliceGetAdScene,
                 Method spliceGetSubItems,
                 Class<?> spliceItemClass, Method spliceItemGetPromotion,
                 Method spliceItemGetAdVideo, Method spliceItemGetCornerMark) {
            this.statusClass = statusClass;
            this.pageCardClass = pageCardClass;
            this.statusIsAd = statusIsAd;
            this.statusGetPromotion = statusGetPromotion;
            this.promotionIsInvalid = promotionIsInvalid;
            this.statusGetAdObject = statusGetAdObject;
            this.statusFromTopVision = statusFromTopVision;
            this.itemsField = itemsField;
            this.pageCardIsAd = pageCardIsAd;
            this.trendClass = trendClass;
            this.trendGetData = trendGetData;
            this.trendAdType = trendAdType;
            this.trendGetExposeData = trendGetExposeData;
            this.commentFlowClass = commentFlowClass;
            this.commentFlowGetData = commentFlowGetData;
            this.jsonCommentExtraDesc = jsonCommentExtraDesc;
            this.jsonCommentStatus = jsonCommentStatus;
            this.cardGroupClass = cardGroupClass;
            this.cardGroupGetCards = cardGroupGetCards;
            this.cardGridClass = cardGridClass;
            this.cardGridGetItems = cardGridGetItems;
            this.cardGridGetUnfoldItems = cardGridGetUnfoldItems;
            this.gridItemGetPromotion = gridItemGetPromotion;
            this.gridItemGetType = gridItemGetType;
            this.gridItemGetLeftText = gridItemGetLeftText;
            this.gridItemGetLeftTagImg = gridItemGetLeftTagImg;
            this.cardGridGetPosid = cardGridGetPosid;
            this.marqueeClass = marqueeClass;
            this.marqueeGetAdTag = marqueeGetAdTag;
            this.marqueeGetAdScene = marqueeGetAdScene;
            this.marqueeGetItems = marqueeGetItems;
            this.card285Class = card285Class;
            this.card285GetImage = card285GetImage;
            this.card285GetVideo = card285GetVideo;
            this.card285GetStatus = card285GetStatus;
            this.coverGetAdTag = coverGetAdTag;
            this.videoGetAdTag = videoGetAdTag;
            this.spliceClass = spliceClass;
            this.spliceGetAdTag = spliceGetAdTag;
            this.spliceGetAdScene = spliceGetAdScene;
            this.spliceGetSubItems = spliceGetSubItems;
            this.spliceItemClass = spliceItemClass;
            this.spliceItemGetPromotion = spliceItemGetPromotion;
            this.spliceItemGetAdVideo = spliceItemGetAdVideo;
            this.spliceItemGetCornerMark = spliceItemGetCornerMark;
        }

        static AdFilter create(XposedModule module, Feature f, ClassLoader cl) {
            Class<?> status = null;
            Class<?> pageCard = null;
            Method isAd = null;
            Method getPromotion = null;
            Method promotionIsInvalid = null;
            Method getAdObject = null;
            Field fromTopVision = null;
            Field itemsField = null;
            Method cardIsAd = null;
            try {
                status = Class.forName(STATUS, false, cl);
                isAd = method(status, "isAd");
                getPromotion = method(status, "getPromotion");
                // 注意：Status 对每个微博都会 new 一个 Promotion(null)，所以
                // getPromotion() 永远非 null；只能看 Promotion#isInvalid()。
                try {
                    promotionIsInvalid = method(getPromotion.getReturnType(), "isInvalid");
                } catch (Throwable ignored) {
                    // 版本差异时退化为不按推广单过滤。
                }
                getAdObject = method(status, "getAdObject");
                try {
                    Field field = status.getField("fromTopVision");
                    field.setAccessible(true);
                    fromTopVision = field;
                } catch (Throwable ignored) {
                    // 字段改名不影响其余广告标记。
                }
            } catch (Throwable t) {
                module.log(android.util.Log.WARN, HookKit.TAG,
                        f.id + " Status markers unavailable: " + t);
                HookState.markNotFound(f, "Status 标记不可用");
            }
            try {
                itemsField = findItemsField(cl);
            } catch (Throwable ignored) {
                // 字段不可用时只靠 getItems()。
            }
            try {
                pageCard = Class.forName(PAGE_CARD, false, cl);
                cardIsAd = method(pageCard, "isAd");
            } catch (Throwable t) {
                module.log(android.util.Log.WARN, HookKit.TAG,
                        f.id + " PageCardInfo markers unavailable: " + t);
            }

            Class<?> trend = classOrNull(cl, TREND);
            Method trendGetData = null;
            Method trendAdType = null;
            Method trendGetExpose = null;
            if (trend != null) {
                try {
                    trendGetData = method(trend, "getData");
                    trendAdType = method(trendGetData.getReturnType(), "c");
                    trendGetExpose = method(trend, "getExposeData");
                } catch (Throwable t) {
                    module.log(android.util.Log.WARN, HookKit.TAG,
                            f.id + " comment trend markers unavailable: " + t);
                }
            }

            Class<?> cardGroup = classOrNull(cl, CARD_GROUP);
            Method cardGroupGetCards = null;
            if (cardGroup != null) {
                try {
                    cardGroupGetCards = method(cardGroup, "getCardsList");
                } catch (Throwable ignored) {
                }
            }

            Class<?> cardGrid = classOrNull(cl, CARD_GRID);
            Method gridGetItems = null;
            Method gridGetUnfold = null;
            Method gridItemPromotion = null;
            Method gridItemType = null;
            Method gridItemLeftText = null;
            Method gridItemLeftTagImg = null;
            Method gridPosid = null;
            if (cardGrid != null) {
                try {
                    gridGetItems = method(cardGrid, "getGridItems");
                    gridGetUnfold = method(cardGrid, "getGridItemsUnfold");
                    gridPosid = method(cardGrid, "getPosid");
                    Class<?> gridItem = Class.forName(CARD_GRID_ITEM, false, cl);
                    gridItemPromotion = method(gridItem, "getAdPromotion");
                    gridItemType = method(gridItem, "getType");
                    gridItemLeftText = method(gridItem, "getItem_left_text");
                    gridItemLeftTagImg = method(gridItem, "getLeftTagImg");
                } catch (Throwable t) {
                    module.log(android.util.Log.WARN, HookKit.TAG,
                            f.id + " card grid markers unavailable: " + t);
                }
            }

            Class<?> marquee = classOrNull(cl, CARD_MARQUEE);
            Method marqueeAdTag = null;
            Method marqueeAdScene = null;
            Method marqueeItems = null;
            if (marquee != null) {
                try {
                    marqueeAdTag = method(marquee, "getAdTag");
                    marqueeAdScene = method(marquee, "getAdScene");
                    marqueeItems = method(marquee, "getItems");
                } catch (Throwable ignored) {
                }
            }

            Class<?> card285 = classOrNull(cl, CARD285);
            Method c285Image = null;
            Method c285Video = null;
            Method c285Status = null;
            Method coverTag = null;
            Method videoTag = null;
            if (card285 != null) {
                try {
                    c285Image = method(card285, "getImageInfo");
                    c285Video = method(card285, "getVideoInfo");
                    c285Status = method(card285, "getStatus");
                    coverTag = method(c285Image.getReturnType(), "getAdTag");
                    videoTag = method(c285Video.getReturnType(), "getAdTag");
                } catch (Throwable ignored) {
                }
            }

            Class<?> splice = classOrNull(cl, CARD_SPLICE);
            Method spliceTag = null;
            Method spliceScene = null;
            Method spliceSubItems = null;
            if (splice != null) {
                try {
                    spliceTag = method(splice, "getAdTag");
                    spliceScene = method(splice, "getAdScene");
                    spliceSubItems = method(splice, "getSubItems");
                } catch (Throwable ignored) {
                }
            }
            Class<?> spliceItem = classOrNull(cl, CARD_SPLICE_ITEM);
            Method spliceItemPromo = null;
            Method spliceItemVideo = null;
            Method spliceItemCorner = null;
            if (spliceItem != null) {
                try {
                    spliceItemPromo = method(spliceItem, "getPromotion");
                    spliceItemVideo = method(spliceItem, "getAdVideoInfo");
                    spliceItemCorner = method(spliceItem, "getAdCornerMarkData");
                } catch (Throwable ignored) {
                }
            }

            Class<?> commentFlow = classOrNull(cl, COMMENT_FLOW);
            Method commentGetData = null;
            Method jsonExtraDesc = null;
            Field jsonStatus = null;
            if (commentFlow != null) {
                try {
                    commentGetData = method(commentFlow, "getData");
                    Class<?> jsonComment = commentGetData.getReturnType();
                    jsonExtraDesc = method(jsonComment, "getExtraDesc");
                    jsonStatus = jsonComment.getField("status");
                    jsonStatus.setAccessible(true);
                } catch (Throwable t) {
                    module.log(android.util.Log.WARN, HookKit.TAG,
                            f.id + " comment markers unavailable: " + t);
                }
            }

            return new AdFilter(status, pageCard, isAd, getPromotion, promotionIsInvalid,
                    getAdObject, fromTopVision, itemsField, cardIsAd,
                    trend, trendGetData, trendAdType, trendGetExpose,
                    commentFlow, commentGetData, jsonExtraDesc, jsonStatus,
                    cardGroup, cardGroupGetCards,
                    cardGrid, gridGetItems, gridGetUnfold, gridItemPromotion, gridItemType,
                    gridItemLeftText, gridItemLeftTagImg, gridPosid,
                    marquee, marqueeAdTag, marqueeAdScene, marqueeItems,
                    card285, c285Image, c285Video, c285Status, coverTag, videoTag,
                    splice, spliceTag, spliceScene, spliceSubItems, spliceItem, spliceItemPromo,
                    spliceItemVideo, spliceItemCorner);
        }

        private static Method method(Class<?> cls, String name) throws NoSuchMethodException {
            Method m = cls.getMethod(name);
            m.setAccessible(true);
            return m;
        }

        /**
         * items 字段是正文流二次过滤的抓手：先按已知类名找，找不到再在
         * {@code flow.model} 包里找声明 {@code List items} 的类，避免类名混淆变化后失效。
         */
        private static Field findItemsField(ClassLoader cl) {
            Field field = itemsFieldOf(classOrNull(cl, FLOW_LIST));
            if (field != null) {
                return field;
            }
            try {
                for (String name : DexInventory.classNames(cl)) {
                    if (!name.startsWith("com.sina.weibo.flow.model.")
                            || name.contains("$")) {
                        continue;
                    }
                    field = itemsFieldOf(classOrNull(cl, name));
                    if (field != null) {
                        return field;
                    }
                }
            } catch (Throwable ignored) {
            }
            return null;
        }

        private static Field itemsFieldOf(Class<?> cls) {
            if (cls == null) {
                return null;
            }
            try {
                Field f = cls.getField("items");
                if (List.class.isAssignableFrom(f.getType())) {
                    f.setAccessible(true);
                    return f;
                }
            } catch (Throwable ignored) {
            }
            try {
                Field f = cls.getDeclaredField("items");
                if (List.class.isAssignableFrom(f.getType())) {
                    f.setAccessible(true);
                    return f;
                }
            } catch (Throwable ignored) {
            }
            return null;
        }

        /**
         * 原地移除广告条目；列表不可修改时返回过滤后的副本。
         * 返回值与入参相同表示原地完成。
         */
        List<?> apply(List<?> list) {
            lastRemoved = 0;
            lastRemovedClass = null;
            try {
                for (int i = list.size() - 1; i >= 0; i--) {
                    Object item = list.get(i);
                    if (isAd(item)) {
                        list.remove(i);
                        lastRemoved++;
                        if (item != null) {
                            lastRemovedClass = item.getClass().getName();
                        }
                    }
                }
                return list;
            } catch (UnsupportedOperationException e) {
                List<Object> copy = new ArrayList<>(list.size());
                for (Object item : list) {
                    if (!isAd(item)) {
                        copy.add(item);
                    } else {
                        lastRemoved++;
                    }
                }
                return copy;
            }
        }

        List<?> itemsOf(Object container) {
            if (container == null || itemsField == null) {
                return null;
            }
            try {
                Object value = itemsField.get(container);
                return value instanceof List ? (List<?>) value : null;
            } catch (Throwable t) {
                return null;
            }
        }

        void setItems(Object container, List<?> list) {
            if (container == null || itemsField == null) {
                return;
            }
            try {
                itemsField.set(container, list);
            } catch (Throwable ignored) {
                // 字段不可写时保持原列表，下一次 getItems() 仍会过滤。
            }
        }

        boolean isAd(Object item) {
            if (item == null) {
                return false;
            }
            try {
                if (AD_CARD_CLASSES.contains(item.getClass().getName())) {
                    return true;
                }
                if (isAdByAnnotations(item)) {
                    return true;
                }
                if (trendClass != null && trendClass.isInstance(item)) {
                    Object data = trendGetData == null ? null : trendGetData.invoke(item);
                    if (data != null && trendAdType != null) {
                        Object adType = trendAdType.invoke(data);
                        if (adType instanceof String && !((String) adType).isEmpty()) {
                            return true;
                        }
                    }
                    Object status = trendGetExposeData == null
                            ? null : trendGetExposeData.invoke(item);
                    return status != null && isAdStatus(status);
                }
                if (commentFlowClass != null && commentFlowClass.isInstance(item)) {
                    Object comment = commentFlowGetData == null
                            ? null : commentFlowGetData.invoke(item);
                    if (comment != null) {
                        if (jsonCommentExtraDesc != null) {
                            Object desc = jsonCommentExtraDesc.invoke(comment);
                            if (desc instanceof String && isAdText((String) desc)) {
                                return true;
                            }
                        }
                        if (jsonCommentStatus != null) {
                            Object status = jsonCommentStatus.get(comment);
                            if (status != null && isAdStatus(status)) {
                                return true;
                            }
                        }
                    }
                    return false;
                }
                if (spliceItemClass != null && spliceItemClass.isInstance(item)) {
                    if (spliceItemGetPromotion != null) {
                        Object promo = spliceItemGetPromotion.invoke(item);
                        if (promo instanceof String && !((String) promo).isEmpty()) {
                            return true;
                        }
                    }
                    if (spliceItemGetAdVideo != null
                            && spliceItemGetAdVideo.invoke(item) != null) {
                        return true;
                    }
                    if (spliceItemGetCornerMark != null
                            && spliceItemGetCornerMark.invoke(item) != null) {
                        return true;
                    }
                    return false;
                }
                if (spliceClass != null && spliceClass.isInstance(item)) {
                    if (spliceGetAdTag != null) {
                        Object tag = spliceGetAdTag.invoke(item);
                        if (tag instanceof String && !((String) tag).isEmpty()) {
                            return true;
                        }
                    }
                    if (spliceGetAdScene != null) {
                        Object scene = spliceGetAdScene.invoke(item);
                        if (scene instanceof String && !((String) scene).isEmpty()) {
                            return true;
                        }
                    }
                    if (spliceGetSubItems != null) {
                        Object raw = spliceGetSubItems.invoke(item);
                        if (raw instanceof List) {
                            List<?> children = (List<?>) raw;
                            boolean removed = false;
                            for (int i = children.size() - 1; i >= 0; i--) {
                                Object child = children.get(i);
                                if (child != null && isAd(child)) {
                                    children.remove(i);
                                    removed = true;
                                }
                            }
                            if (removed) {
                                return true;
                            }
                        }
                    }
                    return false;
                }
                if (card285Class != null && card285Class.isInstance(item)) {
                    if (card285GetImage != null && coverGetAdTag != null) {
                        Object info = card285GetImage.invoke(item);
                        if (info != null) {
                            Object tag = coverGetAdTag.invoke(info);
                            if (tag instanceof String && !((String) tag).isEmpty()) {
                                return true;
                            }
                        }
                    }
                    if (card285GetVideo != null && videoGetAdTag != null) {
                        Object info = card285GetVideo.invoke(item);
                        if (info != null) {
                            Object tag = videoGetAdTag.invoke(info);
                            if (tag instanceof String && !((String) tag).isEmpty()) {
                                return true;
                            }
                        }
                    }
                    if (card285GetStatus != null) {
                        Object status = card285GetStatus.invoke(item);
                        if (status != null && isAdStatus(status)) {
                            return true;
                        }
                    }
                    return false;
                }
                if (marqueeClass != null && marqueeClass.isInstance(item)) {
                    if (marqueeGetAdTag != null) {
                        Object tag = marqueeGetAdTag.invoke(item);
                        if (tag instanceof String && !((String) tag).isEmpty()) {
                            return true;
                        }
                    }
                    if (marqueeGetAdScene != null) {
                        Object scene = marqueeGetAdScene.invoke(item);
                        if (scene instanceof String && !((String) scene).isEmpty()) {
                            return true;
                        }
                    }
                    if (marqueeGetItems != null) {
                        Object raw = marqueeGetItems.invoke(item);
                        if (raw instanceof List) {
                            for (Object child : (List<?>) raw) {
                                if (child != null && isAd(child)) {
                                    return true;
                                }
                            }
                        }
                    }
                    return false;
                }
                if (cardGridClass != null && cardGridClass.isInstance(item)) {
                    boolean removedAny = false;
                    boolean allRemoved = true;
                    removedAny |= removePromoted(gridItems(cardGridGetItems, item));
                    removedAny |= removePromoted(gridItems(cardGridGetUnfoldItems, item));
                    List<?> main = gridItems(cardGridGetItems, item);
                    if (main != null && !main.isEmpty()) {
                        allRemoved = false;
                    }
                    if (removedAny) {
                        return allRemoved;
                    }
                    return false;
                }
                if (cardGroupClass != null && cardGroupClass.isInstance(item)) {
                    if (pageCardIsAd != null && Boolean.TRUE.equals(pageCardIsAd.invoke(item))) {
                        return true;
                    }
                    if (cardGroupGetCards != null) {
                        Object raw = cardGroupGetCards.invoke(item);
                        if (raw instanceof List) {
                            List<?> children = (List<?>) raw;
                            int removed = 0;
                            for (int i = children.size() - 1; i >= 0; i--) {
                                Object child = children.get(i);
                                if (child != null && isAd(child)) {
                                    children.remove(i);
                                    removed++;
                                }
                            }
                            return children.isEmpty();
                        }
                    }
                    return false;
                }
                if (statusClass != null && statusClass.isInstance(item)) {
                    return isAdStatus(item);
                }
                if (pageCardClass != null && pageCardClass.isInstance(item) && pageCardIsAd != null) {
                    return Boolean.TRUE.equals(pageCardIsAd.invoke(item));
                }
            } catch (Throwable ignored) {
                // 任一标记读取失败按“非广告”处理，避免误删正常内容。
            }
            return false;
        }

        private List<?> gridItems(Method getter, Object grid) {
            if (getter == null) {
                return null;
            }
            try {
                Object value = getter.invoke(grid);
                return value instanceof List ? (List<?>) value : null;
            } catch (Throwable t) {
                return null;
            }
        }

        /** 移除带 promotion 或“商/广告/推广”类型标记的 Grid item。 */
        private boolean removePromoted(List<?> list) {
            if (list == null) {
                return false;
            }
            boolean removed = false;
            try {
                for (int i = list.size() - 1; i >= 0; i--) {
                    Object it = list.get(i);
                    if (isGridItemAd(it)) {
                        list.remove(i);
                        removed = true;
                    }
                }
            } catch (Throwable ignored) {
                return false;
            }
            return removed;
        }

        private boolean isGridItemAd(Object it) throws Exception {
            if (it == null) {
                return false;
            }
            if (gridItemGetPromotion != null) {
                Object p = gridItemGetPromotion.invoke(it);
                if (p instanceof String && !((String) p).isEmpty()) {
                    return true;
                }
            }
            if (gridItemGetType != null) {
                Object t = gridItemGetType.invoke(it);
                if (t instanceof String && isAdTag((String) t)) {
                    return true;
                }
            }
            if (gridItemGetLeftText != null) {
                Object t = gridItemGetLeftText.invoke(it);
                if (t instanceof String && isAdTag((String) t)) {
                    return true;
                }
            }
            if (gridItemGetLeftTagImg != null) {
                Object t = gridItemGetLeftTagImg.invoke(it);
                if (t instanceof String && isAdImage((String) t)) {
                    return true;
                }
            }
            return false;
        }

        /** 按 Gson 序列化字段/广告 getter 做跨版本兜底。 */
        private static boolean isAdByAnnotations(Object item) {
            Class<?> cls = item.getClass();
            for (Class<?> c = cls; c != null && c != Object.class; c = c.getSuperclass()) {
                for (Field f : adFields(c)) {
                    try {
                        f.setAccessible(true);
                        if (isAdValue(f.get(item))) {
                            return true;
                        }
                    } catch (Throwable ignored) {
                    }
                }
                for (Method m : adMethods(c)) {
                    try {
                        m.setAccessible(true);
                        if (isAdValue(m.invoke(item))) {
                            return true;
                        }
                    } catch (Throwable ignored) {
                    }
                }
            }
            return false;
        }

        private static List<Field> adFields(Class<?> c) {
            return AD_FIELD_CACHE.computeIfAbsent(c, k -> {
                List<Field> out = new ArrayList<>();
                try {
                    for (Field f : k.getDeclaredFields()) {
                        if (serializedName(f) != null && AD_SERIALIZED_KEYS.contains(serializedName(f))) {
                            out.add(f);
                        }
                    }
                } catch (Throwable ignored) {
                }
                return out;
            });
        }

        private static List<Method> adMethods(Class<?> c) {
            return AD_METHOD_CACHE.computeIfAbsent(c, k -> {
                List<Method> out = new ArrayList<>();
                try {
                    for (Method m : k.getDeclaredMethods()) {
                        if (m.getParameterTypes().length == 0 && AD_METHOD_NAMES.contains(m.getName())) {
                            out.add(m);
                        }
                    }
                } catch (Throwable ignored) {
                }
                return out;
            });
        }

        private static String serializedName(Field f) {
            try {
                for (Annotation a : f.getAnnotations()) {
                    if (!"com.google.gson.annotations.SerializedName"
                            .equals(a.annotationType().getName())) {
                        continue;
                    }
                    Method value = a.annotationType().getMethod("value");
                    Object v = value.invoke(a);
                    return v instanceof String ? (String) v : null;
                }
            } catch (Throwable ignored) {
            }
            return null;
        }

        private static boolean isAdValue(Object v) {
            if (v == null) {
                return false;
            }
            if (v instanceof String) {
                return !((String) v).isEmpty();
            }
            if (v instanceof Boolean) {
                return (Boolean) v;
            }
            if (v instanceof Number) {
                return ((Number) v).intValue() != 0;
            }
            // Promotion 对象：空对象 isInvalid()==true，不能仅凭非 null 判定。
            if (v.getClass().getName().equals("com.sina.weibo.models.Promotion")) {
                try {
                    Method isInvalid = v.getClass().getMethod("isInvalid");
                    Object invalid = isInvalid.invoke(v);
                    return Boolean.FALSE.equals(invalid);
                } catch (Throwable ignored) {
                    return true;
                }
            }
            return true;
        }

        private boolean isAdStatus(Object status) {
            try {
                if (statusIsAd != null && Boolean.TRUE.equals(statusIsAd.invoke(status))) {
                    return true;
                }
                if (statusGetPromotion != null && promotionIsInvalid != null) {
                    Object promotion = statusGetPromotion.invoke(status);
                    if (promotion != null
                            && Boolean.FALSE.equals(promotionIsInvalid.invoke(promotion))) {
                        return true;
                    }
                }
                if (statusGetAdObject != null && statusGetAdObject.invoke(status) != null) {
                    return true;
                }
                return statusFromTopVision != null && statusFromTopVision.getBoolean(status);
            } catch (Throwable ignored) {
                return false;
            }
        }

        private static boolean isAdTag(String s) {
            return s.contains("商") || s.contains("广告")
                    || s.contains("推广") || s.contains("商业") || s.contains("赞助");
        }

        private static boolean isAdImage(String url) {
            // 热搜/榜单里的“商”角标是服务端下发的 left_tag_img；普通热/新角标
            // 走 hot_icon/type 文本，不使用这个字段。
            return url != null && !url.isEmpty();
        }

        private static boolean isAdText(String s) {
            return s.contains("广告") || s.contains("推广") || s.contains("赞助");
        }
    }
}

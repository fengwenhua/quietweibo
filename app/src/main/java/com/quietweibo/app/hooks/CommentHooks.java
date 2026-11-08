package com.quietweibo.app.hooks;

import com.quietweibo.app.Feature;
import com.quietweibo.app.HookState;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.libxposed.api.XposedModule;

/**
 * 评论流广告。
 *
 * <p>正文页评论列表来自 {@code com.sina.weibo.models.JsonCommentList#commentList}。
 * 16.10.3 的评论模型没有独立的“is_ad”字段，广告评论用以下方式标记：
 * <ul>
 *   <li>{@code JsonComment#extraDesc} 含“广告/推广/赞助”；</li>
 *   <li>{@code JsonComment#status} 是广告微博（promotion/ad_object/mblogtype）；</li>
 *   <li>{@code JsonComment#isCommentSupp() == 1} 时展示的是补充微博，
 *       该微博命中广告标记。</li>
 * </ul>
 * 只删命中广告标记的评论，普通评论原样保留。
 */
final class CommentHooks {

    private static final String COMMENT_LIST = "com.sina.weibo.models.JsonCommentList";
    private static final String COMMENT = "com.sina.weibo.models.JsonComment";
    private static final String JSON_OBJECT = "org.json.JSONObject";

    private static final AtomicBoolean sInstalled = new AtomicBoolean();

    private CommentHooks() {
    }

    static void install(XposedModule module, Feature f, ClassLoader cl) {
        if (!sInstalled.compareAndSet(false, true)) {
            return;
        }
        try {
            Class<?> listClass = HookKit.findClassOptional(module, f, cl, COMMENT_LIST);
            Class<?> commentClass = classOrNull(cl, COMMENT);
            Class<?> jsonObject = classOrNull(cl, JSON_OBJECT);
            if (listClass == null || commentClass == null || jsonObject == null) {
                sInstalled.set(false);
                return;
            }
            Field commentList = listClass.getField("commentList");
            commentList.setAccessible(true);
            Field statusField = commentClass.getField("status");
            statusField.setAccessible(true);
            Method extraDesc = commentClass.getMethod("getExtraDesc");
            extraDesc.setAccessible(true);
            FeedAdHooks.AdFilter adFilter = FeedAdHooks.AdFilter.create(module, f, cl);

            Method init = HookKit.findMethodQuiet(listClass, "initFromJsonObject", jsonObject);
            HookKit.install(module, f, "JsonCommentList#initFromJsonObject", init, chain -> {
                Object result = chain.proceed();
                try {
                    Object list = commentList.get(result);
                    if (!(list instanceof List)) {
                        return result;
                    }
                    List<?> comments = (List<?>) list;
                    int removed = 0;
                    for (int i = comments.size() - 1; i >= 0; i--) {
                        Object comment = comments.get(i);
                        if (comment == null || !commentClass.isInstance(comment)) {
                            continue;
                        }
                        if (isAdComment(comment, statusField, extraDesc, adFilter)) {
                            comments.remove(i);
                            removed++;
                        }
                    }
                    if (removed > 0) {
                        HookKit.hit(module, f, "comment ads removed=" + removed);
                    }
                } catch (Throwable t) {
                    HookKit.error(module, f.id + " comment filter failed", t);
                }
                return result;
            });
            HookState.note(f, "评论流：JsonCommentList 广告评论过滤");
        } catch (Throwable t) {
            sInstalled.set(false);
            HookKit.error(module, f.id + " comment hooks aborted", t);
            HookState.note(f, "安装异常");
        }
    }

    private static boolean isAdComment(Object comment, Field statusField, Method extraDesc,
                                       FeedAdHooks.AdFilter adFilter) {
        try {
            Object desc = extraDesc.invoke(comment);
            if (desc instanceof String) {
                String text = ((String) desc).toLowerCase(Locale.ROOT);
                if (text.contains("广告") || text.contains("推广") || text.contains("赞助")) {
                    return true;
                }
            }
            Object status = statusField.get(comment);
            return status != null && adFilter.isAd(status);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static Class<?> classOrNull(ClassLoader cl, String name) {
        try {
            return Class.forName(name, false, cl);
        } catch (Throwable t) {
            return null;
        }
    }
}

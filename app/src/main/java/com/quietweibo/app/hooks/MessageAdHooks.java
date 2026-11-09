package com.quietweibo.app.hooks;

import android.view.View;
import android.view.ViewGroup;

import com.quietweibo.app.Feature;
import com.quietweibo.app.HookState;

import java.lang.reflect.Method;

import io.github.libxposed.api.XposedModule;

/**
 * 消息页“动态”广告。
 *
 * <p>私信/动态列表使用 {@code com.sina.weibo.weiyou.feed.notice.DMNoticeAdapter}，
 * 广告项对应 {@code DMNoticeAd*CardItemView}。这些 View 只服务广告，
 * 在绑定 MessageFlow 后直接把自身隐藏并压成 0 高度即可。
 */
final class MessageAdHooks {

    private static final String MESSAGE_FLOW =
            "com.sina.weibo.weiyou.feed.models.MessageFlow";
    private static final String[] AD_ITEM_VIEWS = {
            "com.sina.weibo.weiyou.feed.itemview.DMNoticeAdCardItemView",
            "com.sina.weibo.weiyou.feed.itemview.DMNoticeAdMultiPicCardItemView",
            "com.sina.weibo.weiyou.feed.itemview.DMNoticeVideoAdCardItemView",
    };

    private MessageAdHooks() {
    }

    static void install(XposedModule module, Feature f, ClassLoader cl) {
        Class<?> flow = classOrNull(cl, MESSAGE_FLOW);
        if (flow == null) {
            return;
        }
        int count = 0;
        for (String name : AD_ITEM_VIEWS) {
            Class<?> cls = HookKit.findClassOptional(module, f, cl, name);
            if (cls == null) {
                continue;
            }
            Method bind = HookKit.findMethodQuiet(cls, "a", flow);
            if (bind == null) {
                continue;
            }
            count++;
            HookKit.install(module, f, simple(name) + "#a(MessageFlow)", bind, chain -> {
                Object result = chain.proceed();
                try {
                    Object self = chain.getThisObject();
                    if (self instanceof View) {
                        View view = (View) self;
                        view.setVisibility(View.GONE);
                        view.setMinimumHeight(0);
                        ViewGroup.LayoutParams params = view.getLayoutParams();
                        if (params != null) {
                            params.height = 0;
                            view.setLayoutParams(params);
                        }
                        if (view.getParent() instanceof View) {
                            ((View) view.getParent()).requestLayout();
                        }
                    }
                    HookKit.hit(module, f, simple(name) + " ad item hidden");
                } catch (Throwable ignored) {
                }
                return result;
            });
        }
        if (count > 0) {
            HookState.note(f, "消息动态：隐藏 DMNotice 广告项");
        }
    }

    private static String simple(String name) {
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

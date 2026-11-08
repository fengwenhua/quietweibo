package com.quietweibo.app.hooks;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** AD-14 动态扫描的类名判定：广告类要命中，普通 Load/Preload 服务不能误伤。 */
public class AdNameMatcherTest {

    @Test
    public void matchesKnownAdServices() {
        String[] names = {
                "com.sina.weibo.feedv2.home.service.ad.AdChannelService",
                "com.sina.weibo.discover.ad.DiscoverFlowAdService",
                "com.sina.weibo.search.SearchAdService",
                "com.sina.weibo.rank.RankAdService",
                "com.sina.weibo.home.AdPullPauseOperatorServiceV2",
                "com.sina.weibo.home.StreamAdInsertService",
                "com.sina.weibo.home.HomeAdTopVisionService",
                "com.sina.weibo.home.HomeZoomAdDialogService",
                "com.sina.weibo.feed.AdFeedExitWindowService",
                "com.sina.weibo.feed.AdReOrderService",
                "com.sina.weibo.mobileads.view.FlashAd",
                "com.sina.weibo.promotion.PromotionService",
                "com.sina.weibo.advert.AdvertManager",
                "com.sina.weibo.ads.internal.Network",
                "com.sina.weibo.ad.engine.Loader",
                "com.sina.weibo.foo.SplashAdHelper",
        };
        for (String name : names) {
            assertTrue(name, AdNameMatcher.looksLikeAdService(name));
        }
    }

    @Test
    public void ignoresPreloadAndLoadServices() {
        String[] names = {
                "com.sina.weibo.feedv2.home.service.didload.HomeDidLoadService",
                "com.sina.weibo.search.aipreload.SearchResultAiPreloadService",
                "com.sina.weibo.feedv2.home.service.pushpreload.PushPreloadService",
                "com.sina.weibo.detail.preload.DetailFeedPreloadService",
                "com.sina.weibo.video.preload.HVPreloadService",
                "com.sina.weibo.home.preload.TabFlowPreloadService",
                "com.sina.weibo.ai.card.AICardReadService",
                "com.sina.weibo.share.download.ShareDownloadService",
                "com.sina.weibo.play.record.PlayRecordParamUploadService",
                "com.sina.weibo.guide.DragGuideService",
                "com.sina.weibo.play.space.PlaySpaceService",
                "com.sina.weibo.debug.TestPositionService",
                "com.sina.weibo.airborne.AirborneUnreadService",
                "com.sina.weibo.comment.buttons.CommentButtonsPreLoadService",
                "com.sina.weibo.page.preload.BasePagePreloadService",
                "com.sina.weibo.scroll.preload.ScrollPreloadService",
                "com.sina.weibo.dm.unread.DMUnreadService",
                "com.sina.weibo.discover.unread.DiscoverUnreadService",
                "com.sina.weibo.profile.head.ProfileHeadService",
                "com.sina.weibo.read.recent.RecentlyReadService",
                "com.sina.weibo.immersion.head.ImmersionHeadService",
        };
        for (String name : names) {
            assertFalse(name, AdNameMatcher.looksLikeAdService(name));
        }
    }

    @Test
    public void ignoresInnerClassesAndForeignPackages() {
        assertFalse(AdNameMatcher.looksLikeAdService("com.sina.weibo.ad.AdService$1"));
        assertFalse(AdNameMatcher.looksLikeAdService("com.thirdparty.ad.AdService"));
        assertFalse(AdNameMatcher.looksLikeAdService(null));
    }

    @Test
    public void sensorAndSplashMatchers() {
        assertTrue(AdNameMatcher.looksLikeSensor("com.sina.weibo.mobileads.util.f"));
        assertTrue(AdNameMatcher.looksLikeSensor("com.sina.weibo.foo.ShakeSensor"));
        assertFalse(AdNameMatcher.looksLikeSensor("com.sina.weibo.foo.PreloadService"));

        assertTrue(AdNameMatcher.looksLikeSplash("com.sina.weibo.mobileads.view.FlashAd"));
        assertTrue(AdNameMatcher.looksLikeSplash("com.sina.weibo.startup.SplashManager"));
        assertFalse(AdNameMatcher.looksLikeSplash("com.sina.weibo.feed.HomeService"));
    }
}

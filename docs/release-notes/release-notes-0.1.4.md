# 0.1.4

继续修复发现页广告。

## 修复

- 微博热搜卡片按条目过滤：`CardHotSearchInfo.items` 中 `hot_text` 为
  “商/广告/推广/赞助”或 `status` 为广告的条目会被移除，普通热搜词保留。
- 发现页视频/图片轮播卡：`CardHeartBeatInfo.items[].ad_video` /
  `ad_logo_text` 命中时移除整个轮播。
- 补充 `HomeAdDialog` / `HomeDialogManager` 的倒计时弹窗入口。
- 保留 0.1.3 的 `CardCarousel`、`DiscoverSearchServiceV2` 和递归防护。

## 兼容性

- 最新已测试兼容：微博 **16.11.0（versionCode 8250）**
- 继续兼容：微博 16.10.3（8234）
- 设置页/关于页继续显示已测试/未测试兼容状态。

## 验证

- 82 个 JVM 单测通过
- 微博 16.11.0 静态锚点 60/60

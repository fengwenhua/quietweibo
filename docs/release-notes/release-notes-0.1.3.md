# 0.1.3

修复发现页广告，并加固详情页/图片浏览的稳定性。

## 修复

- 发现页微博热搜里的“商”推广词：新增 `DiscoverSearchServiceV2` 热搜列表过滤。
- 热搜下方的 ViewPager 轮播广告：新增 `CardCarousel -> CarouselItem -> subCards`
  递归识别，命中广告子卡时移除整个轮播。
- 倒计时关闭的发现页弹窗：补充 `DiscoverAdDialogV2#showAd` 展示入口拦截。
- 卡片递归识别增加深度上限和对象身份环检测，避免异常卡片结构导致
  `StackOverflowError` 使微博进程重启（针对 issue #1 的防御性修复）。

## 兼容性

- 最新已测试兼容：微博 **16.11.0（versionCode 8250）**
- 继续兼容：微博 16.10.3（8234）
- 设置页会显示当前微博版本是否已测试；未测试版本显示红色高亮横幅。
- 关于页「兼容版本」为可滚动列表，按从新到旧排列。

## 验证

- 82 个 JVM 单测通过
- 微博 16.11.0 静态锚点 60/60
- 发现页热搜横幅、热搜“商”词、发现页弹窗已修复

对应源码：[fengwenhua/quietweibo](https://github.com/fengwenhua/quietweibo)。

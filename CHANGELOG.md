# 更新日志

## 0.1.0 - 2026-11-08

首个版本，基于 QuietBox 工程骨架重写为微博（`com.sina.weibo`）去广告模块。

- 目标应用：微博 16.10.3（versionCode 8234）。
- 14 项广告净化：开屏、热启动、信息流、首页顶导、下拉悬停、大视窗/浮层/弹窗、
  发现页、搜索/榜单、退屏/切页、广告重排、视频悬浮、摇一摇/扭一扭、广告预加载、
  跨版本动态兜底（AD-14）。
- 信息流/卡片过滤依据：`Status#isAd`、`promotion`、`ad_object`、`fromTopVision`、
  `PageCardInfo#isAd`、`CardGridItem#getAdPromotion`、`CardMarqueeAlpha#getAdTag`、
  `CardSpliceMultiple(Item)`、`Card285Info` 的 `ad_tag`、
  `flow.h.b#getData().adType`（评论广告）、`JsonComment#getExtraDesc/status`。
- 增加 `BaseAdapterWrapper#collectAdapterDatas` 兜底：数据层没拦住的广告 ViewModel
  不会进入最终 adapter。
- 跨版本策略：
  - `DexInventory` 只读 `classes*.dex` 解析类名，进程内缓存，不加载/执行目标代码；
  - `AdNameMatcher` 用“包名段 + CamelCase 单词”识别广告类，修复
    `HomeDidLoadService`/`SearchResultAiPreloadService` 被 `...adService` 子串误伤的问题；
  - AD-14 动态挂载广告服务 `onInit*(IPagePresenter)`、广告传感器、开屏同构类；
  - 流列表/解析器按结构发现（`flow.model.*` 的 `getItems(): List`、
    `typeadapter.*Flow*#doRead`），混淆短名变化时仍能命中；
  - 热搜词同时过滤 `CardList` 与 `CardSearch`，识别 `promotion`、`note`、
    `note_tag.text` 的“商/广告/推广/赞助”标记。
- 修复冷启动卡死：不再跳过 `BizSplashActor#b`，改为在
  `FlashAd#loadAdWithLoadManager` 里主动回调 `onFailedToReceiveAd`，让启动流程走
  官方失败分支。
- 真机验证：OPPO PKH110 / Android 16 / LSPosed API 102；开屏、首页信息流、
  正文页底部广告、评论区广告、发现页热搜下方通栏广告均已移除。
- 更换应用图标：微博橙黄渐变 + 白色眼睛 + 深色斜杠，并支持 Android 13+ 主题图标。
- 修正 `proguard-rules.pro` 中残留的参考项目包名，避免 release/R8 误删入口类、
  `StatusProvider` 和自定义 Preference。
- 新增 `tools/check-anchors.py`，对 16.10.3 的 21 个应用 DEX 体检：
  60 个锚点全部 OK，0 个必需锚点缺失。
- 79 个 JVM 单测通过，`assembleDebug` 与 `lintDebug` 成功。
- 已知残留：H5/小程序内部网页广告（热搜“商”标已加入过滤，待逐页目视确认）。

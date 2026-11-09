# 更新日志

## 0.1.7 - 2026-11-10

- 发现页轮播卡隐藏后压成 0 高度，消除热搜下方的大块空白。
- 新增消息页“动态”广告过滤：隐藏 `DMNoticeAd*CardItemView`。
- 保留 0.1.6 的 `BaseCardView#setCardInfo` 卡片兜底，覆盖搜索结果等卡片广告。

## 0.1.6 - 2026-11-10

- 发现页四列热搜：过滤 `CardDiscoverHotSearchUnfoldInfo` 中带 promotion /
  “商”标签的 group。
- 新增 `BaseCardView#setCardInfo` 卡片视图兜底：即使页面绕过 adapter，
  也会过滤嵌套广告并把整体广告卡设为 GONE。
- 覆盖消息“动态”和搜索结果页中通过卡片 View 直接渲染的广告。
- 保留 0.1.4/0.1.5 的发现页轮播与倒计时弹窗修复。

## 0.1.4 - 2026-11-10

继续修复发现页广告。

- 微博热搜卡片改为按条目过滤：识别 `CardHotSearchInfo.items` 中
  `hot_text` 为“商/广告/推广/赞助”或 `status` 为广告的条目，只移除广告词。
- 发现页视频/图片轮播卡：识别 `CardHeartBeatInfo.items[].ad_video` /
  `ad_logo_text`，命中时移除整个轮播。
- 补充 `HomeAdDialog`/`HomeDialogManager` 倒计时弹窗入口，覆盖发现页倒计时广告。
- 保留 0.1.3 的 `CardCarousel`、`DiscoverSearchServiceV2` 和递归防护。
- 82 个 JVM 单测通过。

## 0.1.3 - 2026-11-10

修复发现页广告并加固详情页/图片浏览的稳定性。

- 修复发现页微博热搜里的“商”推广词：新增 `DiscoverSearchServiceV2` 列表过滤。
- 修复热搜下方的 ViewPager 轮播广告：新增 `CardCarousel -> CarouselItem -> subCards`
  递归识别，命中广告子卡时移除整个轮播。
- 补充 `DiscoverAdDialogV2#showAd` 展示入口拦截，覆盖倒计时弹窗的另一条路径。
- 为卡片递归识别增加深度上限和对象身份环检测，避免异常卡片结构导致
  `StackOverflowError` 使宿主进程重启（针对 issue #1 的防御性修复）。
- 82 个 JVM 单测通过。

## 0.1.2 - 2026-11-08

改进兼容版本的展示方式，避免版本增多后对话框放不下。

- 关于页“兼容版本”改为可滚动的嵌套页面，按从新到旧列出全部已测试版本；
- 支持系统返回键/页面内“返回关于页”回到关于主页；
- 版本列表随清单增长自然滚动，不再受对话框高度限制；
- 保持 0.1.1 的微博 16.11.0 兼容性判断和红色未测试横幅；
- 82 个 JVM 单测通过。

## 0.1.1 - 2026-11-08

适配微博 16.11.0，并增加版本兼容性可视化。

- 目标应用：微博 16.11.0（versionCode 8250）；16.10.3 继续保留在兼容清单。
- 对 16.11.0 的 20 个应用 DEX 复验：60/60 锚点通过。
- 真机日志确认 14 个功能全部挂载；AD-14 动态识别 23 个广告服务、1 个传感器。
- 设置页顶部状态卡新增兼容性判断：
  - 已测试版本显示“兼容性：已测试（最新兼容 16.11.0）”；
  - 未测试版本显示“兼容性：未测试”，并显示红色高亮横幅，提示仍按尽力兼容运行。
- 关于页新增“兼容版本”入口，点击按从新到旧展示已测试版本，最新版本带“最新”标记。
- 新增 `VersionGateTest`，覆盖兼容清单顺序、已测试判断和 fail-open 范围。
- 82 个 JVM 单测通过。

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

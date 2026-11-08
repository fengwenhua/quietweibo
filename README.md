# 微博静净 (QuietWeibo)

面向微博 Android 客户端（`com.sina.weibo`）的 LSPosed 去广告模块。基于
[libxposed](https://github.com/libxposed) Modern API 102，纯 Java 实现，独立 APK，
不需要改包、不需要 Xposed 旧 API。

> 当前版本 **0.1.2**，目标应用 **微博 16.11.0（versionCode 8250）**。
> 已对 16.11.0 的 20 个应用 DEX 校验 **60/60 锚点**，并在真机日志确认全部功能挂载；
> 16.10.3 为最初的静态分析样本，同样在兼容清单内。
> 除静态锚点外，AD-14 会在运行时扫描 DEX 类名/结构补挂载点，尽量保证微博升级后仍可用。
> ⚠️ 本项目与微博官方无任何关联，仅供学习、研究和个人设备使用。
> 使用前请阅读文末的[免责声明](#免责声明)。

工程骨架来自 [静盒 (QuietBox)](https://github.com/yigenhuobah/quietbox)（MIT）。
Hook 目标来自对微博 16.10.3 / 16.11.0 `base.apk` 的独立静态分析。

---

## 一、微博的广告画像与会员权益

### 1. 会员权益里能确认的“免广告”范围

微博会员（VIP / SVIP）没有“一键关闭全部广告”的本地开关，权益由服务端按投放类型
决定。结合官方权益说明和公开实测：

| 权益 | 效果 | 客户端表现 |
|---|---|---|
| 信息流广告减免 | 主要减少**竞价广告**，竞价单数量明显下降 | `Status.mblogtype == 1` / `promotion != null` 的条目变少 |
| 影音前置广告 | 会员可跳过视频前贴 | 视频播放器不再等待贴片倒计时 |
| 开屏广告 | 通常只是“可快速跳过”，**不会完全消失** | `FlashAd` 仍会创建，只是跳过按钮更积极 |
| 合约/品牌广告 | **不在免除范围** | 置顶热搜、活动 Banner、品牌大视窗仍会下发 |

所以本模块**不伪造会员状态**，而是像 QuietBox 系列模块一样在客户端逐个广告位拦断：
效果等价于“会员在客户端能感知到的免广告”，同时不改变账号身份、不拦截网络请求。

### 2. 16.10.3 的广告位（DEX 中确认）

| 广告位 | 关键类 / 入口 | 说明 |
|---|---|---|
| 开屏/开机 | `com.sina.weibo.startup.actor.a.b`（BizSplashActor）、`mobileads.view.FlashAd` | 冷启动创建并加载 `FlashAd`，加载完成后 `showFromLoadManager` |
| 热启动开屏 | `com.sina.weibo.utils.cd` | 回前台判定、后台预加载、实时广告直接展示 |
| 信息流 | `com.sina.weibo.flow.model.a` 的 `List<IFlowItem>` | 广告微博是 `Status`，广告卡是 `PageCardInfo`；标记见下 |
| 首页顶导 | `feedv2.home.service.ad.AdChannelService` | 首页顶部导航广告位 |
| 下拉悬停 | `AdPullPauseOperatorServiceV2` / `DiscoverAdPullPauseOperatorServiceV2` | 首页/发现页下拉时插入的悬停广告 |
| 大视窗/浮层/弹窗 | `HomeAdTopVisionService`、`StreamAdInsertService`、`HomeZoomAdDialogService`、`AdVisionFloatAnimateService`、`HomeZoomAdAnimServise` | “实时首页浮层”“大视窗”“首页弹窗” |
| 发现页 | `DiscoverFlowAdService`、`com.sina.weibo.biz.discover.d` | 发现页广告流、浮层、`DiscoverAdDialogV2` |
| 搜索/榜单 | `SearchAdService`、`RankAdService` | 搜索结果广告、榜单页广告 |
| 退屏/切页 | `AdFeedExitWindowService`、`AdFeedStreamService` | 离开页面时的广告窗口/流 |
| 广告重排 | `AdReOrderService` | 按位置把广告重排进信息流 |
| 视频悬浮 | `com.sina.weibo.biz.e`、`FloatViewFactory` | 视频/直播广告悬浮窗（`AdVisionFloatHandler` 的混淆名） |
| 摇一摇/扭一扭 | `mobileads.util.f/e/g` | 广告点击区域的加速度/旋转传感器 |
| 预加载 | `RefreshService#startFetchData` | 广告配置与物料的统一拉取出口 |

### 3. 广告标记（AD-03 的依据）

`com.sina.weibo.models.Status`：

- `isAd()` → `mblogtype == 1`（服务端标记的广告微博）
- `getPromotion() != null` → 推广单（`promotion` JSON）
- `getAdObject() != null` → 广告物料对象（`ad_object` JSON）
- `fromTopVision == true` → 大视窗广告插入位

`com.sina.weibo.card.model.PageCardInfo#isAd()` → `is_ad_card == 1`（广告卡片）。

---

## 二、参考项目分析

| 项目 | 学到什么 | 本模块怎么用 |
|---|---|---|
| **QuietBox** | libxposed API 102 入口、`Feature/Config/HookState/Report/StatusProvider` 骨架、remote prefs、逐功能 fail-open、诊断页、DeferredInstall、设置页状态四态 | **直接以此为基础**复制工程骨架并替换 Feature/Hook |
| **QuietMap** | Feature→Installer 注册表完整性校验；“零成功挂载才标 critical”；DexKit 只用于混淆类名 | 注册表/关键锚点判定沿用；未引入 DexKit（微博类名基本可读，混淆类用 DEX 真名） |
| **TiebaQuiet** | 锚点表 + 运行时反射校验、诊断状态机、展示层兜底 | 增加了 `tools/check-anchors.py`，对静态 DEX 校验全部 60 个锚点 |

与参考项目最大的差异：**微博没有整体加固**。r0dump 抓到的 21 个应用 DEX 与
`base.apk` 逐字节一致，因此不需要 NIS/易盾那套“等壳解密后再安装”的复杂逻辑；
`DeferredInstall` 只保留“立即试装 + 100ms 轮询兜底”。

---

## 三、功能清单

| 编号 | key | 功能 | 默认 |
|---|---|---|---|
| AD-01 | `ad_splash` | 冷启动开屏广告 | 开 |
| AD-02 | `ad_hot_splash` | 热启动/回前台开屏 | 开 |
| AD-03 | `ad_feed` | 信息流广告（含视频/卡片/详情流） | 开 |
| AD-04 | `ad_home_top` | 首页顶导广告 | 开 |
| AD-05 | `ad_pull_pause` | 下拉悬停广告（首页/发现页） | 开 |
| AD-06 | `ad_overlay` | 大视窗/浮层/弹窗广告 | 开 |
| AD-07 | `ad_discover` | 发现页广告流/弹窗 | 开 |
| AD-08 | `ad_search` | 搜索与榜单广告 | 开 |
| AD-09 | `ad_exit` | 退屏/切页广告 | 开 |
| AD-10 | `ad_reorder` | 广告重排/插卡 | 开 |
| AD-11 | `ad_float` | 视频/直播悬浮广告 | 开 |
| AD-12 | `ad_shake` | 摇一摇/扭一扭跳转 | 开 |
| AD-13 | `ad_preload` | 广告预加载/策略刷新断源 | 开 |
| AD-14 | `ad_dynamic` | 跨版本动态兜底（DEX 扫描补挂载点） | 开 |

实现原则：

- 只 Hook 微博自己的业务类，不碰广告 SDK 内部、不碰 `android.*` / `java.*`；
- 每个 `intercept` 整段 `try/catch(Throwable)`，异常时 `chain.proceed()` 放行；
- 广告服务只跳过 `onInit/onInitView`，不修改返回值、不伪造会员；
- 信息流过滤在 `getItems()` 返回前移除广告条目，列表不可修改时返回过滤副本；
- 开屏不跳过整个 `BizSplashActor`：让 `FlashAd` 正常创建，在
  `loadAdWithLoadManager` 里主动回调 `onFailedToReceiveAd` 并返回 false，
  启动流程走官方失败分支，不会卡启动页。

### 跨版本策略（“通杀”部分）

微博更新后最容易变的是混淆类名和方法名，因此除了固定锚点，模块还准备了几层
结构性兜底，任何一层失效都不会影响其它层：

| 层 | 做法 | 覆盖 |
|---|---|---|
| 数据模型 | Gson `@SerializedName` 字段/广告 getter 递归检测（`adid`、`ad_object`、`ad_tag`、`is_ad*`、`promotion` 等） | 字段级换名、卡片类换名 |
| 流列表 | 优先 `flow.model.a#getItems`；缺失时扫描 `com.sina.weibo.flow.model.*` 中无参 `getItems(): List` 的结构 | 混淆短名变化 |
| 解析器 | `models.gson.typeadapter.*` 中类名带 Flow/Feed 且声明 `doRead(JsonReader)` 的适配器全部挂载 | 解析入口改名 |
| 卡片 | `CardList` + `CardSearch` 的 `getHotwordList` 都挂钩；热搜词按 `promotion`、`note`、`note_tag.text` 的“商/广告/推广/赞助”标记过滤 | 商业热搜“商”标 |
| 广告服务 | `AdNameMatcher` 按包名段 + CamelCase 单词识别，再扫描 DEX 中声明 `onInit*(IPagePresenter)` 的类 | 新增/改名广告服务，且不误伤 `...LoadService`/`...PreloadService` |
| 适配器 | `BaseAdapterWrapper#collectAdapterDatas` 按 `ViewModel#getData()` 再筛一次 | 数据层遗漏的广告 ViewModel |
| 传感器 | DEX 中 `mobileads`/sensor/shake/twist 包的 `SensorEventListener` 动态 hook | 摇一摇/扭一扭改类名 |
| 版本门禁 | `16.10+` / `17+` 均放行，锚点缺失 fail-open 并显示诊断 | 新版本直接试跑 |

AD-14 只读取 APK 内 `classes*.dex` 的类名（不加载、不执行目标代码），一次解析后
进程内缓存；单个类失败只记日志，不影响其它挂载点。

### 兼容版本与提示

已测试兼容清单（从新到旧）：

| 微博版本 | versionCode |
|---|---|
| 16.11.0 | 8250 |
| 16.10.3 | 8234 |

设置页顶部状态卡会显示当前注入的微博版本属于哪一类：

- **已测试**：显示“兼容性：已测试（最新兼容 16.11.0）”；
- **未测试**：显示“兼容性：未测试”，并在状态卡下方显示红色高亮横幅；
  模块仍会按 16.10+ / 17+ 尽力兼容运行，方便先升级再反馈。

关于页新增“兼容版本”入口，点击后进入可滚动的嵌套页面，按从新到旧列出全部已测试版本，最新一条带“最新”标记；版本增多时页面自然滚动，不受对话框高度限制。

---

## 四、Hook 锚点（微博 16.10.3 / 16.11.0）

| 功能 | 锚点 |
|---|---|
| AD-01 | `FlashAd#loadAdWithLoadManager`（不发请求、主动回调 `onFailedToReceiveAd`）；`FlashAd#loadAndShowAd` |
| AD-02 | `com.sina.weibo.utils.cd#a(boolean,String)` / `b(Context)` / `c(Context)` |
| AD-03 | `FlowListData#getItems`；`FeedFlowListTypeAdapter#doRead`；`CardList#getCardList/getCardMblogStatusList/getHotwordList`；`CardGrid#getGridItems` + `CardGridItem#getAdPromotion`；`CardGroup#getCardsList`；`Status#isAd/getPromotion/getAdObject/fromTopVision`、`Promotion#isInvalid`；`PageCardInfo#isAd`；评论趋势 `flow.h.b#getData`（`adType`）、`flow.comment.b#getData` + `JsonComment#getExtraDesc/status`；`BaseAdapterWrapper#collectAdapterDatas` 兜底 |
| AD-04 | `AdChannelService#onInit` |
| AD-05 | `AdPullPauseOperatorServiceV2#onInit`、`DiscoverAdPullPauseOperatorServiceV2#onInit` |
| AD-06 | `HomeAdTopVisionService#onInit`、`StreamAdInsertService#onInit`、`HomeZoomAdDialogService#onInit`、`AdVisionFloatAnimateService#onInitView`、`HomeZoomAdAnimServise#onInitView` |
| AD-07 | `DiscoverFlowAdService#onInit`、`com.sina.weibo.biz.discover.d#show`；`CardMarqueeAlpha#getAdTag/getItems`（通栏广告）、`CardSpliceMultiple#getAdTag/getSubItems` + `CardSpliceMultipleItem#getPromotion/getAdVideoInfo`（拼图广告）、`Card285Info` 的 `cover/video#getAdTag` |
| AD-08 | `SearchAdService#onInit`、`RankAdService#onInitSafe` |
| AD-09 | `AdFeedExitWindowService#onInitView`、`AdFeedStreamService#onInitView` |
| AD-10 | `AdReOrderService#onInitView`、`DetailAdReOrderService#onInitView` |
| AD-11 | `com.sina.weibo.biz.e#a(Activity,int,int)`（Kotlin 原名 AdVisionFloatHandler）、`FloatViewFactory#getFloatView/getFloatViewModel`、`com.sina.weibo.floatvideo.b#j`（原名 AdVisionFloatManager） |
| AD-12 | `mobileads.util.f#f`、`mobileads.util.e#h`、`mobileads.util.g#a` |
| AD-13 | `RefreshService#startFetchData` |
| AD-14 | DEX 扫描：广告服务 `onInit*(IPagePresenter)`、广告 `SensorEventListener#onSensorChanged`、`loadAdWithLoadManager+getListener` 的开屏同构类 |

> 注意：`AdVisionFloatHandler` / `AdVisionFloatManager` 是 jadx 从 Kotlin Metadata
> 还原出的名字，DEX 里的真名分别是 `com.sina.weibo.biz.e` 和
> `com.sina.weibo.floatvideo.b`。Hooks 里两个名字都会尝试。

锚点体检：

```bash
python3 tools/check-anchors.py <dex 目录或文件...>
```

2026-11-08 对 16.10.3 的 21 个应用 DEX 体检结果：**60 个锚点全部 OK**；
2026-11-08 对 16.11.0 的 20 个应用 DEX 复验：**60 个锚点全部 OK，0 个必需锚点缺失**。

---

## 五、代码结构

```
app/src/main/java/com/quietweibo/app/
├── QuietWeiboModule.java      # libxposed 入口，仅目标包 + 主进程
├── DeferredInstall.java       # 立即试装 + 轮询兜底
├── Feature.java               # 14 个功能清单
├── Config.java                # 目标进程读 remote prefs
├── ConfigStore.java           # 模块 App 写 remote prefs
├── HookState.java             # 逐功能 reg/hit/skip/critical
├── hooks/
│   ├── Hooks.java             # Feature → Installer 注册表
│   ├── HookKit.java           # 反射定位、PROTECTIVE hook、hit 计数
│   ├── SplashHooks.java       # AD-01/02
│   ├── FeedAdHooks.java       # AD-03 数据层广告标记（Status/PageCard/Card*）
│   ├── CommentHooks.java      # AD-03 评论流广告
│   ├── CardListHooks.java     # AD-03/07 CardList/CardSearch 卡片与热搜推广词
│   ├── DetailFlowHooks.java   # AD-03 正文流 fetch 返回值过滤
│   ├── AdapterHooks.java      # AD-03 最终 adapter ViewModel 兜底
│   ├── ServiceHooks.java      # AD-04 ~ AD-10
│   ├── FloatAdHooks.java      # AD-11
│   ├── SensorHooks.java       # AD-12
│   ├── PreloadHooks.java      # AD-13
│   ├── DynamicHooks.java      # AD-14 动态补挂载点
│   ├── DexInventory.java      # 只读 classes*.dex 的类名清单（带缓存）
│   ├── AdNameMatcher.java     # 广告类名判定（包名段 + CamelCase 单词）
│   ├── Report.java            # 目标进程 → 模块 App 诊断回传
│   └── ...
├── StatusProvider.java        # 校验调用方 UID + 落盘诊断
├── SettingsFragment.java      # 设置页
├── DiagnosticsFragment.java   # 诊断页
└── AboutFragment.java         # 关于页
```

---

## 六、安装与使用

1. 安装 `app/build/outputs/apk/debug/app-debug.apk`（或 release 包）。
2. LSPosed 管理器中启用「微博静净」，作用域勾选 **微博**（`com.sina.weibo`）。
3. 强制停止微博后重新打开。
4. 模块 App 状态卡显示「已激活」即注入成功。
5. 按需开关；改动后强制停止并重启微博生效。

要求：Android 10+（minSdk 29）、支持 libxposed API 102 的 LSPosed / Vector。

---

## 七、构建与验证

工具链：AGP 9.4.1 + Gradle 9.7.1（wrapper）+ JDK 17/21/25 + compileSdk 37 / minSdk 29 / targetSdk 36。

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug
# debug 产物：app/build/outputs/apk/debug/app-debug.apk
```

当前本地结果：**82 个 JVM 单测通过，`assembleDebug` 成功**。

### CI 与正式发布

- `.github/workflows/build.yml`：push/PR 自动跑单测、Lint、debug 构建，并上传 debug APK。
- `.github/workflows/release.yml`：手动输入已提交的 `v版本` tag；build job 只产 unsigned APK，
  sign job 在受保护的 `release` 环境里签名，publish job 回读四个附件校验后发布。
- `.github/workflows/bootstrap-signing.yml`：一次性在 GitHub Runner 生成 release keystore 与
  manifest 密钥，私钥直接写入 `release` 环境 Secret，不落本地、不进 artifact。
- 正式 Release 四附件：`QuietWeibo-{version}.apk`、`.apk.sha256`、`release-manifest.json`、
  `release-manifest.sig`；主仓与 `Xposed-Modules-Repo/io.github.fengwenhua.quietweibo` 使用同一组附件。

首次初始化签名后，`release-trust.properties` 与 `.github/trust/release-certificate.pem`
必须提交；之后每次发布只需更新 `version.properties`、发布说明和 tag。

GitHub Releases：
<https://github.com/fengwenhua/quietweibo/releases>
Xposed 模块仓：
<https://github.com/Xposed-Modules-Repo/io.github.fengwenhua.quietweibo>

### 真机验证（2026-11-08）

设备：OPPO PKH110 / Android 16 / LSPosed API 102；目标微博 **16.11.0（8250）**，16.10.3 为上一版样本。

已确认：

- **冷启动**：不再卡启动页，正常进入 `MainTabActivity`。修复过程中发现，
  直接跳过 `BizSplashActor#b` 会导致广告加载回调永远不触发、启动流程卡死；
  现改为在 `FlashAd#loadAdWithLoadManager` 里主动回调 `onFailedToReceiveAd`。
- **首页信息流**：广告微博/广告卡片被移除。
- **正文页底部**：穿山甲/优量汇通栏广告移除。
- **评论区**：`UNIQLO` 等带“广告”标记的评论卡移除。
- **发现页**：热搜下方的两卡通栏广告移除。
- **热搜推广词**：已按 `promotion`、`note`、`note_tag.text` 的“商/广告/推广/赞助”
  标记过滤，覆盖 `CardList` 与 `CardSearch` 两个载体（待逐页目视确认）。

仍可能残留：H5/小程序内部网页广告。

已知限制：

- 已真机验证核心场景；静态锚点 60/60 只说明类/方法存在，其它页面仍需逐项验证。
- 广告服务被跳过的是 `onInit/onInitView`；如果某版本把广告初始化挪到别处，诊断页会显示
  `registered` 但 `hit 0`，可据此重新定位。
- 信息流过滤依据 `mblogtype/promotion/adObject/fromTopVision/is_ad_card`；若服务端换用
  新字段，广告可能漏过，但不会误删普通内容。
- H5/小程序内部的网页广告不在本模块范围；本模块不拦截网络、不修改账号数据。
- 与其它 Hook 微博的模块可能冲突，建议只保留一个。

---

## 八、许可证与免责声明

MIT。工程骨架来自 [QuietBox](https://github.com/yigenhuobah/quietbox)；
上游版权声明保留在 `LICENSE`。Hook 目标与文案来自对
微博 16.10.3 / 16.11.0 APK 的独立分析。

- 本项目仅供**学习、研究和个人设备使用**，不得用于商业用途、二次售卖或批量分发。
- 本项目与微博及其运营方**无任何隶属、授权或合作关系**。
- 本项目**不包含**目标应用的任何代码、资源或商标素材；不修改目标应用安装包；
  不拦截网络请求；不提供代理、后台服务或常驻进程。
- 使用本模块可能**违反目标应用的用户服务协议**。请自行确认使用场景是否合规。
- Hook 可能导致应用异常、崩溃、数据丢失或设备不稳定，**所有风险由使用者自行承担**。
- 如权利人认为本项目侵犯其权益，请通过 Issue 联系，作者会立即停止分发并删除相关内容。
- **下载后请于 24 小时内删除。如不同意以上任何一条，请不要下载或使用。**

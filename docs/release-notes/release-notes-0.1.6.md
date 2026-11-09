# 0.1.6

- 发现页四列热搜：过滤 `CardDiscoverHotSearchUnfoldInfo` 中带 promotion /
  “商”标签的 group。
- 新增 `BaseCardView#setCardInfo` 卡片视图兜底：即使页面绕过 adapter，
  也会过滤嵌套广告并把整体广告卡设为 GONE。
- 覆盖消息“动态”和搜索结果页中通过卡片 View 直接渲染的广告。
- 保留之前的发现页轮播、倒计时弹窗和递归防护修复。

# 微博静净 (QuietWeibo)

面向微博 Android 客户端的 LSPosed 去广告模块。基于 libxposed Modern API 102，
独立 APK，不需要修改微博安装包。

## 效果

- 移除开屏、热启动开屏广告
- 移除首页/发现/关注/正文信息流广告与广告卡片
- 移除评论区广告、正文页底部通栏广告
- 移除发现页热搜下方通栏广告
- 跳过首页顶导、下拉悬停、大视窗、浮层、弹窗、搜索、榜单、退屏、广告重排
- 关闭视频/直播悬浮广告、摇一摇/扭一扭广告跳转、广告预加载
- AD-14 动态扫描 DEX 类名，微博更新后尽量自动补挂载点

## 实测环境

**OPPO PKH110 · Android 16 · LSPosed API 102**，目标微博 **16.11.0**。
16.10.3 也在已测试兼容清单中；其它 16.10+ / 17+ 版本按尽力兼容运行。

已验证冷启动、首页信息流、正文页底部、评论区、发现页热搜下方广告位。
其它页面仍建议按实际使用逐项确认。

## 安装

1. 从 Releases 下载 APK。
2. 在 LSPosed 中启用「微博静净」，作用域勾选 **微博（`com.sina.weibo`）**。
3. 强制停止微博后重新打开。

要求 Android 10+、支持 libxposed API 102 的 LSPosed / Vector。

## 边界

- 不伪造会员状态、不拦截网络请求、不修改账号数据。
- 只 Hook 微博自己的业务类，不碰广告 SDK 内部。
- H5/小程序内部网页广告不在范围内。
- 与其它 Hook 微博的模块可能冲突，建议只保留一个。

## 源码与反馈

源码：[fengwenhua/quietweibo](https://github.com/fengwenhua/quietweibo)
问题反馈：[Issues](https://github.com/fengwenhua/quietweibo/issues)

本文件是模块仓首页的发布源文件，完整说明以源码仓 README 为准。

## 许可证

MIT。工程骨架来自 [QuietBox](https://github.com/yigenhuobah/quietbox)；
上游版权声明保留在源码仓 `LICENSE`。

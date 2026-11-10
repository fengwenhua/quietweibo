# 0.1.8

- 关于页新增「隐藏桌面图标」开关（issue #2）。
- 桌面入口改为独立 `activity-alias`，隐藏只禁用 alias；MainActivity 保留 `MODULE_SETTINGS` 入口，仍可从 LSPosed 管理器打开并恢复。
- 组件状态为唯一真相源，清数据或 adb 修改后回到关于页会自动同步。
- 新增 `LauncherIconTest`。

# Ribbon UI LDLib2 最佳实践修复设计

> **状态：** 已批准
> **日期：** 2026-07-31
> **范围：** `gui/ribbon/` 与 `gui/editor/ribbon/` 的 Ribbon 实现
> **目标：** 用 LDLib2 原生组件（TabView / Tab / Toggle / ToggleGroup / ScrollerView）替换当前 Button 拼凑实现，补齐 LSS 主题，消除全局静态状态。
> **关联文档：**
> - 原始 Ribbon 框架设计：`2026-07-31-ribbon-framework-design.md`
> - LDLib2 API 审计：`2026-07-27-ldlib2-api-verification.md`
> - 修复计划：`../plans/2026-07-31-ribbon-ldlib2-repair-plan.md`

---

## 1. 背景

2026-07-31 架构审计发现当前 Ribbon 实现存在 4 项 Critical 违规：

1. 选项卡用 `Button` 自建，未复用 `TabView`/`Tab`。
2. Toggle 工具用普通 `Button`，未使用 `Toggle`/`ToggleGroup`。
3. 完全缺失 LSS 样式表，视觉回退为默认 `Button`。
4. `KpViewContextProvider` 使用全局可变静态状态。

本 spec 定义修复后的架构与接口契约。

---

## 2. 目标与非目标

### 2.1 目标

- 选项卡基于 `TabView`/`Tab` 重建，获得原生选中态、横向滚动、内容切换。
- Tools tab 的 Pan/Select/Line/Bezier/Snap 使用 `ToggleGroup` 互斥选择。
- View tab 的布尔开关使用独立 `Toggle`。
- 新建 `assets/kinetic_planner/lss/kp.lss` 暗色主题。
- 清理 `KpViewContextProvider` 静态状态。
- 更新单元测试。

### 2.2 非目标

- FLOATING 模式、右键菜单、KeyTips、亮色主题（延后）。

---

## 3. 架构

数据定义层保持不变，仅重写 UI 构建层：

```
RibbonBar (UIElement)
└── TabView
    ├── tabContentContainer
    │   └── [选中 Tab 的 content 面板]
    └── tabHeaderContainer (ROW)
        ├── QatBar
        ├── LEADING RibbonHeaderComponent × N
        ├── tabScroller (ScrollerView)
        │   └── Tab × N
        └── TRAILING RibbonHeaderComponent × N
```

`TabView` 默认 `COLUMN_REVERSE`，因此 header 在上、content 在下。`tabScroller` 提供 tab 横向滚动。

---

## 4. 组件设计

### 4.1 `RibbonBar`

```java
public final class RibbonBar extends UIElement {
    private final TabView tabView;
    private final ViewContextProvider contextProvider;
    private final RibbonPreferences preferences;
    private final DefaultQuickAccessToolbar qat;
    private final Map<ResourceLocation, RibbonTabState> tabStates;

    public RibbonBar(ViewContextProvider contextProvider,
                     RibbonPreferenceStore preferenceStore) { ... }
}
```

- 不再 `clearAllChildren()` 重建；所有 tab/content 一次性构建，切换由 `TabView` 处理。
- 上下文变化时只修改 contextual tab 的 `display`，不重建。

### 4.2 `RibbonBuilder`

```java
public final class RibbonBuilder {
    public static void build(RibbonBar bar,
                             Map<ResourceLocation, RibbonTabState> tabStates,
                             DefaultQuickAccessToolbar qat,
                             Optional<ResourceLocation> preferredSelectedTabId);

    private static void buildHeader(TabView tabView, DefaultQuickAccessToolbar qat);
    private static void buildTabs(TabView tabView,
                                  Map<ResourceLocation, RibbonTabState> tabStates,
                                  RibbonBar bar);
}
```

- 核心 tabs 与 contextual tabs 统一构建。
- 每个 tab 的 content 是包含该 tab 所有 `GroupPanel` 的 `UIElement`。
- `TabView.setOnTabSelected(tab -> bar.onTabSelected(tab.getId()))`。

### 4.3 `ToolWidgetFactory`

```java
public final class ToolWidgetFactory {
    public static UIElement create(RibbonToolDefinition tool,
                                   @Nullable Toggle.ToggleGroup group);
}
```

- `ButtonAction` + `toggle=true` → `Toggle`，加入 `group`。
- `ButtonAction` + `toggle=false` → `Button`。
- `SeparatorAction` → 1px 竖线 `UIElement`。
- `Toggle.setOnToggleChanged(...)` 调用 `RibbonToggleCommand.setActive(boolean)`。

### 4.4 `GroupPanel`

```java
public final class GroupPanel extends UIElement {
    public static UIElement build(RibbonToolGroupDefinition group,
                                  @Nullable Toggle.ToggleGroup sharedGroup);
}
```

- 外壳 `FlexDirection.COLUMN`：上方是工具行，下方是组标签。
- 工具行 `FlexDirection.ROW`：工具横向排列。
- 如果整组工具都是互斥 toggle，传入同一个 `ToggleGroup`。

### 4.5 `QatBar`

- 使用图标优先：`Button.setIcon(IGuiTexture)` 或 `Button.preIcon(...)`。
- 无图标时显示显示名首字母。
- 空状态显示淡化星标图标。

### 4.6 `KpViewContextProvider`

移除 `static volatile boolean demoContextActive` 与 `INSTANCES`。改为：

```java
public final class KpViewContextProvider implements ViewContextProvider {
    private final Supplier<Set<ResourceLocation>> contextSupplier;
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();

    public KpViewContextProvider(Supplier<Set<ResourceLocation>> contextSupplier) { ... }
}
```

调用方 `KpMapEditor` 提供一个持有当前激活上下文集合的 supplier。

---

## 5. LSS 主题

新建 `src/main/resources/assets/kinetic_planner/lss/kp.lss`：

```lss
.kp-ribbon-bar {
    background: rect(#ff2c2c34);
}

.kp-ribbon-tab.__selected__ {
    background: sdf(#FF7C57D4, 0, 2, #FF7C57D4);
}

.kp-ribbon-tab:hover {
    background: sdf(#ffffff10, 0, 0);
}

.kp-ribbon-group {
    flex-direction: column;
    margin-horizontal: 4;
    padding-horizontal: 4;
}

.kp-ribbon-group-label {
    color: #ff9ca3af;
    height: 9;
}

.kp-ribbon-tool-row {
    flex-direction: row;
}

.kp-ribbon-tool.__on__ {
    background: sdf(#FF7C57D4, 0, 1, #FF7C57D4);
}

.kp-ribbon-qat {
    gap-column: 2;
}

.kp-ribbon-separator {
    width: 1;
    height: 100%;
    background: rect(#ff4b5563);
    margin-horizontal: 4;
}
```

注意：LDLib2 不支持 CSS 变量、`border-*` 简写、`padding` 简写；颜色用 `#AARRGGBB`；`:hover` 已支持。

---

## 6. 测试策略

### 6.1 单元测试

- `RibbonBuilderTest`：验证构建后 `TabView` 包含预期数量的 `Tab`；点击 tab 时 `TabView.getSelectedTab()` 变化。
- `ToolWidgetFactoryTest`：Tools tab 的 5 个工具属于同一个 `Toggle.ToggleGroup`；View tab 的开关无 group。
- `KpViewContextProviderTest`：传入固定 supplier，验证 `getActiveContexts()` 与监听器触发。

### 6.2 手动验收

1. 点击 tab 切换，内容面板正确显示对应工具组。
2. 当前选中 tab 有紫色背景/下划线。
3. Pan/Select/Line/Bezier/Snap 只有一个高亮。
4. View tab 开关独立，on/off 视觉反馈正确。
5. QAT 显示图标或首字母。
6. 窗口缩窄时 tab 可横向滚动。
7. 切换上下文，Ctx Tools tab 出现/消失。

---

## 7. 风险与规避

| 风险 | 规避 |
|---|---|
| `TabView.addTab` 首次自动触发 `selectTab` | 在 `setOnTabSelected` 回调中检查 content 是否非空 |
| LDLib2 `Tab` 反选时 `__tab_selected__` 类移除有 typo | 样式依赖 `__selected__` 类 |
| `ToggleGroup` 切换时旧 toggle 触发 `false` 回调 | `RibbonToggleCommand.setActive(false)` 优雅忽略 |
| LSS 不支持某些 CSS 特性 | 严格按 LDLib2 审计表写法 |

---

## 8. 文件变更清单

| 文件 | 操作 |
|---|---|
| `src/main/resources/assets/kinetic_planner/lss/kp.lss` | 新建 |
| `gui/ribbon/RibbonBar.java` | 修改 |
| `gui/ribbon/internal/RibbonBuilder.java` | 重写 |
| `gui/ribbon/internal/ToolWidgetFactory.java` | 重写 |
| `gui/ribbon/internal/GroupPanel.java` | 修改 |
| `gui/ribbon/QatBar.java` | 修改 |
| `gui/editor/ribbon/KpViewContextProvider.java` | 修改 |
| `gui/editor/KpMapEditor.java` | 修改 |
| `test/java/.../gui/ribbon/*` | 新增/更新 |
| `test/java/.../gui/editor/ribbon/KpViewContextProviderTest.java` | 更新 |

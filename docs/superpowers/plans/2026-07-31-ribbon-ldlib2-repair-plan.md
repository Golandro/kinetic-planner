# Ribbon UI LDLib2 最佳实践修复计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` or `superpowers:executing-plans`. Steps use checkbox syntax.

**Goal:** 用 LDLib2 原生组件（TabView / Tab / Toggle / ToggleGroup / ScrollerView）替换当前 Button 拼凑的 Ribbon UI，补齐 LSS 暗色主题，消除 `KpViewContextProvider` 全局静态状态。

**Architecture:** 保持数据定义层不变；重写 UI 构建层。`RibbonBar` 内部持有 `TabView`，QAT 与 header components 放入 `TabView.tabHeaderContainer`；工具按钮根据 `ButtonAction.toggle` 生成 `Button` 或 `Toggle`；样式由 `assets/kinetic_planner/lss/kp.lss` 驱动。

**Tech Stack:** MC 1.21.1 NeoForge, LDLib2 2.2.26, JUnit 5, Mockito.

## Global Constraints

- `gui/ribbon/` 不得 import `net.jsmua.kinetic_planner.*`。
- LSS 不支持 CSS 变量、`border-*` 简写、`padding` 简写；颜色用 `#AARRGGBB`。
- `Button.setOnClick(UIEventListener)`，`Toggle.setOnToggleChanged(BooleanConsumer)`。
- `TabView.addTab` 首次调用会自动触发 `selectTab` 回调。
- 服务端短路：`layout(...)` / `style(...)` lambda 不执行。

## File Structure

| 文件 | 操作 | 职责 |
|---|---|---|
| `src/main/resources/assets/kinetic_planner/lss/kp.lss` | 新建 | 暗色主题样式 |
| `gui/ribbon/RibbonBar.java` | 修改 | Ribbon 容器，持有 TabView |
| `gui/ribbon/internal/RibbonBuilder.java` | 重写 | definition → TabView/Tab/content |
| `gui/ribbon/internal/ToolWidgetFactory.java` | 重写 | tool → Button / Toggle |
| `gui/ribbon/internal/GroupPanel.java` | 修改 | COLUMN 外壳 + ROW 工具行 |
| `gui/ribbon/QatBar.java` | 修改 | 图标优先 QAT |
| `gui/editor/ribbon/KpViewContextProvider.java` | 修改 | 移除静态状态 |
| `gui/editor/KpMapEditor.java` | 修改 | 传入 context supplier |
| `test/java/.../gui/ribbon/RibbonBuilderTest.java` | 新增/更新 | TabView 构建测试 |
| `test/java/.../gui/ribbon/ToolWidgetFactoryTest.java` | 新增/更新 | Toggle 测试 |
| `test/java/.../gui/editor/ribbon/KpViewContextProviderTest.java` | 更新 | supplier 测试 |

---

### Task 1: LSS 暗色主题

**Files:**
- Create: `src/main/resources/assets/kinetic_planner/lss/kp.lss`

**Steps:**

- [ ] 新建 `kp.lss`，定义 `.kp-ribbon-bar`、`.kp-ribbon-tab`、`.kp-ribbon-tab.__selected__`、`.kp-ribbon-group`、`.kp-ribbon-tool-row`、`.kp-ribbon-tool.__on__`、`.kp-ribbon-qat`、`.kp-ribbon-separator`。
- [ ] 运行 `gradlew runClient` 确认无 LSS 解析错误。
- [ ] Commit: `feat: add ribbon dark theme lss`

关键样式值：
- 背景：`#ff2c2c34`
- KP accent：`#FF7C57D4`
- 组标签文字：`#ff9ca3af`
- 分隔符：`#ff4b5563`

---

### Task 2: `ToolWidgetFactory` 支持 Toggle

**Files:**
- Modify: `gui/ribbon/internal/ToolWidgetFactory.java`
- Test: `test/.../gui/ribbon/ToolWidgetFactoryTest.java`

**Interfaces:**
- `create(RibbonToolDefinition, @Nullable Toggle.ToggleGroup) : UIElement`

**Steps:**

- [ ] 写测试：`ButtonAction.toggle=true` 返回 `Toggle`；`toggle=false` 返回 `Button`。
- [ ] 运行测试，确认失败。
- [ ] 实现：
  - plain `ButtonAction` → `Button` + `setText` + 可选 `setIcon` + `setOnClick(execute)`。
  - toggle `ButtonAction` → 校验 `command instanceof RibbonToggleCommand`，创建 `Toggle`，`setText`，可选图标，`setToggleGroup(group)`，`setOn(isActive, false)`，`setOnToggleChanged(setActive)`。
  - `SeparatorAction` → `UIElement` + `.kp-ribbon-separator`。
- [ ] 运行测试，确认通过。
- [ ] Commit: `feat: support Toggle/ToggleGroup in ToolWidgetFactory`

---

### Task 3: `GroupPanel` 改为 COLUMN 外壳 + ROW 工具行

**Files:**
- Modify: `gui/ribbon/internal/GroupPanel.java`

**Interfaces:**
- `build(RibbonToolGroupDefinition, @Nullable Toggle.ToggleGroup) : UIElement`

**Steps:**

- [ ] 写测试：验证返回的 `GroupPanel` 第一个子元素是 `UIElement`（工具行），工具行内包含与 `group.getTools()` 数量一致的子元素。
- [ ] 运行测试，确认失败（当前是 COLUMN 堆叠）。
- [ ] 实现：
  - `GroupPanel` 自身 `FlexDirection.COLUMN`，添加 class `kp-ribbon-group`。
  - 创建工具行 `UIElement`，`FlexDirection.ROW`，class `kp-ribbon-tool-row`。
  - 遍历 `group.getTools()`，调用 `ToolWidgetFactory.create(tool, group)`，加入工具行。
  - 如果 `group.getDisplayName()` 存在，在工具行下方添加 `Label` 显示组名，class `kp-ribbon-group-label`。
  - 将工具行和标签加入 `GroupPanel`。
- [ ] 运行测试，确认通过。
- [ ] Commit: `refactor: GroupPanel uses row layout with bottom label`

---

### Task 4: `RibbonBuilder` 使用 `TabView`/`Tab`

**Files:**
- Modify: `gui/ribbon/internal/RibbonBuilder.java`

**Interfaces:**
- `build(RibbonBar, Map<ResourceLocation, RibbonTabState>, DefaultQuickAccessToolbar, Optional<ResourceLocation>)`

**Steps:**

- [ ] 写测试：`RibbonBar` 构造后内部 `TabView` 包含 2 个核心 tab + N 个 contextual tab；点击 tab 切换后 `TabView.getSelectedTab()` 变化。
- [ ] 运行测试，确认失败。
- [ ] 实现：
  - `RibbonBuilder.build` 创建 `TabView` 并设置给 `RibbonBar`。
  - 组装 header：`QatBar` + LEADING components + `TabView.tabScroller` 占位 + TRAILING components。实际通过操作 `TabView.tabHeaderContainer` 完成：清空后按顺序添加 QAT、LEADING 组件、`TabView.tabScroller`（内部由 TabView 管理）、TRAILING 组件。
  - 对每个可见 tab 创建 `Tab`，`tab.setText(def.getDisplayName())`，`tab.addClass("kp-ribbon-tab")`。
  - 为每个 tab 创建 content `UIElement`（`FlexDirection.ROW`，class `kp-ribbon-content`），遍历 tab 的 groups 调用 `GroupPanel.build(group, sharedToggleGroup)` 加入 content。注意：每个 tab 内部若存在互斥工具组，应传入独立的 `ToggleGroup`。
  - `tabView.addTab(tab, content)`。
  - `tabView.setOnTabSelected(selectedTab -> bar.onTabSelected(tabIdOf(selectedTab)))`。
  - 默认选中 preferredSelectedTabId 对应的 tab：找到后 `tabView.selectTab(tab)`。
- [ ] 运行测试，确认通过。
- [ ] Commit: `refactor: RibbonBuilder uses LDLib2 TabView`

注意：首次 `addTab` 会自动触发 `selectTab` 回调，需在 `onTabSelected` 中检查 content 是否已构建，避免空指针。

---

### Task 5: `RibbonBar` 持有 `TabView` 并清理重建逻辑

**Files:**
- Modify: `gui/ribbon/RibbonBar.java`

**Interfaces:**
- `onTabSelected(ResourceLocation id)` — 持久化 selectedTabId
- `onContextChanged()` — 仅调整 contextual tab display

**Steps：**

- [ ] 移除 `selectTab()` 中的 `clearAllChildren()` 和完整重建。
- [ ] `RibbonBar` 构造函数内创建 `TabView`，调用 `RibbonBuilder.build(...)` 一次性构建。
- [ ] `onTabSelected(ResourceLocation id)` 仅更新 `preferences.setSelectedTabId(id)`。
- [ ] `onContextChanged()` 遍历 contextual tabs，根据 active contexts 调用 `tab.setDisplay(true/false)`，不重建。
- [ ] 运行 `RibbonBuilderTest` 和相关测试，确认通过。
- [ ] Commit: `refactor: RibbonBar owns TabView, no full rebuild on tab switch`

---

### Task 6: `QatBar` 图标优先

**Files:**
- Modify: `gui/ribbon/QatBar.java`

**Steps：**

- [ ] 写测试：QAT 中的工具如果有 icon，生成的按钮应设置图标；无 icon 时显示显示名首字母。
- [ ] 运行测试，确认失败（当前显示完整文本）。
- [ ] 实现：
  - `rebuild(List<ResourceLocation>, lookup)` 中，对每个 tool ID：
    - 查找 `RibbonToolDefinition`。
    - 若 `action` 为 `ButtonAction`，创建 `Button`。
    - 优先 `tool.getIcon().ifPresent(btn::setIcon)`，否则 `btn.setText(firstChar(tool.getDisplayName()))`。
    - `btn.setOnClick(event -> btnAction.command().execute())`。
    - 空状态显示淡化星标 `Button`（可用 `Label` 或 `Button` + `Component.literal("★")` + class `kp-ribbon-qat-empty`）。
- [ ] 运行测试，确认通过。
- [ ] Commit: `refactor: QatBar icon-first rendering`

---

### Task 7: 清理 `KpViewContextProvider` 静态状态

**Files:**
- Modify: `gui/editor/ribbon/KpViewContextProvider.java`
- Modify: `gui/editor/KpMapEditor.java`
- Test: `test/.../gui/editor/ribbon/KpViewContextProviderTest.java`

**Interfaces：**
- `KpViewContextProvider(Supplier<Set<ResourceLocation>> contextSupplier)`

**Steps：**

- [ ] 写测试：传入固定 supplier `() -> Set.of(rl("kp", "ctx"))`，验证 `getActiveContexts()` 返回该集合；调用 `notifyContextChanged()` 触发监听器。
- [ ] 运行测试，确认失败（当前依赖静态状态）。
- [ ] 实现：
  - 删除 `demoContextActive`、`INSTANCES`、`toggleDemoContext()`、`isDemoContextActive()`。
  - 新增 `private final Supplier<Set<ResourceLocation>> contextSupplier`。
  - 构造函数接收 supplier。
  - `getActiveContexts()` 返回 `contextSupplier.get()`。
- [ ] 更新 `KpMapEditor`：在 `initMenus()` 中创建 `KpViewContextProvider` 时传入 supplier。Demo 模式下可用 `KpMapEditor` 实例字段 `Set<ResourceLocation> activeContexts` + 切换方法。
- [ ] 更新 `KpRibbonRegistration` 中上下文切换按钮：不再调用静态 `KpViewContextProvider.toggleDemoContext()`，而是通过 `KpMapEditor` 实例方法切换上下文。
- [ ] 运行测试，确认通过。
- [ ] Commit: `refactor: remove static state from KpViewContextProvider`

---

### Task 8: 样式绑定与类名应用

**Files：**
- Modify: `gui/ribbon/RibbonBar.java`
- Modify: `gui/ribbon/internal/RibbonBuilder.java`
- Modify: `gui/ribbon/internal/ToolWidgetFactory.java`
- Modify: `gui/ribbon/internal/GroupPanel.java`
- Modify: `gui/ribbon/QatBar.java`

**Steps：**

- [ ] 确保所有 Ribbon 元素添加正确 class：
  - `RibbonBar`: `kp-ribbon-bar`
  - `Tab`: `kp-ribbon-tab`
  - `GroupPanel`: `kp-ribbon-group`
  - 工具行：`.kp-ribbon-tool-row`
  - 工具：`.kp-ribbon-tool`
  - QAT：`.kp-ribbon-qat`
  - 分隔符：`.kp-ribbon-separator`
- [ ] 运行 `gradlew compileClientJava` 确认无编译错误。
- [ ] Commit: `style: apply ribbon css classes`

---

### Task 9: 运行测试与编译

**Files：** 全部

**Steps：**

- [ ] 运行 `gradlew compileJava compileClientJava compileServerJava`。
- [ ] 运行 `gradlew test`。
- [ ] 修复编译/测试失败。
- [ ] Commit: `test: green build after ribbon repair`

---

### Task 10: 手动验收

**Files：** 无

**Steps：**

- [ ] 运行 `gradlew runClient`。
- [ ] 打开编辑器，验证：
  - tab 切换正常，内容面板切换。
  - 当前 tab 有紫色选中态。
  - Pan/Select/Line/Bezier/Snap 互斥高亮。
  - View tab 开关独立 on/off。
  - QAT 显示图标或首字母。
  - 窗口缩窄时 tab 可横向滚动。
  - 切换上下文，Ctx Tools tab 出现/消失。
- [ ] 截图记录验收结果。
- [ ] Commit: `docs: update STATUS.md ribbon section`

---

## Self-Review Checklist

- [ ] Spec 中所有 4 项 Critical 违规都有对应任务：TabView（Task 4）、Toggle/ToggleGroup（Task 2/3）、LSS（Task 1/8）、静态状态（Task 7）。
- [ ] 无 "TBD"/"TODO"/"稍后实现" 占位符。
- [ ] 文件路径全部使用绝对或从项目根开始的相对路径。
- [ ] 类型/方法名在各任务间一致（`RibbonToggleCommand`、`Toggle.ToggleGroup`、`ButtonAction.toggle()`）。

---

## Execution Handoff

Plan saved to `docs/superpowers/plans/2026-07-31-ribbon-ldlib2-repair-plan.md`.

**Two execution options:**

1. **Subagent-Driven (recommended)** — dispatch a fresh subagent per task, review between tasks.
2. **Inline Execution** — execute tasks in this session using `executing-plans`, batch execution with checkpoints.

Which approach?

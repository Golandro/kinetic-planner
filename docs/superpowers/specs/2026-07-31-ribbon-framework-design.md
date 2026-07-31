# Ribbon 框架设计规格

> **状态：** Draft (Rev.2 — 解耦边界澄清 + 上下文选项卡)
> **日期：** 2026-07-31
> **范围：** 可扩展、可自定义的 Ribbon 栏框架，支持 Mod 插件级注册、用户 QAT 微调、四种选项卡显示模式（PINNED/FLOATING/HIDDEN/CONTEXTUAL）
> **文档关系：** 本规格是对 `2026-07-29-editor-viewport-event-spec.md` §6.4（Ribbon 菜单栏）的重新设计。当本规格与原始编辑器规格中 Ribbon 相关内容冲突时，以本规格为准。
>
> **Rev.2 变更摘要**：
> - 框架与 KP 解耦：API 层改用 `ResourceLocation`（不再依赖 `KPId`/`KPRegistry`），配置抽象为 `RibbonPreferenceStore`，禁止 `getInstance()` 静态单例访问
> - 新增 `CONTEXTUAL` 选项卡模式 + `ContextualTabGroup` + `ViewContextProvider`（§10），明确仅用于视图驱动场景，不适配 CAD 对象选择编辑
> - 投机性 sealed 子类型（`SplitButtonAction`/`DropdownAction`/`CustomDropdown`/`WidgetTooltip`）标记为 Phase 2+ 延后
> - `RibbonTabBarComponent` 重命名为 `RibbonHeaderComponent`
> - 包结构拆分 `gui/ribbon/`（通用框架）与 `gui/editor/ribbon/`（KP 适配层）

---

## 1. 设计目标

为 Kinetic Planner 编辑器建立一套**通用、可扩展**的 Ribbon 栏框架，**框架本身与 KP 解耦**，可被任意 LDLib2 mod 复用：

- **Mod 插件级扩展**：其他 mod 可通过 `RibbonRegistry` 注册自定义选项卡、工具组、头部组件
- **用户可微调 QAT**：用户可右键工具"添加到快速访问工具栏"、隐藏/显示选项卡
- **选项卡显示模式**：
  - **核心模式（用户控制）**：固定（PINNED）、浮动（FLOATING）、隐藏（HIDDEN）—— 适配 CAD "任务驱动"工作流
  - **上下文模式（视图驱动）**：CONTEXTUAL —— 由当前视图上下文自动激活/停用，不适配对象选择编辑场景
- **数据驱动**：所有 Ribbon 内容由 definition 数据接口描述，Ribbon 内部 Builder 将数据转化为 UIElement
- **与用户自定义 Collection 呈现接轨**：definition 数据模式具备可内省性，未来对象 Collection 也可用同一模式驱动 UI

### 1.1 解耦边界

框架与 KP 之间有明确边界：

| 依赖方向 | 允许？ | 说明 |
|---|---|---|
| Ribbon 框架 → LDLib2（`UIElement`/`IGuiTexture`/`Menu`/`Selector` 等） | ✅ 允许 | 框架绑定 LDLib2 作为 UI 基座 |
| Ribbon 框架 → MC（`ResourceLocation`/`Component`） | ✅ 允许 | 作为 LDLib2 的传递依赖 |
| Ribbon 框架 → KP（`KPId`/`KPRegistry`/`EditToolState`/`KPConfig`） | ❌ 禁止 | KP 是 Ribbon 框架的**消费者**，不是它的依赖 |
| KP → Ribbon 框架 | ✅ 允许 | KP 适配层调用框架 API 注册自身工具 |

**标识符约定**：框架 API 层统一使用 MC 原生 `ResourceLocation`（`namespace:path` 格式）作为标识符类型，**不引入** `KPId`。KP 内部使用 `KPId` 的代码在调用框架时通过 `KPId.toResourceLocation()`（或等价转换）适配。

**配置抽象**：框架不直接依赖 `KPConfig`，定义 `RibbonPreferenceStore` 接口，由 KP 提供 `KPConfigRibbonPreferenceStore` 实现。

**单例访问禁止**：框架 API 不调用任何 `getInstance()` 静态单例；所有运行时状态（当前工具、选择集、视图上下文）通过构造注入的接口获取。

### 设计方案

**方案 A：纯数据驱动 Definition + 内部 Builder**（已选定）

Mod 注册纯数据接口（`RibbonTabDefinition`），Ribbon 内部用 Builder 将数据转化为 UIElement 树。

选择理由：
1. QAT 自定义需求强制要求可内省——所有 tool 都有 `ResourceLocation` ID，QAT 可枚举
2. 外观一致性——所有 mod 的 tab 由同一个 Builder 构建
3. 框架与 KP 解耦——任意 LDLib2 mod 可复用，不被 KP 标识符/配置体系污染

---

## 2. 整体架构

### 2.1 UI 树结构

```
RibbonBar (UIElement, FlexDirection.COLUMN, width=100%)
├── RibbonHeader (UIElement, ROW, height=20px)
│   ├── QuickAccessToolbar (ROW, LEADING 侧)
│   │   └── IconButton × N (从 QAT tool ID 列表构建)
│   ├── TabStrip (ROW, flexGrow=1, 可横向滚动)
│   │   └── Tab × N (从 RibbonTabDefinition 列表构建, 按 priority 排序, HIDDEN 的不渲染)
│   └── TabBarTrailing (ROW, TRAILING 侧)
│       └── RibbonHeaderComponent × N (外部 mod 注册的尾部组件)
│
└── RibbonContent (UIElement)
    │
    ├── [PINNED 模式] ROW, height=40px, 参与布局
    │   └── GroupPanel × N (当前选中 tab 的工具组)
    │       ├── Tool widgets (从 RibbonToolDefinition 构建)
    │       └── GroupLabel (底部标注, 可选)
    │
    └── [FLOATING 模式] positionType(ABSOLUTE), zIndex=50, 不参与布局
        └── 同 PINNED 的 GroupPanel 结构
```

### 2.2 高度预算

| 模式 | RibbonBar 高度 |
|------|---------------|
| 活动 tab 为 PINNED | 60px (header 20 + content 40) |
| 活动 tab 为 FLOATING (面板收起) | 20px (仅 header) |
| 活动 tab 为 FLOATING (面板展开) | 20px (header) + 浮层 overlay (不占布局空间) |
| 无可见 tab | 20px (仅 header) |

当前 `KpMapEditor.initMenus()` 中 `top.getLayout().height(24)` 需改为动态高度或固定 60px。

### 2.3 生命周期

```
注册阶段 (Mod 加载时, ClientSetup 之前)
  ↓  Mod 调用 RibbonRegistry.registerTab() / registerHeaderComponent()
  ↓  Registry 在 ClientSetup 冻结

构建阶段 (KpMapEditor.initMenus() 调用时)
  ↓  RibbonBar 构造函数读取已冻结的 Registry
  ↓  按 priority 排序所有 tab definitions
  ↓  从 config 加载用户偏好 (displayMode, selectedTabId, QAT)
  ↓  构建 RibbonHeader (TabStrip + QAT + Trailing)
  ↓  默认选中 selectedTabId 或第一个可见 tab
  ↓  按活动 tab 的 displayMode 构建 RibbonContent

运行时
  ↓  用户点击 tab -> selectTab() -> 切换 RibbonContent
  ↓  用户右键 tool -> Menu 弹出 -> "添加到 QAT" / "移除" / "查看快捷键"
  ↓  用户右键 tab -> Menu 弹出 -> "固定" / "浮动" / "隐藏"
  ↓  QAT/显示模式/选中 tab 持久化到 RibbonPreferenceStore (KP 实现为 KPConfig TOML)

重建 (主题切换等)
  ↓  clearAllChildren() -> 重新从 definitions 构建
```

### 2.4 注册机制

框架自带独立的 `RibbonRegistry`（不复用 `KPRegistry`，避免框架反向依赖 KP）。Mod 不能热加载（#26），静态注册即可。

```java
public final class RibbonRegistry {
    private static final Map<ResourceLocation, RibbonTabDefinition> TABS = new LinkedHashMap<>();
    private static final Map<ResourceLocation, ContextualTabGroup> CONTEXT_GROUPS = new LinkedHashMap<>();
    private static final Map<ResourceLocation, RibbonHeaderComponent> HEADER_COMPONENTS = new LinkedHashMap<>();
    private static boolean frozen = false;

    public static void registerTab(ResourceLocation id, RibbonTabDefinition def) { ... }
    public static void registerContextualGroup(ResourceLocation id, ContextualTabGroup group) { ... }
    public static void registerHeaderComponent(ResourceLocation id, RibbonHeaderComponent comp) { ... }

    /// ClientSetup 调用, 冻结后不可再注册
    public static void freeze() { frozen = true; }

    /// 测试专用：重置注册表状态（仅在 test sourceSet 调用）
    static void resetForTest() { ... }

    /// 返回按 priority 排序的核心 tab 列表（不含 CONTEXTUAL）
    public static List<RibbonTabDefinition> getTabsSortedByPriority() { ... }

    /// 返回按 priority 排序的上下文选项卡组
    public static List<ContextualTabGroup> getContextualGroups() { ... }

    /// 返回按 priority 排序的头部组件, 按 Placement 分组
    public static List<RibbonHeaderComponent> getHeaderComponents(Placement placement) { ... }
}
```

### 2.5 与用户自定义 Collection 呈现接轨

`RibbonToolDefinition` 的 action 不直接是 `Runnable`，而是 `RibbonCommand` 接口实例，携带命令语义（`execute()` / `isEnabled()`）。未来对象 Collection 的 UI 呈现也遵循同一 definition 模式：

- 工具 definition 有 ID（`ResourceLocation`）、displayName、icon、command
- QAT 持久化的是 `ResourceLocation` 而非 UI 引用
- 工具组本身也是一个"工具 Collection"的特化——用户自定义 Collection 可通过适配器包装为 `RibbonToolGroupDefinition` 显示在 Ribbon 中

---

## 3. 选项卡接口

### 3.1 选项卡显示模式

```java
public enum TabDisplayMode {
    /// 固定: 内容面板始终可见, 占据布局空间 (标准 Ribbon)
    /// 适配 CAD "任务驱动"工作流——工具组常驻, 用户随时切换工具
    PINNED,

    /// 浮动: 内容面板浮层弹出, 失焦自动收起, 不占布局空间
    /// 适配低频任务组——节省垂直空间
    FLOATING,

    /// 隐藏: tab 头不渲染, 内容不可访问 (用户可在设置中恢复)
    HIDDEN,

    /// 上下文: tab 头可见性由当前视图上下文决定 (ViewContextProvider)
    /// 不由用户直接控制——视图激活时自动显示, 视图退出时自动隐藏
    /// 适配"特定视图"场景 (如 Map Editor 视图 / Track Graph Editor 视图)
    /// **不适配对象选择编辑**——CAD 对象编辑应使用 PINNED 任务驱动模式
    CONTEXTUAL
}
```

### 3.2 RibbonTabDefinition（Mod 注册的不可变数据）

```java
public interface RibbonTabDefinition {
    /// 唯一标识 (MC ResourceLocation, namespace:path 格式)
    ResourceLocation getId();

    /// 显示名称
    Component getDisplayName();

    /// tab 头图标 (可选)
    Optional<IGuiTexture> getIcon();

    /// 排序优先级 (越小越靠左)
    int getPriority();

    /// 此 tab 包含的工具组
    List<RibbonToolGroupDefinition> getGroups();

    /// 建议的默认显示模式 (用户可覆盖, 但 CONTEXTUAL 由系统控制)
    default TabDisplayMode getDefaultDisplayMode() { return TabDisplayMode.PINNED; }

    /// 是否允许用户隐藏此 tab (核心 tab 可禁止)
    default boolean isUserHideable() { return true; }

    /// 若 getDefaultDisplayMode() == CONTEXTUAL, 返回所属上下文组 ID
    /// 非 CONTEXTUAL tab 返回 Optional.empty()
    /// 上下文组通过 RibbonRegistry.registerContextualGroup() 注册
    default Optional<ResourceLocation> getContextualGroupId() { return Optional.empty(); }
}
```

### 3.3 RibbonTabState（运行时状态, RibbonBar 内部管理）

```java
public final class RibbonTabState {
    private final RibbonTabDefinition definition;
    private TabDisplayMode displayMode;    // 当前模式 (CONTEXTUAL 由 ViewContextProvider 驱动, 其他由用户修改)
    private boolean contentVisible;        // FLOATING 模式下面板是否展开
    private boolean contextActive;         // 仅 CONTEXTUAL: 所属上下文是否激活

    // 状态不变量:
    //   HIDDEN       -> contentVisible = false
    //   PINNED       -> contentVisible = true
    //   FLOATING     -> contentVisible 由用户交互控制
    //   CONTEXTUAL   -> 头部可见性 = contextActive; contentVisible 遵循 PINNED/FLOATING 子模式

    public void setDisplayMode(TabDisplayMode mode) {
        this.displayMode = mode;
        this.contentVisible = (mode == TabDisplayMode.PINNED);
    }

    /// 仅 FLOATING 模式有效
    public void toggleContentVisible() {
        if (displayMode == TabDisplayMode.FLOATING) {
            contentVisible = !contentVisible;
        }
    }

    /// 仅 CONTEXTUAL 模式: 视图上下文激活/停用
    public void setContextActive(boolean active) {
        if (displayMode == TabDisplayMode.CONTEXTUAL) {
            this.contextActive = active;
            if (!active) this.contentVisible = false;
        }
    }

    /// CONTEXTUAL tab 头是否渲染
    public boolean isHeaderVisible() {
        return displayMode != TabDisplayMode.HIDDEN
            && (displayMode != TabDisplayMode.CONTEXTUAL || contextActive);
    }
}
```

### 3.4 状态转换

**用户控制模式（PINNED/FLOATING/HIDDEN）**：

| 用户操作 | 状态变化 |
|---------|---------|
| 右键 tab -> "固定" | FLOATING/HIDDEN -> PINNED |
| 右键 tab -> "浮动显示" | PINNED/HIDDEN -> FLOATING |
| 右键 tab -> "隐藏" | PINNED/FLOATING -> HIDDEN (受 `isUserHideable` 约束) |
| Settings -> 勾选/取消勾选 tab | HIDDEN <-> 恢复 (恢复时用 `definition.getDefaultDisplayMode()`) |
| 点击 FLOATING tab 头 | `contentVisible` toggle |
| 点击 FLOATING 面板外部 | `contentVisible = false` |

**上下文驱动模式（CONTEXTUAL）**：

| 事件 | 状态变化 |
|------|---------|
| `ViewContextProvider` 报告上下文激活 | `contextActive = true`, tab 头出现 |
| `ViewContextProvider` 报告上下文停用 | `contextActive = false`, `contentVisible = false`, tab 头消失 |
| 用户点击活跃的 CONTEXTUAL tab 头 | 行为同 PINNED 或 FLOATING（由上下文组配置决定子模式） |

**关键约束**：CONTEXTUAL tab 不出现在用户右键菜单的"固定/浮动/隐藏"选项中——它的可见性完全由视图驱动，用户不能手动覆盖。如需让用户控制，应注册为 PINNED/FLOATING 而非 CONTEXTUAL。

### 3.5 持久化

`RibbonPreferences` 通过 `RibbonPreferenceStore` 接口持久化（KP 提供 `KPConfigRibbonPreferenceStore` 实现，写入 `KPConfig` TOML，#28）：

```toml
[ribbon.tabs]
"kp:tools" = "PINNED"
"kp:file" = "FLOATING"
"kp:view" = "HIDDEN"
"othermod:custom" = "PINNED"

[ribbon.qat]
tools = ["kp:tool_pan", "kp:tool_select", "kp:toggle_overlay"]

[ribbon.state]
selected_tab = "kp:tools"
```

首次加载时，对未出现在 config 中的 tab，使用 `definition.getDefaultDisplayMode()`。

---

## 4. 工具模型

### 4.1 工具尺寸与动作矩阵

工具是一个 **Size × Action** 矩阵。**首版（Phase 1）只实现 BUTTON + SEPARATOR**；SPLIT_BUTTON / DROPDOWN_ONLY 标记为 Phase 2+ 延后实现，待真实需求出现再加（sealed interface 扩展成本低，前置无收益）：

| | BUTTON | SPLIT_BUTTON (按钮+下拉) | DROPDOWN_ONLY | SEPARATOR |
|---|---|---|---|---|
| **Phase 1** | ✅ 支持 (可 toggle) | ⏳ 延后 | ⏳ 延后 | ✅ 支持 |
| **LARGE** (2x宽, 图标上文字下) | ✅ | ⏳ | ⏳ | - |
| **SMALL** (1x宽, 图标左文字右) | ✅ | ⏳ | ⏳ | ✅ |

### 4.2 工具尺寸

```java
public enum ToolSize {
    /// 2列宽, 垂直排列 (图标上, 文字下), 或无文字纯图标
    LARGE,

    /// 1列宽, 水平排列 (图标左, 文字右), 或无文字纯图标
    SMALL
}
```

### 4.3 命令接口（回调层, 与命令树后端解耦）

```java
/// 所有动作都是回调 (#3)
public interface RibbonCommand {
    void execute();
    default boolean isEnabled() { return true; }
}

/// 开关型命令
public interface RibbonToggleCommand extends RibbonCommand {
    boolean isActive();
    void setActive(boolean active);
}
```

`RibbonCommand` 作为命令树的 UI 适配层，不直接替代 `KpCommandHandlers`。`KpCommandHandlers` 的 27 个方法可以在其实现中创建 `RibbonCommand` 适配器。

**测试 seam 要求**：`RibbonCommand` 实现禁止直接调用 `getInstance()` 静态单例。命令适配器通过构造函数注入所需依赖（见 §4.8 示例），保证 §12.1 单测可在不 mock 静态方法的前提下验证命令行为。

### 4.4 工具动作（sealed interface）

```java
public sealed interface ToolAction
    permits ButtonAction, SplitButtonAction, DropdownAction, SeparatorAction {}

/// 普通按钮 / 开关按钮 —— Phase 1 实现
public record ButtonAction(
    RibbonCommand command,
    boolean toggle           // true = 开关型, 需 command 为 RibbonToggleCommand
) implements ToolAction {}

/// 分裂按钮: 主按钮 + 附带下拉 —— Phase 2+ 延后实现 (无当前消费者)
public record SplitButtonAction(
    RibbonCommand command,   // 主按钮回调
    DropdownContent dropdown // 下拉内容 (简单/复杂)
) implements ToolAction {}

/// 纯下拉 —— Phase 2+ 延后实现 (无当前消费者)
public record DropdownAction(
    DropdownContent dropdown // 下拉内容
) implements ToolAction {}

/// 分隔符 —— Phase 1 实现
public record SeparatorAction() implements ToolAction {}
```

### 4.5 下拉内容（泛型, 支持简单列表和自定义子 UI）

> **Phase 2+ 延后实现**。首版无 SPLIT_BUTTON / DROPDOWN_ONLY 消费者，此接口及其实现暂不创建。保留设计以备 Phase 2 加回时无需改 API 形状。

```java
public sealed interface DropdownContent
    permits SimpleDropdown, CustomDropdown {}

/// 简单下拉: 候选列表 + 选择回调 (内部用 LDLib2 Selector<T>)
public record SimpleDropdown<T>(
    List<T> candidates,
    UIElementProvider<T> itemUIProvider,   // 每个候选项的 UI
    Consumer<T> onSelected,                // 选择回调
    @Nullable T defaultValue
) implements DropdownContent {}

/// 复杂下拉: 自定义子 UI (任意 UIElement)
/// 理论上可以自己实现一个子 UI (#3)
public record CustomDropdown(
    Supplier<UIElement> contentSupplier    // 每次展开时调用, 返回自定义面板
) implements DropdownContent {}
```

### 4.6 Tooltip 内容

```java
public sealed interface TooltipContent
    permits TextTooltip, WidgetTooltip {}

/// MC Component 文本 tooltip —— Phase 1 实现
public record TextTooltip(Component text) implements TooltipContent {}

/// 自定义 Widget tooltip —— Phase 2+ 延后实现 (无当前消费者, #1: 技术上支持)
public record WidgetTooltip(Supplier<UIElement> widgetSupplier) implements TooltipContent {}
```

### 4.7 工具定义

```java
public interface RibbonToolDefinition {
    /// 唯一标识 (MC ResourceLocation)
    ResourceLocation getId();
    Component getDisplayName();
    Optional<IGuiTexture> getIcon();

    /// Tooltip: MC Component 或自定义 Widget
    Optional<TooltipContent> getTooltip();

    /// 快捷键显示标签 (如 "P", "Ctrl+S") —— 仅用于 tooltip 显示
    Optional<String> getShortcutLabel();

    /// KeyTip 序列 (如 ["P", "1"] 用于 Alt+P → 1 二级导航) —— Phase 2+ 预留
    /// 首版返回 Optional.empty()；保留字段避免后续加字段破坏 definition 实现 binary compat
    default Optional<java.util.List<String>> getKeyTips() { return Optional.empty(); }

    /// 工具尺寸
    ToolSize getSize();

    /// 工具动作 (回调)
    ToolAction getAction();

    /// 溢出权重 (#9): 数值越大越先被缩窄/隐藏
    default int getOverflowWeight() { return RibbonConstants.DEFAULT_OVERFLOW_WEIGHT; }
}
```

### 4.8 与 EditToolState.Tool 的关系

`RibbonToolDefinition` 包装 `Tool` 枚举值，`Tool` 枚举保持为后端状态（#5）。KP 适配层位于 `gui/editor/ribbon/`，不属于通用框架。

`EditToolState.Tool` 枚举（NAVIGATION/SELECT/DRAW_LINE/DRAW_BEZIER/SNAP）不做修改。KP 内部为每个 Tool 值创建对应的 `RibbonToolDefinition`：

```java
// 示例: Pan 工具定义 (KP 适配层, gui/editor/ribbon/KpToolDefinitions.java)
// 集中所有 Tool -> RibbonToolDefinition 映射, 避免散落多处 (DRY)
public final class KpToolDefinitions {
    private final EditToolState toolState;  // 构造注入, 非 getInstance()

    public KpToolDefinitions(EditToolState toolState) {
        this.toolState = toolState;
    }

    public RibbonToolDefinition panTool() {
        return RibbonToolDefinition.builder(
            ResourceLocation.fromNamespaceAndPath("kp", "tool_pan"),
            Component.literal("Pan"),
            ToolSize.LARGE,
            new ButtonAction(new ToolToggleCommand(toolState, Tool.NAVIGATION), true)
        )
            .icon(Icons.PAN)
            .tooltip(new TextTooltip(Component.literal("平移视图 (P)")))
            .shortcutLabel("P")
            .build();
    }

    // ... 其他工具定义集中于此
}

/// ToolToggleCommand 适配 EditToolState —— 构造注入, 不调 getInstance()
final class ToolToggleCommand implements RibbonToggleCommand {
    private final EditToolState toolState;  // 注入
    private final Tool tool;

    ToolToggleCommand(EditToolState toolState, Tool tool) {
        this.toolState = toolState;
        this.tool = tool;
    }

    @Override public void execute() { toolState.setCurrentTool(tool); }
    @Override public boolean isActive() { return toolState.getCurrentTool() == tool; }
    @Override public void setActive(boolean a) { if (a) execute(); }
    @Override public boolean isEnabled() { return true; }
}
```

**关键约束**：
- `ToolToggleCommand` 通过构造函数接收 `EditToolState`，**不调用** `EditToolState.getInstance()`，保证可单测
- `KpToolDefinitions` 集中所有工具定义，避免 Tool 概念在 `Tool` 枚举 / `RibbonToolDefinition` / `RibbonCommand` / QAT 包装四处分散（DRY）
- `ResourceLocation.fromNamespaceAndPath("kp", "tool_pan")` 是 MC 原生 API，不引入 `KPId`

---

## 5. 工具组接口

### 5.1 RibbonToolGroupDefinition

```java
public interface RibbonToolGroupDefinition {
    /// 唯一标识 (MC ResourceLocation)
    ResourceLocation getId();

    /// 底部标注 (可选, 为空则不标注, 为低分辨率屏幕着想 #7)
    Optional<Component> getDisplayName();

    /// 工具列表
    List<RibbonToolDefinition> getTools();

    /// 溢出权重 (#9): 数值越大越先被缩窄
    default int getOverflowWeight() { return RibbonConstants.DEFAULT_OVERFLOW_WEIGHT; }

    /// Dialog Launcher: 组右下角小箭头按钮, 打开详细设置对话框 —— Phase 2+ 预留
    /// 首版返回 Optional.empty()；保留字段避免后续加字段破坏 definition 实现 binary compat
    default Optional<RibbonCommand> getDialogLauncher() { return Optional.empty(); }
}
```

**`RibbonConstants`**：框架公共常量类，集中 `DEFAULT_OVERFLOW_WEIGHT = 100` 等默认值，避免在 `RibbonToolDefinition` / `RibbonToolGroupDefinition` 等多处重复字面量（DRY）。

### 5.2 布局与视觉

- **组内布局**：通过 CSS class + Taffy flex 实现（#6）。推荐基于 CSS 的响应式布局
- **组标签位置**：底部标注，小字灰色。可为空（低分辨率屏幕）（#7）
- **组间分隔**：竖线（1px 分隔符 UIElement）（#8）

### 5.3 溢出处理

当 `RibbonContent` 宽度不足时：

1. 按 `overflowWeight` 从大到小依次缩窄 group
2. 缩窄顺序：LARGE 按钮 -> SMALL 按钮 -> 隐藏按钮 -> 进溢出菜单
3. 溢出菜单用 LDLib2 `Menu<K,T>` 实现，显示被隐藏的工具
4. 尽量通过 CSS 或 Taffy flex 实现（#9）

### 5.4 Collection 包装

工具组本身是"工具 Collection"的特化。未来用户自定义 Collection 可通过适配器包装为 `RibbonToolGroupDefinition`：

```java
// 未来: 用户自定义 Collection -> RibbonToolGroupDefinition 适配
public class CollectionGroupAdapter implements RibbonToolGroupDefinition {
    // 将 Collection 中的对象转换为 RibbonToolDefinition
    // 每个对象成为一个 BUTTON 或 DROPDOWN 工具
    // Collection 内可设置权重 (缩窄顺序) (#9)
}
```

QAT 内部也包装工具 definition（#11: "Collection 里面也得包装, 其实就是封装了工具"）。用户添加 tool 到 QAT 时，QAT 查找对应的 `RibbonToolDefinition`，用其 icon/tooltip 构建图标按钮，点击时调用原 definition 的 `ToolAction`。

---

## 6. 选项卡栏头部组件接口

### 6.1 RibbonHeaderComponent

> **命名澄清**：原名 `RibbonTabBarComponent` 易与 LDLib2 `Tab`/`TabView` 混淆，且实际语义是"选项卡栏（header）中的附加组件"，故重命名为 `RibbonHeaderComponent`。

```java
public interface RibbonHeaderComponent {
    /// 唯一标识 (MC ResourceLocation)
    ResourceLocation getId();

    /// 放置位置
    Placement getPlacement();

    /// 排序优先级 (同侧内, 越小越靠外)
    int getPriority();

    /// 创建 UI 元素
    UIElement createElement();

    enum Placement { LEADING, TRAILING }
}
```

### 6.2 组件顺序

顺序由客户端配置文件刷新决定（#12）。`RibbonRegistry.getHeaderComponents(Placement)` 返回按 `priority` 排序的列表。

### 6.3 当前内置组件

**无**（#16: "CAD 该完全插件化"）。包括 Settings 齿轮、撤销/重做等全部由插件注册。KP 自身通过 `RibbonRegistry` 注册自己的 tab 和 component。

---

## 7. 快速访问工具栏接口

### 7.1 QAT 接口

```java
public interface QuickAccessToolbar {
    /// QAT 中的工具 ID 列表 (持久化到 config)
    List<ResourceLocation> getToolIds();

    /// 添加工具到 QAT (只接受 Ribbon tool ID, #13)
    boolean addTool(ResourceLocation toolId);

    /// 从 QAT 移除
    boolean removeTool(ResourceLocation toolId);

    boolean containsTool(ResourceLocation toolId);

    /// 构建 QAT 的 UIElement (图标按钮列表)
    UIElement createElement();

    /// 从 PreferenceStore 加载 / 保存 (不直接依赖 KPConfig)
    void loadPreferences(RibbonPreferenceStore store);
    void savePreferences(RibbonPreferenceStore store);
}
```

### 7.2 QAT 定位

QAT 是**独立一等公民**（#11），不是 `RibbonHeaderComponent` 的特化。但 QAT 的 UI 元素放置在 `RibbonHeader` 的 LEADING 侧。

**LEADING 侧排序**：QAT 始终排在所有 LEADING 侧 `RibbonHeaderComponent` 的最左侧（即 QAT -> LEADING components -> TabStrip）。QAT 不参与 `RibbonHeaderComponent` 的 `priority` 排序。

### 7.3 QAT 内容来源

只从 Ribbon tool 添加（#13）。用户通过右键菜单"添加到快速访问工具栏"（#14）。

### 7.4 QAT 渲染

- 图标优先，无图标则显示文字首字母（#15）
- 始终为 SMALL 尺寸
- QAT 内部包装工具 definition：查找 `RibbonToolDefinition` by `ResourceLocation`，用其 icon/tooltip 构建按钮

### 7.5 QAT 空状态

QAT 为空时显示淡色提示图标（如星标），hover 提示"右键工具添加到快速访问"。

---

## 8. 右键上下文菜单

### 8.1 LDLib2 Menu 组件

直接复用 LDLib2 `Menu<K,T>`（`gui/ui/elements/Menu.java`）。

已验证能力：
- 构造：`new Menu<>(ITreeNode root, UIElementProvider provider)`
- 自动 `positionType(ABSOLUTE)` + `zIndex(100)` 浮层定位
- `autoClose=true` + `onBlur` 失焦关闭
- 支持嵌套子菜单（hover 展开）
- 屏幕边界自适应
- `MenuStyle` 可自定义纹理（node/leaf background, hover, arrow）

### 8.2 Tool 右键菜单

```java
Menu<String, Void> toolContextMenu = new Menu<>(
    ITreeNode.root(
        ITreeNode.leaf("addToQat", "Add to Quick Access Toolbar"),
        ITreeNode.leaf("removeFromTab", "Remove from this tab"),
        ITreeNode.leaf("viewShortcut", "Shortcut: " + toolDef.getShortcutLabel().orElse("None"))
    ),
    key -> new Label().setText(key)
);
toolContextMenu.setOnNodeClicked(node -> {
    switch (node.getKey()) {
        case "addToQat" -> qat.addTool(toolDef.getId());
        case "removeFromTab" -> /* 从当前 tab 移除 */;
    }
});
```

### 8.3 Tab 右键菜单

```java
Menu<String, Void> tabContextMenu = new Menu<>(
    ITreeNode.root(
        // CONTEXTUAL tab 不显示固定/浮动/隐藏选项 (可见性由视图驱动, 用户不可覆盖)
        tabDef.getDefaultDisplayMode() != TabDisplayMode.CONTEXTUAL
            ? ITreeNode.leaf("pin", "Pin Tab") : null,
        tabDef.getDefaultDisplayMode() != TabDisplayMode.CONTEXTUAL
            ? ITreeNode.leaf("float", "Float Tab") : null,
        (tabDef.getDefaultDisplayMode() != TabDisplayMode.CONTEXTUAL && tabDef.isUserHideable())
            ? ITreeNode.leaf("hide", "Hide Tab") : null
    ).filter(Objects::nonNull),
    key -> new Label().setText(key)
);
tabContextMenu.setOnNodeClicked(node -> {
    switch (node.getKey()) {
        case "pin"   -> tabState.setDisplayMode(TabDisplayMode.PINNED);
        case "float" -> tabState.setDisplayMode(TabDisplayMode.FLOATING);
        case "hide"  -> tabState.setDisplayMode(TabDisplayMode.HIDDEN);
    }
});
```

### 8.4 菜单触发与关闭

- 触发：`MOUSE_DOWN` 事件 `button == 1`（右键）
- 关闭：点击外部（`onBlur` 自动处理）或 ESC
- 支持 LDLib2 事件系统的 `BLUR` 事件（#17-19）

---

## 9. FLOATING 模式技术实现

### 9.1 绝对定位

已验证 LDLib2 支持 `TaffyPosition.ABSOLUTE`（在 `Menu`、`Selector`、`Dialog`、`GraphView` 等组件中广泛使用）。

### 9.2 浮动内容面板实现

```java
private UIElement createFloatingContent(RibbonTabState tab) {
    var panel = new UIElement();
    panel.layout(layout -> {
        layout.positionType(TaffyPosition.ABSOLUTE);
        layout.top(20);           // header 高度
        layout.left(0);
        layout.widthPercent(100);
        layout.height(40);
    });
    panel.getStyle().zIndex(50);
    panel.getStyle().backgroundTexture(Sprites.BORDER_THICK_RT1);
    panel.setFocusable(true);
    panel.addEventListener(UIEvents.BLUR, e -> {
        // 失焦检测, 复用 Menu.onBlur 模式 (#21)
        if (!panel.isSelfOrChildHover()) {
            tab.setContentVisible(false);
            removeChild(panel);
        }
    });

    // 填充工具组 (与 PINNED 模式相同的 GroupPanel 构建)
    buildGroupPanels(panel, tab.getDefinition().getGroups());
    return panel;
}
```

### 9.3 失焦检测

复用 LDLib2 的 `BLUR` 事件 + `isSelfOrChildHover()` 模式（与 `Menu.onBlur` 一致）：
- 面板获得焦点后，点击面板外部触发 `BLUR`
- `BLUR` 回调中检查 `isSelfOrChildHover()`，若鼠标不在面板或其子元素上，则收起

### 9.4 视觉与动画

- 浮动面板使用 `Sprites.BORDER_THICK_RT1` 背景（#22）
- PINNED -> FLOATING 切换无动画，瞬间切换（#23）

---

## 10. 上下文选项卡（Contextual Tabs）

### 10.1 设计立场：任务驱动 vs 视图驱动

Ribbon 的上下文选项卡在办公软件中常用于"选中对象 → 显示相关工具"（如选中图片显示 Picture Tools）。但 CAD 编辑器的对象编辑工作流通常是**任务驱动**而非选择驱动，KP 明确区分两种边界：

| 工作流类型 | 描述 | 适配的 TabDisplayMode | KP 用法 |
|---|---|---|---|
| **任务驱动**（KP 默认） | 用户先选任务（Pan/Select/Draw Line/Draw Bezier/Snap），任务工具组常驻 Ribbon，与对象选择无关。适配 CAD "先选工具再操作对象"心智模型 | `PINNED` / `FLOATING` | KP 5 个核心 Tool 全部用 PINNED |
| **视图驱动** | 编辑器进入特定**视图**（如 Map Editor 视图、Track Graph Editor 视图、Signal Editor 视图）时，自动显示该视图专用工具组；退出视图时自动隐藏 | `CONTEXTUAL` | KP 未来多视图场景使用 |
| **选择驱动**（不推荐） | 选中某类对象 → 显示该对象专用工具 | 不支持 | CAD 对象编辑不适用——会导致工具组频繁闪烁，破坏任务流 |

**关键约束**：CONTEXTUAL 模式**只用于视图切换**，不用于对象选择。若第三方 mod 想实现"选中对象显示工具"，应自行用 `RibbonHeaderComponent` 或独立面板实现，不滥用 CONTEXTUAL tab。

### 10.2 ContextualTabGroup 接口

```java
/// 上下文选项卡组：一组基于视图上下文激活的选项卡
public interface ContextualTabGroup {
    /// 唯一标识 (MC ResourceLocation)
    ResourceLocation getId();

    /// 组显示名称 (用于组头部标签, 如 "Map Editor Tools")
    Component getDisplayName();

    /// 组头部图标 (可选)
    Optional<IGuiTexture> getIcon();

    /// 排序优先级 (多个上下文组同时激活时, 越小越靠左)
    int getPriority();

    /// 组的视觉强调色 (可选, 用于组头部底色与 tab 底色, 区分不同上下文)
    Optional<IColor> getAccentColor();

    /// 此组包含的选项卡定义 (每个 tab 的 getDefaultDisplayMode() 应返回 CONTEXTUAL,
    /// 且 getContextualGroupId() 应返回此组的 getId())
    List<RibbonTabDefinition> getTabs();

    /// 激活时 tab 内容的默认显示子模式 (PINNED 或 FLOATING)
    /// CONTEXTUAL tab 激活后, contentVisible 遵循此子模式
    default TabDisplayMode getActiveSubMode() { return TabDisplayMode.PINNED; }
}
```

### 10.3 ViewContextProvider 接口

```java
/// 视图上下文提供者：报告当前活动的视图上下文 ID 集合
/// RibbonBar 通过此接口决定哪些 ContextualTabGroup 应显示
public interface ViewContextProvider {
    /// 当前活动的视图上下文 ID 集合 (可同时多个, 如同时处于 Map Editor 和 Signal Editor)
    Set<ResourceLocation> getActiveContexts();

    /// 注册上下文变化监听器 (RibbonBar 在构造时注册, 上下文变化时刷新 tab 可见性)
    void addContextChangeListener(Runnable listener);

    /// 移除监听器 (RibbonBar 销毁时调用, 避免泄漏)
    void removeContextChangeListener(Runnable listener);
}
```

**测试 seam**：`ViewContextProvider` 是框架提供的注入点。KP 测试时可传入返回固定 `Set` 的 stub 实现，无需启动真实视图系统。

### 10.4 激活流程

```
视图切换 (如 KpMapEditor 进入 Track Graph Editor 视图)
  ↓  ViewContextProvider.getActiveContexts() 返回新的 Set
  ↓  触发已注册的 contextChangeListener
  ↓
RibbonBar.onContextChanged()
  ↓  遍历 RibbonRegistry.getContextualGroups()
  ↓  对每个 group: 检查 group.getId() 是否在 activeContexts 中
  ↓    在 -> group 内所有 tab 的 RibbonTabState.setContextActive(true)
  ↓    不在 -> group 内所有 tab 的 RibbonTabState.setContextActive(false)
  ↓  重建 RibbonHeader (只渲染 isHeaderVisible() == true 的 tab)
  ↓  若当前选中 tab 被停用, 切换到第一个可见的核心 tab
```

### 10.5 KP 集成示例

```java
// KP 适配层: gui/editor/ribbon/KpViewContextProvider.java
public final class KpViewContextProvider implements ViewContextProvider {
    private final KpEditorScreen editorScreen;  // 构造注入
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();

    public KpViewContextProvider(KpEditorScreen editorScreen) {
        this.editorScreen = editorScreen;
    }

    @Override
    public Set<ResourceLocation> getActiveContexts() {
        Set<ResourceLocation> ctx = new HashSet<>();
        // 示例: 根据当前编辑模式报告上下文
        if (editorScreen.isMapEditorActive()) {
            ctx.add(ResourceLocation.fromNamespaceAndPath("kp", "map_editor"));
        }
        if (editorScreen.isTrackGraphEditorActive()) {
            ctx.add(ResourceLocation.fromNamespaceAndPath("kp", "track_graph_editor"));
        }
        return ctx;
    }

    @Override
    public void addContextChangeListener(Runnable listener) { listeners.add(listener); }

    @Override
    public void removeContextChangeListener(Runnable listener) { listeners.remove(listener); }

    /// KP 编辑模式切换时调用
    public void notifyContextChanged() {
        listeners.forEach(Runnable::run);
    }
}
```

### 10.6 RibbonBar 构造注入

`RibbonBar` 接受 `ViewContextProvider` 作为构造参数（可为 `Optional`，无上下文场景传空实现）：

```java
public final class RibbonBar extends UIElement {
    private final ViewContextProvider contextProvider;
    private final RibbonPreferenceStore preferenceStore;

    public RibbonBar(ViewContextProvider contextProvider,
                     RibbonPreferenceStore preferenceStore) {
        this.contextProvider = contextProvider;
        this.preferenceStore = preferenceStore;
        contextProvider.addContextChangeListener(this::onContextChanged);
        // ... 构建 header / content
    }

    private void onContextChanged() {
        Set<ResourceLocation> active = contextProvider.getActiveContexts();
        for (ContextualTabGroup group : RibbonRegistry.getContextualGroups()) {
            boolean isActive = active.contains(group.getId());
            for (RibbonTabDefinition tab : group.getTabs()) {
                tabStates.get(tab.getId()).setContextActive(isActive);
            }
        }
        rebuildHeader();
    }
}
```

**关键约束**：
- `RibbonBar` 通过构造注入获取 `ViewContextProvider`，**不调用**任何 `getInstance()` 静态单例
- 无上下文场景的 mod 可传入 `ViewContextProvider.empty()`（返回空 `Set` 的默认实现）
- `ViewContextProvider` 是测试 seam：单测可 stub 固定上下文，验证 `RibbonTabState.setContextActive()` 行为

---

## 11. 与现有架构集成

### 11.1 RibbonBar 替换

新建通用 `RibbonBar` 类（非 KP 前缀，#24: 通用化），旧 `KpRibbonBar` 删除。

`KpMapEditor.initMenus()` 修改：

```java
@Override
protected void initMenus() {
    menuContainer.clearAllChildren();
    // RibbonBar 通过构造注入获取 ViewContextProvider 和 PreferenceStore (不调 getInstance)
    RibbonBar ribbonBar = new RibbonBar(
        kpViewContextProvider,               // KP 提供的 ViewContextProvider
        new KPConfigRibbonPreferenceStore(config)  // KP 提供的 PreferenceStore 实现
    );
    menuContainer.addChild(ribbonBar);
    top.getLayout().height(60);                // 60px (header + content)
}
```

### 11.2 ToolPanelView 去留

**保留**（#25）。`ToolPanelView` 未来可封装 Ribbon 菜单栏的命令，支持经典菜单式命令布局 + ToolPanel 组合。当前阶段两者并存：Ribbon 提供完整工具组，ToolPanel 提供精简垂直工具栏（仅图标）。

### 11.3 Registry 事件

静态注册（#26: "反正模组不能热加载"）。Mod 在 `ClientSetup` 之前调用 `RibbonRegistry.registerTab()` / `registerContextualGroup()` / `registerHeaderComponent()`，`ClientSetup` 时调用 `RibbonRegistry.freeze()`。无需 NeoForge Event。

### 11.4 包结构

通用框架（`gui/ribbon/`）与 KP 适配层（`gui/editor/ribbon/`）分离，框架不依赖 KP：

```
gui/
├── ribbon/                                # ★ 通用 Ribbon 框架 (不依赖 KP)
│   ├── RibbonBar.java                     # Ribbon 栏主容器 (构造注入 ViewContextProvider + PreferenceStore)
│   ├── RibbonConstants.java               # 公共常量 (DEFAULT_OVERFLOW_WEIGHT 等)
│   ├── api/                               # 定义层 (纯数据接口, 框架 API)
│   │   ├── RibbonTabDefinition.java
│   │   ├── RibbonTabState.java
│   │   ├── TabDisplayMode.java            # PINNED / FLOATING / HIDDEN / CONTEXTUAL
│   │   ├── ContextualTabGroup.java        # 上下文选项卡组
│   │   ├── ViewContextProvider.java       # 视图上下文提供者接口 (测试 seam)
│   │   ├── RibbonToolGroupDefinition.java
│   │   ├── RibbonToolDefinition.java
│   │   ├── ToolSize.java
│   │   ├── ToolAction.java                # sealed interface (Phase 1: ButtonAction + SeparatorAction)
│   │   ├── ButtonAction.java
│   │   ├── SeparatorAction.java
│   │   ├── # Phase 2+ 延后: SplitButtonAction / DropdownAction / DropdownContent / SimpleDropdown / CustomDropdown
│   │   ├── RibbonCommand.java
│   │   ├── RibbonToggleCommand.java
│   │   ├── TooltipContent.java            # sealed interface (Phase 1: TextTooltip)
│   │   ├── TextTooltip.java
│   │   ├── # Phase 2+ 延后: WidgetTooltip
│   │   ├── RibbonHeaderComponent.java     # 原 RibbonTabBarComponent, 重命名
│   │   ├── RibbonPreferenceStore.java     # 配置持久化抽象接口
│   │   └── QuickAccessToolbar.java
│   ├── registry/
│   │   └── RibbonRegistry.java            # 独立注册表 (不封装 KPRegistry)
│   ├── internal/                          # 内部实现 (非 API)
│   │   ├── RibbonBuilder.java             # definition -> UIElement 树
│   │   ├── GroupPanel.java                # 工具组面板渲染
│   │   ├── ToolWidgetFactory.java         # ToolAction -> UIElement
│   │   ├── OverflowMenu.java              # 溢出菜单
│   │   ├── FloatingContentPanel.java      # FLOATING 模式浮层
│   │   ├── ContextMenuFactory.java        # 右键菜单构建
│   │   └── RibbonPreferences.java         # 通过 RibbonPreferenceStore 读写
│   └── QatBar.java                        # QAT UI 实现
│
├── editor/
│   ├── ribbon/                            # ★ KP 适配层 (依赖 gui/ribbon 框架 + KP 自身类)
│   │   ├── KpToolDefinitions.java         # Tool 枚举 -> RibbonToolDefinition 集中映射 (DRY)
│   │   ├── KpViewContextProvider.java     # ViewContextProvider 实现, 报告 KP 视图上下文
│   │   ├── KPConfigRibbonPreferenceStore.java  # RibbonPreferenceStore 实现, 读写 KPConfig TOML
│   │   ├── KpRibbonRegistration.java      # ClientSetup 时注册 KP 自身的 tab/group/component
│   │   └── ToolToggleCommand.java         # RibbonToggleCommand 适配 EditToolState (构造注入)
│   ├── KpMapEditor.java                   # 修改 initMenus() 使用 RibbonBar
│   └── ...
│
└── widgets/                               # 既有可复用组件
    ├── KpIconButton.java
    └── ...
```

**包结构约束**：
- `gui/ribbon/` 内任何类**不得** import `net.jsmua.kinetic_planner.*`（KP 包）—— 可通过 CI 检查 import 强制
- `gui/ribbon/` 可 import `com.lowdragmc.ldlib2.*`（LDLib2）和 `net.minecraft.*`（MC）
- `gui/editor/ribbon/` 是 KP 适配层，可同时 import `gui/ribbon/` 和 KP 类

### 11.5 Config 集成

框架定义 `RibbonPreferenceStore` 接口，KP 提供 `KPConfigRibbonPreferenceStore` 实现写入 `KPConfig` TOML（#28）：

```java
// 框架 API: RibbonPreferenceStore (gui/ribbon/api/)
public interface RibbonPreferenceStore {
    Optional<TabDisplayMode> getTabDisplayMode(ResourceLocation tabId);
    void setTabDisplayMode(ResourceLocation tabId, TabDisplayMode mode);

    List<ResourceLocation> getQatToolIds();
    void setQatToolIds(List<ResourceLocation> ids);

    Optional<ResourceLocation> getSelectedTabId();
    void setSelectedTabId(ResourceLocation id);
}

// KP 适配层: KPConfigRibbonPreferenceStore (gui/editor/ribbon/)
final class KPConfigRibbonPreferenceStore implements RibbonPreferenceStore {
    private final IKPConfig config;  // 构造注入 KP 配置中介

    KPConfigRibbonPreferenceStore(IKPConfig config) {
        this.config = config;
    }
    // ... 实现读写 KPConfig 的 [ribbon] 段
}
```

```java
// KPConfig 新增 [ribbon] 段 (main sourceSet)
private static final ModConfigSpec.ConfigValue<String> RIBBON_SELECTED_TAB =
    BUILDER.comment("Last selected ribbon tab")
        .define("ribbon.state.selected_tab", "kp:tools");

// tab display modes 和 QAT tools 列表用 JSON 字符串存储在 TOML 中
// 或用 ModConfigSpec 的 ListConfigValue
```

---

## 12. QoL 实现

1. **Tooltip 系统**：hover 延迟 500ms 显示，支持 `TextTooltip`（Component 文本）两种模式（`WidgetTooltip` Phase 2+ 延后）
2. **快捷键标签**：在 tooltip 中显示快捷键（如 "Pan (P)"），不显示在按钮上
3. **QAT 空状态**：QAT 为空时显示淡色星标图标，hover 提示"右键工具添加到快速访问"
4. **Tab 溢出滚动**：tab 数量超出宽度时，复用 LDLib2 `ScrollerView` 横向滚动（TabView 已内置）
5. **记忆选中 tab**：持久化 `selectedTabId` 到 config，重开编辑器时恢复
6. **工具禁用灰显**：`RibbonCommand.isEnabled()` 返回 false 时，按钮 `disabled()` + 灰色滤镜
7. **Toggle 视觉反馈**：active 状态用 LDLib2 Button 的 `pressedTexture` 或 `addClass("__active__")` + CSS（#4: 能复用就复用, 之后提供主题）
8. **右键菜单快捷键标注**：`Menu` 的 leaf 节点 UI 包含快捷键标签
9. **FLOATING 面板屏幕边界自适应**：复用 `Menu.onLayoutChanged()` 中的屏幕边界检测逻辑
10. **CONTEXTUAL tab 视觉区分**：上下文 tab 组用 `getAccentColor()` 染色头部底色，与核心 tab 视觉区分

---

## 13. 测试策略

### 13.1 单元测试（client sourceSet, 纯 JVM + Mockito）

| 测试目标 | 验证点 |
|---------|--------|
| `RibbonRegistry` 冻结生命周期 | 冻结前可注册，冻结后拒绝注册；`resetForTest()` 可重置 |
| `RibbonTabState` 状态不变量 | HIDDEN -> contentVisible=false；PINNED -> contentVisible=true；FLOATING -> 可切换；CONTEXTUAL -> setContextActive 控制头部可见性 |
| `TabDisplayMode` 状态转换 | PINNED <-> FLOATING <-> HIDDEN 用户控制转换正确；CONTEXTUAL 不响应 setDisplayMode |
| `RibbonPreferenceStore` stub | 用 in-memory stub 实现，验证 RibbonPreferences 读写往返一致（不依赖真实 KPConfig） |
| `QuickAccessToolbar` 增删查 | addTool/removeTool/containsTool 逻辑正确，ID 类型为 `ResourceLocation` |
| `ToolToggleCommand` 构造注入 | 注入 mock `EditToolState`，验证 execute/isActive/setActive 调用 mock，**不调用** `getInstance()` |
| `ViewContextProvider` stub | stub 返回固定 `Set<ResourceLocation>`，验证 `RibbonBar.onContextChanged()` 正确激活/停用 ContextualTabGroup |
| `ContextualTabGroup` 注册 | 通过 `RibbonRegistry.registerContextualGroup()` 注册后可查询 |

### 13.2 手动验收清单

| 验收项 | 操作 | 预期结果 |
|--------|------|---------|
| Tab 切换 | 点击不同 tab | RibbonContent 切换到对应工具组 |
| PINNED 模式 | 固定 tab | 内容面板占据 40px 高度 |
| FLOATING 模式 | 浮动 tab, 点击 tab 头 | 浮层面板弹出, 不占布局空间 |
| FLOATING 失焦 | 点击浮层外部 | 浮层收起 |
| HIDDEN 模式 | 隐藏 tab | tab 头消失, 设置中可恢复 |
| CONTEXTUAL 激活 | 切换到对应视图 | 上下文 tab 组出现 |
| CONTEXTUAL 停用 | 退出视图 | 上下文 tab 组消失，选中切回核心 tab |
| CONTEXTUAL 右键 | 右键上下文 tab | 不显示"固定/浮动/隐藏"选项 |
| 右键 tool 菜单 | 右键工具按钮 | Menu 弹出, 含"添加到 QAT"等选项 |
| 添加到 QAT | 右键 -> "添加到快速访问" | QAT 出现新图标按钮 |
| QAT 点击 | 点击 QAT 按钮 | 执行原工具的 command |
| 工具禁用 | command.isEnabled()=false | 按钮灰显, 不可点击 |
| Toggle active | 切换 toggle 工具 | 按钮视觉反映 on/off |
| Tooltip | hover 工具按钮 500ms | 显示 tooltip (含快捷键) |
| 溢出菜单 | 缩小窗口宽度 | 工具按权重缩窄, 最终进溢出菜单 |
| 持久化 | 修改 displayMode + QAT -> 重开 | 偏好恢复 |

---

## 14. 相关文件索引

### 14.1 通用框架（`gui/ribbon/`，不依赖 KP）

| 文件 | 职责 | 状态 |
|------|------|------|
| `gui/ribbon/RibbonBar.java` | Ribbon 栏主容器（构造注入 ViewContextProvider + PreferenceStore） | `[TODO]` |
| `gui/ribbon/RibbonConstants.java` | 公共常量（DEFAULT_OVERFLOW_WEIGHT 等） | `[TODO]` |
| `gui/ribbon/api/RibbonTabDefinition.java` | 选项卡定义接口 | `[TODO]` |
| `gui/ribbon/api/RibbonTabState.java` | 选项卡运行时状态 | `[TODO]` |
| `gui/ribbon/api/TabDisplayMode.java` | 显示模式枚举（PINNED/FLOATING/HIDDEN/CONTEXTUAL） | `[TODO]` |
| `gui/ribbon/api/ContextualTabGroup.java` | 上下文选项卡组接口 | `[TODO]` |
| `gui/ribbon/api/ViewContextProvider.java` | 视图上下文提供者接口（测试 seam） | `[TODO]` |
| `gui/ribbon/api/RibbonToolDefinition.java` | 工具定义接口 | `[TODO]` |
| `gui/ribbon/api/RibbonToolGroupDefinition.java` | 工具组定义接口 | `[TODO]` |
| `gui/ribbon/api/ToolAction.java` | 工具动作 sealed interface（Phase 1: Button+Separator） | `[TODO]` |
| `gui/ribbon/api/ButtonAction.java` | 按钮动作 record | `[TODO]` |
| `gui/ribbon/api/SeparatorAction.java` | 分隔符动作 record | `[TODO]` |
| `gui/ribbon/api/RibbonCommand.java` | 命令回调接口 | `[TODO]` |
| `gui/ribbon/api/RibbonToggleCommand.java` | 开关型命令接口 | `[TODO]` |
| `gui/ribbon/api/TooltipContent.java` | Tooltip sealed interface（Phase 1: TextTooltip） | `[TODO]` |
| `gui/ribbon/api/TextTooltip.java` | 文本 tooltip record | `[TODO]` |
| `gui/ribbon/api/RibbonHeaderComponent.java` | 头部组件接口（原 RibbonTabBarComponent） | `[TODO]` |
| `gui/ribbon/api/RibbonPreferenceStore.java` | 配置持久化抽象接口 | `[TODO]` |
| `gui/ribbon/api/QuickAccessToolbar.java` | QAT 接口 | `[TODO]` |
| `gui/ribbon/registry/RibbonRegistry.java` | 独立注册表（不封装 KPRegistry） | `[TODO]` |
| `gui/ribbon/internal/RibbonBuilder.java` | definition -> UIElement 构建 | `[TODO]` |
| `gui/ribbon/internal/GroupPanel.java` | 工具组面板渲染 | `[TODO]` |
| `gui/ribbon/internal/ToolWidgetFactory.java` | ToolAction -> UIElement | `[TODO]` |
| `gui/ribbon/internal/OverflowMenu.java` | 溢出菜单 | `[TODO]` |
| `gui/ribbon/internal/FloatingContentPanel.java` | FLOATING 浮层 | `[TODO]` |
| `gui/ribbon/internal/ContextMenuFactory.java` | 右键菜单构建 | `[TODO]` |
| `gui/ribbon/internal/RibbonPreferences.java` | 通过 RibbonPreferenceStore 读写 | `[TODO]` |
| `gui/ribbon/QatBar.java` | QAT UI 实现 | `[TODO]` |

### 14.2 KP 适配层（`gui/editor/ribbon/`，依赖框架 + KP）

| 文件 | 职责 | 状态 |
|------|------|------|
| `gui/editor/ribbon/KpToolDefinitions.java` | Tool 枚举 -> RibbonToolDefinition 集中映射（DRY） | `[TODO]` |
| `gui/editor/ribbon/KpViewContextProvider.java` | ViewContextProvider 实现 | `[TODO]` |
| `gui/editor/ribbon/KPConfigRibbonPreferenceStore.java` | RibbonPreferenceStore 实现，读写 KPConfig | `[TODO]` |
| `gui/editor/ribbon/KpRibbonRegistration.java` | ClientSetup 时注册 KP tab/group/component | `[TODO]` |
| `gui/editor/ribbon/ToolToggleCommand.java` | RibbonToggleCommand 适配 EditToolState（构造注入） | `[TODO]` |
| `gui/editor/KpMapEditor.java` | 修改 initMenus() 使用 RibbonBar | `[TODO]` |
| `gui/widgets/ribbon/KpRibbonBar.java` | 旧实现, 删除 | `[TODO: DELETE]` |

### 14.3 Phase 2+ 延后文件（不在首版创建）

| 文件 | 延后原因 |
|------|---------|
| `SplitButtonAction.java` / `DropdownAction.java` | 无当前消费者，YAGNI |
| `DropdownContent.java` / `SimpleDropdown.java` / `CustomDropdown.java` | 依赖上述动作，一并延后 |
| `WidgetTooltip.java` | 无当前消费者，YAGNI |

---

## 15. LDLib2 依赖验证记录

| 组件 | 用途 | 验证状态 |
|------|------|---------|
| `UIElement` | 基类, flex layout, CSS class, event system | 已验证 |
| `Button` | 工具按钮, 支持 preIcon/postIcon/disabled/buttonStyle | 已验证 |
| `Label` | 组标签, 文本显示 | 已验证 |
| `Toggle` + `ToggleGroup` | 开关型工具, 单选行为 | 已验证 |
| `Tab` + `TabView` | 选项卡容器 (可参考, 但 Ribbon 自建 tab 切换逻辑) | 已验证 |
| `Menu<K,T>` | 右键上下文菜单, 嵌套子菜单, autoClose, 屏幕自适应 | 已验证 |
| `Selector<T>` | 简单下拉 (Phase 2+ SimpleDropdown 内部使用) | 已验证 |
| `ScrollerView` | tab strip 横向滚动 | 已验证 |
| `TaffyPosition.ABSOLUTE` | FLOATING 模式绝对定位 | 已验证 |
| `Sprites.BORDER_THICK_RT1` | 浮动面板背景 | 已验证 |
| `UIEvents.BLUR` | 失焦检测 | 已验证 |
| `Stylesheet` / LSS | CSS 样式系统, 主题支持 | 已验证 |

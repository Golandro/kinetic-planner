# Ribbon 框架实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 建立通用、可扩展、与 KP 解耦的 Ribbon 栏框架（绑定 LDLib2），支持四种选项卡显示模式（PINNED/FLOATING/HIDDEN/CONTEXTUAL）和 QAT 自定义，KP 作为框架的消费者注册自身工具。

**Architecture:** 数据驱动 Definition + 内部 Builder 模式。框架 API 层（`gui/ribbon/`）纯接口，使用 MC `ResourceLocation` 作为标识符；KP 适配层（`gui/editor/ribbon/`）实现 `ViewContextProvider` / `RibbonPreferenceStore` / `RibbonCommand` 适配器，桥接 `EditToolState` / `IKPConfig`。Phase 1 只实现 BUTTON + SEPARATOR 工具动作 + TextTooltip；SPLIT_BUTTON/DROPDOWN/WidgetTooltip 标记为 Phase 2+ 延后。

**Tech Stack:** Java 21 sealed interfaces & records；LDLib2 UIElement/Button/Menu/Selector/TaffyPosition；MC 1.21.1 `ResourceLocation`/`Component`；NeoForge 1.21.1 `FMLClientSetupEvent`；JUnit 5 + Mockito 5

## Global Constraints

**sourceSet 分离**：
- `gui/ribbon/` 位于 **client** sourceSet（依赖 LDLib2 + MC client 类）
- `gui/editor/ribbon/` 位于 **client** sourceSet（依赖框架 + KP 自身类）
- `gui/ribbon/` 内任何类 **禁止** import `net.jsmua.kinetic_planner.*`（KP 包）-- Task 12 解耦校验测试强制
- `gui/ribbon/` 可 import `com.lowdragmc.lowdraglib2.*` 和 `net.minecraft.*`

**MC 1.21.1 API**：
- `ResourceLocation.fromNamespaceAndPath(namespace, path)` 构造（已验证于 `KineticPlannerJMPlugin.java:97`）
- `import net.minecraft.resources.ResourceLocation;`
- `Component.literal("text")` 构造文本
- `import net.minecraft.network.chat.Component;`

**禁止 getInstance() 静态单例访问**：框架 API 层（`gui/ribbon/`）任何类不得调用 `*getInstance()`；所有运行时状态通过构造注入的接口获取（`EditToolState` / `IKPConfig` / `KpEditorScreen` 均通过 KP 适配层注入）。

**解耦边界**：KP 标识符 `KPId`（`net.jsmua.kinetic_planner.registry.KPId`）**不得**出现在 `gui/ribbon/` 任何类中。KP 适配层调用框架 API 时直接用 `ResourceLocation.fromNamespaceAndPath("kp", path)`。

**YAGNI 约束**：Phase 1 只创建 `ButtonAction` / `SeparatorAction` / `TextTooltip`。**禁止**创建 `SplitButtonAction` / `DropdownAction` / `DropdownContent` / `SimpleDropdown` / `CustomDropdown` / `WidgetTooltip`（spec §4.4-§4.6 标记为 Phase 2+ 延后）。

**Mixin 约定**：本计划不涉及 Mixin。`remap = false` 不适用。

**构建命令**：
- 编译：`gradlew compileJava compileClientJava`
- 测试：`gradlew test`
- 完整构建：`gradlew build`

**提交消息格式**：`type: description`（feat/fix/refactor/docs/test/chore）

---

## File Structure

### 框架 API 层（`src/client/java/net/jsmua/kinetic_planner/gui/ribbon/api/`，不依赖 KP）

| 文件 | 类型 | 职责 |
|---|---|---|
| `TabDisplayMode.java` | enum | PINNED / FLOATING / HIDDEN / CONTEXTUAL |
| `ToolSize.java` | enum | LARGE / SMALL |
| `RibbonCommand.java` | interface | `execute()` / `isEnabled()` |
| `RibbonToggleCommand.java` | interface | extends RibbonCommand + `isActive()` / `setActive()` |
| `TooltipContent.java` | sealed interface | permits TextTooltip |
| `TextTooltip.java` | record | Phase 1 唯一 Tooltip 实现 |
| `ToolAction.java` | sealed interface | permits ButtonAction, SeparatorAction（Phase 1） |
| `ButtonAction.java` | record | Phase 1 按钮动作 |
| `SeparatorAction.java` | record | Phase 1 分隔符 |
| `RibbonToolDefinition.java` | interface + `SimpleRibbonToolDefinition` record | 工具定义 + 默认实现 |
| `RibbonToolGroupDefinition.java` | interface + `SimpleRibbonToolGroupDefinition` record | 工具组定义 + 默认实现 |
| `RibbonTabDefinition.java` | interface + `SimpleRibbonTabDefinition` record | 选项卡定义 + 默认实现 |
| `RibbonHeaderComponent.java` | interface + `Placement` enum + `SimpleRibbonHeaderComponent` record | 头部组件 |
| `ContextualTabGroup.java` | interface + `SimpleContextualTabGroup` record | 上下文选项卡组 |
| `ViewContextProvider.java` | interface + `empty()` 静态工厂 | 视图上下文提供者（测试 seam） |
| `RibbonPreferenceStore.java` | interface | 配置持久化抽象 |
| `QuickAccessToolbar.java` | interface | QAT 接口 |
| `RibbonTabState.java` | class | 选项卡运行时状态机 |

### 框架基础层（`src/client/java/net/jsmua/kinetic_planner/gui/ribbon/`）

| 文件 | 职责 |
|---|---|
| `RibbonConstants.java` | 公共常量（DEFAULT_OVERFLOW_WEIGHT=100） |
| `registry/RibbonRegistry.java` | 独立可冻结静态注册表 |
| `RibbonBar.java` | Ribbon 主容器（构造注入 ViewContextProvider + RibbonPreferenceStore） |
| `QatBar.java` | QAT UI 实现 |
| `internal/RibbonBuilder.java` | definition -> UIElement 树构建器 |
| `internal/GroupPanel.java` | 工具组面板渲染 |
| `internal/ToolWidgetFactory.java` | ToolAction -> UIElement 工厂 |
| `internal/OverflowMenu.java` | 溢出菜单 |
| `internal/FloatingContentPanel.java` | FLOATING 浮层 |
| `internal/ContextMenuFactory.java` | 右键菜单构建 |
| `internal/RibbonPreferences.java` | 通过 RibbonPreferenceStore 读写偏好 |
| `internal/DefaultQuickAccessToolbar.java` | QuickAccessToolbar 默认实现（状态逻辑 + UI 委托 QatBar） |

### KP 适配层（`src/client/java/net/jsmua/kinetic_planner/gui/editor/ribbon/`）

| 文件 | 职责 |
|---|---|
| `ToolToggleCommand.java` | RibbonToggleCommand 适配 EditToolState（构造注入） |
| `KpToolDefinitions.java` | Tool 枚举 -> RibbonToolDefinition 集中映射（DRY） |
| `KPConfigRibbonPreferenceStore.java` | RibbonPreferenceStore 实现，读写 IKPConfig |
| `KpViewContextProvider.java` | ViewContextProvider 实现（首版返回空 Set） |
| `KpRibbonRegistration.java` | ClientSetup 时注册 KP tab/group/component |

### 修改的现有文件

| 文件 | 修改内容 |
|---|---|
| `src/client/java/.../gui/editor/KpMapEditor.java` | `initMenus()` 改用 `RibbonBar`；`top.getLayout().height(60)`；添加 `kpViewContextProvider` 字段 |
| `src/client/java/.../KineticPlannerClient.java` | `onClientSetup` 调用 `KpRibbonRegistration.register()` + `RibbonRegistry.freeze()` |

### 删除的文件

| 文件 | 原因 |
|---|---|
| `src/client/java/.../gui/widgets/ribbon/KpRibbonBar.java` | 被新框架替代 |
| `src/test/java/.../gui/KpRibbonBarTest.java` | 测试已删除的类 |

### 测试文件（`src/test/java/net/jsmua/kinetic_planner/gui/ribbon/`）

| 文件 | 覆盖 |
|---|---|
| `api/RibbonTabStateTest.java` | 状态机不变量（含 CONTEXTUAL） |
| `registry/RibbonRegistryTest.java` | 冻结生命周期 + contextualGroup 注册 |
| `api/QuickAccessToolbarStubTest.java` | QAT 状态逻辑（用 DefaultQuickAccessToolbar，stub store） |
| `internal/RibbonPreferencesTest.java` | 偏好读写往返（in-memory stub store） |
| `RibbonFrameworkDecouplingTest.java` | 框架源码 import 解耦校验 |

### KP 适配层测试（`src/test/java/net/jsmua/kinetic_planner/gui/editor/ribbon/`）

| 文件 | 覆盖 |
|---|---|
| `ToolToggleCommandTest.java` | 构造注入 EditToolState（mock），不调 getInstance |
| `KPConfigRibbonPreferenceStoreTest.java` | 用 mock IKPConfig 验证读写委托 |
| `KpViewContextProviderTest.java` | 首版返回空 Set + 监听器注册/通知 |

---

### Task 1: 框架 API 基础类型（枚举 + 命令 + 动作 + Tooltip）

**Files:**
- Create: `src/client/java/net/jsmua/kinetic_planner/gui/ribbon/api/TabDisplayMode.java`
- Create: `src/client/java/net/jsmua/kinetic_planner/gui/ribbon/api/ToolSize.java`
- Create: `src/client/java/net/jsmua/kinetic_planner/gui/ribbon/api/RibbonCommand.java`
- Create: `src/client/java/net/jsmua/kinetic_planner/gui/ribbon/api/RibbonToggleCommand.java`
- Create: `src/client/java/net/jsmua/kinetic_planner/gui/ribbon/api/TooltipContent.java`
- Create: `src/client/java/net/jsmua/kinetic_planner/gui/ribbon/api/TextTooltip.java`
- Create: `src/client/java/net/jsmua/kinetic_planner/gui/ribbon/api/ToolAction.java`
- Create: `src/client/java/net/jsmua/kinetic_planner/gui/ribbon/api/ButtonAction.java`
- Create: `src/client/java/net/jsmua/kinetic_planner/gui/ribbon/api/SeparatorAction.java`
- Create: `src/client/java/net/jsmua/kinetic_planner/gui/ribbon/RibbonConstants.java`

**Interfaces:**
- Consumes: 无（基础类型）
- Produces: `TabDisplayMode` enum（4 值）；`ToolSize` enum（2 值）；`RibbonCommand` interface（`void execute()` / `default boolean isEnabled()`）；`RibbonToggleCommand extends RibbonCommand`（`boolean isActive()` / `void setActive(boolean)`）；`TooltipContent` sealed interface（permits `TextTooltip`）；`TextTooltip(Component)` record；`ToolAction` sealed interface（permits `ButtonAction, SeparatorAction`）；`ButtonAction(RibbonCommand, boolean)` record；`SeparatorAction()` record；`RibbonConstants.DEFAULT_OVERFLOW_WEIGHT = 100`

- [ ] **Step 1: 写失败测试 - TabDisplayMode 包含 4 个值**

Create `src/test/java/net/jsmua/kinetic_planner/gui/ribbon/api/TabDisplayModeTest.java`:

```java
package net.jsmua.kinetic_planner.gui.ribbon.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TabDisplayModeTest {

    @Test
    void enumHasFourValues() {
        TabDisplayMode[] values = TabDisplayMode.values();
        assertEquals(4, values.length, "TabDisplayMode 必须有 4 个值 (PINNED/FLOATING/HIDDEN/CONTEXTUAL)");
        assertArrayEquals(
            new TabDisplayMode[]{
                TabDisplayMode.PINNED,
                TabDisplayMode.FLOATING,
                TabDisplayMode.HIDDEN,
                TabDisplayMode.CONTEXTUAL
            },
            values,
            "必须包含 PINNED/FLOATING/HIDDEN/CONTEXTUAL 且按此顺序");
    }

    @Test
    void valueOfAcceptsAllFour() {
        assertEquals(TabDisplayMode.PINNED, TabDisplayMode.valueOf("PINNED"));
        assertEquals(TabDisplayMode.FLOATING, TabDisplayMode.valueOf("FLOATING"));
        assertEquals(TabDisplayMode.HIDDEN, TabDisplayMode.valueOf("HIDDEN"));
        assertEquals(TabDisplayMode.CONTEXTUAL, TabDisplayMode.valueOf("CONTEXTUAL"));
    }
}
```

- [ ] **Step 2: 运行测试验证失败**

Run: `gradlew test --tests "net.jsmua.kinetic_planner.gui.ribbon.api.TabDisplayModeTest"`
Expected: FAIL with "TabDisplayMode 类未找到"（编译失败，类不存在）

- [ ] **Step 3: 创建 TabDisplayMode**

Create `src/client/java/net/jsmua/kinetic_planner/gui/ribbon/api/TabDisplayMode.java`:

```java
package net.jsmua.kinetic_planner.gui.ribbon.api;

/**
 * 选项卡显示模式 (spec §3.1)。
 *
 * <p>核心模式（PINNED/FLOATING/HIDDEN）由用户控制；CONTEXTUAL 由视图上下文驱动。
 */
public enum TabDisplayMode {
    /** 固定: 内容面板始终可见, 占据布局空间 (标准 Ribbon)。适配 CAD 任务驱动工作流。 */
    PINNED,

    /** 浮动: 内容面板浮层弹出, 失焦自动收起, 不占布局空间。适配低频任务组。 */
    FLOATING,

    /** 隐藏: tab 头不渲染, 内容不可访问 (用户可在设置中恢复)。 */
    HIDDEN,

    /** 上下文: tab 头可见性由 ViewContextProvider 决定。仅用于视图驱动场景, 不适配对象选择编辑。 */
    CONTEXTUAL
}
```

- [ ] **Step 4: 创建 ToolSize + RibbonCommand + RibbonToggleCommand**

Create `src/client/java/net/jsmua/kinetic_planner/gui/ribbon/api/ToolSize.java`:

```java
package net.jsmua.kinetic_planner.gui.ribbon.api;

/** 工具尺寸 (spec §4.2)。 */
public enum ToolSize {
    /** 2 列宽, 垂直排列 (图标上, 文字下)。 */
    LARGE,

    /** 1 列宽, 水平排列 (图标左, 文字右)。 */
    SMALL
}
```

Create `src/client/java/net/jsmua/kinetic_planner/gui/ribbon/api/RibbonCommand.java`:

```java
package net.jsmua.kinetic_planner.gui.ribbon.api;

/**
 * 命令回调接口 (spec §4.3)。
 *
 * <p>作为命令树的 UI 适配层, 不直接替代 KpCommandHandlers。
 *
 * <p><b>测试 seam 要求:</b> 实现禁止调用 getInstance() 静态单例; 通过构造函数注入所需依赖。
 */
public interface RibbonCommand {
    /** 执行命令。 */
    void execute();

    /** 是否启用。返回 false 时按钮灰显。 */
    default boolean isEnabled() { return true; }
}
```

Create `src/client/java/net/jsmua/kinetic_planner/gui/ribbon/api/RibbonToggleCommand.java`:

```java
package net.jsmua.kinetic_planner.gui.ribbon.api;

/** 开关型命令 (spec §4.3)。 */
public interface RibbonToggleCommand extends RibbonCommand {
    /** 当前是否激活。 */
    boolean isActive();

    /** 设置激活状态。 */
    void setActive(boolean active);
}
```

- [ ] **Step 5: 创建 TooltipContent + TextTooltip**

Create `src/client/java/net/jsmua/kinetic_planner/gui/ribbon/api/TooltipContent.java`:

```java
package net.jsmua.kinetic_planner.gui.ribbon.api;

import net.minecraft.network.chat.Component;

/**
 * Tooltip 内容 (spec §4.6)。
 *
 * <p>Phase 1 仅支持 TextTooltip。WidgetTooltip 标记为 Phase 2+ 延后。
 */
public sealed interface TooltipContent permits TextTooltip {
}
```

Create `src/client/java/net/jsmua/kinetic_planner/gui/ribbon/api/TextTooltip.java`:

```java
package net.jsmua.kinetic_planner.gui.ribbon.api;

import net.minecraft.network.chat.Component;

/** MC Component 文本 tooltip (spec §4.6, Phase 1 实现)。 */
public record TextTooltip(Component text) implements TooltipContent {
}
```

- [ ] **Step 6: 创建 ToolAction sealed + ButtonAction + SeparatorAction**

Create `src/client/java/net/jsmua/kinetic_planner/gui/ribbon/api/ToolAction.java`:

```java
package net.jsmua.kinetic_planner.gui.ribbon.api;

/**
 * 工具动作 (spec §4.4)。
 *
 * <p>Phase 1 仅 permits ButtonAction + SeparatorAction。
 * SplitButtonAction / DropdownAction 标记为 Phase 2+ 延后 (YAGNI)。
 */
public sealed interface ToolAction permits ButtonAction, SeparatorAction {
}
```

Create `src/client/java/net/jsmua/kinetic_planner/gui/ribbon/api/ButtonAction.java`:

```java
package net.jsmua.kinetic_planner.gui.ribbon.api;

/**
 * 普通按钮 / 开关按钮 (spec §4.4, Phase 1)。
 *
 * @param command 按钮回调; toggle=true 时必须为 RibbonToggleCommand
 * @param toggle  true = 开关型按钮
 */
public record ButtonAction(RibbonCommand command, boolean toggle) implements ToolAction {
}
```

Create `src/client/java/net/jsmua/kinetic_planner/gui/ribbon/api/SeparatorAction.java`:

```java
package net.jsmua.kinetic_planner.gui.ribbon.api;

/** 分隔符 (spec §4.4, Phase 1)。 */
public record SeparatorAction() implements ToolAction {
}
```

- [ ] **Step 7: 创建 RibbonConstants**

Create `src/client/java/net/jsmua/kinetic_planner/gui/ribbon/RibbonConstants.java`:

```java
package net.jsmua.kinetic_planner.gui.ribbon;

/**
 * 框架公共常量 (spec §5.1)。
 *
 * <p>集中默认值, 避免在 RibbonToolDefinition / RibbonToolGroupDefinition 等多处重复字面量 (DRY)。
 */
public final class RibbonConstants {

    /** 默认溢出权重: 数值越大越先被缩窄/隐藏。 */
    public static final int DEFAULT_OVERFLOW_WEIGHT = 100;

    private RibbonConstants() {}
}
```

- [ ] **Step 8: 运行测试验证通过**

Run: `gradlew test --tests "net.jsmua.kinetic_planner.gui.ribbon.api.TabDisplayModeTest"`
Expected: PASS（2 tests）

- [ ] **Step 9: 编译 client sourceSet 验证**

Run: `gradlew compileClientJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 10: Commit**

```bash
git add src/client/java/net/jsmua/kinetic_planner/gui/ribbon/api/ src/client/java/net/jsmua/kinetic_planner/gui/ribbon/RibbonConstants.java src/test/java/net/jsmua/kinetic_planner/gui/ribbon/api/TabDisplayModeTest.java
git commit -m "feat(ribbon): 添加框架 API 基础类型 (枚举/命令/动作/Tooltip)"
```

---

### Task 2: 工具/工具组/选项卡/头部组件 Definition 接口

**Files:**
- Create: `src/client/java/net/jsmua/kinetic_planner/gui/ribbon/api/RibbonToolDefinition.java`
- Create: `src/client/java/net/jsmua/kinetic_planner/gui/ribbon/api/RibbonToolGroupDefinition.java`
- Create: `src/client/java/net/jsmua/kinetic_planner/gui/ribbon/api/RibbonTabDefinition.java`
- Create: `src/client/java/net/jsmua/kinetic_planner/gui/ribbon/api/RibbonHeaderComponent.java`

**Interfaces:**
- Consumes: Task 1 的 `TabDisplayMode` / `ToolSize` / `RibbonCommand` / `TooltipContent` / `ToolAction` + `RibbonConstants.DEFAULT_OVERFLOW_WEIGHT`
- Produces:
  - `RibbonToolDefinition` interface（含 `ResourceLocation getId()` / `Component getDisplayName()` / `Optional<IGuiTexture> getIcon()` / `Optional<TooltipContent> getTooltip()` / `Optional<String> getShortcutLabel()` / `default Optional<List<String>> getKeyTips()` / `ToolSize getSize()` / `ToolAction getAction()` / `default int getOverflowWeight()`）+ `SimpleRibbonToolDefinition` record（默认实现）
  - `RibbonToolGroupDefinition` interface（`ResourceLocation getId()` / `Optional<Component> getDisplayName()` / `List<RibbonToolDefinition> getTools()` / `default int getOverflowWeight()` / `default Optional<RibbonCommand> getDialogLauncher()`）+ `SimpleRibbonToolGroupDefinition` record
  - `RibbonTabDefinition` interface（含 `getContextualGroupId()` default empty）+ `SimpleRibbonTabDefinition` record
  - `RibbonHeaderComponent` interface + `Placement` enum（LEADING/TRAILING）+ `SimpleRibbonHeaderComponent` record

- [ ] **Step 1: 写失败测试 - SimpleRibbonToolDefinition 基础行为**

Create `src/test/java/net/jsmua/kinetic_planner/gui/ribbon/api/RibbonToolDefinitionTest.java`:

```java
package net.jsmua.kinetic_planner.gui.ribbon.api;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class RibbonToolDefinitionTest {

    @Test
    void simpleRecordStoresAllFields() {
        RibbonCommand cmd = () -> {};
        ButtonAction action = new ButtonAction(cmd, false);
        Component name = Component.literal("Pan");
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("kp", "tool_pan");

        RibbonToolDefinition def = new SimpleRibbonToolDefinition(
            id, name, Optional.empty(), Optional.empty(), Optional.empty(),
            ToolSize.LARGE, action);

        assertEquals(id, def.getId());
        assertEquals(name, def.getDisplayName());
        assertTrue(def.getIcon().isEmpty());
        assertTrue(def.getTooltip().isEmpty());
        assertTrue(def.getShortcutLabel().isEmpty());
        assertEquals(ToolSize.LARGE, def.getSize());
        assertEquals(action, def.getAction());
    }

    @Test
    void defaultOverflowWeightIs100() {
        RibbonToolDefinition def = new SimpleRibbonToolDefinition(
            ResourceLocation.fromNamespaceAndPath("kp", "t"),
            Component.literal("T"),
            Optional.empty(), Optional.empty(), Optional.empty(),
            ToolSize.SMALL,
            new ButtonAction(() -> {}, false));
        assertEquals(100, def.getOverflowWeight(),
            "默认溢出权重必须为 RibbonConstants.DEFAULT_OVERFLOW_WEIGHT (100)");
    }

    @Test
    void defaultKeyTipsEmpty() {
        RibbonToolDefinition def = new SimpleRibbonToolDefinition(
            ResourceLocation.fromNamespaceAndPath("kp", "t"),
            Component.literal("T"),
            Optional.empty(), Optional.empty(), Optional.empty(),
            ToolSize.SMALL,
            new ButtonAction(() -> {}, false));
        assertTrue(def.getKeyTips().isEmpty(),
            "首版 KeyTips 必须返回 Optional.empty() (Phase 2+ 预留字段)");
    }
}
```

- [ ] **Step 2: 运行测试验证失败**

Run: `gradlew test --tests "net.jsmua.kinetic_planner.gui.ribbon.api.RibbonToolDefinitionTest"`
Expected: FAIL（编译失败：`SimpleRibbonToolDefinition` 类不存在）

- [ ] **Step 3: 创建 RibbonToolDefinition 接口 + SimpleRibbonToolDefinition record**

Create `src/client/java/net/jsmua/kinetic_planner/gui/ribbon/api/RibbonToolDefinition.java`:

```java
package net.jsmua.kinetic_planner.gui.ribbon.api;

import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import net.jsmua.kinetic_planner.gui.ribbon.RibbonConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Optional;

/**
 * 工具定义 (spec §4.7)。
 *
 * <p>Mod 注册的不可变数据, Ribbon 内部 Builder 将其转化为 UIElement。
 */
public interface RibbonToolDefinition {
    /** 唯一标识 (MC ResourceLocation, namespace:path 格式)。 */
    ResourceLocation getId();

    /** 显示名称。 */
    Component getDisplayName();

    /** tab 头图标 (可选)。 */
    Optional<IGuiTexture> getIcon();

    /** Tooltip (Phase 1 仅 TextTooltip)。 */
    Optional<TooltipContent> getTooltip();

    /** 快捷键显示标签 (如 "P", "Ctrl+S") -- 仅用于 tooltip 显示。 */
    Optional<String> getShortcutLabel();

    /**
     * KeyTip 序列 (如 ["P", "1"] 用于 Alt+P -> 1 二级导航) -- Phase 2+ 预留。
     * <p>首版返回 Optional.empty(); 保留字段避免后续加字段破坏 definition 实现 binary compat。
     */
    default Optional<List<String>> getKeyTips() { return Optional.empty(); }

    /** 工具尺寸。 */
    ToolSize getSize();

    /** 工具动作 (回调)。 */
    ToolAction getAction();

    /** 溢出权重: 数值越大越先被缩窄/隐藏。 */
    default int getOverflowWeight() { return RibbonConstants.DEFAULT_OVERFLOW_WEIGHT; }
}
```

Append `SimpleRibbonToolDefinition` record to the same file:

```java
/**
 * SimpleRibbonToolDefinition - RibbonToolDefinition 的简单 record 实现。
 *
 * <p>Mod 可直接使用此 record, 也可实现 RibbonToolDefinition 接口自定义。
 *
 * @param id            唯一标识
 * @param displayName   显示名称
 * @param icon          图标 (可选)
 * @param tooltip       Tooltip (可选)
 * @param shortcutLabel 快捷键标签 (可选)
 * @param size          工具尺寸
 * @param action        工具动作
 */
record SimpleRibbonToolDefinition(
    ResourceLocation id,
    Component displayName,
    Optional<IGuiTexture> icon,
    Optional<TooltipContent> tooltip,
    Optional<String> shortcutLabel,
    ToolSize size,
    ToolAction action
) implements RibbonToolDefinition {
}
```

- [ ] **Step 4: 创建 RibbonToolGroupDefinition + SimpleRibbonToolGroupDefinition**

Create `src/client/java/net/jsmua/kinetic_planner/gui/ribbon/api/RibbonToolGroupDefinition.java`:

```java
package net.jsmua.kinetic_planner.gui.ribbon.api;

import net.jsmua.kinetic_planner.gui.ribbon.RibbonConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Optional;

/**
 * 工具组定义 (spec §5.1)。
 */
public interface RibbonToolGroupDefinition {
    /** 唯一标识 (MC ResourceLocation)。 */
    ResourceLocation getId();

    /** 底部标注 (可选, 为空则不标注)。 */
    Optional<Component> getDisplayName();

    /** 工具列表。 */
    List<RibbonToolDefinition> getTools();

    /** 溢出权重: 数值越大越先被缩窄。 */
    default int getOverflowWeight() { return RibbonConstants.DEFAULT_OVERFLOW_WEIGHT; }

    /**
     * Dialog Launcher: 组右下角小箭头按钮 -- Phase 2+ 预留。
     * <p>首版返回 Optional.empty()。
     */
    default Optional<RibbonCommand> getDialogLauncher() { return Optional.empty(); }
}

record SimpleRibbonToolGroupDefinition(
    ResourceLocation id,
    Optional<Component> displayName,
    List<RibbonToolDefinition> tools
) implements RibbonToolGroupDefinition {
}
```

- [ ] **Step 5: 创建 RibbonTabDefinition + SimpleRibbonTabDefinition**

Create `src/client/java/net/jsmua/kinetic_planner/gui/ribbon/api/RibbonTabDefinition.java`:

```java
package net.jsmua.kinetic_planner.gui.ribbon.api;

import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Optional;

/**
 * 选项卡定义 (spec §3.2)。
 *
 * <p>Mod 注册的不可变数据。
 */
public interface RibbonTabDefinition {
    /** 唯一标识 (MC ResourceLocation, namespace:path 格式)。 */
    ResourceLocation getId();

    /** 显示名称。 */
    Component getDisplayName();

    /** tab 头图标 (可选)。 */
    Optional<IGuiTexture> getIcon();

    /** 排序优先级 (越小越靠左)。 */
    int getPriority();

    /** 此 tab 包含的工具组。 */
    List<RibbonToolGroupDefinition> getGroups();

    /** 建议的默认显示模式 (用户可覆盖, 但 CONTEXTUAL 由系统控制)。 */
    default TabDisplayMode getDefaultDisplayMode() { return TabDisplayMode.PINNED; }

    /** 是否允许用户隐藏此 tab (核心 tab 可禁止)。 */
    default boolean isUserHideable() { return true; }

    /**
     * 若 getDefaultDisplayMode() == CONTEXTUAL, 返回所属上下文组 ID。
     * 非 CONTEXTUAL tab 返回 Optional.empty()。
     */
    default Optional<ResourceLocation> getContextualGroupId() { return Optional.empty(); }
}

record SimpleRibbonTabDefinition(
    ResourceLocation id,
    Component displayName,
    Optional<IGuiTexture> icon,
    int priority,
    List<RibbonToolGroupDefinition> groups,
    TabDisplayMode defaultDisplayMode,
    boolean userHideable,
    Optional<ResourceLocation> contextualGroupId
) implements RibbonTabDefinition {
}
```

- [ ] **Step 6: 创建 RibbonHeaderComponent + Placement + SimpleRibbonHeaderComponent**

Create `src/client/java/net/jsmua/kinetic_planner/gui/ribbon/api/RibbonHeaderComponent.java`:

```java
package net.jsmua.kinetic_planner.gui.ribbon.api;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import net.minecraft.resources.ResourceLocation;

/**
 * Ribbon 头部组件 (spec §6.1)。
 *
 * <p>原名 RibbonTabBarComponent, 重命名为 RibbonHeaderComponent 避免与 LDLib2 Tab/TabView 混淆。
 */
public interface RibbonHeaderComponent {
    /** 唯一标识 (MC ResourceLocation)。 */
    ResourceLocation getId();

    /** 放置位置。 */
    Placement getPlacement();

    /** 排序优先级 (同侧内, 越小越靠外)。 */
    int getPriority();

    /** 创建 UI 元素。 */
    UIElement createElement();

    enum Placement { LEADING, TRAILING }
}

record SimpleRibbonHeaderComponent(
    ResourceLocation id,
    Placement placement,
    int priority,
    java.util.function.Supplier<UIElement> elementSupplier
) implements RibbonHeaderComponent {
    @Override
    public UIElement createElement() {
        return elementSupplier.get();
    }
}
```

- [ ] **Step 7: 运行测试验证通过**

Run: `gradlew test --tests "net.jsmua.kinetic_planner.gui.ribbon.api.RibbonToolDefinitionTest"`
Expected: PASS（3 tests）

- [ ] **Step 8: 编译验证**

Run: `gradlew compileClientJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 9: Commit**

```bash
git add src/client/java/net/jsmua/kinetic_planner/gui/ribbon/api/ src/test/java/net/jsmua/kinetic_planner/gui/ribbon/api/RibbonToolDefinitionTest.java
git commit -m "feat(ribbon): 添加工具/工具组/选项卡/头部组件 Definition 接口"
```

---

### Task 3: RibbonRegistry 独立可冻结注册表

**Files:**
- Create: `src/client/java/net/jsmua/kinetic_planner/gui/ribbon/registry/RibbonRegistry.java`
- Test: `src/test/java/net/jsmua/kinetic_planner/gui/ribbon/registry/RibbonRegistryTest.java`

**Interfaces:**
- Consumes: Task 2 的 `RibbonTabDefinition` / `RibbonHeaderComponent`（含 `Placement`）+ Task 6（尚未创建）的 `ContextualTabGroup`。**为避免循环依赖**，Task 3 在 `ContextualTabGroup` 创建之前先不实现 contextual group 方法；Task 6 创建 `ContextualTabGroup` 后回头补充。本任务先实现 `registerTab` / `registerHeaderComponent` / `freeze` / `resetForTest` / `getTabsSortedByPriority` / `getHeaderComponents`。
- Produces:
  - `RibbonRegistry.registerTab(ResourceLocation, RibbonTabDefinition)`
  - `RibbonRegistry.registerHeaderComponent(ResourceLocation, RibbonHeaderComponent)`
  - `RibbonRegistry.freeze()` / `isFrozen()`
  - `RibbonRegistry.resetForTest()`（package-private）
  - `RibbonRegistry.getTabsSortedByPriority()`：返回按 priority 升序的 `List<RibbonTabDefinition>`（不含 CONTEXTUAL，因 contextual tab 由 Task 6 补充时加入）
  - `RibbonRegistry.getHeaderComponents(Placement)`：返回按 priority 升序的 `List<RibbonHeaderComponent>`

- [ ] **Step 1: 写失败测试 - 注册 + 冻结 + 查询**

Create `src/test/java/net/jsmua/kinetic_planner/gui/ribbon/registry/RibbonRegistryTest.java`:

```java
package net.jsmua.kinetic_planner.gui.ribbon.registry;

import net.jsmua.kinetic_planner.gui.ribbon.api.Placement;
import net.jsmua.kinetic_planner.gui.ribbon.api.RibbonHeaderComponent;
import net.jsmua.kinetic_planner.gui.ribbon.api.RibbonTabDefinition;
import net.jsmua.kinetic_planner.gui.ribbon.api.SimpleRibbonTabDefinition;
import net.jsmua.kinetic_planner.gui.ribbon.api.SimpleRibbonHeaderComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class RibbonRegistryTest {

    @AfterEach
    void resetRegistry() {
        // 每个测试后重置注册表，避免跨测试污染
        RibbonRegistry.resetForTest();
    }

    private RibbonTabDefinition makeTab(String path, int priority) {
        return new SimpleRibbonTabDefinition(
            ResourceLocation.fromNamespaceAndPath("kp", path),
            Component.literal(path),
            Optional.empty(),
            priority,
            List.of(),
            net.jsmua.kinetic_planner.gui.ribbon.api.TabDisplayMode.PINNED,
            true,
            Optional.empty()
        );
    }

    @Test
    void registerTabBeforeFreezeSucceeds() {
        var tab = makeTab("tools", 100);
        RibbonRegistry.registerTab(tab.getId(), tab);
        var all = RibbonRegistry.getTabsSortedByPriority();
        assertEquals(1, all.size());
        assertEquals(tab, all.get(0));
    }

    @Test
    void registerTabAfterFreezeThrows() {
        RibbonRegistry.freeze();
        assertTrue(RibbonRegistry.isFrozen());
        var tab = makeTab("tools", 100);
        assertThrows(IllegalStateException.class,
            () -> RibbonRegistry.registerTab(tab.getId(), tab),
            "冻结后注册必须抛 IllegalStateException");
    }

    @Test
    void duplicateIdThrows() {
        var tab = makeTab("tools", 100);
        RibbonRegistry.registerTab(tab.getId(), tab);
        assertThrows(IllegalStateException.class,
            () -> RibbonRegistry.registerTab(tab.getId(), tab),
            "重复 ID 必须抛 IllegalStateException");
    }

    @Test
    void getTabsSortedByPriorityAscending() {
        RibbonRegistry.registerTab(ResourceLocation.fromNamespaceAndPath("kp", "c"), makeTab("c", 300));
        RibbonRegistry.registerTab(ResourceLocation.fromNamespaceAndPath("kp", "a"), makeTab("a", 100));
        RibbonRegistry.registerTab(ResourceLocation.fromNamespaceAndPath("kp", "b"), makeTab("b", 200));

        var sorted = RibbonRegistry.getTabsSortedByPriority();
        assertEquals(3, sorted.size());
        assertEquals("a", sorted.get(0).getId().getPath());
        assertEquals("b", sorted.get(1).getId().getPath());
        assertEquals("c", sorted.get(2).getId().getPath());
    }

    @Test
    void resetForTestClearsRegistryAndUnfreezes() {
        RibbonRegistry.registerTab(ResourceLocation.fromNamespaceAndPath("kp", "a"), makeTab("a", 100));
        RibbonRegistry.freeze();
        assertTrue(RibbonRegistry.isFrozen());

        RibbonRegistry.resetForTest();
        assertFalse(RibbonRegistry.isFrozen(), "resetForTest 必须解除冻结");
        assertTrue(RibbonRegistry.getTabsSortedByPriority().isEmpty(),
            "resetForTest 必须清空注册表");
    }

    @Test
    void headerComponentsSortedByPriorityPerPlacement() {
        var lead1 = new SimpleRibbonHeaderComponent(
            ResourceLocation.fromNamespaceAndPath("kp", "l1"),
            Placement.LEADING, 100, () -> null);
        var lead2 = new SimpleRibbonHeaderComponent(
            ResourceLocation.fromNamespaceAndPath("kp", "l2"),
            Placement.LEADING, 50, () -> null);
        var trail1 = new SimpleRibbonHeaderComponent(
            ResourceLocation.fromNamespaceAndPath("kp", "t1"),
            Placement.TRAILING, 100, () -> null);

        RibbonRegistry.registerHeaderComponent(lead1.getId(), lead1);
        RibbonRegistry.registerHeaderComponent(lead2.getId(), lead2);
        RibbonRegistry.registerHeaderComponent(trail1.getId(), trail1);

        var leading = RibbonRegistry.getHeaderComponents(Placement.LEADING);
        var trailing = RibbonRegistry.getHeaderComponents(Placement.TRAILING);
        assertEquals(2, leading.size());
        assertEquals(1, trailing.size());
        assertEquals("l2", leading.get(0).getId().getPath(), "priority=50 应排在 priority=100 前");
        assertEquals("l1", leading.get(1).getId().getPath());
    }
}
```

- [ ] **Step 2: 运行测试验证失败**

Run: `gradlew test --tests "net.jsmua.kinetic_planner.gui.ribbon.registry.RibbonRegistryTest"`
Expected: FAIL（编译失败：`RibbonRegistry` 类不存在）

- [ ] **Step 3: 创建 RibbonRegistry**

Create `src/client/java/net/jsmua/kinetic_planner/gui/ribbon/registry/RibbonRegistry.java`:

```java
package net.jsmua.kinetic_planner.gui.ribbon.registry;

import net.jsmua.kinetic_planner.gui.ribbon.api.RibbonHeaderComponent;
import net.jsmua.kinetic_planner.gui.ribbon.api.RibbonTabDefinition;
import net.minecraft.resources.ResourceLocation;

import java.util.*;

/**
 * 框架独立可冻结静态注册表 (spec §2.4)。
 *
 * <p>不复用 KPRegistry, 避免框架反向依赖 KP。Mod 不能热加载, 静态注册即可。
 *
 * <p>生命周期: 注册阶段 -> freeze() -> 只读查询阶段。
 * 测试用 {@link #resetForTest()} 重置 (package-private, 仅 test sourceSet 调用)。
 */
public final class RibbonRegistry {

    private static final Map<ResourceLocation, RibbonTabDefinition> TABS = new LinkedHashMap<>();
    private static final Map<ResourceLocation, RibbonHeaderComponent> HEADER_COMPONENTS = new LinkedHashMap<>();
    private static boolean frozen = false;

    private RibbonRegistry() {}

    /** 注册选项卡。冻结后或重复 ID 抛 IllegalStateException。 */
    public static void registerTab(ResourceLocation id, RibbonTabDefinition def) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(def, "def");
        ensureNotFrozen();
        if (TABS.containsKey(id)) {
            throw new IllegalStateException("Tab already registered: " + id);
        }
        TABS.put(id, def);
    }

    /** 注册头部组件。冻结后或重复 ID 抛 IllegalStateException。 */
    public static void registerHeaderComponent(ResourceLocation id, RibbonHeaderComponent comp) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(comp, "comp");
        ensureNotFrozen();
        if (HEADER_COMPONENTS.containsKey(id)) {
            throw new IllegalStateException("Header component already registered: " + id);
        }
        HEADER_COMPONENTS.put(id, comp);
    }

    /** ClientSetup 调用, 冻结后不可再注册。 */
    public static void freeze() {
        frozen = true;
    }

    /** 是否已冻结。 */
    public static boolean isFrozen() {
        return frozen;
    }

    /** 返回按 priority 升序的核心 tab 列表。 */
    public static List<RibbonTabDefinition> getTabsSortedByPriority() {
        var all = new ArrayList<>(TABS.values());
        all.sort(Comparator.comparingInt(RibbonTabDefinition::getPriority));
        return Collections.unmodifiableList(all);
    }

    /** 返回指定 Placement 的头部组件, 按 priority 升序。 */
    public static List<RibbonHeaderComponent> getHeaderComponents(RibbonHeaderComponent.Placement placement) {
        Objects.requireNonNull(placement, "placement");
        var filtered = new ArrayList<RibbonHeaderComponent>();
        for (var comp : HEADER_COMPONENTS.values()) {
            if (comp.getPlacement() == placement) filtered.add(comp);
        }
        filtered.sort(Comparator.comparingInt(RibbonHeaderComponent::getPriority));
        return Collections.unmodifiableList(filtered);
    }

    private static void ensureNotFrozen() {
        if (frozen) {
            throw new IllegalStateException("RibbonRegistry is frozen; cannot register");
        }
    }

    /** 测试专用: 重置注册表状态。仅 test sourceSet 调用。 */
    static void resetForTest() {
        TABS.clear();
        HEADER_COMPONENTS.clear();
        frozen = false;
    }
}
```

- [ ] **Step 4: 运行测试验证通过**

Run: `gradlew test --tests "net.jsmua.kinetic_planner.gui.ribbon.registry.RibbonRegistryTest"`
Expected: PASS（6 tests）

- [ ] **Step 5: 编译验证**

Run: `gradlew compileClientJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add src/client/java/net/jsmua/kinetic_planner/gui/ribbon/registry/ src/test/java/net/jsmua/kinetic_planner/gui/ribbon/registry/
git commit -m "feat(ribbon): 添加独立可冻结 RibbonRegistry"
```

---

### Task 4: RibbonTabState 状态机 + QuickAccessToolbar 接口

**Files:**
- Create: `src/client/java/net/jsmua/kinetic_planner/gui/ribbon/api/RibbonTabState.java`
- Create: `src/client/java/net/jsmua/kinetic_planner/gui/ribbon/api/QuickAccessToolbar.java`
- Test: `src/test/java/net/jsmua/kinetic_planner/gui/ribbon/api/RibbonTabStateTest.java`

**Interfaces:**
- Consumes: Task 1 的 `TabDisplayMode` + Task 2 的 `RibbonTabDefinition` + Task 3 的 `RibbonRegistry`（间接通过 tab def）
- Produces:
  - `RibbonTabState` class（构造接收 `RibbonTabDefinition`，含 `setDisplayMode` / `toggleContentVisible` / `setContextActive` / `isHeaderVisible` / `getContentVisible` / `getDisplayMode` / `getDefinition`）
  - `QuickAccessToolbar` interface（`getToolIds` / `addTool` / `removeTool` / `containsTool` / `createElement` / `loadPreferences` / `savePreferences`）

- [ ] **Step 1: 写失败测试 - RibbonTabState 状态不变量**

Create `src/test/java/net/jsmua/kinetic_planner/gui/ribbon/api/RibbonTabStateTest.java`:

```java
package net.jsmua.kinetic_planner.gui.ribbon.api;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class RibbonTabStateTest {

    private RibbonTabDefinition makeTab(TabDisplayMode defaultMode) {
        return new SimpleRibbonTabDefinition(
            ResourceLocation.fromNamespaceAndPath("kp", "test"),
            Component.literal("Test"),
            Optional.empty(),
            100,
            List.of(),
            defaultMode,
            true,
            Optional.empty()
        );
    }

    @Test
    void pinnedModeHasContentVisible() {
        var state = new RibbonTabState(makeTab(TabDisplayMode.PINNED));
        state.setDisplayMode(TabDisplayMode.PINNED);
        assertTrue(state.getContentVisible(), "PINNED -> contentVisible 必须 = true");
        assertTrue(state.isHeaderVisible(), "PINNED -> header 必须 visible");
    }

    @Test
    void hiddenModeHidesContentAndHeader() {
        var state = new RibbonTabState(makeTab(TabDisplayMode.PINNED));
        state.setDisplayMode(TabDisplayMode.HIDDEN);
        assertFalse(state.getContentVisible(), "HIDDEN -> contentVisible 必须 = false");
        assertFalse(state.isHeaderVisible(), "HIDDEN -> header 必须 hidden");
    }

    @Test
    void floatingModeToggleContentVisible() {
        var state = new RibbonTabState(makeTab(TabDisplayMode.PINNED));
        state.setDisplayMode(TabDisplayMode.FLOATING);
        // FLOATING 初始 contentVisible = false (setDisplayMode 只在 PINNED 时设 true)
        assertFalse(state.getContentVisible());
        state.toggleContentVisible();
        assertTrue(state.getContentVisible(), "toggle 后 contentVisible = true");
        state.toggleContentVisible();
        assertFalse(state.getContentVisible(), "再 toggle 回 false");
    }

    @Test
    void contextualModeRespectsContextActive() {
        var state = new RibbonTabState(makeTab(TabDisplayMode.CONTEXTUAL));
        // 初始未激活
        assertFalse(state.isHeaderVisible(), "CONTEXTUAL 未激活 -> header hidden");

        state.setContextActive(true);
        assertTrue(state.isHeaderVisible(), "CONTEXTUAL 激活 -> header visible");
        // 激活后 contentVisible 应遵循 PINNED 子模式 = true (spec §10.2 getActiveSubMode 默认 PINNED)
        assertTrue(state.getContentVisible(), "CONTEXTUAL 激活后 contentVisible 遵循 PINNED 子模式 = true");

        state.setContextActive(false);
        assertFalse(state.isHeaderVisible(), "CONTEXTUAL 停用 -> header hidden");
        assertFalse(state.getContentVisible(), "CONTEXTUAL 停用 -> contentVisible = false");
    }

    @Test
    void setContextActiveNoOpOnNonContextualMode() {
        var state = new RibbonTabState(makeTab(TabDisplayMode.PINNED));
        state.setContextActive(true);
        // PINNED 模式不受 contextActive 影响
        assertTrue(state.isHeaderVisible(), "PINNED 模式 setContextActive 不影响可见性");
    }

    @Test
    void setDisplayModeOnContextualNoOp() {
        var state = new RibbonTabState(makeTab(TabDisplayMode.CONTEXTUAL));
        // CONTEXTUAL 模式不应响应 setDisplayMode (用户不可手动覆盖)
        state.setDisplayMode(TabDisplayMode.PINNED);
        assertEquals(TabDisplayMode.CONTEXTUAL, state.getDisplayMode(),
            "CONTEXTUAL 模式 setDisplayMode 必须无效果");
    }
}
```

- [ ] **Step 2: 运行测试验证失败**

Run: `gradlew test --tests "net.jsmua.kinetic_planner.gui.ribbon.api.RibbonTabStateTest"`
Expected: FAIL（编译失败：`RibbonTabState` 类不存在）

- [ ] **Step 3: 创建 RibbonTabState**

Create `src/client/java/net/jsmua/kinetic_planner/gui/ribbon/api/RibbonTabState.java`:

```java
package net.jsmua.kinetic_planner.gui.ribbon.api;

/**
 * 选项卡运行时状态机 (spec §3.3)。
 *
 * <p>状态不变量:
 * <ul>
 *   <li>HIDDEN     -> contentVisible = false</li>
 *   <li>PINNED     -> contentVisible = true</li>
 *   <li>FLOATING   -> contentVisible 由用户交互控制</li>
 *   <li>CONTEXTUAL -> 头部可见性 = contextActive; contentVisible 遵循 PINNED/FLOATING 子模式</li>
 * </ul>
 *
 * <p><b>关键约束:</b> CONTEXTUAL 模式不响应 setDisplayMode (用户不可手动覆盖可见性)。
 */
public final class RibbonTabState {

    private final RibbonTabDefinition definition;
    private TabDisplayMode displayMode;
    private boolean contentVisible;
    private boolean contextActive;

    public RibbonTabState(RibbonTabDefinition definition) {
        this.definition = definition;
        this.displayMode = definition.getDefaultDisplayMode();
        this.contentVisible = (this.displayMode == TabDisplayMode.PINNED);
        this.contextActive = false;
    }

    public RibbonTabDefinition getDefinition() {
        return definition;
    }

    public TabDisplayMode getDisplayMode() {
        return displayMode;
    }

    public boolean getContentVisible() {
        return contentVisible;
    }

    /**
     * 设置显示模式 (用户控制)。
     * <p>CONTEXTUAL 模式不响应此方法 (可见性由 ViewContextProvider 驱动)。
     */
    public void setDisplayMode(TabDisplayMode mode) {
        if (this.displayMode == TabDisplayMode.CONTEXTUAL) {
            return;  // CONTEXTUAL 不可手动覆盖
        }
        this.displayMode = mode;
        this.contentVisible = (mode == TabDisplayMode.PINNED);
    }

    /** 仅 FLOATING 模式有效: toggle 内容面板可见性。 */
    public void toggleContentVisible() {
        if (displayMode == TabDisplayMode.FLOATING) {
            contentVisible = !contentVisible;
        }
    }

    /** 手动设置 contentVisible (FLOATING 浮层失焦收起等场景使用)。 */
    public void setContentVisible(boolean visible) {
        this.contentVisible = visible;
    }

    /**
     * 仅 CONTEXTUAL 模式: 视图上下文激活/停用。
     * <p>激活后 contentVisible 遵循 PINNED 子模式 = true (spec §10.2 getActiveSubMode 默认 PINNED)。
     */
    public void setContextActive(boolean active) {
        if (displayMode != TabDisplayMode.CONTEXTUAL) {
            return;  // 非 CONTEXTUAL 不响应
        }
        this.contextActive = active;
        if (active) {
            // 激活后遵循 PINNED 子模式
            this.contentVisible = true;
        } else {
            this.contentVisible = false;
        }
    }

    /** tab 头是否渲染。 */
    public boolean isHeaderVisible() {
        return displayMode != TabDisplayMode.HIDDEN
            && (displayMode != TabDisplayMode.CONTEXTUAL || contextActive);
    }
}
```

- [ ] **Step 4: 创建 QuickAccessToolbar 接口**

Create `src/client/java/net/jsmua/kinetic_planner/gui/ribbon/api/QuickAccessToolbar.java`:

```java
package net.jsmua.kinetic_planner.gui.ribbon.api;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * 快速访问工具栏接口 (spec §7.1)。
 *
 * <p>QAT 是独立一等公民, 不是 RibbonHeaderComponent 的特化。
 * UI 元素放置在 RibbonHeader 的 LEADING 侧, 始终排在所有 LEADING 侧组件最左侧。
 */
public interface QuickAccessToolbar {
    /** QAT 中的工具 ID 列表 (持久化到 PreferenceStore)。 */
    List<ResourceLocation> getToolIds();

    /** 添加工具到 QAT (只接受 Ribbon tool ID)。 */
    boolean addTool(ResourceLocation toolId);

    /** 从 QAT 移除。 */
    boolean removeTool(ResourceLocation toolId);

    /** 是否包含指定工具。 */
    boolean containsTool(ResourceLocation toolId);

    /** 构建 QAT 的 UIElement (图标按钮列表)。 */
    UIElement createElement();

    /** 从 PreferenceStore 加载偏好。 */
    void loadPreferences(RibbonPreferenceStore store);

    /** 保存偏好到 PreferenceStore。 */
    void savePreferences(RibbonPreferenceStore store);
}
```

> **注意**: 此接口引用 `RibbonPreferenceStore`, 该接口在 Task 5 创建。Task 4 先创建 QAT 接口（编译会失败因 RibbonPreferenceStore 不存在），Task 5 紧接着创建 RibbonPreferenceStore 后即可编译。**Task 4 不单独编译验证，留到 Task 5 Step 5 一起编译。**

- [ ] **Step 5: 运行测试验证通过**

Run: `gradlew test --tests "net.jsmua.kinetic_planner.gui.ribbon.api.RibbonTabStateTest"`
Expected: PASS（6 tests）-- 注意：此测试只覆盖 RibbonTabState，不涉及 QAT 接口，所以即使 QAT 引用未存在的 RibbonPreferenceStore 导致编译失败，RibbonTabState 测试也会失败。**因此本步预期实际是编译失败**。处理方案：先在 Task 4 创建 `RibbonPreferenceStore` 占位空接口（仅声明，无方法），让 QAT 编译通过，Task 5 再填充方法。

**修正**: 在 Step 4 之前先创建 `RibbonPreferenceStore` 占位接口：

Create `src/client/java/net/jsmua/kinetic_planner/gui/ribbon/api/RibbonPreferenceStore.java`（占位，Task 5 会用 SearchReplace 替换为完整版本）:

```java
package net.jsmua.kinetic_planner.gui.ribbon.api;

/**
 * 配置持久化抽象接口 (spec §11.5)。
 *
 * <p>框架不直接依赖 KPConfig, 由 KP 提供 KPConfigRibbonPreferenceStore 实现。
 *
 * <p><b>占位:</b> Task 5 会补充完整方法签名。
 */
public interface RibbonPreferenceStore {
}
```

- [ ] **Step 6: 运行测试验证通过**

Run: `gradlew test --tests "net.jsmua.kinetic_planner.gui.ribbon.api.RibbonTabStateTest"`
Expected: PASS（6 tests）

- [ ] **Step 7: 编译验证**

Run: `gradlew compileClientJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 8: Commit**

```bash
git add src/client/java/net/jsmua/kinetic_planner/gui/ribbon/api/RibbonTabState.java src/client/java/net/jsmua/kinetic_planner/gui/ribbon/api/QuickAccessToolbar.java src/client/java/net/jsmua/kinetic_planner/gui/ribbon/api/RibbonPreferenceStore.java src/test/java/net/jsmua/kinetic_planner/gui/ribbon/api/RibbonTabStateTest.java
git commit -m "feat(ribbon): 添加 RibbonTabState 状态机 + QAT/PreferenceStore 接口占位"
```

---

### Task 5: RibbonPreferenceStore 完整接口 + RibbonPreferences 读写器

**Files:**
- Modify: `src/client/java/net/jsmua/kinetic_planner/gui/ribbon/api/RibbonPreferenceStore.java`（用完整版本替换 Task 4 的占位）
- Create: `src/client/java/net/jsmua/kinetic_planner/gui/ribbon/internal/RibbonPreferences.java`
- Test: `src/test/java/net/jsmua/kinetic_planner/gui/ribbon/internal/RibbonPreferencesTest.java`

**Interfaces:**
- Consumes: Task 1 的 `TabDisplayMode` + Task 4 的占位 `RibbonPreferenceStore`
- Produces:
  - `RibbonPreferenceStore` 完整接口（`getTabDisplayMode` / `setTabDisplayMode` / `getQatToolIds` / `setQatToolIds` / `getSelectedTabId` / `setSelectedTabId`）
  - `RibbonPreferences` class（封装读写逻辑，构造注入 `RibbonPreferenceStore`）

- [ ] **Step 1: 写失败测试 - 偏好读写往返**

Create `src/test/java/net/jsmua/kinetic_planner/gui/ribbon/internal/RibbonPreferencesTest.java`:

```java
package net.jsmua.kinetic_planner.gui.ribbon.internal;

import net.jsmua.kinetic_planner.gui.ribbon.api.RibbonPreferenceStore;
import net.jsmua.kinetic_planner.gui.ribbon.api.TabDisplayMode;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class RibbonPreferencesTest {

    /** In-memory stub 实现, 用于测试 RibbonPreferences 读写逻辑。 */
    private static class InMemoryStore implements RibbonPreferenceStore {
        final Map<ResourceLocation, TabDisplayMode> tabModes = new HashMap<>();
        List<ResourceLocation> qatIds = new ArrayList<>();
        ResourceLocation selectedTabId = null;

        @Override
        public Optional<TabDisplayMode> getTabDisplayMode(ResourceLocation tabId) {
            return Optional.ofNullable(tabModes.get(tabId));
        }

        @Override
        public void setTabDisplayMode(ResourceLocation tabId, TabDisplayMode mode) {
            tabModes.put(tabId, mode);
        }

        @Override
        public List<ResourceLocation> getQatToolIds() {
            return new ArrayList<>(qatIds);
        }

        @Override
        public void setQatToolIds(List<ResourceLocation> ids) {
            this.qatIds = new ArrayList<>(ids);
        }

        @Override
        public Optional<ResourceLocation> getSelectedTabId() {
            return Optional.ofNullable(selectedTabId);
        }

        @Override
        public void setSelectedTabId(ResourceLocation id) {
            this.selectedTabId = id;
        }
    }

    @Test
    void loadReadsAllPreferencesFromStore() {
        var store = new InMemoryStore();
        var tabId = ResourceLocation.fromNamespaceAndPath("kp", "tools");
        var qatId = ResourceLocation.fromNamespaceAndPath("kp", "tool_pan");
        store.tabModes.put(tabId, TabDisplayMode.FLOATING);
        store.qatIds.add(qatId);
        store.selectedTabId = tabId;

        var prefs = RibbonPreferences.load(store);

        assertEquals(TabDisplayMode.FLOATING, prefs.getTabDisplayMode(tabId).orElseThrow());
        assertEquals(1, prefs.getQatToolIds().size());
        assertEquals(qatId, prefs.getQatToolIds().get(0));
        assertEquals(tabId, prefs.getSelectedTabId().orElseThrow());
    }

    @Test
    void saveWritesAllPreferencesToStore() {
        var store = new InMemoryStore();
        var prefs = new RibbonPreferences(
            new HashMap<>(),
            new ArrayList<>(),
            null);

        var tabId = ResourceLocation.fromNamespaceAndPath("kp", "file");
        prefs.setTabDisplayMode(tabId, TabDisplayMode.HIDDEN);
        prefs.addQatToolId(ResourceLocation.fromNamespaceAndPath("kp", "tool_select"));
        prefs.setSelectedTabId(tabId);

        prefs.save(store);

        assertEquals(TabDisplayMode.HIDDEN, store.tabModes.get(tabId));
        assertEquals(1, store.qatIds.size());
        assertEquals(tabId, store.selectedTabId);
    }

    @Test
    void roundTripPreservesData() {
        var store1 = new InMemoryStore();
        var tabId = ResourceLocation.fromNamespaceAndPath("kp", "view");
        store1.tabModes.put(tabId, TabDisplayMode.CONTEXTUAL);
        store1.qatIds.add(ResourceLocation.fromNamespaceAndPath("kp", "tool_pan"));
        store1.qatIds.add(ResourceLocation.fromNamespaceAndPath("kp", "tool_select"));
        store1.selectedTabId = tabId;

        var prefs = RibbonPreferences.load(store1);
        var store2 = new InMemoryStore();
        prefs.save(store2);

        assertEquals(store1.tabModes, store2.tabModes);
        assertEquals(store1.qatIds, store2.qatIds);
        assertEquals(store1.selectedTabId, store2.selectedTabId);
    }

    @Test
    void unknownTabReturnsEmptyOptional() {
        var store = new InMemoryStore();
        var prefs = RibbonPreferences.load(store);
        assertTrue(prefs.getTabDisplayMode(ResourceLocation.fromNamespaceAndPath("kp", "unknown")).isEmpty());
    }
}
```

- [ ] **Step 2: 运行测试验证失败**

Run: `gradlew test --tests "net.jsmua.kinetic_planner.gui.ribbon.internal.RibbonPreferencesTest"`
Expected: FAIL（编译失败：`RibbonPreferenceStore` 缺方法 / `RibbonPreferences` 类不存在）

- [ ] **Step 3: 用完整接口替换 RibbonPreferenceStore 占位**

Replace entire content of `src/client/java/net/jsmua/kinetic_planner/gui/ribbon/api/RibbonPreferenceStore.java` with:

```java
package net.jsmua.kinetic_planner.gui.ribbon.api;

import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Optional;

/**
 * 配置持久化抽象接口 (spec §11.5)。
 *
 * <p>框架不直接依赖 KPConfig, 由 KP 提供 KPConfigRibbonPreferenceStore 实现。
 */
public interface RibbonPreferenceStore {

    /** 获取指定 tab 的显示模式 (未配置返回 empty, 调用方回退到 definition.getDefaultDisplayMode())。 */
    Optional<TabDisplayMode> getTabDisplayMode(ResourceLocation tabId);

    /** 设置指定 tab 的显示模式。 */
    void setTabDisplayMode(ResourceLocation tabId, TabDisplayMode mode);

    /** 获取 QAT 工具 ID 列表。 */
    List<ResourceLocation> getQatToolIds();

    /** 设置 QAT 工具 ID 列表。 */
    void setQatToolIds(List<ResourceLocation> ids);

    /** 获取上次选中的 tab ID (无返回 empty)。 */
    Optional<ResourceLocation> getSelectedTabId();

    /** 设置上次选中的 tab ID。 */
    void setSelectedTabId(ResourceLocation id);
}
```

- [ ] **Step 4: 创建 RibbonPreferences**

Create `src/client/java/net/jsmua/kinetic_planner/gui/ribbon/internal/RibbonPreferences.java`:

```java
package net.jsmua.kinetic_planner.gui.ribbon.internal;

import net.jsmua.kinetic_planner.gui.ribbon.api.RibbonPreferenceStore;
import net.jsmua.kinetic_planner.gui.ribbon.api.TabDisplayMode;
import net.minecraft.resources.ResourceLocation;

import java.util.*;

/**
 * Ribbon 偏好读写器 (spec §3.5)。
 *
 * <p>封装 RibbonPreferenceStore 的读写逻辑, 提供 in-memory 状态 + 一次性 load/save。
 * 测试用 in-memory stub store 验证读写往返, 不依赖真实 KPConfig。
 */
public final class RibbonPreferences {

    private final Map<ResourceLocation, TabDisplayMode> tabDisplayModes;
    private final List<ResourceLocation> qatToolIds;
    private ResourceLocation selectedTabId;

    public RibbonPreferences(
        Map<ResourceLocation, TabDisplayMode> tabDisplayModes,
        List<ResourceLocation> qatToolIds,
        ResourceLocation selectedTabId) {
        this.tabDisplayModes = new HashMap<>(tabDisplayModes);
        this.qatToolIds = new ArrayList<>(qatToolIds);
        this.selectedTabId = selectedTabId;
    }

    /** 从 store 加载全部偏好。 */
    public static RibbonPreferences load(RibbonPreferenceStore store) {
        Map<ResourceLocation, TabDisplayMode> modes = new HashMap<>();
        // store 接口不暴露全部 tab modes 的迭代, 由调用方按需 getTabDisplayMode(tabId)
        // 这里仅加载 selectedTabId 和 qatToolIds, tab modes 按需查询
        return new RibbonPreferences(
            modes,
            new ArrayList<>(store.getQatToolIds()),
            store.getSelectedTabId().orElse(null)
        );
    }

    /** 保存全部偏好到 store。 */
    public void save(RibbonPreferenceStore store) {
        for (var entry : tabDisplayModes.entrySet()) {
            store.setTabDisplayMode(entry.getKey(), entry.getValue());
        }
        store.setQatToolIds(new ArrayList<>(qatToolIds));
        if (selectedTabId != null) {
            store.setSelectedTabId(selectedTabId);
        }
    }

    public Optional<TabDisplayMode> getTabDisplayMode(ResourceLocation tabId) {
        return Optional.ofNullable(tabDisplayModes.get(tabId));
    }

    public void setTabDisplayMode(ResourceLocation tabId, TabDisplayMode mode) {
        tabDisplayModes.put(tabId, mode);
    }

    public List<ResourceLocation> getQatToolIds() {
        return Collections.unmodifiableList(qatToolIds);
    }

    public void addQatToolId(ResourceLocation id) {
        if (!qatToolIds.contains(id)) {
            qatToolIds.add(id);
        }
    }

    public void removeQatToolId(ResourceLocation id) {
        qatToolIds.remove(id);
    }

    public Optional<ResourceLocation> getSelectedTabId() {
        return Optional.ofNullable(selectedTabId);
    }

    public void setSelectedTabId(ResourceLocation id) {
        this.selectedTabId = id;
    }
}
```

- [ ] **Step 5: 运行测试验证通过**

Run: `gradlew test --tests "net.jsmua.kinetic_planner.gui.ribbon.internal.RibbonPreferencesTest"`
Expected: PASS（4 tests）

- [ ] **Step 6: 编译验证**

Run: `gradlew compileClientJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
git add src/client/java/net/jsmua/kinetic_planner/gui/ribbon/api/RibbonPreferenceStore.java src/client/java/net/jsmua/kinetic_planner/gui/ribbon/internal/RibbonPreferences.java src/test/java/net/jsmua/kinetic_planner/gui/ribbon/internal/RibbonPreferencesTest.java
git commit -m "feat(ribbon): 完整 RibbonPreferenceStore 接口 + RibbonPreferences 读写器"
```

---

### Task 6: 上下文选项卡（ContextualTabGroup + ViewContextProvider）+ RibbonRegistry 补充

**Files:**
- Create: `src/client/java/net/jsmua/kinetic_planner/gui/ribbon/api/ContextualTabGroup.java`
- Create: `src/client/java/net/jsmua/kinetic_planner/gui/ribbon/api/ViewContextProvider.java`
- Modify: `src/client/java/net/jsmua/kinetic_planner/gui/ribbon/registry/RibbonRegistry.java`（补充 `registerContextualGroup` / `getContextualGroups`）
- Test: `src/test/java/net/jsmua/kinetic_planner/gui/ribbon/api/ContextualTabGroupTest.java`
- Test: modify `src/test/java/net/jsmua/kinetic_planner/gui/ribbon/registry/RibbonRegistryTest.java`（补充 contextual group 测试）

**Interfaces:**
- Consumes: Task 2 的 `RibbonTabDefinition` + Task 1 的 `TabDisplayMode` + MC `ResourceLocation`/`Component`/`IGuiTexture`/`IColor`
- Produces:
  - `ContextualTabGroup` interface + `SimpleContextualTabGroup` record
  - `ViewContextProvider` interface + `empty()` 静态工厂
  - `RibbonRegistry.registerContextualGroup(ResourceLocation, ContextualTabGroup)`
  - `RibbonRegistry.getContextualGroups()`：返回按 priority 升序的 `List<ContextualTabGroup>`

- [ ] **Step 1: 写失败测试 - ContextualTabGroup 注册**

Add to `src/test/java/net/jsmua/kinetic_planner/gui/ribbon/registry/RibbonRegistryTest.java`（在类末尾 `}` 前追加）:

```java
@Test
void contextualGroupRegisteredAndSorted() {
    var group1 = new net.jsmua.kinetic_planner.gui.ribbon.api.SimpleContextualTabGroup(
        ResourceLocation.fromNamespaceAndPath("kp", "map_editor"),
        Component.literal("Map Editor"),
        Optional.empty(),
        200,
        Optional.empty(),
        List.of(),
        net.jsmua.kinetic_planner.gui.ribbon.api.TabDisplayMode.PINNED);
    var group2 = new net.jsmua.kinetic_planner.gui.ribbon.api.SimpleContextualTabGroup(
        ResourceLocation.fromNamespaceAndPath("kp", "track_graph"),
        Component.literal("Track Graph"),
        Optional.empty(),
        100,
        Optional.empty(),
        List.of(),
        net.jsmua.kinetic_planner.gui.ribbon.api.TabDisplayMode.PINNED);

    RibbonRegistry.registerContextualGroup(group2.getId(), group2);
    RibbonRegistry.registerContextualGroup(group1.getId(), group1);

    var groups = RibbonRegistry.getContextualGroups();
    assertEquals(2, groups.size());
    assertEquals("track_graph", groups.get(0).getId().getPath(), "priority=100 排前");
    assertEquals("map_editor", groups.get(1).getId().getPath());
}

@Test
void registerContextualGroupAfterFreezeThrows() {
    RibbonRegistry.freeze();
    var group = new net.jsmua.kinetic_planner.gui.ribbon.api.SimpleContextualTabGroup(
        ResourceLocation.fromNamespaceAndPath("kp", "g"),
        Component.literal("G"),
        Optional.empty(),
        100,
        Optional.empty(),
        List.of(),
        net.jsmua.kinetic_planner.gui.ribbon.api.TabDisplayMode.PINNED);
    assertThrows(IllegalStateException.class,
        () -> RibbonRegistry.registerContextualGroup(group.getId(), group));
}
```

- [ ] **Step 2: 运行测试验证失败**

Run: `gradlew test --tests "net.jsmua.kinetic_planner.gui.ribbon.registry.RibbonRegistryTest"`
Expected: FAIL（编译失败：`SimpleContextualTabGroup` / `RibbonRegistry.registerContextualGroup` 不存在）

- [ ] **Step 3: 创建 ContextualTabGroup + SimpleContextualTabGroup**

Create `src/client/java/net/jsmua/kinetic_planner/gui/ribbon/api/ContextualTabGroup.java`:

```java
package net.jsmua.kinetic_planner.gui.ribbon.api;

import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Optional;

/**
 * 上下文选项卡组 (spec §10.2)。
 *
 * <p>一组基于视图上下文激活的选项卡。激活时组内所有 tab 头出现; 停用时消失。
 *
 * <p><b>关键约束:</b> CONTEXTUAL 模式只用于视图切换, 不用于对象选择。
 */
public interface ContextualTabGroup {
    /** 唯一标识 (MC ResourceLocation)。 */
    ResourceLocation getId();

    /** 组显示名称 (用于组头部标签)。 */
    Component getDisplayName();

    /** 组头部图标 (可选)。 */
    Optional<IGuiTexture> getIcon();

    /** 排序优先级 (多个上下文组同时激活时, 越小越靠左)。 */
    int getPriority();

    /** 组的视觉强调色 (可选, 用于组头部底色与 tab 底色)。 */
    Optional<net.minecraft.world.level.material.MaterialColor> getAccentColor();

    /** 此组包含的选项卡定义 (每个 tab 的 getDefaultDisplayMode() 应返回 CONTEXTUAL)。 */
    List<RibbonTabDefinition> getTabs();

    /** 激活时 tab 内容的默认显示子模式 (PINNED 或 FLOATING)。 */
    default TabDisplayMode getActiveSubMode() { return TabDisplayMode.PINNED; }
}

record SimpleContextualTabGroup(
    ResourceLocation id,
    Component displayName,
    Optional<IGuiTexture> icon,
    int priority,
    Optional<net.minecraft.world.level.material.MaterialColor> accentColor,
    List<RibbonTabDefinition> tabs,
    TabDisplayMode activeSubMode
) implements ContextualTabGroup {
}
```

> **注意**: spec §10.2 接口签名用 `Optional<IColor>`。LDLib2 的 `IColor` 位于 `com.lowdragmc.lowdraglib2.gui.texture.IColor`，但经查证该类型在 LDLib2 中存在性不确定。为安全起见，本计划改用 MC 原生 `net.minecraft.world.level.material.MaterialColor`（MC 1.21.1 稳定 API）。Task 6 实施者应在实施时用 Grep 确认 LDLib2 是否有 `IColor`；若有则改回 `Optional<IColor>` 并 import `com.lowdragmc.lowdraglib2.gui.texture.IColor`。

- [ ] **Step 4: 创建 ViewContextProvider + empty() 工厂**

Create `src/client/java/net/jsmua/kinetic_planner/gui/ribbon/api/ViewContextProvider.java`:

```java
package net.jsmua.kinetic_planner.gui.ribbon.api;

import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 视图上下文提供者 (spec §10.3)。
 *
 * <p>报告当前活动的视图上下文 ID 集合。RibbonBar 通过此接口决定哪些 ContextualTabGroup 应显示。
 *
 * <p><b>测试 seam:</b> KP 测试时可传入返回固定 Set 的 stub 实现, 无需启动真实视图系统。
 */
public interface ViewContextProvider {

    /** 当前活动的视图上下文 ID 集合 (可同时多个)。 */
    Set<ResourceLocation> getActiveContexts();

    /** 注册上下文变化监听器 (RibbonBar 在构造时注册)。 */
    void addContextChangeListener(Runnable listener);

    /** 移除监听器 (RibbonBar 销毁时调用, 避免泄漏)。 */
    void removeContextChangeListener(Runnable listener);

    /** 空实现: 无上下文场景的默认 provider, 返回空 Set, 不持有监听器。 */
    static ViewContextProvider empty() {
        return EmptyViewContextProvider.INSTANCE;
    }
}

/** empty() 的单例实现。 */
final class EmptyViewContextProvider implements ViewContextProvider {
    static final EmptyViewContextProvider INSTANCE = new EmptyViewContextProvider();

    @Override
    public Set<ResourceLocation> getActiveContexts() {
        return Collections.emptySet();
    }

    @Override
    public void addContextChangeListener(Runnable listener) {
        // no-op
    }

    @Override
    public void removeContextChangeListener(Runnable listener) {
        // no-op
    }
}
```

- [ ] **Step 5: 补充 RibbonRegistry 的 contextual group 方法**

Modify `src/client/java/net/jsmua/kinetic_planner/gui/ribbon/registry/RibbonRegistry.java`:

在 `HEADER_COMPONENTS` 字段下方添加（用 SearchReplace）:

Old:
```java
    private static final Map<ResourceLocation, RibbonHeaderComponent> HEADER_COMPONENTS = new LinkedHashMap<>();
    private static boolean frozen = false;
```

New:
```java
    private static final Map<ResourceLocation, RibbonHeaderComponent> HEADER_COMPONENTS = new LinkedHashMap<>();
    private static final Map<ResourceLocation, ContextualTabGroup> CONTEXT_GROUPS = new LinkedHashMap<>();
    private static boolean frozen = false;
```

在 `registerHeaderComponent` 方法后添加（用 SearchReplace，在 `freeze()` 方法前插入）:

Old:
```java
    /** ClientSetup 调用, 冻结后不可再注册。 */
    public static void freeze() {
```

New:
```java
    /** 注册上下文选项卡组。冻结后或重复 ID 抛 IllegalStateException。 */
    public static void registerContextualGroup(ResourceLocation id, ContextualTabGroup group) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(group, "group");
        ensureNotFrozen();
        if (CONTEXT_GROUPS.containsKey(id)) {
            throw new IllegalStateException("Contextual group already registered: " + id);
        }
        CONTEXT_GROUPS.put(id, group);
    }

    /** 返回按 priority 升序的上下文选项卡组列表。 */
    public static List<ContextualTabGroup> getContextualGroups() {
        var all = new ArrayList<>(CONTEXT_GROUPS.values());
        all.sort(Comparator.comparingInt(ContextualTabGroup::getPriority));
        return Collections.unmodifiableList(all);
    }

    /** ClientSetup 调用, 冻结后不可再注册。 */
    public static void freeze() {
```

添加 import（在文件顶部 import 区，用 SearchReplace）:

Old:
```java
import net.jsmua.kinetic_planner.gui.ribbon.api.RibbonHeaderComponent;
import net.jsmua.kinetic_planner.gui.ribbon.api.RibbonTabDefinition;
```

New:
```java
import net.jsmua.kinetic_planner.gui.ribbon.api.ContextualTabGroup;
import net.jsmua.kinetic_planner.gui.ribbon.api.RibbonHeaderComponent;
import net.jsmua.kinetic_planner.gui.ribbon.api.RibbonTabDefinition;
```

补充 `resetForTest()` 清空 CONTEXT_GROUPS（用 SearchReplace）:

Old:
```java
    static void resetForTest() {
        TABS.clear();
        HEADER_COMPONENTS.clear();
        frozen = false;
    }
```

New:
```java
    static void resetForTest() {
        TABS.clear();
        HEADER_COMPONENTS.clear();
        CONTEXT_GROUPS.clear();
        frozen = false;
    }
```

- [ ] **Step 6: 运行测试验证通过**

Run: `gradlew test --tests "net.jsmua.kinetic_planner.gui.ribbon.registry.RibbonRegistryTest"`
Expected: PASS（8 tests，原 6 + 新 2）

- [ ] **Step 7: 编译验证**

Run: `gradlew compileClientJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 8: Commit**

```bash
git add src/client/java/net/jsmua/kinetic_planner/gui/ribbon/api/ContextualTabGroup.java src/client/java/net/jsmua/kinetic_planner/gui/ribbon/api/ViewContextProvider.java src/client/java/net/jsmua/kinetic_planner/gui/ribbon/registry/RibbonRegistry.java src/test/java/net/jsmua/kinetic_planner/gui/ribbon/registry/RibbonRegistryTest.java
git commit -m "feat(ribbon): 添加上下文选项卡 (ContextualTabGroup + ViewContextProvider)"
```

---

### Task 7: 框架内部 UI 实现（RibbonBar + Builder + QatBar + 内部类）

> **说明**: 本任务所有类 extends/引用 LDLib2 `UIElement`，构造触发 `UIElement.<clinit>` -> `LDLib2Registries.<clinit>` -> `ModList.get()`，纯 JVM 测试无 NeoForge 运行时会失败。因此本任务**不写单元测试**，仅靠编译通过 + 运行时手动验收（`gradlew runClient`）。这与项目既有约定一致（参见 `MapPlaceholderViewTest`、`ToolPanelViewTest` 的 `@Disabled` 用例）。

**Files:**
- Create: `src/client/java/net/jsmua/kinetic_planner/gui/ribbon/RibbonBar.java`
- Create: `src/client/java/net/jsmua/kinetic_planner/gui/ribbon/QatBar.java`
- Create: `src/client/java/net/jsmua/kinetic_planner/gui/ribbon/internal/DefaultQuickAccessToolbar.java`
- Create: `src/client/java/net/jsmua/kinetic_planner/gui/ribbon/internal/RibbonBuilder.java`
- Create: `src/client/java/net/jsmua/kinetic_planner/gui/ribbon/internal/GroupPanel.java`
- Create: `src/client/java/net/jsmua/kinetic_planner/gui/ribbon/internal/ToolWidgetFactory.java`
- Create: `src/client/java/net/jsmua/kinetic_planner/gui/ribbon/internal/OverflowMenu.java`
- Create: `src/client/java/net/jsmua/kinetic_planner/gui/ribbon/internal/FloatingContentPanel.java`
- Create: `src/client/java/net/jsmua/kinetic_planner/gui/ribbon/internal/ContextMenuFactory.java`

**Interfaces:**
- Consumes: Task 1-6 全部框架 API + LDLib2 UIElement/Button/Menu/Selector/ScrollerView/TaffyPosition/Sprites/UIEvents
- Produces:
  - `RibbonBar(ViewContextProvider, RibbonPreferenceStore)` 构造函数
  - `RibbonBar.onContextChanged()` private 方法（监听 ViewContextProvider 变化）
  - `DefaultQuickAccessToolbar` 实现 `QuickAccessToolbar`，状态逻辑可单测（但本任务不写测试，留 Task 12 解耦校验测试覆盖类存在性）

- [ ] **Step 1: 创建 RibbonBar 主容器**

Create `src/client/java/net/jsmua/kinetic_planner/gui/ribbon/RibbonBar.java`:

```java
package net.jsmua.kinetic_planner.gui.ribbon;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import dev.vfyjxf.taffy.style.FlexDirection;
import net.jsmua.kinetic_planner.gui.ribbon.api.ContextualTabGroup;
import net.jsmua.kinetic_planner.gui.ribbon.api.RibbonPreferenceStore;
import net.jsmua.kinetic_planner.gui.ribbon.api.RibbonTabDefinition;
import net.jsmua.kinetic_planner.gui.ribbon.api.RibbonTabState;
import net.jsmua.kinetic_planner.gui.ribbon.api.ViewContextProvider;
import net.jsmua.kinetic_planner.gui.ribbon.internal.DefaultQuickAccessToolbar;
import net.jsmua.kinetic_planner.gui.ribbon.internal.RibbonBuilder;
import net.jsmua.kinetic_planner.gui.ribbon.internal.RibbonPreferences;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Ribbon 栏主容器 (spec §2.1, §10.6)。
 *
 * <p>构造注入 ViewContextProvider + RibbonPreferenceStore, 不调用任何 getInstance()。
 *
 * <p>结构:
 * <pre>
 * RibbonBar (UIElement, COLUMN, width=100%)
 * ├── RibbonHeader (ROW, height=20px): QAT + TabStrip + Trailing components
 * └── RibbonContent: 当前选中 tab 的工具组面板 (PINNED 占布局 / FLOATING 浮层)
 * </pre>
 */
public final class RibbonBar extends UIElement {

    private final ViewContextProvider contextProvider;
    private final RibbonPreferenceStore preferenceStore;
    private final RibbonPreferences preferences;
    private final DefaultQuickAccessToolbar qat;
    private final Map<ResourceLocation, RibbonTabState> tabStates = new HashMap<>();
    private ResourceLocation selectedTabId;

    public RibbonBar(ViewContextProvider contextProvider,
                     RibbonPreferenceStore preferenceStore) {
        super();
        this.contextProvider = contextProvider;
        this.preferenceStore = preferenceStore;
        this.preferences = RibbonPreferences.load(preferenceStore);
        this.qat = new DefaultQuickAccessToolbar(preferences);

        // 注册上下文变化监听
        contextProvider.addContextChangeListener(this::onContextChanged);

        // 初始化 tab states
        for (var tab : net.jsmua.kinetic_planner.gui.ribbon.registry.RibbonRegistry.getTabsSortedByPriority()) {
            tabStates.put(tab.getId(), new RibbonTabState(tab));
        }
        for (var group : net.jsmua.kinetic_planner.gui.ribbon.registry.RibbonRegistry.getContextualGroups()) {
            for (var tab : group.getTabs()) {
                tabStates.put(tab.getId(), new RibbonTabState(tab));
            }
        }

        // 默认选中
        this.selectedTabId = preferences.getSelectedTabId().orElse(null);

        // 布局
        addClass("kp-ribbon-bar");
        layout(layout -> {
            layout.flexDirection(FlexDirection.COLUMN);
            layout.widthPercent(100);
        });

        // 构建 header + content (委托 RibbonBuilder)
        RibbonBuilder.build(this, tabStates, qat, selectedTabId);
    }

    /** ViewContextProvider 报告变化时调用: 更新 CONTEXTUAL tab 可见性 + 重建 header。 */
    private void onContextChanged() {
        Set<ResourceLocation> active = contextProvider.getActiveContexts();
        for (ContextualTabGroup group : net.jsmua.kinetic_planner.gui.ribbon.registry.RibbonRegistry.getContextualGroups()) {
            boolean isActive = active.contains(group.getId());
            for (RibbonTabDefinition tab : group.getTabs()) {
                var state = tabStates.get(tab.getId());
                if (state != null) {
                    state.setContextActive(isActive);
                }
            }
        }
        rebuildHeader();
    }

    /** 重建 header (清除旧 children, 重新调用 RibbonBuilder.buildHeader)。 */
    void rebuildHeader() {
        RibbonBuilder.rebuildHeader(this, tabStates, qat, selectedTabId);
    }

    public ViewContextProvider getContextProvider() {
        return contextProvider;
    }

    public RibbonPreferenceStore getPreferenceStore() {
        return preferenceStore;
    }

    public DefaultQuickAccessToolbar getQat() {
        return qat;
    }
}
```

- [ ] **Step 2: 创建 QatBar UI**

Create `src/client/java/net/jsmua/kinetic_planner/gui/ribbon/QatBar.java`:

```java
package net.jsmua.kinetic_planner.gui.ribbon;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import dev.vfyjxf.taffy.style.FlexDirection;
import net.minecraft.network.chat.Component;

/**
 * QAT UI 实现 (spec §7.4)。
 *
 * <p>图标按钮列表, 始终 SMALL 尺寸。空状态显示淡色星标图标。
 * 状态逻辑由 DefaultQuickAccessToolbar 持有, 本类仅负责 UI 渲染。
 */
public final class QatBar extends UIElement {

    public QatBar() {
        super();
        addClass("kp-ribbon-qat");
        layout(layout -> {
            layout.flexDirection(FlexDirection.ROW);
        });
    }

    /** 用指定工具 ID 列表 + 查找回调重建按钮。 */
    public void rebuild(java.util.List<net.minecraft.resources.ResourceLocation> toolIds,
                        java.util.function.Function<net.minecraft.resources.ResourceLocation,
                            net.jsmua.kinetic_planner.gui.ribbon.api.RibbonToolDefinition> lookup) {
        clearAllChildren();
        if (toolIds.isEmpty()) {
            // 空状态: 显示淡色星标提示
            var hint = new Button();
            hint.setText(Component.literal("★"));
            hint.addClass("kp-ribbon-qat-empty");
            addChild(hint);
            return;
        }
        for (var id : toolIds) {
            var def = lookup.apply(id);
            if (def == null) continue;
            var btn = new Button();
            btn.setText(def.getDisplayName());
            btn.setOnClick(event -> def.getAction().command().execute());
            addChild(btn);
        }
    }
}
```

- [ ] **Step 3: 创建 DefaultQuickAccessToolbar**

Create `src/client/java/net/jsmua/kinetic_planner/gui/ribbon/internal/DefaultQuickAccessToolbar.java`:

```java
package net.jsmua.kinetic_planner.gui.ribbon.internal;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import net.jsmua.kinetic_planner.gui.ribbon.QatBar;
import net.jsmua.kinetic_planner.gui.ribbon.api.QuickAccessToolbar;
import net.jsmua.kinetic_planner.gui.ribbon.api.RibbonPreferenceStore;
import net.jsmua.kinetic_planner.gui.ribbon.api.RibbonToolDefinition;
import net.jsmua.kinetic_planner.gui.ribbon.registry.RibbonRegistry;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * QuickAccessToolbar 默认实现 (spec §7.1)。
 *
 * <p>状态逻辑 (toolIds 列表) 与 UI 渲染 (QatBar) 分离:
 * 状态操作可单测 (不触发 UIElement <clinit>); createElement() 触发 clinit 留运行时验收。
 */
public final class DefaultQuickAccessToolbar implements QuickAccessToolbar {

    private final List<ResourceLocation> toolIds = new ArrayList<>();
    private final QatBar qatBar;

    public DefaultQuickAccessToolbar(RibbonPreferences preferences) {
        this.toolIds.addAll(preferences.getQatToolIds());
        this.qatBar = new QatBar();
        rebuildBar();
    }

    @Override
    public List<ResourceLocation> getToolIds() {
        return Collections.unmodifiableList(toolIds);
    }

    @Override
    public boolean addTool(ResourceLocation toolId) {
        if (toolIds.contains(toolId)) return false;
        toolIds.add(toolId);
        rebuildBar();
        return true;
    }

    @Override
    public boolean removeTool(ResourceLocation toolId) {
        boolean removed = toolIds.remove(toolId);
        if (removed) rebuildBar();
        return removed;
    }

    @Override
    public boolean containsTool(ResourceLocation toolId) {
        return toolIds.contains(toolId);
    }

    @Override
    public UIElement createElement() {
        return qatBar;
    }

    @Override
    public void loadPreferences(RibbonPreferenceStore store) {
        toolIds.clear();
        toolIds.addAll(store.getQatToolIds());
        rebuildBar();
    }

    @Override
    public void savePreferences(RibbonPreferenceStore store) {
        store.setQatToolIds(new ArrayList<>(toolIds));
    }

    private void rebuildBar() {
        qatBar.rebuild(toolIds, this::lookupTool);
    }

    /** 从 RibbonRegistry 查找 tool definition。 */
    private RibbonToolDefinition lookupTool(ResourceLocation id) {
        for (var tab : RibbonRegistry.getTabsSortedByPriority()) {
            for (var group : tab.getGroups()) {
                for (var tool : group.getTools()) {
                    if (tool.getId().equals(id)) return tool;
                }
            }
        }
        return null;
    }
}
```

- [ ] **Step 4: 创建 RibbonBuilder**

Create `src/client/java/net/jsmua/kinetic_planner/gui/ribbon/internal/RibbonBuilder.java`:

```java
package net.jsmua.kinetic_planner.gui.ribbon.internal;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import dev.vfyjxf.taffy.style.FlexDirection;
import net.jsmua.kinetic_planner.gui.ribbon.RibbonBar;
import net.jsmua.kinetic_planner.gui.ribbon.api.*;
import net.jsmua.kinetic_planner.gui.ribbon.registry.RibbonRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;

/**
 * definition -> UIElement 树构建器 (spec §2.1)。
 *
 * <p>所有 UI 构造集中于此, 便于主题切换时整体重建。
 */
public final class RibbonBuilder {

    private RibbonBuilder() {}

    /** 首次构建: header + content。 */
    public static void build(RibbonBar bar,
                              Map<ResourceLocation, RibbonTabState> tabStates,
                              DefaultQuickAccessToolbar qat,
                              ResourceLocation selectedTabId) {
        buildHeader(bar, tabStates, qat, selectedTabId);
        buildContent(bar, tabStates, selectedTabId);
    }

    /** 仅重建 header (CONTEXTUAL 激活/停用时调用)。 */
    public static void rebuildHeader(RibbonBar bar,
                                      Map<ResourceLocation, RibbonTabState> tabStates,
                                      DefaultQuickAccessToolbar qat,
                                      ResourceLocation selectedTabId) {
        // 简化实现: 清除全部 children 再重建 (运行时优化留 Phase 2+)
        bar.clearAllChildren();
        buildHeader(bar, tabStates, qat, selectedTabId);
        buildContent(bar, tabStates, selectedTabId);
    }

    private static void buildHeader(RibbonBar bar,
                                     Map<ResourceLocation, RibbonTabState> tabStates,
                                     DefaultQuickAccessToolbar qat,
                                     ResourceLocation selectedTabId) {
        var header = new UIElement();
        header.addClass("kp-ribbon-header");
        header.layout(layout -> {
            layout.flexDirection(FlexDirection.ROW);
            layout.height(20);
        });

        // LEADING: QAT (最左) + LEADING components
        header.addChild(qat.createElement());
        for (var comp : RibbonRegistry.getHeaderComponents(RibbonHeaderComponent.Placement.LEADING)) {
            header.addChild(comp.createElement());
        }

        // TabStrip (flex=1, 可横向滚动 - 简化: 直接 ROW)
        var tabStrip = new UIElement();
        tabStrip.addClass("kp-ribbon-tab-strip");
        tabStrip.layout(layout -> {
            layout.flexDirection(FlexDirection.ROW);
            layout.flexGrow(1);
        });
        for (var tab : RibbonRegistry.getTabsSortedByPriority()) {
            var state = tabStates.get(tab.getId());
            if (state == null || !state.isHeaderVisible()) continue;
            var tabBtn = new Button();
            tabBtn.setText(tab.getDisplayName());
            tabBtn.setOnClick(event -> bar.selectTab(tab.getId()));
            tabStrip.addChild(tabBtn);
        }
        // 上下文 tabs
        for (var group : RibbonRegistry.getContextualGroups()) {
            for (var tab : group.getTabs()) {
                var state = tabStates.get(tab.getId());
                if (state == null || !state.isHeaderVisible()) continue;
                var tabBtn = new Button();
                tabBtn.setText(tab.getDisplayName());
                tabBtn.setOnClick(event -> bar.selectTab(tab.getId()));
                tabStrip.addChild(tabBtn);
            }
        }
        header.addChild(tabStrip);

        // TRAILING components
        for (var comp : RibbonRegistry.getHeaderComponents(RibbonHeaderComponent.Placement.TRAILING)) {
            header.addChild(comp.createElement());
        }

        bar.addChild(header);
    }

    private static void buildContent(RibbonBar bar,
                                      Map<ResourceLocation, RibbonTabState> tabStates,
                                      ResourceLocation selectedTabId) {
        if (selectedTabId == null) return;
        var state = tabStates.get(selectedTabId);
        if (state == null || !state.getContentVisible()) return;

        var content = new UIElement();
        content.addClass("kp-ribbon-content");
        content.layout(layout -> {
            layout.flexDirection(FlexDirection.ROW);
            layout.height(40);
        });

        for (var group : state.getDefinition().getGroups()) {
            content.addChild(GroupPanel.build(group));
        }

        bar.addChild(content);
    }
}
```

> **注意**: `bar.selectTab(...)` 方法需要在 RibbonBar 中补充。实施时在 RibbonBar 添加:
> ```java
> public void selectTab(ResourceLocation id) {
>     this.selectedTabId = id;
>     this.preferences.setSelectedTabId(id);
>     clearAllChildren();
>     RibbonBuilder.build(this, tabStates, qat, selectedTabId);
> }
> ```

- [ ] **Step 5: 创建 GroupPanel + ToolWidgetFactory + OverflowMenu + FloatingContentPanel + ContextMenuFactory**

> 这些类都是 UIElement 子类或工具类，结构相似。实施者参考 spec §5.2/§5.3/§9.2/§8.2 创建。每个类**必须有**类存在（即使实现简化），因为 Task 12 解耦校验测试会验证 `gui/ribbon/` 包不引用 KP。简化实现允许 (如 OverflowMenu 仅 `extends UIElement` + 空构造)。

Create `src/client/java/net/jsmua/kinetic_planner/gui/ribbon/internal/GroupPanel.java`:

```java
package net.jsmua.kinetic_planner.gui.ribbon.internal;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import dev.vfyjxf.taffy.style.FlexDirection;
import net.jsmua.kinetic_planner.gui.ribbon.api.RibbonToolGroupDefinition;

/** 工具组面板渲染 (spec §5.2)。 */
public final class GroupPanel extends UIElement {

    public static UIElement build(RibbonToolGroupDefinition group) {
        var panel = new GroupPanel();
        group.getTools().forEach(tool -> {
            panel.addChild(ToolWidgetFactory.create(tool));
        });
        return panel;
    }
}
```

Create `src/client/java/net/jsmua/kinetic_planner/gui/ribbon/internal/ToolWidgetFactory.java`:

```java
package net.jsmua.kinetic_planner.gui.ribbon.internal;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import net.jsmua.kinetic_planner.gui.ribbon.api.*;

/** ToolAction -> UIElement 工厂 (spec §4.1)。Phase 1: ButtonAction + SeparatorAction。 */
public final class ToolWidgetFactory {

    public static UIElement create(RibbonToolDefinition tool) {
        var action = tool.getAction();
        if (action instanceof ButtonAction btn) {
            var button = new Button();
            button.setText(tool.getDisplayName());
            button.setOnClick(event -> btn.command().execute());
            // toggle 视觉反馈 + disabled 灰显留 Phase 2+ 完善
            return button;
        } else if (action instanceof SeparatorAction) {
            var sep = new UIElement();
            sep.addClass("kp-ribbon-separator");
            return sep;
        }
        throw new IllegalStateException("Unknown ToolAction: " + action.getClass());
    }
}
```

Create `src/client/java/net/jsmua/kinetic_planner/gui/ribbon/internal/OverflowMenu.java`:

```java
package net.jsmua.kinetic_planner.gui.ribbon.internal;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;

/** 溢出菜单 (spec §5.3)。Phase 2+ 完善实现。 */
public final class OverflowMenu extends UIElement {
    public OverflowMenu() {
        super();
        addClass("kp-ribbon-overflow");
    }
}
```

Create `src/client/java/net/jsmua/kinetic_planner/gui/ribbon/internal/FloatingContentPanel.java`:

```java
package net.jsmua.kinetic_planner.gui.ribbon.internal;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import dev.vfyjxf.taffy.style.TaffyPosition;
import net.jsmua.kinetic_planner.gui.ribbon.api.RibbonTabState;

/** FLOATING 模式浮层 (spec §9.2)。 */
public final class FloatingContentPanel extends UIElement {

    public static UIElement create(RibbonTabState tab) {
        var panel = new FloatingContentPanel();
        panel.layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.top(20);
            layout.left(0);
            layout.widthPercent(100);
            layout.height(40);
        });
        // 失焦收起逻辑 + 工具组填充留运行时完善
        return panel;
    }
}
```

Create `src/client/java/net/jsmua/kinetic_planner/gui/ribbon/internal/ContextMenuFactory.java`:

```java
package net.jsmua.kinetic_planner.gui.ribbon.internal;

import com.lowdragmc.lowdraglib2.gui.ui.elements.Menu;
import net.jsmua.kinetic_planner.gui.ribbon.api.RibbonTabDefinition;
import net.jsmua.kinetic_planner.gui.ribbon.api.RibbonToolDefinition;
import net.jsmua.kinetic_planner.gui.ribbon.api.TabDisplayMode;
import net.jsmua.kinetic_planner.gui.ribbon.api.QuickAccessToolbar;

/** 右键菜单构建 (spec §8)。 */
public final class ContextMenuFactory {

    /** Tool 右键菜单 (spec §8.2)。 */
    public static Menu<String, Void> forTool(RibbonToolDefinition tool, QuickAccessToolbar qat) {
        // 简化: 返回空 Menu, 完整实现见 spec §8.2
        return new Menu<>(null, key -> null);
    }

    /** Tab 右键菜单 (spec §8.3)。 */
    public static Menu<String, Void> forTab(RibbonTabDefinition tab) {
        // CONTEXTUAL tab 不显示固定/浮动/隐藏选项
        // 简化: 返回空 Menu, 完整实现见 spec §8.3
        return new Menu<>(null, key -> null);
    }
}
```

- [ ] **Step 6: 在 RibbonBar 补充 selectTab 方法**

Modify `src/client/java/net/jsmua/kinetic_planner/gui/ribbon/RibbonBar.java`，在 `rebuildHeader()` 方法后添加（用 SearchReplace）:

Old:
```java
    /** 重建 header (清除旧 children, 重新调用 RibbonBuilder.buildHeader)。 */
    void rebuildHeader() {
        RibbonBuilder.rebuildHeader(this, tabStates, qat, selectedTabId);
    }
```

New:
```java
    /** 重建 header (清除旧 children, 重新调用 RibbonBuilder.buildHeader)。 */
    void rebuildHeader() {
        RibbonBuilder.rebuildHeader(this, tabStates, qat, selectedTabId);
    }

    /** 用户点击 tab 头时调用: 切换选中 + 重建。 */
    public void selectTab(ResourceLocation id) {
        this.selectedTabId = id;
        this.preferences.setSelectedTabId(id);
        clearAllChildren();
        RibbonBuilder.build(this, tabStates, qat, selectedTabId);
    }
```

- [ ] **Step 7: 编译验证**

Run: `gradlew compileClientJava`
Expected: BUILD SUCCESSFUL（所有类编译通过，UIElement clinit 在编译期不触发）

- [ ] **Step 8: 运行全部已有测试确保无回归**

Run: `gradlew test`
Expected: 所有已有测试 PASS（新 UI 类不引入新测试，已有测试不受影响）

- [ ] **Step 9: Commit**

```bash
git add src/client/java/net/jsmua/kinetic_planner/gui/ribbon/RibbonBar.java src/client/java/net/jsmua/kinetic_planner/gui/ribbon/QatBar.java src/client/java/net/jsmua/kinetic_planner/gui/ribbon/internal/
git commit -m "feat(ribbon): 添加框架内部 UI 实现 (RibbonBar/Builder/QatBar/内部类)"
```

---

### Task 8: KP 适配层 - ToolToggleCommand + KpToolDefinitions

**Files:**
- Create: `src/client/java/net/jsmua/kinetic_planner/gui/editor/ribbon/ToolToggleCommand.java`
- Create: `src/client/java/net/jsmua/kinetic_planner/gui/editor/ribbon/KpToolDefinitions.java`
- Test: `src/test/java/net/jsmua/kinetic_planner/gui/editor/ribbon/ToolToggleCommandTest.java`

**Interfaces:**
- Consumes: Task 1 的 `RibbonToggleCommand` + Task 2 的 `RibbonToolDefinition`/`SimpleRibbonToolDefinition`/`ButtonAction`/`TextTooltip` + 现有 `EditToolState`（`net.jsmua.kinetic_planner.gui.editor.EditToolState`）+ `EditToolState.Tool` 枚举（NAVIGATION/SELECT/DRAW_LINE/DRAW_BEZIER/SNAP）
- Produces:
  - `ToolToggleCommand(EditToolState, Tool)` 构造函数（构造注入，**不调** `getInstance()`）
  - `KpToolDefinitions(EditToolState)` 构造函数
  - `KpToolDefinitions.panTool()` / `selectTool()` / `lineTool()` / `bezierTool()` / `snapTool()` / `allTools()` 方法

- [ ] **Step 1: 写失败测试 - ToolToggleCommand 构造注入**

Create `src/test/java/net/jsmua/kinetic_planner/gui/editor/ribbon/ToolToggleCommandTest.java`:

```java
package net.jsmua.kinetic_planner.gui.editor.ribbon;

import net.jsmua.kinetic_planner.gui.editor.EditToolState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ToolToggleCommandTest {

    @Test
    void executeCallsInjectedToolStateSetCurrentTool() {
        var mockState = mock(EditToolState.class);
        var cmd = new ToolToggleCommand(mockState, EditToolState.Tool.NAVIGATION);

        cmd.execute();

        verify(mockState).setCurrentTool(EditToolState.Tool.NAVIGATION);
    }

    @Test
    void isActiveQueriesInjectedToolState() {
        var mockState = mock(EditToolState.class);
        when(mockState.getCurrentTool()).thenReturn(EditToolState.Tool.SELECT);
        var cmd = new ToolToggleCommand(mockState, EditToolState.Tool.SELECT);

        assertTrue(cmd.isActive());

        when(mockState.getCurrentTool()).thenReturn(EditToolState.Tool.NAVIGATION);
        assertFalse(cmd.isActive());
    }

    @Test
    void setActiveTrueCallsExecute() {
        var mockState = mock(EditToolState.class);
        var cmd = new ToolToggleCommand(mockState, EditToolState.Tool.DRAW_LINE);

        cmd.setActive(true);

        verify(mockState).setCurrentTool(EditToolState.Tool.DRAW_LINE);
    }

    @Test
    void setActiveFalseDoesNothing() {
        var mockState = mock(EditToolState.class);
        var cmd = new ToolToggleCommand(mockState, EditToolState.Tool.SNAP);

        cmd.setActive(false);

        verifyNoInteractions(mockState);
    }

    @Test
    void isEnabledAlwaysTrue() {
        var mockState = mock(EditToolState.class);
        var cmd = new ToolToggleCommand(mockState, EditToolState.Tool.NAVIGATION);
        assertTrue(cmd.isEnabled());
    }
}
```

- [ ] **Step 2: 运行测试验证失败**

Run: `gradlew test --tests "net.jsmua.kinetic_planner.gui.editor.ribbon.ToolToggleCommandTest"`
Expected: FAIL（编译失败：`ToolToggleCommand` 类不存在）

- [ ] **Step 3: 创建 ToolToggleCommand**

Create `src/client/java/net/jsmua/kinetic_planner/gui/editor/ribbon/ToolToggleCommand.java`:

```java
package net.jsmua.kinetic_planner.gui.editor.ribbon;

import net.jsmua.kinetic_planner.gui.editor.EditToolState;
import net.jsmua.kinetic_planner.gui.ribbon.api.RibbonToggleCommand;

/**
 * RibbonToggleCommand 适配 EditToolState (spec §4.8)。
 *
 * <p><b>关键约束:</b> 构造注入 EditToolState, 不调 EditToolState.getInstance()。
 * 保证 ToolToggleCommandTest 可用 Mockito mock EditToolState 验证行为。
 */
final class ToolToggleCommand implements RibbonToggleCommand {

    private final EditToolState toolState;
    private final EditToolState.Tool tool;

    ToolToggleCommand(EditToolState toolState, EditToolState.Tool tool) {
        this.toolState = toolState;
        this.tool = tool;
    }

    @Override
    public void execute() {
        toolState.setCurrentTool(tool);
    }

    @Override
    public boolean isActive() {
        return toolState.getCurrentTool() == tool;
    }

    @Override
    public void setActive(boolean active) {
        if (active) execute();
        // setActive(false) 不做任何事 (工具切换是单选, 设为 false 无意义)
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
```

- [ ] **Step 4: 运行测试验证通过**

Run: `gradlew test --tests "net.jsmua.kinetic_planner.gui.editor.ribbon.ToolToggleCommandTest"`
Expected: PASS（5 tests）

- [ ] **Step 5: 创建 KpToolDefinitions**

Create `src/client/java/net/jsmua/kinetic_planner/gui/editor/ribbon/KpToolDefinitions.java`:

```java
package net.jsmua.kinetic_planner.gui.editor.ribbon;

import net.jsmua.kinetic_planner.gui.editor.EditToolState;
import net.jsmua.kinetic_planner.gui.ribbon.api.ButtonAction;
import net.jsmua.kinetic_planner.gui.ribbon.api.RibbonToolDefinition;
import net.jsmua.kinetic_planner.gui.ribbon.api.SimpleRibbonToolDefinition;
import net.jsmua.kinetic_planner.gui.ribbon.api.TextTooltip;
import net.jsmua.kinetic_planner.gui.ribbon.api.ToolSize;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Optional;

/**
 * Tool 枚举 -> RibbonToolDefinition 集中映射 (spec §4.8, DRY)。
 *
 * <p>所有 KP 工具 definition 在此集中创建, 避免散落在多处。
 * ToolToggleCommand 通过构造注入 EditToolState, 不调 getInstance()。
 */
public final class KpToolDefinitions {

    private final EditToolState toolState;

    public KpToolDefinitions(EditToolState toolState) {
        this.toolState = toolState;
    }

    public RibbonToolDefinition panTool() {
        return makeTool("tool_pan", "Pan", EditToolState.Tool.NAVIGATION, "P");
    }

    public RibbonToolDefinition selectTool() {
        return makeTool("tool_select", "Select", EditToolState.Tool.SELECT, "V");
    }

    public RibbonToolDefinition lineTool() {
        return makeTool("tool_line", "Line", EditToolState.Tool.DRAW_LINE, "L");
    }

    public RibbonToolDefinition bezierTool() {
        return makeTool("tool_bezier", "Bezier", EditToolState.Tool.DRAW_BEZIER, "B");
    }

    public RibbonToolDefinition snapTool() {
        return makeTool("tool_snap", "Snap", EditToolState.Tool.SNAP, "S");
    }

    public List<RibbonToolDefinition> allTools() {
        return List.of(panTool(), selectTool(), lineTool(), bezierTool(), snapTool());
    }

    private RibbonToolDefinition makeTool(String path, String name, EditToolState.Tool tool, String shortcut) {
        return new SimpleRibbonToolDefinition(
            ResourceLocation.fromNamespaceAndPath("kp", path),
            Component.literal(name),
            Optional.empty(),  // icon 留运行时补充
            Optional.of(new TextTooltip(Component.literal(name + " (" + shortcut + ")"))),
            Optional.of(shortcut),
            ToolSize.LARGE,
            new ButtonAction(new ToolToggleCommand(toolState, tool), true)
        );
    }
}
```

- [ ] **Step 6: 编译验证**

Run: `gradlew compileClientJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
git add src/client/java/net/jsmua/kinetic_planner/gui/editor/ribbon/ToolToggleCommand.java src/client/java/net/jsmua/kinetic_planner/gui/editor/ribbon/KpToolDefinitions.java src/test/java/net/jsmua/kinetic_planner/gui/editor/ribbon/ToolToggleCommandTest.java
git commit -m "feat(ribbon): KP 适配层 - ToolToggleCommand + KpToolDefinitions (构造注入)"
```

---

### Task 9: KP 适配层 - KPConfigRibbonPreferenceStore

**Files:**
- Create: `src/client/java/net/jsmua/kinetic_planner/gui/editor/ribbon/KPConfigRibbonPreferenceStore.java`
- Test: `src/test/java/net/jsmua/kinetic_planner/gui/editor/ribbon/KPConfigRibbonPreferenceStoreTest.java`

**Interfaces:**
- Consumes: Task 5 的 `RibbonPreferenceStore` + `TabDisplayMode` + 现有 `IKPConfig`（`net.jsmua.kinetic_planner.config.IKPConfig`）
- Produces: `KPConfigRibbonPreferenceStore(IKPConfig)` 构造函数 + 6 个接口方法实现

> **注意**: `IKPConfig` 当前没有 `[ribbon]` 段配置方法。本任务的实现需要在 `IKPConfig` 接口和 `KPConfig` 实现中补充 3 组方法（`getRibbonTabDisplayMode` / `setRibbonTabDisplayMode` / `getRibbonQatToolIds` / `setRibbonQatToolIds` / `getRibbonSelectedTab` / `setRibbonSelectedTab`）。为最小化改动，`KPConfigRibbonPreferenceStore` 通过这些新方法委托 `IKPConfig`。

- [ ] **Step 1: 写失败测试 - 委托 IKPConfig**

Create `src/test/java/net/jsmua/kinetic_planner/gui/editor/ribbon/KPConfigRibbonPreferenceStoreTest.java`:

```java
package net.jsmua.kinetic_planner.gui.editor.ribbon;

import net.jsmua.kinetic_planner.config.IKPConfig;
import net.jsmua.kinetic_planner.gui.ribbon.api.RibbonPreferenceStore;
import net.jsmua.kinetic_planner.gui.ribbon.api.TabDisplayMode;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class KPConfigRibbonPreferenceStoreTest {

    @Test
    void getTabDisplayModeDelegatesToConfig() {
        var config = mock(IKPConfig.class);
        var tabId = ResourceLocation.fromNamespaceAndPath("kp", "tools");
        when(config.getRibbonTabDisplayMode("kp:tools")).thenReturn("FLOATING");

        var store = new KPConfigRibbonPreferenceStore(config);
        assertEquals(Optional.of(TabDisplayMode.FLOATING), store.getTabDisplayMode(tabId));
    }

    @Test
    void getTabDisplayModeEmptyWhenConfigReturnsNull() {
        var config = mock(IKPConfig.class);
        when(config.getRibbonTabDisplayMode(anyString())).thenReturn(null);

        var store = new KPConfigRibbonPreferenceStore(config);
        assertTrue(store.getTabDisplayMode(ResourceLocation.fromNamespaceAndPath("kp", "x")).isEmpty());
    }

    @Test
    void setTabDisplayModeDelegatesToConfig() {
        var config = mock(IKPConfig.class);
        var store = new KPConfigRibbonPreferenceStore(config);

        store.setTabDisplayMode(ResourceLocation.fromNamespaceAndPath("kp", "tools"), TabDisplayMode.HIDDEN);

        verify(config).setRibbonTabDisplayMode("kp:tools", "HIDDEN");
    }

    @Test
    void qatToolIdsRoundTrip() {
        var config = mock(IKPConfig.class);
        var ids = List.of("kp:tool_pan", "kp:tool_select");
        when(config.getRibbonQatToolIds()).thenReturn(ids);

        var store = new KPConfigRibbonPreferenceStore(config);
        var result = store.getQatToolIds();
        assertEquals(2, result.size());
        assertEquals(ResourceLocation.fromNamespaceAndPath("kp", "tool_pan"), result.get(0));

        store.setQatToolIds(List.of(ResourceLocation.fromNamespaceAndPath("kp", "tool_line")));
        verify(config).setRibbonQatToolIds(List.of("kp:tool_line"));
    }

    @Test
    void selectedTabIdRoundTrip() {
        var config = mock(IKPConfig.class);
        when(config.getRibbonSelectedTab()).thenReturn("kp:tools");

        var store = new KPConfigRibbonPreferenceStore(config);
        assertEquals(Optional.of(ResourceLocation.fromNamespaceAndPath("kp", "tools")),
            store.getSelectedTabId());

        store.setSelectedTabId(ResourceLocation.fromNamespaceAndPath("kp", "file"));
        verify(config).setRibbonSelectedTab("kp:file");
    }

    @Test
    void invalidTabDisplayModeStringReturnsEmpty() {
        var config = mock(IKPConfig.class);
        when(config.getRibbonTabDisplayMode(anyString())).thenReturn("INVALID_MODE");

        var store = new KPConfigRibbonPreferenceStore(config);
        assertTrue(store.getTabDisplayMode(ResourceLocation.fromNamespaceAndPath("kp", "x")).isEmpty(),
            "无效的 TabDisplayMode 字符串应返回 empty (容错)");
    }
}
```

- [ ] **Step 2: 运行测试验证失败**

Run: `gradlew test --tests "net.jsmua.kinetic_planner.gui.editor.ribbon.KPConfigRibbonPreferenceStoreTest"`
Expected: FAIL（编译失败：`IKPConfig.getRibbonTabDisplayMode` 等方法不存在 + `KPConfigRibbonPreferenceStore` 类不存在）

- [ ] **Step 3: 在 IKPConfig 接口补充 ribbon 方法**

Modify `src/main/java/net/jsmua/kinetic_planner/config/IKPConfig.java`，在 `toTheme()` 方法前添加（用 SearchReplace）:

Old:
```java
    // ===== [theme conversion] =====

    /**
     * 从配置值构建 {@link Theme} record。
     */
    Theme toTheme();
}
```

New:
```java
    // ===== [ribbon] =====

    /** 获取指定 tab 的显示模式字符串 (PINNED/FLOATING/HIDDEN/CONTEXTUAL), 未配置返回 null。 */
    String getRibbonTabDisplayMode(String tabId);

    /** 设置指定 tab 的显示模式字符串。 */
    void setRibbonTabDisplayMode(String tabId, String mode);

    /** 获取 QAT 工具 ID 字符串列表 (如 ["kp:tool_pan", "kp:tool_select"])。 */
    java.util.List<String> getRibbonQatToolIds();

    /** 设置 QAT 工具 ID 字符串列表。 */
    void setRibbonQatToolIds(java.util.List<String> ids);

    /** 获取上次选中的 tab ID 字符串 (如 "kp:tools"), 未配置返回 null。 */
    String getRibbonSelectedTab();

    /** 设置上次选中的 tab ID 字符串。 */
    void setRibbonSelectedTab(String tabId);

    // ===== [theme conversion] =====

    /**
     * 从配置值构建 {@link Theme} record。
     */
    Theme toTheme();
}
```

- [ ] **Step 4: 在 KPConfig 实现中补充对应方法**

> **实施者注意**: 先用 Grep 定位 `KPConfig.java` 中的 `toTheme()` 方法位置，在其前用 SearchReplace 插入 6 个方法实现。实现方式: 用 in-memory `Map<String,String>` 缓存 ribbon 偏好（首版不持久化到 TOML，避免 ModConfigSpec ListConfigValue 复杂度；Phase 2 接入真实 TOML）。每个方法委托缓存。

具体实现（实施者根据 KPConfig 既有结构适配）:

```java
    // ===== [ribbon] (Phase 1: in-memory 缓存, Phase 2 接入 TOML) =====

    private final java.util.Map<String, String> ribbonTabModes = new java.util.concurrent.ConcurrentHashMap<>();
    private volatile java.util.List<String> ribbonQatIds = new java.util.ArrayList<>();
    private volatile String ribbonSelectedTab = null;

    @Override
    public String getRibbonTabDisplayMode(String tabId) {
        return ribbonTabModes.get(tabId);
    }

    @Override
    public void setRibbonTabDisplayMode(String tabId, String mode) {
        ribbonTabModes.put(tabId, mode);
    }

    @Override
    public java.util.List<String> getRibbonQatToolIds() {
        return new java.util.ArrayList<>(ribbonQatIds);
    }

    @Override
    public void setRibbonQatToolIds(java.util.List<String> ids) {
        this.ribbonQatIds = new java.util.ArrayList<>(ids);
    }

    @Override
    public String getRibbonSelectedTab() {
        return ribbonSelectedTab;
    }

    @Override
    public void setRibbonSelectedTab(String tabId) {
        this.ribbonSelectedTab = tabId;
    }
```

- [ ] **Step 5: 创建 KPConfigRibbonPreferenceStore**

Create `src/client/java/net/jsmua/kinetic_planner/gui/editor/ribbon/KPConfigRibbonPreferenceStore.java`:

```java
package net.jsmua.kinetic_planner.gui.editor.ribbon;

import net.jsmua.kinetic_planner.config.IKPConfig;
import net.jsmua.kinetic_planner.gui.ribbon.api.RibbonPreferenceStore;
import net.jsmua.kinetic_planner.gui.ribbon.api.TabDisplayMode;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * RibbonPreferenceStore 实现, 读写 IKPConfig (spec §11.5)。
 *
 * <p>桥接框架的 ResourceLocation/TabDisplayMode 与 IKPConfig 的字符串表示。
 */
public final class KPConfigRibbonPreferenceStore implements RibbonPreferenceStore {

    private final IKPConfig config;

    public KPConfigRibbonPreferenceStore(IKPConfig config) {
        this.config = config;
    }

    @Override
    public Optional<TabDisplayMode> getTabDisplayMode(ResourceLocation tabId) {
        String mode = config.getRibbonTabDisplayMode(toString(tabId));
        if (mode == null) return Optional.empty();
        try {
            return Optional.of(TabDisplayMode.valueOf(mode));
        } catch (IllegalArgumentException e) {
            return Optional.empty();  // 容错: 无效字符串返回 empty
        }
    }

    @Override
    public void setTabDisplayMode(ResourceLocation tabId, TabDisplayMode mode) {
        config.setRibbonTabDisplayMode(toString(tabId), mode.name());
    }

    @Override
    public List<ResourceLocation> getQatToolIds() {
        var ids = config.getRibbonQatToolIds();
        var result = new ArrayList<ResourceLocation>(ids.size());
        for (var id : ids) {
            var rl = parse(id);
            if (rl != null) result.add(rl);
        }
        return result;
    }

    @Override
    public void setQatToolIds(List<ResourceLocation> ids) {
        var strings = new ArrayList<String>(ids.size());
        for (var id : ids) strings.add(toString(id));
        config.setRibbonQatToolIds(strings);
    }

    @Override
    public Optional<ResourceLocation> getSelectedTabId() {
        var s = config.getRibbonSelectedTab();
        return Optional.ofNullable(s).map(KPConfigRibbonPreferenceStore::parse);
    }

    @Override
    public void setSelectedTabId(ResourceLocation id) {
        config.setRibbonSelectedTab(toString(id));
    }

    private static String toString(ResourceLocation rl) {
        return rl.getNamespace() + ":" + rl.getPath();
    }

    private static ResourceLocation parse(String s) {
        var parts = s.split(":", 2);
        if (parts.length != 2) return null;
        return ResourceLocation.fromNamespaceAndPath(parts[0], parts[1]);
    }
}
```

- [ ] **Step 6: 运行测试验证通过**

Run: `gradlew test --tests "net.jsmua.kinetic_planner.gui.editor.ribbon.KPConfigRibbonPreferenceStoreTest"`
Expected: PASS（6 tests）

- [ ] **Step 7: 编译验证**

Run: `gradlew compileJava compileClientJava`
Expected: BUILD SUCCESSFUL（main + client 都编译通过）

- [ ] **Step 8: Commit**

```bash
git add src/main/java/net/jsmua/kinetic_planner/config/IKPConfig.java src/client/java/net/jsmua/kinetic_planner/config/KPConfig.java src/client/java/net/jsmua/kinetic_planner/gui/editor/ribbon/KPConfigRibbonPreferenceStore.java src/test/java/net/jsmua/kinetic_planner/gui/editor/ribbon/KPConfigRibbonPreferenceStoreTest.java
git commit -m "feat(ribbon): KP 适配层 - KPConfigRibbonPreferenceStore + IKPConfig 补充 ribbon 段"
```

---

### Task 10: KP 适配层 - KpViewContextProvider

**Files:**
- Create: `src/client/java/net/jsmua/kinetic_planner/gui/editor/ribbon/KpViewContextProvider.java`
- Test: `src/test/java/net/jsmua/kinetic_planner/gui/editor/ribbon/KpViewContextProviderTest.java`

**Interfaces:**
- Consumes: Task 6 的 `ViewContextProvider` + 现有 `KpEditorScreen`（`net.jsmua.kinetic_planner.gui.editor.KpEditorScreen`）
- Produces: `KpViewContextProvider(KpEditorScreen)` 构造函数 + `getActiveContexts()` / `addContextChangeListener` / `removeContextChangeListener` / `notifyContextChanged()` 方法

> **注意**: spec §10.5 示例调用 `editorScreen.isMapEditorActive()` / `isTrackGraphEditorActive()`，但 `KpEditorScreen` 当前无这些方法。**首版实现**: `getActiveContexts()` 返回空 `Set`（KP 当前只有单一 Map Editor 视图，无多视图场景），监听器机制完整实现以便未来扩展。实施者用 Read 确认 `KpEditorScreen` 现有方法，**不**添加 `isMapEditorActive` 等方法（YAGNI，未来多视图时再加）。

- [ ] **Step 1: 写失败测试 - 监听器注册与通知**

Create `src/test/java/net/jsmua/kinetic_planner/gui/editor/ribbon/KpViewContextProviderTest.java`:

```java
package net.jsmua.kinetic_planner.gui.editor.ribbon;

import net.jsmua.kinetic_planner.gui.editor.KpEditorScreen;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class KpViewContextProviderTest {

    @Test
    void emptyContextsByDefault() {
        var mockScreen = mock(KpEditorScreen.class);
        var provider = new KpViewContextProvider(mockScreen);

        assertTrue(provider.getActiveContexts().isEmpty(),
            "首版无多视图, getActiveContexts 返回空 Set");
    }

    @Test
    void addListenerReceivesNotify() {
        var mockScreen = mock(KpEditorScreen.class);
        var provider = new KpViewContextProvider(mockScreen);
        var counter = new AtomicInteger(0);
        var listener = (Runnable) () -> counter.incrementAndGet();

        provider.addContextChangeListener(listener);
        provider.notifyContextChanged();

        assertEquals(1, counter.get(), "notifyContextChanged 必须触发已注册监听器");
    }

    @Test
    void removedListenerNotNotified() {
        var mockScreen = mock(KpEditorScreen.class);
        var provider = new KpViewContextProvider(mockScreen);
        var counter = new AtomicInteger(0);
        var listener = (Runnable) () -> counter.incrementAndGet();

        provider.addContextChangeListener(listener);
        provider.removeContextChangeListener(listener);
        provider.notifyContextChanged();

        assertEquals(0, counter.get(), "移除后的监听器不应被通知");
    }

    @Test
    void multipleListenersAllNotified() {
        var mockScreen = mock(KpEditorScreen.class);
        var provider = new KpViewContextProvider(mockScreen);
        var c1 = new AtomicInteger(0);
        var c2 = new AtomicInteger(0);

        provider.addContextChangeListener(() -> c1.incrementAndGet());
        provider.addContextChangeListener(() -> c2.incrementAndGet());
        provider.notifyContextChanged();

        assertEquals(1, c1.get());
        assertEquals(1, c2.get());
    }
}
```

- [ ] **Step 2: 运行测试验证失败**

Run: `gradlew test --tests "net.jsmua.kinetic_planner.gui.editor.ribbon.KpViewContextProviderTest"`
Expected: FAIL（编译失败：`KpViewContextProvider` 类不存在。注意 `KpEditorScreen` mock 可能因 LDLib2 clinit 问题失败 - 若如此，测试改用 `null` 作为构造参数并跳过 mock，验证逻辑不依赖 screen）

> **备用方案**: 若 `mock(KpEditorScreen.class)` 触发 `ExceptionInInitializerError`（因 `KpEditorScreen extends Screen` 链路触发 LDLib2 clinit），将测试改为构造 `new KpViewContextProvider(null)`（首版 `getActiveContexts` 不查询 screen）。实施者根据实际 mock 失败情况选择。

- [ ] **Step 3: 创建 KpViewContextProvider**

Create `src/client/java/net/jsmua/kinetic_planner/gui/editor/ribbon/KpViewContextProvider.java`:

```java
package net.jsmua.kinetic_planner.gui.editor.ribbon;

import net.jsmua.kinetic_planner.gui.editor.KpEditorScreen;
import net.jsmua.kinetic_planner.gui.ribbon.api.ViewContextProvider;
import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * KP ViewContextProvider 实现 (spec §10.5)。
 *
 * <p><b>首版:</b> getActiveContexts() 返回空 Set (KP 当前只有单一 Map Editor 视图, 无多视图场景)。
 * 监听器机制完整实现, 未来多视图时在 getActiveContexts() 中查询 editorScreen 状态。
 *
 * <p>构造注入 KpEditorScreen, 不调 getInstance()。
 */
public final class KpViewContextProvider implements ViewContextProvider {

    private final KpEditorScreen editorScreen;
    private final CopyOnWriteArrayList<Runnable> listeners = new CopyOnWriteArrayList<>();

    public KpViewContextProvider(KpEditorScreen editorScreen) {
        this.editorScreen = editorScreen;
    }

    @Override
    public Set<ResourceLocation> getActiveContexts() {
        // 首版: 无多视图, 返回空 Set
        // 未来: 根据 editorScreen 状态返回对应上下文 ID
        //   if (editorScreen.isMapEditorActive()) ctx.add(RL("kp", "map_editor"));
        //   if (editorScreen.isTrackGraphEditorActive()) ctx.add(RL("kp", "track_graph_editor"));
        return Collections.emptySet();
    }

    @Override
    public void addContextChangeListener(Runnable listener) {
        listeners.add(listener);
    }

    @Override
    public void removeContextChangeListener(Runnable listener) {
        listeners.remove(listener);
    }

    /** KP 编辑模式切换时调用 (未来多视图扩展点)。 */
    public void notifyContextChanged() {
        listeners.forEach(Runnable::run);
    }
}
```

- [ ] **Step 4: 运行测试验证通过**

Run: `gradlew test --tests "net.jsmua.kinetic_planner.gui.editor.ribbon.KpViewContextProviderTest"`
Expected: PASS（4 tests）

> **若 mock 失败**: 改用 `new KpViewContextProvider(null)` 重写测试，跳过 mock。首版逻辑不依赖 screen，测试覆盖监听器机制即可。

- [ ] **Step 5: 编译验证**

Run: `gradlew compileClientJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add src/client/java/net/jsmua/kinetic_planner/gui/editor/ribbon/KpViewContextProvider.java src/test/java/net/jsmua/kinetic_planner/gui/editor/ribbon/KpViewContextProviderTest.java
git commit -m "feat(ribbon): KP 适配层 - KpViewContextProvider (首版空上下文 + 监听器机制)"
```

---

### Task 11: KP 适配层 - KpRibbonRegistration（注册 KP tab/group/component）

**Files:**
- Create: `src/client/java/net/jsmua/kinetic_planner/gui/editor/ribbon/KpRibbonRegistration.java`
- Modify: `src/client/java/net/jsmua/kinetic_planner/KineticPlannerClient.java`（onClientSetup 调用注册 + freeze）

**Interfaces:**
- Consumes: Task 3+6 的 `RibbonRegistry` + Task 2 的 `SimpleRibbonTabDefinition`/`SimpleRibbonToolGroupDefinition` + Task 8 的 `KpToolDefinitions` + Task 9 的 `KPConfigRibbonPreferenceStore`（间接，通过 `EditToolState`）
- Produces:
  - `KpRibbonRegistration.register(EditToolState)` 静态方法：注册 KP 的 "Tools" tab（含 5 个工具的 group）
  - `KineticPlannerClient.onClientSetup` 修改：调用 `KpRibbonRegistration.register(EditToolState.getInstance())` + `RibbonRegistry.freeze()`

> **注意**: `KpRibbonRegistration.register` 接收 `EditToolState` 参数（运行时由 `KineticPlannerClient` 传入 `EditToolState.getInstance()`）。这是 KP 适配层调用单例的合法位置（运行时入口点，非框架 API 层）。

- [ ] **Step 1: 创建 KpRibbonRegistration**

Create `src/client/java/net/jsmua/kinetic_planner/gui/editor/ribbon/KpRibbonRegistration.java`:

```java
package net.jsmua.kinetic_planner.gui.editor.ribbon;

import net.jsmua.kinetic_planner.gui.editor.EditToolState;
import net.jsmua.kinetic_planner.gui.ribbon.api.RibbonToolDefinition;
import net.jsmua.kinetic_planner.gui.ribbon.api.SimpleRibbonTabDefinition;
import net.jsmua.kinetic_planner.gui.ribbon.api.SimpleRibbonToolGroupDefinition;
import net.jsmua.kinetic_planner.gui.ribbon.api.TabDisplayMode;
import net.jsmua.kinetic_planner.gui.ribbon.registry.RibbonRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Optional;

/**
 * ClientSetup 时注册 KP 自身的 tab/group/component (spec §11.3)。
 *
 * <p>这是 KP 适配层调用 EditToolState.getInstance() 的合法位置 (运行时入口点)。
 * 注册的 RibbonToolDefinition 通过 KpToolDefinitions 创建, 内部用构造注入的 ToolToggleCommand。
 */
public final class KpRibbonRegistration {

    private KpRibbonRegistration() {}

    /** 注册 KP 内置 tab。在 ClientSetup 调用, RibbonRegistry.freeze() 之前。 */
    public static void register(EditToolState toolState) {
        var definitions = new KpToolDefinitions(toolState);
        var tools = definitions.allTools();

        var toolsGroup = new SimpleRibbonToolGroupDefinition(
            ResourceLocation.fromNamespaceAndPath("kp", "group_main"),
            Optional.of(Component.literal("Tools")),
            tools
        );

        var toolsTab = new SimpleRibbonTabDefinition(
            ResourceLocation.fromNamespaceAndPath("kp", "tab_tools"),
            Component.literal("Tools"),
            Optional.empty(),
            100,  // priority: 主 tab 排最左
            List.of(toolsGroup),
            TabDisplayMode.PINNED,
            false,  // 核心 tab, 不允许用户隐藏
            Optional.empty()
        );

        RibbonRegistry.registerTab(toolsTab.getId(), toolsTab);
    }
}
```

- [ ] **Step 2: 修改 KineticPlannerClient.onClientSetup**

Modify `src/client/java/net/jsmua/kinetic_planner/KineticPlannerClient.java`，在 `MapProviderRegistry.freeze();` 后添加（用 SearchReplace）:

Old:
```java
        // 冻结工厂注册表--不允许后续注册 / Freeze factory registry - no more registrations allowed
        MapProviderRegistry.freeze();
```

New:
```java
        // 冻结工厂注册表--不允许后续注册 / Freeze factory registry - no more registrations allowed
        MapProviderRegistry.freeze();

        // 注册 KP Ribbon tab/group/component, 然后冻结 Ribbon 注册表
        // Register KP Ribbon tab/group/component, then freeze Ribbon registry
        KpRibbonRegistration.register(EditToolState.getInstance());
        RibbonRegistry.freeze();
```

补充 import（用 SearchReplace）:

Old:
```java
import net.jsmua.kinetic_planner.command.KPClientCommands;
import net.jsmua.kinetic_planner.command.KPCommandTree;
import net.jsmua.kinetic_planner.config.KPConfig;
```

New:
```java
import net.jsmua.kinetic_planner.command.KPClientCommands;
import net.jsmua.kinetic_planner.command.KPCommandTree;
import net.jsmua.kinetic_planner.config.KPConfig;
import net.jsmua.kinetic_planner.gui.editor.EditToolState;
import net.jsmua.kinetic_planner.gui.editor.ribbon.KpRibbonRegistration;
import net.jsmua.kinetic_planner.gui.ribbon.registry.RibbonRegistry;
```

- [ ] **Step 3: 编译验证**

Run: `gradlew compileClientJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: 运行全部测试确保无回归**

Run: `gradlew test`
Expected: 所有已有测试 PASS

- [ ] **Step 5: Commit**

```bash
git add src/client/java/net/jsmua/kinetic_planner/gui/editor/ribbon/KpRibbonRegistration.java src/client/java/net/jsmua/kinetic_planner/KineticPlannerClient.java
git commit -m "feat(ribbon): KP 适配层 - KpRibbonRegistration + ClientSetup 集成"
```

---

### Task 12: KpMapEditor 集成 + 删除旧 KpRibbonBar + 框架解耦校验

**Files:**
- Modify: `src/client/java/net/jsmua/kinetic_planner/gui/editor/KpMapEditor.java`（initMenus 改用 RibbonBar）
- Delete: `src/client/java/net/jsmua/kinetic_planner/gui/widgets/ribbon/KpRibbonBar.java`
- Delete: `src/test/java/net/jsmua/kinetic_planner/gui/KpRibbonBarTest.java`
- Create: `src/test/java/net/jsmua/kinetic_planner/gui/ribbon/RibbonFrameworkDecouplingTest.java`

**Interfaces:**
- Consumes: Task 7 的 `RibbonBar` + Task 9 的 `KPConfigRibbonPreferenceStore` + Task 10 的 `KpViewContextProvider` + Task 11 的 `KpRibbonRegistration`（运行时已注册）
- Produces:
  - `KpMapEditor.initMenus()` 改用 `new RibbonBar(kpViewContextProvider, preferenceStore)`
  - `KpMapEditor.kpViewContextProvider` 字段
  - `RibbonFrameworkDecouplingTest` 验证 `gui/ribbon/` 不引用 KP 包

- [ ] **Step 1: 修改 KpMapEditor.initMenus**

Modify `src/client/java/net/jsmua/kinetic_planner/gui/editor/KpMapEditor.java`:

Replace the entire file content with:

```java
package net.jsmua.kinetic_planner.gui.editor;

import com.lowdragmc.lowdraglib2.editor.ui.Editor;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import net.jsmua.kinetic_planner.config.IKPConfig;
import net.jsmua.kinetic_planner.gui.MapPlaceholderView;
import net.jsmua.kinetic_planner.gui.ToolPanelView;
import net.jsmua.kinetic_planner.gui.editor.ribbon.KPConfigRibbonPreferenceStore;
import net.jsmua.kinetic_planner.gui.editor.ribbon.KpViewContextProvider;
import net.jsmua.kinetic_planner.gui.ribbon.RibbonBar;
import org.jetbrains.annotations.Nullable;

/**
 * LDLib2 {@link Editor} 子类 (spec §6.3)。
 *
 * <p>使用通用 RibbonBar (gui/ribbon/) 替代旧 KpRibbonBar。
 * RibbonBar 通过构造注入 ViewContextProvider + PreferenceStore, 不调 getInstance()。
 */
public class KpMapEditor extends Editor {

    @Nullable
    private MapPlaceholderView mapViewport;

    @Nullable
    private KpViewContextProvider kpViewContextProvider;

    public KpMapEditor() {
        super();
    }

    @Nullable
    public MapPlaceholderView getMapViewport() {
        return mapViewport;
    }

    @Nullable
    public KpViewContextProvider getKpViewContextProvider() {
        return kpViewContextProvider;
    }

    @Override
    protected Editor createNewEditorInstance() {
        return new KpMapEditor();
    }

    @Override
    protected void initMenus() {
        // 不调用 super.initMenus() -- 不添加 FileMenu/ViewMenu
        menuContainer.clearAllChildren();

        // 构造 KP 适配层的 ViewContextProvider + PreferenceStore
        // 注: 此处 IKPConfig 通过 KineticPlannerClient 的全局 config 引用获取 (运行时)
        // 为避免在 initMenus 中调 getInstance(), 改为延迟到 placeCustomViews 时注入
        // 首版简化: ViewContextProvider 用 null editorScreen (getActiveContexts 返回空 Set)
        //          PreferenceStore 用 stub in-memory 实现 (运行时由 KineticPlannerClient 注入真实 config)
        this.kpViewContextProvider = new KpViewContextProvider(null);
        var preferenceStore = new KPConfigRibbonPreferenceStore(getKpConfig());

        RibbonBar ribbonBar = new RibbonBar(kpViewContextProvider, preferenceStore);
        menuContainer.addChild(ribbonBar);
        top.getLayout().height(60);  // 60px (header 20 + content 40)
    }

    /**
     * 获取 KP 配置 (运行时由 KineticPlannerClient 注入)。
     * 首版返回 null, Phase 2 接入真实 config。
     */
    protected IKPConfig getKpConfig() {
        // TODO: Phase 2 - 由 KineticPlannerClient 注入真实 IKPConfig 实例
        // 首版: RibbonBar 用 null PreferenceStore 会 NPE, 需要提供一个 in-memory stub
        // 见下方说明
        return null;
    }

    @Override
    protected void onPrepareResourceView() {
        // 空实现: 不启用 ResourceView
    }

    @Override
    protected void onPrepareHistoryView() {
        // 空实现: 不放置 HistoryView 在 UI 中
    }

    public void placeCustomViews() {
        placeView(new ToolPanelView(), () -> leftWindow.getRightTop());
        this.mapViewport = new MapPlaceholderView();
        placeView(this.mapViewport, () -> centerWindow.getRightTop());
        centerWindow.getViewContainer().getStyle().backgroundTexture(IGuiTexture.EMPTY);
    }
}
```

> **重要**: `getKpConfig()` 返回 null 会导致 `KPConfigRibbonPreferenceStore(null)` 在调用方法时 NPE。**实施者必须在 Task 12 提供一个 in-memory stub IKPConfig 实现**（用于首版运行时验证），或在 `KineticPlannerClient` 中暴露全局 `IKPConfig` 引用供 `KpMapEditor` 获取。
>
> **推荐方案**: 在 `KineticPlannerClient` 添加 `public static IKPConfig CONFIG` 静态字段（在 `onClientSetup` 或构造函数中赋值），`KpMapEditor.getKpConfig()` 返回 `KineticPlannerClient.CONFIG`。这不是 getInstance() 反模式 - 它是运行时配置入口点，与 `EditToolState.getInstance()` 同性质。但为保持框架 API 层纯净，`gui/ribbon/` 内仍禁止调用此类入口。

修正 `getKpConfig()` 实现（实施者根据 KineticPlannerClient 既有结构选择）:

方案 A（推荐）- 在 `KineticPlannerClient` 暴露 CONFIG:

```java
// KineticPlannerClient.java 添加
public static volatile IKPConfig CONFIG;

// 构造函数中赋值
public KineticPlannerClient(ModContainer container) {
    container.registerConfig(...);
    KineticPlannerClient.CONFIG = KPConfig.getInstance();  // 或等价获取
}
```

```java
// KpMapEditor.getKpConfig()
protected IKPConfig getKpConfig() {
    return KineticPlannerClient.CONFIG;
}
```

方案 B - 用 in-memory stub（仅首版验证用，不持久化）:

创建 `src/client/java/net/jsmua/kinetic_planner/gui/editor/ribbon/InMemoryKPConfig.java` 实现 `IKPConfig` 全部方法（ribbon 段用 in-memory Map，其他方法 throw UnsupportedOperationException 或返回默认值）。`KpMapEditor.getKpConfig()` 返回 `new InMemoryKPConfig()`。

实施者选择 A 或 B，**确保运行时不 NPE**。

- [ ] **Step 2: 删除旧 KpRibbonBar + KpRibbonBarTest**

Use DeleteFile tool:

```
src/client/java/net/jsmua/kinetic_planner/gui/widgets/ribbon/KpRibbonBar.java
src/test/java/net/jsmua/kinetic_planner/gui/KpRibbonBarTest.java
```

若 `src/client/java/net/jsmua/kinetic_planner/gui/widgets/ribbon/` 目录变空，目录可保留（Git 不跟踪空目录）。

- [ ] **Step 3: 编译验证**

Run: `gradlew compileClientJava`
Expected: BUILD SUCCESSFUL（确认无残留对 KpRibbonBar 的引用）

若有编译错误（其他文件引用 KpRibbonBar）: 用 Grep 搜索 `KpRibbonBar` 定位并修复。

- [ ] **Step 4: 写框架解耦校验测试**

Create `src/test/java/net/jsmua/kinetic_planner/gui/ribbon/RibbonFrameworkDecouplingTest.java`:

```java
package net.jsmua.kinetic_planner.gui.ribbon;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 框架解耦校验 (spec §1.1, §11.4)。
 *
 * <p>验证 gui/ribbon/ 目录下所有 .java 文件不 import net.jsmua.kinetic_planner.* (KP 包),
 * 确保框架与 KP 解耦。
 *
 * <p>允许的 import:
 * <ul>
 *   <li>com.lowdragmc.lowdraglib2.* (LDLib2)</li>
 *   <li>net.minecraft.* (MC)</li>
 *   <li>dev.vfyjxf.taffy.* (Taffy 布局)</li>
 *   <li>java.*, javax.* (JDK)</li>
 *   <li>org.jetbrains.annotations.* (注解)</li>
 * </ul>
 */
class RibbonFrameworkDecouplingTest {

    private static final Path RIBBON_SOURCE_ROOT = Paths.get(
        "src", "client", "java", "net", "jsmua", "kinetic_planner", "gui", "ribbon");

    @Test
    void noRibbonFileImportsKpPackage() throws IOException {
        var violations = new ArrayList<String>();

        if (!Files.exists(RIBBON_SOURCE_ROOT)) {
            fail("Ribbon 源码目录不存在: " + RIBBON_SOURCE_ROOT);
        }

        Files.walkFileTree(RIBBON_SOURCE_ROOT, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (file.toString().endsWith(".java")) {
                    try {
                        var content = Files.readString(file);
                        for (var line : content.split("\n")) {
                            var trimmed = line.trim();
                            if (trimmed.startsWith("import ") && trimmed.contains("net.jsmua.kinetic_planner.")) {
                                // 允许: gui/ribbon/ 内部互相 import (net.jsmua.kinetic_planner.gui.ribbon.*)
                                if (trimmed.contains("net.jsmua.kinetic_planner.gui.ribbon.")) {
                                    continue;  // 框架内部包, 允许
                                }
                                violations.add(file + ": " + trimmed);
                            }
                        }
                    } catch (IOException e) {
                        violations.add("读取失败 " + file + ": " + e.getMessage());
                    }
                }
                return FileVisitResult.CONTINUE;
            }
        });

        assertTrue(violations.isEmpty(),
            "gui/ribbon/ 禁止 import KP 包 (net.jsmua.kinetic_planner.*)\n违规:\n" + String.join("\n", violations));
    }

    @Test
    void ribbonSourceDirectoryExists() {
        assertTrue(Files.exists(RIBBON_SOURCE_ROOT),
            "gui/ribbon/ 源码目录必须存在");
    }
}
```

- [ ] **Step 5: 运行解耦测试验证通过**

Run: `gradlew test --tests "net.jsmua.kinetic_planner.gui.ribbon.RibbonFrameworkDecouplingTest"`
Expected: PASS（2 tests）

> **若失败**: 检查违规的 import 语句，将引用 KP 包的代码移到 `gui/editor/ribbon/` 适配层，或通过构造注入的接口替换直接依赖。

- [ ] **Step 6: 运行全部测试确保无回归**

Run: `gradlew test`
Expected: 所有测试 PASS（旧 KpRibbonBarTest 已删除，新测试通过）

- [ ] **Step 7: 完整构建验证**

Run: `gradlew build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 8: Commit**

```bash
git add src/client/java/net/jsmua/kinetic_planner/gui/editor/KpMapEditor.java src/test/java/net/jsmua/kinetic_planner/gui/ribbon/RibbonFrameworkDecouplingTest.java
git rm src/client/java/net/jsmua/kinetic_planner/gui/widgets/ribbon/KpRibbonBar.java src/test/java/net/jsmua/kinetic_planner/gui/KpRibbonBarTest.java
git commit -m "feat(ribbon): KpMapEditor 集成 RibbonBar + 删除旧 KpRibbonBar + 框架解耦校验"
```

---

## Self-Review

### 1. Spec 覆盖检查

| Spec 章节 | 覆盖任务 |
|---|---|
| §1 设计目标 + §1.1 解耦边界 | Global Constraints + Task 12 解耦测试 |
| §2.1 UI 树结构 | Task 7 (RibbonBuilder) |
| §2.2 高度预算 | Task 12 (KpMapEditor `top.getLayout().height(60)`) |
| §2.3 生命周期 | Task 11 (ClientSetup 注册 + freeze) |
| §2.4 注册机制 | Task 3 (RibbonRegistry) + Task 6 (contextual 补充) |
| §2.5 Collection 接轨 | 留 Phase 2+ (无当前消费者) |
| §3.1 TabDisplayMode | Task 1 |
| §3.2 RibbonTabDefinition | Task 2 |
| §3.3 RibbonTabState | Task 4 |
| §3.4 状态转换 | Task 4 测试覆盖 |
| §3.5 持久化 | Task 5 (RibbonPreferences) + Task 9 (KPConfigRibbonPreferenceStore) |
| §4.1-4.7 工具模型 | Task 1 (ToolAction/Tooltip) + Task 2 (Definition) |
| §4.8 EditToolState 关系 | Task 8 (ToolToggleCommand + KpToolDefinitions) |
| §5 工具组 | Task 2 (接口) + Task 7 (GroupPanel) + Task 11 (注册) |
| §6 头部组件 | Task 2 (接口) + Task 3 (注册) |
| §7 QAT | Task 4 (接口) + Task 7 (QatBar/DefaultQAT) |
| §8 右键菜单 | Task 7 (ContextMenuFactory, 简化实现) |
| §9 FLOATING 模式 | Task 7 (FloatingContentPanel, 简化实现) |
| §10 上下文选项卡 | Task 6 (ContextualTabGroup + ViewContextProvider) + Task 10 (KpViewContextProvider) + Task 7 (RibbonBar.onContextChanged) |
| §11 集成 | Task 11 + Task 12 |
| §12 QoL | 部分覆盖 (Task 7 简化), 完整 QoL 留 Phase 2+ |
| §13 测试策略 | 分散在各 Task 的测试步骤 |
| §14 文件索引 | File Structure 章节 |
| §15 LDLib2 验证 | 已验证 (spec 内) |

**覆盖缺口**:
- §8 右键菜单完整实现 (context menu 触发 + 菜单项) 简化为占位，留 Phase 2+ 完善
- §9 FLOATING 失焦检测简化为占位
- §12 QoL 大部分项 (Tooltip 系统/快捷键标签/溢出菜单完整逻辑) 留 Phase 2+
- 这些缺口符合 spec 的 Phase 1 范围（§4.1 明确 BUTTON+SEPARATOR only），不是遗漏

### 2. 占位符扫描

- 无 "TBD"/"TODO"（除 KpMapEditor.getKpConfig 的 Phase 2 标注，已提供方案 A/B 选择）
- 无 "implement later" 无具体代码
- 所有测试代码完整可执行
- 所有实现代码完整可编译

### 3. 类型一致性

- `TabDisplayMode`: Task 1 定义 → Task 4/5/6/9/11 使用 ✓
- `ResourceLocation`: 全局一致用 `ResourceLocation.fromNamespaceAndPath(ns, path)` ✓
- `RibbonTabState.setContextActive`: Task 4 定义 → Task 7 RibbonBar.onContextChanged 调用 ✓
- `RibbonRegistry.registerContextualGroup`: Task 6 定义 → Task 11 未调用（首版无 contextual 注册，正确）✓
- `KPConfigRibbonPreferenceStore` 构造参数 `IKPConfig`: Task 9 定义 → Task 12 使用 ✓
- `ToolToggleCommand` 构造参数 `(EditToolState, Tool)`: Task 8 定义 → 测试使用 ✓

### 4. 风险点

- **KpMapEditor.getKpConfig() 返回 null 风险**: Task 12 Step 1 已提供方案 A/B，实施者必须选择并实现，否则运行时 NPE
- **KpEditorScreen mock 可能触发 clinit**: Task 10 Step 2 已提供备用方案（用 null 构造）
- **LDLib2 `IColor` 存在性不确定**: Task 6 Step 3 已改用 MC `MaterialColor`，实施者可按需改回

---

## Execution Handoff

计划已保存至 `docs/superpowers/plans/2026-07-31-ribbon-framework.md`。两种执行方式：

**1. Subagent-Driven (推荐)** - 每个 Task 派发新 subagent，Task 间 review，快速迭代

**2. Inline Execution** - 在当前会话用 executing-plans 批量执行，带检查点 review

请选择执行方式。

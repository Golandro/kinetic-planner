# Ribbon Button-Style Toggle + Mutex Group Encapsulation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把 LDLib2 `Toggle` 封装成 Ribbon 风格的 button 样式 toggle，并把互斥组抽象从 rendering 层抽离到 group 层，使 Collection/Group/Command 三层不依赖 LDLib2 绘制细节。

**Architecture:** 新增 `api.RibbonToggleGroup` 接口 + `internal.toggle.ToggleBasedRibbonToggleGroup` 实现；新增 `internal.toggle.RibbonToggleButton` 包装 LDLib2 `Toggle`，隐藏 markIcon、按 `ToolSize` 渲染为按钮；`ToolWidgetFactory` / `GroupPanel` / `RibbonBuilder` 全部改用 `RibbonToggleGroup` 抽象；Collection 层通过 `RibbonToolGroupDefinition.getMutualExclusionGroupId()` 声明互斥关系。

**Tech Stack:** MC 1.21.1 NeoForge, LDLib2 2.2.26, JUnit 5

## Global Constraints

- `gui/ribbon/` 不得 import `net.jsmua.kinetic_planner.*`。
- LSS 不支持 CSS 变量、`border-*` 简写、`padding` 简写；颜色用 `#AARRGGBB`。
- `Button.setOnClick(UIEventListener)`，`Toggle.setOnToggleChanged(BooleanConsumer)`。
- 直接构造 `new Button()` / `new Toggle()` 会触发 `UIElement.<clinit>` → LDLib2 clinit 陷阱；纯 JVM 测试需用反射或 `@Disabled`，靠 `gradlew runClient` 验收。
- 服务端短路：`layout(...)` / `style(...)` lambda 不执行。
- 提交消息格式：`type: description`（feat/fix/refactor/docs）。

---

## File Structure

| 文件 | 操作 | 职责 |
|---|---|---|
| `src/client/java/net/jsmua/kinetic_planner/gui/ribbon/internal/toggle/RibbonToggleGroup.java` | 新增 | 互斥组抽象接口（internal/toggle 层，避免 api↔internal 循环依赖） |
| `src/client/java/net/jsmua/kinetic_planner/gui/ribbon/internal/toggle/ToggleBasedRibbonToggleGroup.java` | 新增 | 基于 LDLib2 `Toggle.ToggleGroup` 的实现 |
| `src/client/java/net/jsmua/kinetic_planner/gui/ribbon/internal/toggle/RibbonToggleButton.java` | 新增 | button 样式 Toggle 封装，处理 `ToolSize` 布局 |
| `src/client/java/net/jsmua/kinetic_planner/gui/ribbon/internal/ToolWidgetFactory.java` | 修改 | 用 `RibbonToggleButton` 创建 toggle，用 `RibbonToggleGroup` |
| `src/client/java/net/jsmua/kinetic_planner/gui/ribbon/internal/GroupPanel.java` | 修改 | 签名改为 `RibbonToggleGroup` |
| `src/client/java/net/jsmua/kinetic_planner/gui/ribbon/internal/RibbonBuilder.java` | 修改 | 按 mutex id 创建 `RibbonToggleGroup` |
| `src/client/java/net/jsmua/kinetic_planner/gui/ribbon/api/RibbonToolGroupDefinition.java` | 修改 | 增加 `getMutualExclusionGroupId()` 默认方法 |
| `src/client/java/net/jsmua/kinetic_planner/gui/ribbon/api/SimpleRibbonToolGroupDefinition.java` | 修改 | 支持 record 字段 `mutualExclusionGroupId` |
| `src/client/java/net/jsmua/kinetic_planner/gui/editor/ribbon/KpToolDefinitions.java` | 修改 | 为 Tools tab 的 5 个工具设置统一 mutex id |
| `src/client/java/net/jsmua/kinetic_planner/gui/editor/ribbon/KpRibbonRegistration.java` | 修改 | 为 Tools/Selection/Layers/Labels/Lines/Context group 按需设置 mutex id |
| `src/main/resources/assets/kinetic_planner/lss/kp.lss` | 修改 | 调整 `.kp-ribbon-tool`、`.kp-ribbon-tool.__on__`、group 间距 |
| `src/test/java/net/jsmua/kinetic_planner/gui/ribbon/internal/toggle/ToggleBasedRibbonToggleGroupTest.java` | 新增 | 互斥组反射测试 |
| `src/test/java/net/jsmua/kinetic_planner/gui/ribbon/internal/toggle/RibbonToggleButtonTest.java` | 新增 | 类签名反射测试 |
| `src/test/java/net/jsmua/kinetic_planner/gui/ribbon/internal/ToolWidgetFactoryTest.java` | 修改 | 签名反射测试改为 `RibbonToggleGroup` |
| `src/test/java/net/jsmua/kinetic_planner/gui/ribbon/internal/GroupPanelTest.java` | 修改 | 签名反射测试改为 `RibbonToggleGroup` |

---

### Task 1: RibbonToggleGroup 抽象 + ToggleBased 实现

**Files:**
- Create: `src/client/java/net/jsmua/kinetic_planner/gui/ribbon/api/RibbonToggleGroup.java`
- Create: `src/client/java/net/jsmua/kinetic_planner/gui/ribbon/internal/toggle/ToggleBasedRibbonToggleGroup.java`
- Test: `src/test/java/net/jsmua/kinetic_planner/gui/ribbon/internal/toggle/ToggleBasedRibbonToggleGroupTest.java`

**Interfaces:**
- Consumes: `com.lowdragmc.lowdraglib2.gui.ui.elements.Toggle`
- Produces: `RibbonToggleGroup` 接口，`ToggleBasedRibbonToggleGroup` 实现

- [ ] **Step 1: 新增 `RibbonToggleGroup` 接口**

> **审计修正:** 接口放在 `internal/toggle` 包（非 `api`），避免 `api → internal/toggle → api` 包级循环依赖。
> Collection 层只通过 `getMutualExclusionGroupId()` 声明互斥关系，不接触此接口。

```java
package net.jsmua.kinetic_planner.gui.ribbon.internal.toggle;

/**
 * 工具互斥组抽象（internal/toggle 层）。
 *
 * <p>仅由渲染层（ToolWidgetFactory / GroupPanel / RibbonBuilder / RibbonToggleButton）消费。
 * Collection 层不依赖此接口，只通过 RibbonToolGroupDefinition.getMutualExclusionGroupId() 声明互斥关系。
 */
public interface RibbonToggleGroup {
    /** 注册一个 button 样式 toggle；由渲染层在构造时调用。 */
    void register(RibbonToggleButton button);
}
```

- [ ] **Step 2: 新增 `ToggleBasedRibbonToggleGroup` 实现**

```java
package net.jsmua.kinetic_planner.gui.ribbon.internal.toggle;

import com.lowdragmc.lowdraglib2.gui.ui.elements.Toggle;

/**
 * 基于 LDLib2 Toggle.ToggleGroup 的 RibbonToggleGroup 实现。
 */
public final class ToggleBasedRibbonToggleGroup implements RibbonToggleGroup {
    private final Toggle.ToggleGroup delegate = new Toggle.ToggleGroup();

    @Override
    public void register(RibbonToggleButton button) {
        delegate.registerToggle(button.asToggle());
    }
}
```

- [ ] **Step 3: 写反射签名测试**

```java
package net.jsmua.kinetic_planner.gui.ribbon.internal.toggle;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ToggleBasedRibbonToggleGroupTest {

    @Test
    void implementsRibbonToggleGroup() {
        assertTrue(RibbonToggleGroup.class.isAssignableFrom(ToggleBasedRibbonToggleGroup.class),
            "ToggleBasedRibbonToggleGroup 必须实现 RibbonToggleGroup");
    }

    @Test
    void registerMethodSignatureMatchesInterface() throws NoSuchMethodException {
        var interfaceMethod = RibbonToggleGroup.class.getMethod("register", RibbonToggleButton.class);
        var implMethod = ToggleBasedRibbonToggleGroup.class.getMethod("register", RibbonToggleButton.class);
        assertEquals(interfaceMethod.getReturnType(), implMethod.getReturnType());
    }
}
```

- [ ] **Step 4: 运行测试**

Run: `gradlew test --tests "net.jsmua.kinetic_planner.gui.ribbon.internal.toggle.ToggleBasedRibbonToggleGroupTest"`

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/client/java/net/jsmua/kinetic_planner/gui/ribbon/api/RibbonToggleGroup.java
 git add src/client/java/net/jsmua/kinetic_planner/gui/ribbon/internal/toggle/ToggleBasedRibbonToggleGroup.java
 git add src/test/java/net/jsmua/kinetic_planner/gui/ribbon/internal/toggle/ToggleBasedRibbonToggleGroupTest.java
 git commit -m "feat: add RibbonToggleGroup abstraction with Toggle-based implementation"
```

---

### Task 2: RibbonToggleButton（Button 样式 Toggle 封装）

**Files:**
- Create: `src/client/java/net/jsmua/kinetic_planner/gui/ribbon/internal/toggle/RibbonToggleButton.java`
- Test: `src/test/java/net/jsmua/kinetic_planner/gui/ribbon/internal/toggle/RibbonToggleButtonTest.java`

**Interfaces:**
- Consumes: `com.lowdragmc.lowdraglib2.gui.ui.elements.Toggle`, `ToolSize`, `IGuiTexture`, `RibbonToggleGroup`
- Produces: `UIElement`（通过 `asElement()` 暴露 Toggle 包装），`asToggle()` 暴露内部 Toggle

- [ ] **Step 1: 新增 `RibbonToggleButton`**

```java
package net.jsmua.kinetic_planner.gui.ribbon.internal.toggle;

import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Toggle;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import net.jsmua.kinetic_planner.gui.ribbon.api.ToolSize;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * 把 LDLib2 {@link Toggle} 封装为 Ribbon 风格的 button 样式 toggle。
 *
 * <p>封装细节（对上层隐藏）：
 * <ul>
 *   <li>隐藏 Toggle 自带的 markIcon（绿勾）</li>
 *   <li>隐藏 Toggle 自带的 toggleLabel</li>
 *   <li>把文本/图标设置到内部的 {@link Button} 上</li>
 *   <li>根据 {@link ToolSize} 选择水平（SMALL）或垂直（LARGE）布局</li>
 *   <li>激活态通过给内部 Button 添加/移除 {@code __on__} class 驱动 LSS</li>
 * </ul>
 */
public final class RibbonToggleButton {

    private final Toggle toggle;
    private final Button button;
    private final ToolSize size;

    public RibbonToggleButton(Component text,
                              Optional<IGuiTexture> icon,
                              ToolSize size,
                              boolean initiallyActive) {
        this.toggle = new Toggle();
        this.button = toggle.toggleButton;
        this.size = size;

        // 隐藏 Toggle 自带的 label 和 勾选标记
        toggle.noText();
        toggle.markIcon.setDisplay(false);

        // 内部 button 作为真实视觉按钮
        button.setText(text);
        icon.ifPresent(button::addPreIcon);
        button.addClass("kp-ribbon-tool");

        configureLayout();

        // 初始状态（不触发回调）
        toggle.setOn(initiallyActive, false);
        updateButtonStyle(initiallyActive);
    }

    /** 返回内部 Toggle，用于注册到 ToggleBasedRibbonToggleGroup。 */
    public Toggle asToggle() {
        return toggle;
    }

    /** 返回作为 UI 树的根元素（即 Toggle 本身）。 */
    public UIElement asElement() {
        return toggle;
    }

    public ToolSize getSize() {
        return size;
    }

    public boolean isOn() {
        return toggle.isOn();
    }

    public void setOn(boolean on) {
        toggle.setOn(on);
    }

    public void setToggleGroup(@Nullable RibbonToggleGroup group) {
        if (group != null) {
            group.register(this);
        }
    }

    public void setOnToggleChanged(Consumer<Boolean> callback) {
        toggle.setOnToggleChanged(active -> {
            updateButtonStyle(active);
            callback.accept(active);
        });
    }

    private void configureLayout() {
        if (size == ToolSize.LARGE) {
            // 2 列宽，垂直：图标按钮在上，文字标签在下
            toggle.layout(layout -> {
                layout.flexDirection(FlexDirection.COLUMN);
                layout.alignItems(AlignItems.CENTER);
                layout.width(40);
                layout.height(38);
                layout.paddingAll(1);
            });
            button.layout(layout -> {
                layout.width(32);
                layout.height(24);
            });
            // 大号工具的文本以独立 Label 放在 button 下方
            button.noText();
            var label = new Label();
            label.setText(button.getText());
            label.addClass("kp-ribbon-tool-label");
            label.layout(layout -> {
                layout.height(10);
            });
            toggle.addChild(label);
        } else {
            // SMALL：水平排列，图标左文字右
            toggle.layout(layout -> {
                layout.flexDirection(FlexDirection.ROW);
                layout.alignItems(AlignItems.CENTER);
                layout.height(22);
                layout.paddingAll(1);
            });
            button.layout(layout -> {
                layout.height(18);
                layout.paddingHorizontal(4);
                layout.paddingVertical(1);
            });
        }
    }

    private void updateButtonStyle(boolean active) {
        if (active) {
            button.addClass("__on__");
        } else {
            button.removeClass("__on__");
        }
    }
}
```

- [ ] **Step 2: 写反射签名测试**

```java
package net.jsmua.kinetic_planner.gui.ribbon.internal.toggle;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Toggle;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RibbonToggleButtonTest {

    @Test
    @org.junit.jupiter.api.Disabled("UIElement.<clinit> 需要 NeoForge 运行时; 靠 gradlew runClient 验收")
    void exposesToggleAndElement() {
        var btn = new RibbonToggleButton(
            net.minecraft.network.chat.Component.literal("Test"),
            java.util.Optional.empty(),
            net.jsmua.kinetic_planner.gui.ribbon.api.ToolSize.SMALL,
            false
        );
        assertInstanceOf(Toggle.class, btn.asToggle());
        assertInstanceOf(UIElement.class, btn.asElement());
    }

    @Test
    void classExistsAndHasRequiredMethods() throws NoSuchMethodException {
        var clazz = RibbonToggleButton.class;
        clazz.getMethod("asToggle");
        clazz.getMethod("asElement");
        clazz.getMethod("setOnToggleChanged", java.util.function.Consumer.class);
    }
}
```

- [ ] **Step 3: 运行测试**

Run: `gradlew test --tests "net.jsmua.kinetic_planner.gui.ribbon.internal.toggle.RibbonToggleButtonTest"`

Expected: PASS（反射测试），运行时测试 Disabled

- [ ] **Step 4: Commit**

```bash
git add src/client/java/net/jsmua/kinetic_planner/gui/ribbon/internal/toggle/RibbonToggleButton.java
 git add src/test/java/net/jsmua/kinetic_planner/gui/ribbon/internal/toggle/RibbonToggleButtonTest.java
 git commit -m "feat: add RibbonToggleButton button-style Toggle wrapper"
```

---

### Task 3: ToolWidgetFactory 集成 RibbonToggleButton

**Files:**
- Modify: `src/client/java/net/jsmua/kinetic_planner/gui/ribbon/internal/ToolWidgetFactory.java`
- Modify: `src/test/java/net/jsmua/kinetic_planner/gui/ribbon/internal/ToolWidgetFactoryTest.java`

**Interfaces:**
- Consumes: `RibbonToggleGroup`（替代 `Toggle.ToggleGroup`），`RibbonToggleButton`
- Produces: `UIElement`（Toggle 分支返回 `RibbonToggleButton.asElement()`）

- [ ] **Step 1: 修改 `ToolWidgetFactory` 签名和实现**

```java
package net.jsmua.kinetic_planner.gui.ribbon.internal;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import net.jsmua.kinetic_planner.gui.ribbon.api.ButtonAction;
import net.jsmua.kinetic_planner.gui.ribbon.api.RibbonCommand;
import net.jsmua.kinetic_planner.gui.ribbon.api.RibbonToggleCommand;
import net.jsmua.kinetic_planner.gui.ribbon.api.RibbonToolDefinition;
import net.jsmua.kinetic_planner.gui.ribbon.api.SeparatorAction;
import net.jsmua.kinetic_planner.gui.ribbon.api.ToolAction;
import net.jsmua.kinetic_planner.gui.ribbon.api.ToolSize;
import net.jsmua.kinetic_planner.gui.ribbon.internal.toggle.RibbonToggleButton;
import net.jsmua.kinetic_planner.gui.ribbon.internal.toggle.RibbonToggleGroup;
import org.jetbrains.annotations.Nullable;

public final class ToolWidgetFactory {

    private ToolWidgetFactory() {}

    public static UIElement create(RibbonToolDefinition tool,
                                   @Nullable RibbonToggleGroup group) {
        ToolAction action = tool.getAction();
        if (action instanceof ButtonAction btn) {
            if (btn.toggle()) {
                return createToggle(tool, btn, group);
            }
            return createButton(tool, btn);
        } else if (action instanceof SeparatorAction) {
            var sep = new UIElement();
            sep.addClass("kp-ribbon-separator");
            return sep;
        }
        throw new IllegalStateException("Unknown ToolAction: " + action.getClass());
    }

    public static UIElement create(RibbonToolDefinition tool) {
        return create(tool, null);
    }

    private static UIElement createButton(RibbonToolDefinition tool, ButtonAction btn) {
        var button = new Button();
        button.setText(tool.getDisplayName());
        tool.getIcon().ifPresent(button::addPreIcon);
        button.setOnClick(event -> btn.command().execute());
        button.addClass("kp-ribbon-tool");
        applySizeLayout(button, tool.getSize());
        return button;
    }

    private static UIElement createToggle(RibbonToolDefinition tool,
                                          ButtonAction btn,
                                          @Nullable RibbonToggleGroup group) {
        if (!(btn.command() instanceof RibbonToggleCommand toggleCmd)) {
            throw new IllegalStateException("ButtonAction.toggle=true 要求 command 为 RibbonToggleCommand, 实际: "
                + btn.command().getClass());
        }
        var wrapper = new RibbonToggleButton(
            tool.getDisplayName(),
            tool.getIcon(),
            tool.getSize(),
            toggleCmd.isActive()
        );
        wrapper.setToggleGroup(group);
        wrapper.setOnToggleChanged(toggleCmd::setActive);
        return wrapper.asElement();
    }

    private static void applySizeLayout(Button button, net.jsmua.kinetic_planner.gui.ribbon.api.ToolSize size) {
        if (size == net.jsmua.kinetic_planner.gui.ribbon.api.ToolSize.LARGE) {
            button.layout(layout -> {
                layout.width(32);
                layout.height(32);
            });
        } else {
            button.layout(layout -> {
                layout.height(18);
                layout.paddingHorizontal(4);
            });
        }
    }
}
```

- [ ] **Step 2: 更新测试签名验证**

把 `ToolWidgetFactoryTest.createMethodSignatureAcceptsNullableToggleGroup()` 里的参数类型从 `Toggle.ToggleGroup` 改为 `RibbonToggleGroup`：

```java
@Test
void createMethodSignatureAcceptsNullableToggleGroup() throws NoSuchMethodException {
    var m = ToolWidgetFactory.class.getMethod("create",
        net.jsmua.kinetic_planner.gui.ribbon.api.RibbonToolDefinition.class,
        net.jsmua.kinetic_planner.gui.ribbon.internal.toggle.RibbonToggleGroup.class);
    assertEquals(com.lowdragmc.lowdraglib2.gui.ui.UIElement.class, m.getReturnType(),
        "create 必须返回 UIElement");
}
```

- [ ] **Step 3: 运行测试**

Run: `gradlew test --tests "net.jsmua.kinetic_planner.gui.ribbon.internal.ToolWidgetFactoryTest"`

Expected: PASS

- [ ] **Step 4: Commit**

```bash
git add src/client/java/net/jsmua/kinetic_planner/gui/ribbon/internal/ToolWidgetFactory.java
 git add src/test/java/net/jsmua/kinetic_planner/gui/ribbon/internal/ToolWidgetFactoryTest.java
 git commit -m "refactor: ToolWidgetFactory uses RibbonToggleButton and RibbonToggleGroup"
```

---

### Task 4: GroupPanel 与 RibbonBuilder 接入 RibbonToggleGroup

**Files:**
- Modify: `src/client/java/net/jsmua/kinetic_planner/gui/ribbon/internal/GroupPanel.java`
- Modify: `src/client/java/net/jsmua/kinetic_planner/gui/ribbon/internal/RibbonBuilder.java`
- Modify: `src/test/java/net/jsmua/kinetic_planner/gui/ribbon/internal/GroupPanelTest.java`

**Interfaces:**
- Consumes: `RibbonToggleGroup`
- Produces: `GroupPanel.build(..., RibbonToggleGroup)`，`RibbonBuilder` 按 mutex id 分组合并

- [ ] **Step 1: 修改 `GroupPanel` 签名**

```java
package net.jsmua.kinetic_planner.gui.ribbon.internal;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import net.jsmua.kinetic_planner.gui.ribbon.api.RibbonToolGroupDefinition;
import net.jsmua.kinetic_planner.gui.ribbon.internal.toggle.RibbonToggleGroup;
import org.jetbrains.annotations.Nullable;

public final class GroupPanel extends UIElement {

    private GroupPanel() {
        super();
        addClass("kp-ribbon-group");
        layout(layout -> {
            layout.flexDirection(FlexDirection.COLUMN);
            layout.alignItems(AlignItems.CENTER);
            layout.paddingAll(4);
            layout.gapAll(2);
            layout.minWidth(48);
        });
    }

    public static UIElement build(RibbonToolGroupDefinition group,
                                  @Nullable RibbonToggleGroup toggleGroup) {
        var panel = new GroupPanel();

        var toolRow = new UIElement();
        toolRow.addClass("kp-ribbon-tool-row");
        toolRow.layout(layout -> {
            layout.flexDirection(FlexDirection.ROW);
            layout.alignItems(AlignItems.CENTER);
            layout.gapAll(2);
        });
        for (var tool : group.getTools()) {
            toolRow.addChild(ToolWidgetFactory.create(tool, toggleGroup));
        }
        panel.addChild(toolRow);

        group.getDisplayName().ifPresent(name -> {
            var label = new Label();
            label.setText(name);
            label.addClass("kp-ribbon-group-label");
            label.layout(layout -> {
                layout.height(9);
            });
            panel.addChild(label);
        });

        return panel;
    }

    public static UIElement build(RibbonToolGroupDefinition group) {
        return build(group, null);
    }
}
```

- [ ] **Step 2: 修改 `RibbonBuilder` 按 mutex id 分组合并**

```java
// 在 RibbonBuilder.build 的 for (var tabDef : allTabs) 循环体内、for (var group : tabDef.getGroups()) 循环外，
// 声明 per-tab 的 mutexGroups Map（同 tab 内相同 mutex id 共享组，跨 tab 隔离）。
// 替换原来的 per-group new Toggle.ToggleGroup()
//
// import 调整：
//   import net.jsmua.kinetic_planner.gui.ribbon.internal.toggle.RibbonToggleGroup;
//   import net.jsmua.kinetic_planner.gui.ribbon.internal.toggle.ToggleBasedRibbonToggleGroup;
//   删除 import com.lowdragmc.lowdraglib2.gui.ui.elements.Toggle;

for (var tabDef : allTabs) {
    // ... (state 检查、tab 创建、content 创建)

    // per-tab mutex 组映射：同 tab 内相同 mutex id 的 group 共享一个 RibbonToggleGroup
    Map<ResourceLocation, RibbonToggleGroup> mutexGroups = new HashMap<>();

    for (var group : tabDef.getGroups()) {
        RibbonToggleGroup mutexGroup = null;
        var mutexId = group.getMutualExclusionGroupId();
        if (mutexId.isPresent()) {
            mutexGroup = mutexGroups.computeIfAbsent(
                mutexId.get(),
                k -> new ToggleBasedRibbonToggleGroup()
            );
        }
        content.addChild(GroupPanel.build(group, mutexGroup));
    }

    // ... (tab display、addTab、registerTab)
}
```

- [ ] **Step 3: 更新 `GroupPanelTest` 签名验证**

```java
@Test
void buildMethodAcceptsNullableToggleGroup() throws NoSuchMethodException {
    var m = GroupPanel.class.getMethod("build",
        net.jsmua.kinetic_planner.gui.ribbon.api.RibbonToolGroupDefinition.class,
        net.jsmua.kinetic_planner.gui.ribbon.internal.toggle.RibbonToggleGroup.class);
    assertEquals(com.lowdragmc.lowdraglib2.gui.ui.UIElement.class, m.getReturnType(),
        "build 必须返回 UIElement");
}
```

- [ ] **Step 4: 运行测试**

Run: `gradlew test --tests "net.jsmua.kinetic_planner.gui.ribbon.internal.GroupPanelTest" --tests "net.jsmua.kinetic_planner.gui.ribbon.internal.RibbonBuilderTest"`

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/client/java/net/jsmua/kinetic_planner/gui/ribbon/internal/GroupPanel.java
 git add src/client/java/net/jsmua/kinetic_planner/gui/ribbon/internal/RibbonBuilder.java
 git add src/test/java/net/jsmua/kinetic_planner/gui/ribbon/internal/GroupPanelTest.java
 git commit -m "refactor: GroupPanel and RibbonBuilder use RibbonToggleGroup"
```

---

### Task 5: Collection 层声明互斥组 + KP 定义更新

**Files:**
- Modify: `src/client/java/net/jsmua/kinetic_planner/gui/ribbon/api/RibbonToolGroupDefinition.java`
- Modify: `src/client/java/net/jsmua/kinetic_planner/gui/ribbon/api/SimpleRibbonToolGroupDefinition.java`
- Modify: `src/client/java/net/jsmua/kinetic_planner/gui/editor/ribbon/KpToolDefinitions.java`
- Modify: `src/client/java/net/jsmua/kinetic_planner/gui/editor/ribbon/KpRibbonRegistration.java`

**Interfaces:**
- Consumes: `ResourceLocation` mutex id
- Produces: `RibbonToolGroupDefinition.getMutualExclusionGroupId()`

- [ ] **Step 1: 在接口中增加默认方法**

```java
// RibbonToolGroupDefinition.java
/**
 * 互斥组 ID。同一 tab 内相同 ID 的工具组共享一个互斥 toggle 组。
 * <p>返回 Optional.empty() 表示该组工具不互斥。
 */
default Optional<ResourceLocation> getMutualExclusionGroupId() { return Optional.empty(); }
```

- [ ] **Step 2: 修改 record 实现**

```java
public record SimpleRibbonToolGroupDefinition(
    ResourceLocation id,
    Optional<Component> displayName,
    List<RibbonToolDefinition> tools,
    Optional<ResourceLocation> mutualExclusionGroupId
) implements RibbonToolGroupDefinition {
    public SimpleRibbonToolGroupDefinition(ResourceLocation id,
                                           Optional<Component> displayName,
                                           List<RibbonToolDefinition> tools) {
        this(id, displayName, tools, Optional.empty());
    }

    @Override public ResourceLocation getId() { return id; }
    @Override public Optional<Component> getDisplayName() { return displayName; }
    @Override public List<RibbonToolDefinition> getTools() { return tools; }
    @Override public Optional<ResourceLocation> getMutualExclusionGroupId() { return mutualExclusionGroupId; }
}
```

- [ ] **Step 3: 为 Tools tab 的 5 个工具设置统一 mutex id**

在 `KpToolDefinitions.java` 中新增常量：

```java
public static final ResourceLocation TOOLS_MUTEX_ID =
    ResourceLocation.fromNamespaceAndPath("kp", "mutex_tools");
```

修改 `allTools()` 返回的 group 使用新的 record 构造函数传入 `Optional.of(TOOLS_MUTEX_ID)`。如果 `KpToolDefinitions` 目前只返回 `List<RibbonToolDefinition>`，则把 mutex id 放到 `KpRibbonRegistration.registerToolsTab()` 中：

```java
var toolsGroup = new SimpleRibbonToolGroupDefinition(
    rl("kp", "group_tools"),
    Optional.of(Component.literal("Tools")),
    definitions.allTools(),
    Optional.of(rl("kp", "mutex_tools"))
);
```

- [ ] **Step 4: View tab 的开关组不设置 mutex id**

`layersGroup`、`labelsGroup`、`linesGroup`、`contextGroup` 继续使用三参数构造函数（默认不互斥）。

- [ ] **Step 5: 运行相关测试**

Run: `gradlew test --tests "net.jsmua.kinetic_planner.gui.ribbon.api.*"`

Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add src/client/java/net/jsmua/kinetic_planner/gui/ribbon/api/RibbonToolGroupDefinition.java
 git add src/client/java/net/jsmua/kinetic_planner/gui/ribbon/api/SimpleRibbonToolGroupDefinition.java
 git add src/client/java/net/jsmua/kinetic_planner/gui/editor/ribbon/KpToolDefinitions.java
 git add src/client/java/net/jsmua/kinetic_planner/gui/editor/ribbon/KpRibbonRegistration.java
 git commit -m "feat: declare mutual exclusion groups in collection layer"
```

---

### Task 6: LSS 主题与布局修复

**Files:**
- Modify: `src/main/resources/assets/kinetic_planner/lss/kp.lss`

- [ ] **Step 1: 更新 LSS**

```lss
// KP Ribbon dark theme
// 颜色 token:
//   背景紫黑:  #ff2c2c34
//   KP accent: #FF7C57D4
//   组标签灰:  #ff9ca3af
//   分隔符灰:  #ff4b5563

.kp-ribbon-bar {
    background: rect(#ff2c2c34);
    padding-all: 2;
    gap-all: 2;
}

.kp-ribbon-tab {
    base-background: rect(#00000000);
    hover-background: rect(#14555560);
    pressed-background: rect(#FF7C57D4);
    color: #ff9ca3af;
    padding-horizontal: 6;
    padding-vertical: 2;
}
.kp-ribbon-tab.__selected__ {
    base-background: rect(#FF7C57D4);
    color: #fff3efe0;
}
.kp-ribbon-tab.__hovered__ {
    color: #fff3efe0;
}

.kp-ribbon-content {
    flex-direction: row;
    align-items: center;
    height: 44;
    gap-all: 4;
    overflow-x: scroll;
}

.kp-ribbon-group {
    flex-direction: column;
    align-items: center;
    padding-all: 4;
    gap-all: 2;
    min-width: 48;
}
.kp-ribbon-group-label {
    color: #ff9ca3af;
    margin-top: 2;
    height: 9;
}
.kp-ribbon-tool-row {
    flex-direction: row;
    align-items: center;
    gap-all: 2;
}

.kp-ribbon-tool {
    base-background: rect(#803d3d42);
    hover-background: rect(#b04a4a52);
    pressed-background: rect(#807c57d4);
    color: #fff3efe0;
    border: rect(#00000000);
}
.kp-ribbon-tool.__on__ {
    base-background: rect(#FF7C57D4);
    color: #fff3efe0;
}
.kp-ribbon-tool-label {
    color: #fff3efe0;
    height: 9;
}

.kp-ribbon-qat {
    flex-direction: row;
    align-items: center;
    gap-all: 2;
    padding-horizontal: 2;
}
.kp-ribbon-qat-empty {
    color: #ff4b5563;
}

.kp-ribbon-separator {
    width: 1;
    height: 16;
    background: rect(#ff4b5563);
    margin-horizontal: 2;
}
```

- [ ] **Step 2: 确认 RibbonBuilder 给 content 加 `kp-ribbon-content` class**

在 `RibbonBuilder` 中：

```java
var content = new UIElement();
content.addClass("kp-ribbon-content");
```

- [ ] **Step 3: 编译验证 LSS 无语法错误**

Run: `gradlew compileClientJava`

Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/assets/kinetic_planner/lss/kp.lss
 git add src/client/java/net/jsmua/kinetic_planner/gui/ribbon/internal/RibbonBuilder.java
 git commit -m "style: ribbon dark theme for button-style toggles and group layout"
```

---

### Task 7: 全量编译与测试

**Files:** 全部

- [ ] **Step 1: 编译**

Run: `gradlew compileJava compileClientJava compileServerJava`

Expected: BUILD SUCCESSFUL

- [ ] **Step 2: 测试**

Run: `gradlew test`

Expected: BUILD SUCCESSFUL（除已 Disabled 的运行时用例）

- [ ] **Step 3: Commit**

```bash
git commit -a -m "test: green build after ribbon toggle encapsulation"
```

---

### Task 8: 手动验收

**Files:** 无

- [ ] **Step 1: 启动客户端**

Run: `gradlew runClient`

- [ ] **Step 2: 验证清单**

- [ ] 打开编辑器，Ribbon 顶部 tab 可切换
- [ ] Tools tab 的 Pan/Select/Line/Bezier/Snap 为按钮样式，点击其中一个后其他自动取消高亮
- [ ] 当前激活工具显示紫底白字（`.kp-ribbon-tool.__on__`）
- [ ] View tab 的 Tracks/Nodes/Labels 等开关为独立按钮式 toggle，不互斥
- [ ] 无绿勾复选框样式
- [ ] 组标签（Tools/Selection/Layers/Labels 等）不重叠
- [ ] 窗口缩窄时内容区可横向滚动，不截断文字
- [ ] QAT 仍显示图标或首字母

- [ ] **Step 3: 截图记录**

截屏保存到 `docs/superpowers/screenshots/2026-07-31-ribbon-toggle-fix/`。

- [ ] **Step 4: Commit**

```bash
git add docs/superpowers/screenshots/2026-07-31-ribbon-toggle-fix/
git commit -m "docs: ribbon toggle encapsulation manual acceptance screenshots"
```

---

## Self-Review

**1. Spec coverage:**
- ✅ Collection/Group/Command 三层分离：`RibbonToggleGroup` 抽象 + `ToggleBasedRibbonToggleGroup` 实现
- ✅ Button 样式 toggle：`RibbonToggleButton` 隐藏 markIcon、用内部 Button 做视觉
- ✅ 互斥组封装：`getMutualExclusionGroupId()` + `RibbonBuilder` 按 id 合并
- ✅ ToolSize 生效：`RibbonToggleButton.configureLayout()` 区分 LARGE/SMALL
- ✅ LSS 修复：`.kp-ribbon-tool.__on__`、group 间距、content overflow

**2. Placeholder scan:**
- 无 "TBD"/"TODO"/"稍后实现"
- 所有代码块含实际代码
- 所有测试命令含预期输出

**3. 类型一致性：**
- `RibbonToggleGroup.register(RibbonToggleButton)` 在接口、实现、`RibbonToggleButton.setToggleGroup` 中一致
- `ToolWidgetFactory.create(RibbonToolDefinition, RibbonToggleGroup)` 与 `GroupPanel.build(RibbonToolGroupDefinition, RibbonToggleGroup)` 一致
- `SimpleRibbonToolGroupDefinition` 新增字段为 `Optional<ResourceLocation>`，默认 Optional.empty()

**4. 已知风险：**
- `RibbonToggleButton` 直接访问 `Toggle.toggleButton` / `Toggle.markIcon` public 字段，依赖 LDLib2 当前 API；若 LDLib2 升级变更字段可见性，需同步调整。
- LDLib2 `Toggle.ToggleGroup` 默认 `allowEmpty=false`，Tools tab 互斥组会自动保证至少一个选中；若业务需要允许空选，需调用 `delegate.allowEmpty = true`（当前为 package-private，需放在 `com.lowdragmc.lowdraglib2.gui.ui.elements` 包内或反射）。

---

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-07-31-ribbon-button-toggle-encapsulation.md`.

Two execution options:

**1. Subagent-Driven (recommended)** - Dispatch a fresh subagent per task, review between tasks, fast iteration.

**2. Inline Execution** - Execute tasks in this session using `superpowers:executing-plans`, batch execution with checkpoints.

Which approach?
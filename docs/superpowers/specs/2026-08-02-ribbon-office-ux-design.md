# Ribbon 栏 Office 化 UX 改造设计规格

> **状态：** Draft  
> **日期：** 2026-08-02  
> **范围：** Kinetic Planner 编辑器 Ribbon 栏的右侧按钮精简、Office 化视觉、滚轮切换标签、PINNED/FLOATING/HIDDEN 模式交互  
> **前置文档：** `2026-07-31-ribbon-framework-design.md`（框架规格）  
> **文档关系：** 本规格只描述 Phase 1 已落地框架之上的 UX 层改造，不修改框架 API 契约。

---

## 1. 设计目标

在现有 Ribbon 框架基础上，把 Kinetic Planner 编辑器的 Ribbon 栏改造成更接近 Microsoft Office Ribbon 的交互与视觉体验：

1. **精简标题栏右侧**：删除 `?` 帮助按钮与 `×` 关闭按钮，只保留一个 overflow 下拉入口。
2. **整体按钮化**：tab 头、工具按钮、QAT 按钮、下拉入口全部使用统一的按钮化视觉（背景、hover、active、圆角/直角风格）。
3. **滚轮切换标签页**：鼠标在 RibbonBar 主体区域滚动时切换当前选中的 tab；在 tab 头区域滚动且 tab 溢出时优先横向滚动 tab strip。
4. **PINNED / FLOATING / HIDDEN 模式交互**：
   - 双击 tab 头在 **PINNED ↔ FLOATING** 之间切换。
   - 右键 tab 头弹出菜单，可选择 **固定 / 浮动显示 / 隐藏**。
   - overflow 下拉中新增独立的 **Ribbon Display Options** 组件入口，打开后可管理所有 tab 的显示模式与 QAT。
5. **FLOATING 模式浮层**：当 tab 处于 FLOATING 模式时，点击 tab 头在 tab 头下方弹出工具面板浮层，失焦自动收起，不占布局空间。

---

## 2. 当前状态与改造点

| 模块 | 当前状态 | 改造点 |
|---|---|---|
| `KpMapEditor.createRightHeaderWidgets()` | 创建 overflow / help / close 三个按钮 | 删除 help、close，保留 overflow |
| `ribbon.lss` | tab 头、按钮样式较朴素 | 增加 Office 风格按钮背景、hover、active、分组边框 |
| `RibbonBar` / `RibbonBuilder` | 未监听滚轮事件 | 在 `RibbonBar` 或 `TabView` 上监听滚轮，切换选中 tab |
| `RibbonTabState` | 状态机已完整 | 无需修改，直接复用 |
| `ContextMenuFactory` | 空实现 | 实现 tab 右键菜单 |
| `FloatingContentPanel` | 占位实现 | 实现浮层容器、定位、失焦收起、内容填充 |
| `RibbonBuilder` | 构建时一次性加入所有 tab content | FLOATING 模式下需要把 content 从 TabView content 容器移到浮层 |

---

## 3. 右侧按钮精简

### 3.1 变更位置

`src/client/java/net/jsmua/kinetic_planner/gui/editor/KpMapEditor.java`

### 3.2 变更内容

`createRightHeaderWidgets()` 仅返回 overflow 与关闭按钮，删除帮助按钮：

```java
private List<UIElement> createRightHeaderWidgets() {
    var rightWidgets = new ArrayList<UIElement>();

    // 1) overflow 下拉入口
    var overflowButton = new Button();
    overflowButton.setText("▼");
    overflowButton.addClass("kp-ribbon-header-button");
    overflowButton.addClass("kp-ribbon-overflow-button");
    overflowButton.layout(layout -> layout.heightPercent(100));
    overflowButton.setOnClick(event -> {
        var menu = TreeBuilder.Menu.start()
            .leaf("Quick Access Tools", () -> { /* QAT 配置面板留到 Phase 2; Phase 1 仅通过右键工具添加/移除 QAT */ })
            .crossLine()
            .subMenu("Ribbon Display Options", RibbonDisplayOptionsSubMenu.forTabs(ribbonBar))
            .crossLine()
            .leaf("Toggle Demo Context", () -> setDemoContextActive(!isDemoContextActive()));
        openMenu(event.currentElement.getPositionX(),
                 event.currentElement.getPositionY() + event.currentElement.getSizeHeight(),
                 menu);
    });
    rightWidgets.add(overflowButton);

    // 2) 关闭按钮（保留，删除 Settings 与 ? 后只剩 overflow 和关闭）
    var closeButton = new Button();
    closeButton.noText();
    closeButton.addPreIcon(Icons.WINDOW_CLOSE);
    closeButton.addClass("kp-ribbon-header-button");
    closeButton.addClass("__white_icon__");
    closeButton.layout(layout -> layout.heightPercent(100));
    closeButton.setOnClick(event -> close());
    rightWidgets.add(closeButton);

    for (var widget : rightWidgets) {
        widget.layout(layout -> layout.alignItems(AlignItems.CENTER));
    }
    return rightWidgets;
}
```

### 3.3 Ribbon Display Options 子菜单（独立组件）

新增 `RibbonDisplayOptionsSubMenu` 工具类，负责为给定 `RibbonBar` 构建"显示选项"子菜单。该子菜单在 overflow 下拉和右键 tab 菜单中复用：

```java
public final class RibbonDisplayOptionsSubMenu {
    private RibbonDisplayOptionsSubMenu() {}

    public static ITreeNode<String, Void> forTabs(RibbonBar bar) {
        var builder = TreeBuilder.Menu.start();
        for (var tab : RibbonRegistry.getTabsSortedByPriority()) {
            var state = bar.getTabState(tab.getId());
            if (state == null || state.getDisplayMode() == TabDisplayMode.CONTEXTUAL) continue;
            builder.subMenu(tab.getDisplayName().getString(), forSingleTab(bar, tab, state));
        }
        return builder.build();
    }

    private static ITreeNode<String, Void> forSingleTab(RibbonBar bar,
                                                        RibbonTabDefinition tab,
                                                        RibbonTabState state) {
        return TreeBuilder.Menu.start()
            .leaf("Pin the Ribbon",    () -> bar.setTabDisplayMode(tab.getId(), TabDisplayMode.PINNED),
                  state.getDisplayMode() != TabDisplayMode.PINNED)
            .leaf("Float the Ribbon",  () -> bar.setTabDisplayMode(tab.getId(), TabDisplayMode.FLOATING),
                  state.getDisplayMode() != TabDisplayMode.FLOATING)
            .leaf("Hide the Ribbon",   () -> bar.setTabDisplayMode(tab.getId(), TabDisplayMode.HIDDEN),
                  tab.isUserHideable() && state.getDisplayMode() != TabDisplayMode.HIDDEN)
            .build();
    }
}
```

菜单项启用/禁用通过条件控制：当前模式对应的项禁用，不可隐藏的 tab 禁用 Hide 项。
- 底部显示 QAT 工具列表（只读，未来版本支持拖拽调整）。
- 修改后立即通过 `RibbonPreferenceStore` 持久化，并触发 `RibbonBar` 重建或局部刷新。

由于 `RibbonDisplayOptionsButton` 是一个独立组件，它也可以被放到其他位置（如设置面板）。

---

## 4. Office 化视觉设计

### 4.1 设计原则

- **统一按钮化**：所有可点击元素都有明确的按钮背景。
- **状态反馈**：hover 提亮、active/selected 压暗或加边框。
- **分组明确**：工具组之间用竖线分隔或背景色块区分。
- **尊重 KpTheme**：所有色值引用 `KpTheme` 常量，通过 `KpThemeStylesheet` 插值到 LSS。

### 4.2 LSS 新增/修改规则

```lss
// ===== tab 头按钮化 =====
.kp-ribbon-tab {
    padding-horizontal: 10;
    padding-vertical: 4;
    margin-horizontal: 1;
    background: #00000000;
    border: 0;
    align-items: center;
    justify-content: center;
}
.kp-ribbon-tab:hover {
    background: #20FFFFFF;
}
.kp-ribbon-tab.__selected__ {
    background: #30FFFFFF;
    border-bottom: 2 solid #FF7C57D4;   // ACCENT
}
.kp-ribbon-tab.__pinned__ {
    border-bottom: 2 solid #FF7C57D4;
}
.kp-ribbon-tab.__floating__ {
    border-bottom: 2 dashed #FFF3EFE0;  // TEXT_PRIMARY
}

// ===== 工具按钮 Office 化 =====
.kp-ribbon-tool {
    padding-horizontal: 6;
    padding-vertical: 4;
    background: #15FFFFFF;
    border: 1 solid #10FFFFFF;
}
.kp-ribbon-tool:hover {
    background: #30FFFFFF;
    border-color: #30FFFFFF;
}
.kp-ribbon-tool:active,
.kp-ribbon-tool.__active__ {
    background: #FF7C57D4;
    border-color: #FF7C57D4;
}

// ===== 分组面板 =====
.kp-ribbon-group {
    flex-direction: column;
    align-items: center;
    padding-all: 4;
    gap-all: 2;
    min-width: 48;
    border-right: 1 solid #18FFFFFF;    // 组间竖分隔线
}
.kp-ribbon-group:last-child {
    border-right: 0;
}

// ===== QAT 按钮 =====
.kp-ribbon-qat-button {
    width: 22;
    height: 22;
    padding: 2;
    background: #15FFFFFF;
}
.kp-ribbon-qat-button:hover {
    background: #30FFFFFF;
}

// ===== overflow 按钮 =====
.kp-ribbon-overflow-button {
    width: 20;
    background: #15FFFFFF;
}
.kp-ribbon-overflow-button:hover {
    background: #30FFFFFF;
}
```

> 实际颜色值通过 `KpThemeStylesheet` 插值，LSS 文件中只写占位符或近似值；最终颜色由 `KpTheme` 常量决定。

### 4.3 代码中需要配合的 class 标记

- tab 选中：`TabView` 选中的 tab 需要自动获得 `.__selected__`（如果 LDLib2 TabView 没有自动加，则在 `RibbonBuilder` 的 `setOnTabSelected` 回调中手动切换 class）。
- tab 模式标记：在切换 PINNED/FLOATING 时，给 tab 元素添加 `.__pinned__` 或 `.__floating__` class，用于下边框样式区分。
- 工具 active：`RibbonToggleButton` 内部 button 在 toggle on 时添加 `.__active__`。

---

## 5. 滚轮切换标签页

### 5.1 交互规则

- **范围 B**：整个 `RibbonBar` 区域（包括 header 和 content）监听滚轮事件。
- **header 区域溢出优先滚动**：当鼠标位于 `tabScroller` 范围内且 tab 总数超出可视宽度时，滚轮事件优先横向滚动 `tabScroller`；否则切换选中 tab。
- **content 区域总是切换**：鼠标位于 `tabContentContainer` 区域时，滚轮直接切换 tab。
- **切换方向**：向上滚动 → 切换到左侧上一个 tab；向下滚动 → 切换到右侧下一个 tab。与 Minecraft GUI 滚动方向一致。
- **循环**：到最左/最右 tab 后继续滚动不循环（避免误操作）。

### 5.2 实现位置

在 `RibbonBar` 构造函数或 `setTabView()` 之后，给 `tabView` 添加滚轮监听器：

```java
tabView.setOnMouseScroll(event -> {
    if (isOverTabScroller(event)) {
        // 若 tabScroller 可横向滚动，则优先滚动
        if (tabScrollerCanScrollHorizontally()) {
            tabScroller.scrollX(event.scrollDelta * SCROLL_SPEED);
            return;
        }
    }
    // 否则切换 tab
    switchTabByWheel(event.scrollDelta > 0 ? -1 : 1);
});
```

> 具体事件 API 以 LDLib2 `UIEvents.SCROLL` 或 `setOnMouseScroll` 为准，实现时查阅 JavaDoc。

### 5.3 辅助方法

- `isOverTabScroller(MouseEvent event)`：判断鼠标位置是否在 `tabScroller` 的 bounds 内。
- `tabScrollerCanScrollHorizontally()`：判断 `tabScroller` 的 `contentWidth > width`。
- `switchTabByWheel(int direction)`：按方向找到下一个可见 tab，调用 `tabView.selectTab(tab)`。

---

## 6. PINNED / FLOATING / HIDDEN 模式交互

### 6.1 双击 tab 头切换 PINNED ↔ FLOATING

在 `RibbonBuilder` 为每个 `Tab` 设置双击事件：

```java
tab.setOnDoubleClick(event -> {
    var state = tabStates.get(tabDef.getId());
    if (state == null || state.getDisplayMode() == TabDisplayMode.CONTEXTUAL) return;

    var newMode = (state.getDisplayMode() == TabDisplayMode.PINNED)
        ? TabDisplayMode.FLOATING
        : TabDisplayMode.PINNED;
    bar.setTabDisplayMode(tabDef.getId(), newMode);
});
```

`RibbonBar.setTabDisplayMode(ResourceLocation id, TabDisplayMode mode)`：
1. 更新 `RibbonTabState`。
2. 更新 `RibbonPreferenceStore`。
3. 根据新模式重建或局部刷新 UI：
   - PINNED → 把该 tab 的 content 放回 `TabView` 的 `tabContentContainer`。
   - FLOATING → 把该 tab 的 content 从 `TabView` 移除，改由 `FloatingContentPanel` 托管。
   - HIDDEN → 隐藏 tab 头，内容不可访问，选中切回下一个可见 tab。

### 6.2 右键 tab 头菜单

实现 `ContextMenuFactory.forTab(RibbonBar bar, RibbonTabDefinition tab, RibbonTabState state)`：

- 若 `state.getDisplayMode() != CONTEXTUAL`：
  - `leaf("Pin the Ribbon",    () -> setMode(PINNED))`（仅当当前不是 PINNED 时启用）
  - `leaf("Float the Ribbon",  () -> setMode(FLOATING))`（仅当当前不是 FLOATING 时启用）
  - `crossLine()`
  - `leaf("Hide the Ribbon",   () -> setMode(HIDDEN))`（受 `isUserHideable()` 约束）
- 通用项：
  - `subMenu("Ribbon Display Options", RibbonDisplayOptionsSubMenu.forTabs(bar))`

> 之前规格中的独立 `RibbonDisplayOptionsPanel` 改为复用同一个子菜单组件，避免形态不统一。

### 6.3 overflow 下拉中的 Ribbon Display Options

overflow 下拉使用与右键 tab 菜单相同的 `RibbonDisplayOptionsSubMenu.forTabs(bar)` 子菜单，保持入口一致。

---

## 7. FLOATING 模式浮层

### 7.1 行为

- FLOATING tab 被选中时，`tabContentContainer` 中**不显示**该 tab 的内容。
- 点击 FLOATING tab 头：在 tab 头正下方弹出 `FloatingContentPanel`，填充该 tab 的内容。
- 浮层获得焦点；点击浮层外部任意区域：浮层收起（`contentVisible = false`）。
- 切换到其他 tab 时：当前浮层自动收起。

### 7.2 实现

修改 `FloatingContentPanel`：

```java
public final class FloatingContentPanel extends UIElement {

    public static UIElement create(RibbonBar bar, RibbonTabState tabState, UIElement content) {
        var panel = new FloatingContentPanel();
        panel.addClass("kp-ribbon-floating-panel");
        panel.layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.top(20);      // 在 tab header 下方
            layout.left(0);
            layout.widthPercent(100);
            layout.heightAuto(); // 内容决定高度，或固定 40
        });

        // 复制/引用 content
        panel.addChild(content);

        // 失焦收起
        panel.setOnBlur(event -> {
            tabState.setContentVisible(false);
            bar.hideFloatingPanel();
        });

        return panel;
    }
}
```

### 7.3 高度策略

- 浮层高度：如果内容中有固定高度的工具组，使用 `heightAuto()`；否则保持 `height(40)`。
- 为了与 PINNED 模式高度一致，建议统一为 `heightAuto()`，让内容 flex 决定。

### 7.4 边界处理

- 浮层宽度 100%（与 RibbonBar 同宽）。
- 如果浮层底部超出屏幕，向上翻转（`top` 变负）。复用 `Menu.onLayoutChanged()` 中的屏幕边界检测逻辑。

---

## 8. 数据流与状态管理

```
用户交互
   │
   ├─ 双击 tab / 右键菜单 / Display Options 面板
   │   → RibbonBar.setTabDisplayMode(id, mode)
   │       → RibbonTabState.setDisplayMode(mode)
   │       → RibbonPreferenceStore.setTabDisplayMode(id, mode)  [持久化]
   │       → RibbonBar.rebuildOrRefresh()
   │
   ├─ 滚轮
   │   → RibbonBar.switchTabByWheel(dir)
   │       → TabView.selectTab(nextTab)
   │       → RibbonPreferenceStore.setSelectedTabId(id)  [持久化]
   │
   └─ 浮层失焦
       → RibbonTabState.setContentVisible(false)
       → FloatingContentPanel.hide()
```

---

## 9. 边界情况

| 场景 | 处理 |
|---|---|
| 当前选中 tab 被 HIDDEN | 自动选中左侧/右侧下一个可见核心 tab；若全部隐藏，TabView 显示空状态 |
| FLOATING tab 被选中后又被切换为 PINNED | 收起浮层，把 content 移回 TabView |
| 所有核心 tab 都是 HIDDEN | header 只显示 QAT 和 overflow；content 区域空 |
| CONTEXTUAL tab 双击/右键模式项 | 忽略（状态机已阻止） |
| 滚轮在 tabScroller 可滚动时 | 优先滚动 strip，不切换 tab |
| 用户快速连续滚轮 | 防抖或按事件顺序处理，避免跳过 tab |

---

## 10. 测试策略

### 10.1 单元测试

- `RibbonTabState` 模式转换已覆盖，无需新增。
- 新增 `ContextMenuFactoryTest`（反射验证 `forTab` 方法存在，不触发 clinit）。
- 新增 `FloatingContentPanelTest`（反射验证 `create` 静态方法签名）。
- 新增 `RibbonDisplayOptionsButtonTest`（反射验证类存在、构造函数签名）。

### 10.2 手动验收

| 验收项 | 操作 | 预期 |
|---|---|---|
| 右侧按钮精简 | 打开编辑器 | header 右侧只有 ▼ overflow 按钮 |
| Office 视觉 | 观察 tab 和工具 | tab 有 hover/selected 背景，工具有边框和 active 色 |
| 滚轮切换 | 在 content 区滚轮 | 选中 tab 左右切换 |
| 滚轮溢出 | tab 很多时鼠标在 tab 条上滚轮 | tab 条横向滚动，不切换 tab |
| 双击切换 | 双击 tab 头 | PINNED ↔ FLOATING 切换，下边框样式变化 |
| 右键菜单 | 右键 tab 头 | 显示 Pin / Float / Hide / Ribbon Display Options |
| FLOATING 浮层 | FLOATING tab 点击 tab 头 | 下方弹出工具面板，点击外部收起 |
| Display Options | 点击 overflow → Ribbon Display Options | 打开面板，可修改各 tab 模式 |
| 持久化 | 修改模式后关闭重开 | 模式与选中 tab 恢复 |

---

## 11. 实现顺序建议

1. **右侧按钮精简**（最小改动，快速验证）。
2. **Office 化 LSS + class 标记**（纯样式，可独立验证）。
3. **滚轮切换标签页**（事件监听，可独立验证）。
4. **右键菜单 + 双击切换模式**（需要状态刷新逻辑）。
5. **FLOATING 浮层 + Display Options 面板**（最大改动，依赖前序）。

---

## 12. 相关文件索引

| 文件 | 变更类型 | 说明 |
|---|---|---|
| `gui/editor/KpMapEditor.java` | 修改 | 精简右侧按钮，调用 `RibbonDisplayOptionsButton` |
| `gui/ribbon/RibbonBar.java` | 修改 | 添加滚轮监听、模式切换、浮层面板管理 |
| `gui/ribbon/internal/RibbonBuilder.java` | 修改 | tab 双击事件、模式标记 class、content 容器管理 |
| `gui/ribbon/internal/RibbonTabViewAdapter.java` | 可能修改 | 暴露 `tabScroller` 给 `RibbonBar` 用于滚轮判断 |
| `gui/ribbon/internal/ContextMenuFactory.java` | 重写 | 实现 tab 右键菜单 |
| `gui/ribbon/internal/FloatingContentPanel.java` | 重写 | 实现浮层容器 |
| `gui/ribbon/internal/RibbonDisplayOptionsSubMenu.java` | 新增 | 独立组件：为 overflow/右键菜单构建 Display Options 子菜单 |
| `gui/ribbon/internal/RibbonDisplayOptionsPanel.java` | 删除（不创建） | 统一为子菜单形态，避免形态分裂 |
| `lss/ribbon.lss` | 修改 | Office 化样式规则 |
| `gui/theme/KpThemeStylesheet.java` | 可能修改 | 新增颜色 token 插值 |
| `gui/theme/KpTheme.java` | 可能修改 | 新增必要颜色常量 |
| `src/test/.../ribbon/*Test.java` | 新增/修改 | 签名测试更新 |

---

## 13. 决策记录

1. **关闭按钮保留**：已确认保留 `×` 关闭按钮，仅删除 Settings 和 `?` 帮助按钮。
2. **Display Options 形态**：已统一为子菜单，由 `RibbonDisplayOptionsSubMenu` 在 overflow 下拉和右键 tab 菜单中复用。

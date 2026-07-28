# Xaero Map 编辑器集成设计

> **日期：** 2026-07-27（2026-07-28 审查修订）
> **分支：** `feat/refine-ui-basis`
> **阶段：** P2 前置（UI 基础设施重构）
> **状态：** 设计已审查确认，待编写实现计划

---

## 1. 概述

### 1.1 动机

当前 KP（Kinetic Planner）在全屏地图上以 Mixin 叠加层方式渲染 Create 轨道拓扑，处于**只读观看模式**。要进入 P2 阶段（CAD 编辑工具：拾取/捕捉/绘制），需要一个完整的编辑器 UI 框架来承载工具面板、属性检查器、菜单等交互组件。

本设计在**不抛弃现有观看模式**的前提下，新增**编辑模式**：以 LDLib2 Editor 框架为壳，Xaero 地图为背景层，KP CADRenderEngine 为绘制层，构建全屏 CAD 编辑体验。

### 1.2 设计决策摘要

| 决策点 | 选择 | 理由 |
|---|---|---|
| Screen 归属 | Mixin 重定向 | KP 拦截 GuiMap 打开，改为打开 KpEditorScreen，完全控制事件层级 |
| Editor 框架 | LDLib2 Editor 类 + 精简配置 | 复用 SplittableWindow 分栏/InspectorView，禁用不需要的 ResourceView/HistoryView |
| 地图渲染 | 方案 B：cancel render + 手动调用可控部分 | 编辑模式下只渲染可控部分（瓦片+路标），抑制其余全部 Xaero UI |
| 抑制范围 | 实体雷达 + 按钮控件 + HUD 文本 | 保留路标/航点，抑制其余 Xaero UI，只留纯地图瓦片。想看附属叠叠乐？切观看模式去 |
| 面板布局 | Ribbon 顶栏 + 左工具面板 + 右属性检查器 | Ribbon "结构上自包含"；中心透明占位让地图+CAD 透过 |
| 渲染分层 | Screen 分层渲染（非 Editor 内嵌） | 中心区域天然透明（LDLib2 默认无背景），地图+CAD 在 Editor 之下分层渲染 |
| 事件转发 | 泛化 KpUIEventForwarder 返回 boolean | 复用现有成熟逻辑，编辑模式未消费时继续路由到 guiMap/CAD |
| HistoryView | 空 onPrepareHistoryView()，后端自研 undo/redo | InspectorView 不依赖 IProject（已验证）；无简单项目管理，使用 VCS + 自动保存 |
| CAD 交互枢纽 | CADRenderEngine 扩展为连接渲染/交互/事件的 hub | WorldTreeReadOverlay 提供访问器，EditLayerRenderer 使用 CADRenderEngine 渲染编辑图形 |
| closeButton | 重定向到 KpEditorScreen.onClose() | currentProject 为 null 时 askToSaveProject() 跳过对话框（已验证），exit() 调用 ModularUI.getScreen().onClose() |
| GuiMap 生命周期 | 先尝试无 Mixin，验证后按需添加 | 验证 render()/mouseClicked() 是否依赖 mc.screen == this，若不依赖则无需 Mixin |
| Provider 抽象 | 面向接口，不 instanceof | KpEditorScreen 实现 MapOverlayContextProvider 接口 |

### 1.3 核心原则

- **双模式共存**：观看模式（现有 Mixin 叠加）与编辑模式（Editor 全屏）并行存在，通过 `KpClientState.isEditMode()` 切换。
- **最小侵入**：观看模式代码路径不变，仅在 Mixin hook 中增加模式守卫。
- **中心透明**：Editor 中心区域天然透明（LDLib2 Editor/mainView/SplittableWindow/View 默认无背景，已验证）。中心区域无 UIElement 渲染 = 天然透明，无 alpha blend 混合着色。
- **事件转发复用**：泛化 KpUIEventForwarder 返回 boolean，观看模式与编辑模式共用同一事件转发逻辑。
- **面向抽象**：KpEditorScreen 通过接口暴露 GuiMap 实例，MapOverlayProvider 不做 instanceof 检查。

---

## 2. 架构总览

### 2.1 双模式架构

```
┌─────────────────────────────────────────────────────────┐
│                   Xaero's World Map                      │
│                 (打开全屏地图 keybind)                    │
└────────────────────────┬────────────────────────────────┘
                         │
              setScreen(new GuiMap(...))
                         │
              ┌──────────▼──────────┐
              │   VIEWING MODE      │ ← 现有架构，Mixin 叠加
              │   Screen = GuiMap   │
              │   CAD = Mixin hook  │
              └──────────┬──────────┘
                         │
              用户触发"进入编辑"
              (齿轮面板按钮 / /kp edit / 快捷键)
                         │
              ┌──────────▼──────────┐
              │   EDITING MODE      │ ← 新增架构，Editor 全屏
              │   Screen = KpEditor │
              │   GuiMap = 被持有    │
              │   CAD = Editor 管理  │
              └──────────┬──────────┘
                         │
              ESC / closeButton
                         │
              ┌──────────▼──────────┐
              │   VIEWING MODE      │ (或直接退出到游戏)
              │   Screen = GuiMap   │
              └─────────────────────┘
```

### 2.2 渲染层级（编辑模式）

```
┌─────────────────────────────────────────────────────┐
│ Layer 3: LDLib2 Editor UI (ModularUI)               │
│  ┌───────────────────────────────────────────────┐  │
│  │ Ribbon 菜单栏 (顶部, 不透明, top 有背景)        │  │
│  ├───────┬───────────────────────┬───────────────┤  │
│  │ Tool  │ 透明占位 (中心)        │ Inspector     │  │
│  │ Panel │   ↕ 天然透过           │ (右侧, 不透明) │  │
│  │ (左侧) │   (无 UIElement 渲染)  │               │  │
│  ├───────┴───────────────────────┴───────────────┤  │
│  │ (无底栏)                                       │  │
│  └───────────────────────────────────────────────┘  │
├─────────────────────────────────────────────────────┤
│ Layer 2: CADRenderEngine + EditLayerRenderer (全屏)  │
│   轨道拓扑 + 编辑图形 (选择/捕捉/预览/工具光标)       │
├─────────────────────────────────────────────────────┤
│ Layer 1: GuiMap 可控部分渲染 (全屏)                   │
│   地图瓦片 + 路标/航点 (Xaero UI 被 Mixin cancel)     │
├─────────────────────────────────────────────────────┤
│ Layer 0: Minecraft Screen (KpEditorScreen)           │
└─────────────────────────────────────────────────────┘
```

**透明性验证结论**（LDLib2 源码验证，2026-07-28）：

| 组件 | 默认背景 | 来源 |
|---|---|---|
| `Editor` 自身 | **无** | 构造函数仅 `addClass("__editor__")`，LSS 无 `.__editor__` background 规则 |
| `mainView` | **无** | 构造函数仅设 layout |
| `rootWindow` / `centerWindow` / `leftWindow` / `rightWindow` (SplittableWindow) | **无** | 构造函数仅设 layout + 事件监听器，`PropertyRegistry.BACKGROUND` 默认 `IGuiTexture.EMPTY` |
| `View` 子类 | **无** | `drawBackgroundTexture()` 跳过 `IGuiTexture.EMPTY` |
| `top` | **有** `Sprites.RECT_SOLID` | 构造函数内联 `.style(s -> s.backgroundTexture(...))` |

中心区域（centerWindow → MapPlaceholderView）天然透明，无需额外处理。只有 `top`（Ribbon 栏）有不透明背景，这是期望行为。最小化混合着色——中心区域零渲染、零 blend。

---

## 3. 渲染控制流

### 3.1 观看模式渲染流水线（现有 + 模式守卫）

**Screen**：`GuiMap`（Xaero 原生）

```
MC render loop
  └─ GuiMap.render(gg, mouseX, mouseY, partialTicks)
       │
       ├── [Xaero 内部] 地图瓦片 + 路标 + 雷达 + 按钮 + HUD
       │
       ├── @At("RETURN") XaeroMapRenderHook.kp$onMapRender()
       │    └─ if (KpClientState.isEditMode()) return;   ← 编辑模式跳过
       │    └─ WorldTreeReadOverlay.onMapRender(guiMap, gg, ...)
       │         └─ CADRenderEngine: tracks + nodes + edgePoints
       │
       └── @At("RETURN") XaeroMapGearButtonMixin.kp$renderConfigPanel()
            └─ if (KpClientState.isEditMode()) return;   ← 编辑模式跳过
            └─ KpGearButton.render() (always)
            └─ if (configVisible) KpUIEventForwarder.render()
```

**变更**：仅在两个现有 Mixin hook 方法头部增加 `isEditMode()` 守卫，一行代码。

### 3.2 编辑模式渲染流水线（新增）

**Screen**：`KpEditorScreen`（KP 自定义，持有 `GuiMap` 实例）

```
MC render loop
  └─ KpEditorScreen.render(gg, mouseX, mouseY, partialTicks)
       │
       ├── ① 地图层渲染
       │    └─ renderMapLayer(gg, mouseX, mouseY, partialTicks)
       │         ├── 方案 B 最佳情况: 调用 GuiMap 内部瓦片/路标渲染方法
       │         │   (具体方法名需通过反编译 GuiMap 确定)
       │         ├── 方案 B 降级情况: 若 render() 不可分离为子方法，
       │         │   降级为 @Inject 在 render() 内部多个 INVOKE 点 cancel
       │         └─ [XaeroUiSuppressMixin] @Inject at GuiMap.render HEAD
       │              └─ if (isEditMode) ci.cancel()  ← 阻止完整 render
       │
       ├── ② CAD 编辑层渲染
       │    └─ EditLayerRenderer.render(gg, guiMap, editToolState)
       │         ├── 从 WorldTreeReadOverlay 获取 getTransform() / getGeometryCache()
       │         ├── 轨道拓扑（tracks + nodes + edgePoints）← 与观看模式相同数据源
       │         └── 编辑图形（selection / snap / preview / tool cursor）← 新增
       │
       └── ③ Editor UI 层渲染
            └─ eventForwarder.render(gg, mouseX, mouseY, partialTicks)
                 └─ KpMapEditor (extends Editor)
                      ├── top: KpRibbonBar (不透明)
                      ├── mainView:
                      │    ├── leftWindow: ToolPanelView
                      │    ├── centerWindow: MapPlaceholderView (透明，无渲染)
                      │    │    ↑ ①② 层从此处天然透过
                      │    └── rightWindow: InspectorView
                      └── (bottomWindow: 不启用)
```

### 3.3 关键设计点

- **CAD 渲染分离**：观看模式 CAD 由 Mixin hook 渲染；编辑模式 CAD 由 `EditLayerRenderer` 直接调用 `CADRenderEngine` 渲染。`WorldTreeReadOverlay` 提供访问器（`getTransform()` / `getGeometryCache()`），不直接承担编辑层渲染。
- **CADRenderEngine 交互枢纽**：CADRenderEngine 扩展为连接渲染模块、交互区域 Map 模块、事件模块的 hub。编辑模式的 CAD 事件（点击/拖拽/hover）由 CADRenderEngine 统一处理。
- **Mixin hook 模式感知**：所有现有 Xaero Mixin hook 增加 `isEditMode()` 守卫，编辑模式下跳过，避免重复渲染。
- **Xaero UI 抑制（方案 B）**：`XaeroUiSuppressMixin` 在 `GuiMap.render()` HEAD cancel，阻止完整渲染。KpEditorScreen 手动调用可控部分（瓦片+路标）。若 GuiMap 的 render() 不可分离为子方法，降级为在 render() 内部多个 `@At("INVOKE")` 点 cancel 特定子调用。
- **事件转发复用**：KpEditorScreen 通过泛化后的 `KpUIEventForwarder` 转发事件到 ModularUIWidget，未消费时继续路由到 guiMap 或 CADRenderEngine。

---

## 4. 事件路由

### 4.1 观看模式事件流（现有 + 模式守卫）

```
GuiMap.mouseClicked/dragged/scrolled/keyPressed
  ├── [Xaero 内部] 地图导航（平移/缩放）
  ├── @At("HEAD") XaeroMapGearButtonMixin.kp$handleMouseClick()
  │    └─ if (isEditMode) return;   ← 编辑模式跳过
  │    └─ 齿轮按钮 / 配置面板事件处理
  └── [新增] "进入编辑" 触发点
       └─ KpClientState.setEditMode(true)
       └─ KpEditorScreen.create(guiMap)
       └─ Minecraft.setScreen(kpEditorScreen)
```

### 4.2 编辑模式事件流（新增）

```
KpEditorScreen.mouseClicked(mouseX, mouseY, button)
  ├── ① eventForwarder.mouseClicked(mouseX, mouseY, button)
  │    └─ 返回 boolean (是否消费)
  │    └─ Editor 面板命中? (Ribbon/ToolPanel/Inspector)
  │       └─ YES -> return true (消费)
  │
  ├── ② 未消费 -> 按当前工具模式路由:
  │    ├── Navigation 工具:
  │    │    └─ guiMap.mouseClicked(mouseX, mouseY, button)
  │    │       └─ GuiMap 处理地图平移/选中等导航交互
  │    │       └─ [F2 风险] 若 GuiMap 检查 mc.screen == this，需 Mixin 绕过
  │    │
  │    └── Draw/Edit 工具 (Select/Snap/Draw/Bezier...):
  │         └─ CADRenderEngine.handleClick(mouseX, mouseY, button)
  │            └─ 命中检测 -> 选择节点/边 -> 更新 InspectorView
  │
  └── return consumed

KpEditorScreen.mouseDragged(mouseX, mouseY, button, dragX, dragY)
  ├── ① eventForwarder.mouseDragged(...) -> 面板拖拽?
  ├── ② Navigation -> guiMap.mouseDragged(...) (地图平移)
  └── ② Draw -> CADRenderEngine.handleDrag(...)

KpEditorScreen.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
  ├── ① eventForwarder.mouseScrolled(...) -> 面板滚动?
  └── ② Navigation -> guiMap.mouseScrolled(...) (地图缩放)
       ② Draw -> CAD 缩放工具? 或透传给 Navigation

KpEditorScreen.keyPressed(keyCode, scanCode, modifiers)
  ├── ① eventForwarder.keyPressed(...) -> 面板快捷键?
  ├── ② 工具快捷键 (V=Select, L=Line, B=Bezier, P=Pan...)
  ├── ② ESC -> 退出编辑模式:
  │    └─ KpClientState.setEditMode(false)
  │    └─ Minecraft.setScreen(guiMap)
  └── ② Navigation -> guiMap.keyPressed(...)

KpEditorScreen.mouseMoved(mouseX, mouseY)
  ├── ① eventForwarder.mouseMoved(...) -> 面板 hover?
  └── ② Navigation -> guiMap.mouseMoved(...)
       ② Draw -> CADRenderEngine.handleHover(...) (snap 预览/工具光标位置)
```

### 4.3 工具模式

事件路由的"按当前工具模式"分支由 `ToolPanelView` 中选中的工具决定：

| 工具 | 事件路由 | 说明 |
|---|---|---|
| Navigation (Pan) | 全部转发给 `guiMap` | 地图平移/缩放，与观看模式体验一致 |
| Select | CADRenderEngine 命中检测 | 点击选择节点/边，拖拽框选 |
| Draw Line | CADRenderEngine 绘制 | 点击设起点/终点，拖拽预览 |
| Draw Bezier | CADRenderEngine 绘制 | 点击设端点+控制点，拖拽调整 |
| Snap | CADRenderEngine 捕捉 | 鼠标移动时高亮可捕捉点 |

工具模式存储于 `EditToolState`（独立于 `KpClientState`），供 `KpEditorScreen` 事件路由读取。

---

## 5. Mixin Hook 矩阵

| Mixin | 注入目标 | 观看模式 | 编辑模式 | 守卫条件 |
|---|---|---|---|---|
| `XaeroMapRenderHook` | `GuiMap.render` @RETURN | **活跃** - CAD 叠加 | **跳过** | `if (isEditMode) return;` |
| `XaeroMapGearButtonMixin` | `GuiMap.render` @RETURN | **活跃** - 齿轮+面板 | **跳过** | `if (isEditMode) return;` |
| `XaeroMapGearButtonMixin` | `GuiMap.mouseClicked` @HEAD | **活跃** - 事件拦截 | **跳过** | `if (isEditMode) return;` |
| **`XaeroUiSuppressMixin`** (新增) | `GuiMap.render` @HEAD (cancellable) | **跳过** | **活跃** - cancel 完整 render | `if (!isEditMode) return;` → `ci.cancel()` |
| **`XaeroUiSuppressMixin`** (降级) | `GuiMap.render` 内部 INVOKE 点 | **跳过** | **活跃** - cancel 特定子调用 | `if (!isEditMode) return;` → `ci.cancel()` |
| `XaeroMapAccessor` | `GuiMap` 字段 @Accessor | 活跃 | 活跃 | 无守卫，两种模式均读 camera 字段 |
| **`GuiMapRemovedMixin`** (按需) | `GuiMap.removed` @HEAD | N/A | **按需** - 抑制状态清理 | `if (isEditMode) ci.cancel();` |
| **`GuiMapInputMixin`** (按需) | `GuiMap.mouseClicked` 等 @HEAD | N/A | **按需** - 绕过 screen 检查 | `if (isEditMode) { 临时设 mc.screen; }` |

**按需 Mixin 说明**（F1/F2）：

- `GuiMapRemovedMixin`：`setScreen(kpEditor)` 触发 `guiMap.removed()`。若 `removed()` 清理状态导致后续 `render()` 不可用，则需此 Mixin。**先验证，后添加**。
- `GuiMapInputMixin`：`guiMap.mouseClicked()` 在非活跃 Screen 上调用时，若 GuiMap 检查 `mc.screen == this`，则需此 Mixin 临时绕过。**先验证，后添加**。

所有 Xaero 相关 Mixin 使用 `remap = false`，Mixin 配置 `defaultRequire: 0`。

---

## 6. 组件设计

### 6.1 新增组件清单

| 组件 | 包 | sourceSet | 职责 |
|---|---|---|---|
| `KpEditorScreen` | `editor` | client | 编辑模式 Screen，持有 GuiMap + ModularUI，管理渲染分层与事件路由 |
| `KpMapEditor` | `editor` | client | Editor 子类，配置 Ribbon/ToolPanel/Inspector/透明中心 |
| `EditToolState` | `editor` | client | 编辑工具状态管理（当前工具、选择集、绘制状态） |
| `KpRibbonBar` | `gui` | client | 自定义 Ribbon 菜单栏 UIElement（替代 Editor 内置 FileMenu/ViewMenu） |
| `ToolPanelView` | `gui` | client | 左侧工具面板 View，工具选择 + 模式切换 |
| `MapPlaceholderView` | `gui` | client | 中心透明占位 View，无渲染无事件 |
| `EditLayerRenderer` | `cadengine` | client | CAD 编辑图形层渲染器，使用 CADRenderEngine 渲染编辑图形 |
| `XaeroUiSuppressMixin` | `mixin` | client | 新 Mixin，编辑模式抑制 Xaero UI 元素（方案 B） |

### 6.2 KpEditorScreen

```java
public class KpEditorScreen extends Screen implements MapOverlayContextProvider {
    private final GuiMap guiMap;          // 从观看模式传入的 GuiMap 实例
    private KpMapEditor editor;            // Editor 子类实例（构造时创建一次）
    private ModularUI modularUI;           // LDLib2 Editor 容器
    private KpUIEventForwarder eventForwarder;  // 复用泛化后的事件转发器

    // === 生命周期 ===
    // create(GuiMap) -> 静态工厂，设置 isEditMode(true)，new KpEditorScreen(guiMap)
    //   构造时创建 editor + modularUI（避免 init() 重建丢失 View 状态）
    // init() -> modularUI.init(w, h) + 创建 eventForwarder
    //   init() 可被 MC 多次调用（resize），modularUI.init() 可安全重复调用
    // removed() -> 不在此设置 isEditMode(false)
    //   退出由 onClose() 处理（closeButton 链路 + ESC）

    // === MapOverlayContextProvider ===
    @Override
    public GuiMap getGuiMap() { return guiMap; }

    // === 渲染 ===
    // render(gg, mouseX, mouseY, partialTicks):
    //   1. renderMapLayer(gg, ...)     ← 方案 B：手动调用 GuiMap 可控部分
    //   2. EditLayerRenderer.render(gg, guiMap, EditToolState.getInstance())
    //   3. eventForwarder.render(gg, mouseX, mouseY, partialTicks)

    // === 事件（每个方法结构相同）===
    // 1. eventForwarder.xxx(...) 返回 boolean
    // 2. 若未消费，按 EditToolState 路由到 guiMap 或 CADRenderEngine

    // === 退出 ===
    // onClose():
    //   KpClientState.setEditMode(false)
    //   Minecraft.setScreen(guiMap)
    //   ← closeButton 链路: Editor.exit() -> askToSaveProject() (跳过, null project)
    //     -> ModularUI.getScreen().onClose() -> 此方法
}
```

**ModularUI Screen 绑定**：需确保 `ModularUI.getScreen()` 返回 `KpEditorScreen`。具体 API（`modularUI.setScreen(this)` 或通过 `ModularUI.of()` 传入）需在实现时验证 LDLib2 签名。

### 6.3 KpMapEditor

```java
public class KpMapEditor extends Editor {
    // === 必须实现 ===
    @Override
    protected Editor createNewEditorInstance() { return new KpMapEditor(); }

    // === 精简配置 ===
    @Override
    protected void initMenus() {
        // 不调用 super.initMenus() -- 不添加 FileMenu/ViewMenu
        // 只清除 menuContainer（保留 buttonContainer + closeButton）
        // top 布局: [icon] [menuContainer] [topPlaceholder] [buttonContainer]
        menuContainer.clearAllChildren();
        menuContainer.addChild(new KpRibbonBar());
        // 调整 top 高度（Ribbon 需 ~24px，默认可能更矮）
        top.getLayout().height(24);
    }

    @Override
    protected void onPrepareResourceView() {
        // 空实现：不启用 ResourceView
    }

    @Override
    protected void onPrepareHistoryView() {
        // 空实现：不放置 HistoryView
        // HistoryView 对象仍存在（public final 字段），InspectorView 引用其作为 historyStack
        // 但不显示在 UI 中。undo/redo 由后端 VCS 系统处理，不依赖 LDLib2 HistoryView
        // InspectorView 不依赖 IProject（已验证），null project 下正常工作
    }

    @Override
    protected void onPrepareInspectorView() {
        // 默认：inspectorView -> rightWindow
        super.onPrepareInspectorView();
    }

    // === 自定义 View 放置 ===
    // 在构造后（或 init 后）调用:
    //   placeView(new ToolPanelView(), () -> leftWindow.getRightTop());
    //   placeView(new MapPlaceholderView(), () -> centerWindow.getRightTop());
    //   (InspectorView 已由 onPrepareInspectorView 放置到 rightWindow)
    // placeView() 在 savedLayout == null 时使用 fallback Supplier（已验证）
}
```

**initMenus 陷阱**：`top.clearAllChildren()` 会移除 `buttonContainer`（含 `closeButton`）。应只清除 `menuContainer.clearAllChildren()`，保留 `buttonContainer` + `closeButton` 的重定向链路。

**closeButton 链路验证**（LDLib2 源码，2026-07-28）：

```
closeButton.click()
  -> Editor.close()
    -> (window == null) -> Editor.exit(null)
      -> askToSaveProject(onFinish)
        -> isCurrentProjectDirty()
          -> (currentProject == null) -> false   ← 跳过保存对话框
        -> 直接执行 onFinish（非 null 时）
      -> getModularUI().getScreen().onClose()
        -> KpEditorScreen.onClose()
          -> KpClientState.setEditMode(false)
          -> Minecraft.setScreen(guiMap)
```

无需 override `Editor.close()` 或 `exit()`。只需确保 `currentProject` 始终为 null（不加载项目）且 `ModularUI.getScreen()` 返回 `KpEditorScreen`。

### 6.4 KpRibbonBar

Ribbon 菜单栏是自定义 `UIElement`，不使用 LDLib2 内置的 `FileMenu`/`ViewMenu`。

**结构：**
```
KpRibbonBar (UIElement, width=100%, height=~24px, flexDirection=ROW)
├── Tab Group: "File"
│   ├── Button: New Plan
│   ├── Button: Open
│   └── Button: Save
├── Tab Group: "Tools"
│   ├── Button: Select (V)
│   ├── Button: Line (L)
│   ├── Button: Bezier (B)
│   └── Button: Pan (P)
├── Tab Group: "View"
│   ├── Button: Toggle Overlay
│   └── Button: Theme
├── Spacer (flex=1)
└── Button: Settings (齿轮，弹出配置抽屉)
```

**设计原则**："结构上自包含"--工具与命令集中在 Ribbon 中，不依赖独立菜单栏 + 工具栏的分离结构。配置面板以弹出抽屉形式从 Ribbon 触发。

### 6.5 ToolPanelView

```java
public class ToolPanelView extends View {
    // extends LDLib2 editor.ui.View，可被放置到 leftWindow
    // View 默认无背景（已验证），可按需设置不透明背景
    // 内容：工具按钮列表（图标 + 名称）
    // 选中工具时通知 EditToolState.setCurrentTool(tool)
}
```

### 6.6 MapPlaceholderView

```java
public class MapPlaceholderView extends View {
    // 透明背景：View 默认无背景（PropertyRegistry.BACKGROUND = IGuiTexture.EMPTY，
    //   drawBackgroundTexture() 跳过 EMPTY），无需额外设置
    // 无事件监听器（不消费任何事件）
    // 唯一作用：占位，让 Editor 布局正确
    // getLayout() -> widthPercent(100), heightPercent(100)（View 构造函数已设）
}
```

### 6.7 XaeroUiSuppressMixin（方案 B）

```java
@Mixin(value = GuiMap.class, remap = false)
public class XaeroUiSuppressMixin {
    // === 方案 B：cancel 完整 render，KpEditorScreen 手动调用可控部分 ===

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void kp$suppressRender(GuiGraphics gg, int mouseX, int mouseY,
                                    float partialTicks, CallbackInfo ci) {
        if (KpClientState.isEditMode()) {
            ci.cancel();  // 阻止 GuiMap.render() 完整执行
        }
    }

    // KpEditorScreen.renderMapLayer() 手动调用 GuiMap 内部的瓦片/路标渲染方法。
    // 具体方法名需通过反编译 GuiMap 确定（Phase 3 首要验证任务）。
    //
    // === 降级方案（若 render() 不可分离为子方法）===
    // 改为在 render() 内部多个 @At("INVOKE") 点 cancel 特定子调用:
    //   @Inject(method = "render", at = @At(value = "INVOKE",
    //       target = "Lxaero/map/gui/...;renderRadar(...)V"), cancellable = true)
    //   private void kp$suppressRadar(CallbackInfo ci) {
    //       if (KpClientState.isEditMode()) ci.cancel();
    //   }
    //   (类似地抑制按钮/HUD)
}
```

**方案 B 实现路径**：

1. **反编译 GuiMap**，识别 `render()` 内部结构：
   - 地图瓦片渲染：是否为独立方法？
   - 路标/航点渲染：是否为独立方法？
   - 雷达/按钮/HUD：是否为独立方法？

2. **最佳情况**（子方法可分离）：HEAD cancel + KpEditorScreen 手动调用瓦片/路标方法

3. **降级情况**（render() 不可分离）：在 render() 内部多个 INVOKE 点 cancel 特定子调用

4. **最坏情况**（render() 完全单体）：`@Overwrite`（最后手段）

**风险**：此项是整个设计中最高不确定性的环节。列为 Phase 3 首要验证任务。

### 6.8 KpClientState 扩展

```java
public final class KpClientState {
    // 现有: configPanelVisible
    // 新增:
    private static boolean editMode = false;

    public static boolean isEditMode() { return editMode; }
    public static void setEditMode(boolean mode) { editMode = mode; }
}
```

`editMode` 是全局模式标志（Mixin 守卫读取），`EditToolState` 是编辑会话状态（仅编辑模式活跃）。两者生命周期不同。

### 6.9 KpUIEventForwarder 扩展

所有事件转发方法改为返回 `boolean`（ModularUIWidget 是否消费事件）：

```java
// 修改前: public void mouseClicked(...)
// 修改后:
public boolean mouseClicked(double mouseX, double mouseY, int button) {
    return modularUI.getWidget().mouseClicked(mouseX, mouseY, button);
}
// 同理: mouseDragged, mouseScrolled, mouseReleased, keyPressed, keyReleased, charTyped
// 注意: mouseMoved 和 render 的 MC/LDLib2 签名可能不返回 boolean，需逐方法验证
```

**影响范围**：
- `XaeroMapGearButtonMixin`（观看模式）：调用处忽略返回值，行为不变
- `KineticPlannerJMPlugin`（观看模式）：同上
- `KpEditorScreen`（编辑模式）：检查返回值，未消费时继续路由
- 现有 9 个测试用例需同步更新

### 6.10 MapOverlayContextProvider 接口（面向抽象）

```java
// 新接口（或扩展 MapOverlayProvider）
public interface MapOverlayContextProvider {
    @Nullable GuiMap getGuiMap();
}

// KpEditorScreen 实现
public class KpEditorScreen extends Screen implements MapOverlayContextProvider {
    @Override
    public GuiMap getGuiMap() { return guiMap; }
}

// XaeroMapOverlayProvider 不做 instanceof KpEditorScreen
@Override
public boolean isMapOpen(Screen screen) {
    if (screen instanceof GuiMap) return true;
    if (screen instanceof MapOverlayContextProvider p && p.getGuiMap() != null)
        return KpClientState.isEditMode();
    return false;
}

// captureContext 通过接口获取 GuiMap
@Override
public Optional<MapOverlayContext> captureContext(Screen screen) {
    GuiMap guiMap = null;
    if (screen instanceof GuiMap gm) {
        guiMap = gm;
    } else if (screen instanceof MapOverlayContextProvider p) {
        guiMap = p.getGuiMap();
    }
    if (guiMap == null) return Optional.empty();
    // 读取 camera 字段 via XaeroMapAccessor...
}
```

### 6.11 EditLayerRenderer

```java
public class EditLayerRenderer {
    // 使用 CADRenderEngine 渲染编辑图形层
    // 数据来源:
    //   - WorldTreeReadOverlay.getTransform()    // 世界-屏幕变换
    //   - WorldTreeReadOverlay.getGeometryCache() // 拓扑几何缓存
    //   - EditToolState                           // 编辑状态（工具/选择/绘制）
    //
    // render(gg, guiMap, editToolState):
    //   1. 获取 transform + geometryCache
    //   2. CADRenderEngine.beginFrame()
    //   3. 渲染轨道拓扑（与观看模式相同数据源）
    //   4. 渲染编辑图形（selection / snap / preview / tool cursor）
    //   5. CADRenderEngine.endFrame()
}
```

### 6.12 CADRenderEngine 扩展（交互枢纽）

CADRenderEngine 扩展为连接三模块的 hub：

```
CADRenderEngine (hub)
├── 渲染模块（现有）
│   ├── drawLine / drawBezier / drawFilledCircle / drawFilledRect
│   └── beginFrame / endFrame / applyWorldTransform / restoreWorldTransform
├── 交互区域模块（NEW）
│   ├── hitTest(screenX, screenY) -> HitResult          // 命中检测
│   ├── getSelectionBounds() -> Rect                      // 选择框
│   └── getSnapPoints(screenX, screenY, threshold) -> List<Point>  // 捕捉点
├── 事件模块（NEW）
│   ├── handleClick(screenX, screenY, button) -> boolean
│   ├── handleDrag(screenX, screenY, button, dragX, dragY) -> boolean
│   ├── handleHover(screenX, screenY) -> boolean
│   └── handleKey(keyCode, scanCode, modifiers) -> boolean
└── 数据来源
    ├── WorldTreeReadOverlay.getTransform()    // 世界-屏幕变换
    ├── WorldTreeReadOverlay.getGeometryCache() // 拓扑几何缓存
    └── EditToolState                           // 编辑状态
```

**内聚性关注**：CADRenderEngine 从 245 行纯渲染类扩展为 3 模块枢纽。实现阶段先在 CADRenderEngine 内扩展，若超过 ~500 行再拆分为 `CADRenderEngine`（渲染）+ `CADInteractionController`（交互/事件）。避免过早抽象。

### 6.13 WorldTreeReadOverlay 扩展

WorldTreeReadOverlay 新增访问器方法，不直接承担编辑层渲染：

```java
public class WorldTreeReadOverlay {
    // 现有: onMapRender(), onClientTick(), 统计/诊断方法

    // 新增访问器（供 EditLayerRenderer 使用）
    public WorldScreenTransform getTransform() { ... }
    public GeometryCache getGeometryCache() { ... }
}
```

---

## 7. 模式切换控制流

### 7.1 观看 -> 编辑

```
1. 用户在 GuiMap 中触发"进入编辑"
   (齿轮面板中的按钮 / /kp edit 命令 / 快捷键)
2. KpClientState.setEditMode(true)
3. 获取当前 GuiMap 实例: (GuiMap) Minecraft.getInstance().screen
4. KpEditorScreen editorScreen = KpEditorScreen.create(guiMap)
   ├─ 构造时创建 KpMapEditor (Editor 子类)
   ├─ ModularUI.of(UI.of(kpMapEditor))
   ├─ 绑定 Screen: modularUI -> KpEditorScreen (API 需验证)
   └─ kpMapEditor.placeCustomViews()
5. Minecraft.setScreen(editorScreen)
   ├─ GuiMap.removed() 被调用
   │    └─ [F1 验证] 若 removed() 清理状态导致 render() 不可用
   │       → 添加 GuiMapRemovedMixin (按需)
   └─ editorScreen.init() 初始化:
      ├─ modularUI.init(screenW, screenH)  (可安全重复调用)
      └─ 创建 eventForwarder
```

### 7.2 编辑 -> 观看

```
1. 用户按 ESC 或点击 closeButton
2. ESC 路径:
   └─ KpEditorScreen.keyPressed(ESCAPE)
      └─ onClose()
   closeButton 路径:
   └─ Editor.close() -> Editor.exit()
      └─ askToSaveProject() -> (currentProject == null) -> 跳过对话框
      └─ ModularUI.getScreen().onClose()
         └─ KpEditorScreen.onClose()
3. KpEditorScreen.onClose():
   ├─ KpClientState.setEditMode(false)
   └─ Minecraft.setScreen(guiMap)
      ├─ guiMap.init() 可能被重新调用 (MC 行为)
      └─ 恢复观看模式的 Mixin hook 活跃状态
```

---

## 8. 技术风险与缓解

| # | 风险 | 影响 | 概率 | 缓解措施 |
|---|---|---|---|---|
| R1 | `GuiMap.render()` 在非活跃 Screen 上调用异常 | 编辑模式地图不渲染 | 中 | **先验证无 Mixin 是否可行**。方案 B 下 KpEditorScreen 手动调用可控部分，不依赖 MC 调用 guiMap.render()。若手动调用的子方法依赖 Screen 状态，添加按需 Mixin |
| R2 | 方案 B 需要 GuiMap 内部方法名 | 无法手动调用瓦片/路标渲染 | 高 | 反编译 GuiMap 确定 render() 内部结构。若不可分离，降级为 @At("INVOKE") cancel 特定子调用。**Phase 3 首要验证任务** |
| R3 | `GuiMap.mouseClicked` 在编辑模式转发异常 | 地图导航失效 | 中 | **先验证**。若 GuiMap 检查 `mc.screen == this`，添加 GuiMapInputMixin 临时绕过 |
| R4 | 模式切换时 GuiMap 状态丢失 | 切回观看模式后地图位置/缩放重置 | 低 | 不销毁 GuiMap 实例，仅切换 Screen 引用 |
| R5 | LDLib2 Editor `top` 高度固定 | Ribbon 需要更高空间（~24px） | 低 | `top` 是 public final UIElement，可在 initMenus() 中 `top.getLayout().height(24)` |
| R6 | ~~Editor 的 onPrepareHistoryView() 依赖 IProject~~ | ~~InspectorView undo/redo 异常~~ | ~~中~~ | **已解决**（2026-07-28 验证）：InspectorView 不依赖 IProject；askToSaveProject() 在 null project 时跳过对话框；空实现 onPrepareHistoryView() 安全 |
| R7 | Xaero 版本升级导致 Mixin 注入点失效 | 编辑模式无法抑制 UI 或无法渲染 | 高 | `defaultRequire: 0` 容错；回归测试矩阵覆盖（见 STATUS.md §3） |
| R8 | ModularUI.getScreen() 返回 null | closeButton 链路中断 | 低 | 验证 ModularUI Screen 绑定 API，确保 init() 时设置 |
| R9 | CADRenderEngine 扩展后行数过大 | 可维护性下降 | 中 | 先在 CADRenderEngine 内扩展，超过 ~500 行拆分为 CADRenderEngine + CADInteractionController |
| R10 | XaeroPlus Mixin 冲突 | 编辑模式下 XaeroPlus 功能异常 | 低 | 编辑模式下 GuiMap.render() 被 cancel，XaeroPlus 注入不执行（符合预期）。观看模式无冲突（KP 注入 RETURN，XaeroPlus 注入其他位置）。`defaultRequire: 0` 保证不崩溃 |

---

## 9. 与现有架构的关系

### 9.1 保留/扩展的组件

| 组件 | 变更 |
|---|---|
| `WorldTreeReadOverlay` | 新增 `getTransform()` / `getGeometryCache()` 访问器，不直接承担编辑层渲染 |
| `CADRenderEngine` | 扩展为交互枢纽：渲染（现有）+ 交互区域（NEW）+ 事件处理（NEW） |
| `GeometryCache` | 缓存机制不变 |
| `XaeroMapAccessor` | 字段访问器不变 |
| `KpConfigUIFactory` | 配置面板保留，作为 Ribbon 设置抽屉的内容 |
| `KpStylesheet` | LSS 主题保留，扩展支持 Editor 面板样式 |
| `KpGearButton` | 观看模式仍使用；编辑模式由 Ribbon 替代 |
| `KpUIEventForwarder` | **扩展**：转发方法返回 boolean，观看模式忽略返回值，编辑模式用于路由判断 |
| `MapOverlayDispatcher` | 扩展 `isMapOpen` 识别 MapOverlayContextProvider |
| `MapOverlayProvider` | 接口不变 |
| `XaeroMapOverlayProvider` | 扩展 `isMapOpen` / `captureContext` 通过 MapOverlayContextProvider 接口 |
| `KpClientState` | 扩展 `isEditMode()` / `setEditMode()` |

### 9.2 新增的组件

见 §6.1。

### 9.3 JourneyMap 兼容性

本设计聚焦 Xaero's World Map。JourneyMap 的编辑模式支持推迟到后续阶段：
- JM 当前无 Mixin 重定向能力（JM Plugin API 事件驱动，非 Mixin）
- 可考虑为 JM 实现类似的事件拦截机制
- 观看模式 JM 支持不受影响

### 9.4 XaeroPlus 兼容性

[XaeroPlus](https://github.com/rfresh2/XaeroPlus)（rfresh2/XaeroPlus）是客户端独立模组，重度 Mixin Xaero 全家桶（GuiMap、GuiMinimap、WorldMap 等），提供性能优化和额外功能。活跃支持 1.21.1 NeoForge（最新版 2.35.0+neoforge-1.21.1）。

**它不是库/依赖**——是终端消费者模组，不对外提供 API 或钩子供其他模组复用。

| 维度 | 观看模式 | 编辑模式 |
|---|---|---|
| Mixin 注入点竞争 | KP 注入 `GuiMap.render` RETURN，XaeroPlus 注入其他位置/同位置。`defaultRequire: 0` 保证不崩溃 | KP cancel `GuiMap.render`（方案 B），XaeroPlus 的注入**不执行**——符合预期 |
| 事件冲突 | 无（KP 仅在 RETURN 渲染叠加层） | 无（KpEditorScreen 拦截所有事件，GuiMap 事件 Mixin 被 KP 守卫跳过） |
| XaeroPlus 功能 | 正常工作 | **不工作**（预期行为：想看附属叠叠乐？切观看模式去） |

**结论**：兼容性无硬冲突。无需额外适配。建议在 STATUS.md 兼容性矩阵中添加 XaeroPlus 条目。

---

## 10. 测试策略

### 10.1 单元测试（JUnit 5 纯 JVM）

| 测试目标 | 测试类 | 验证点 |
|---|---|---|
| `KpClientState.isEditMode()` | `KpClientStateTest` (扩展) | 模式切换状态正确 |
| `EditToolState` | `EditToolStateTest` | 工具切换、选择集管理 |
| `KpMapEditor` 布局配置 | `KpMapEditorTest` (Mockito) | initMenus 只清除 menuContainer（保留 buttonContainer）；onPrepareResourceView/HistoryView 为空 |
| `KpRibbonBar` 构建 | `KpRibbonBarTest` (Mockito) | 按钮数量、回调注册 |
| `ToolPanelView` 构建 | `ToolPanelViewTest` (Mockito) | View 创建、工具列表 |
| `MapPlaceholderView` | `MapPlaceholderViewTest` | 无背景纹理、无事件监听器 |
| `KpUIEventForwarder` 返回值 | `KpUIEventForwarderTest` (扩展) | 转发方法返回 boolean；未消费时返回 false |
| `MapOverlayContextProvider` | `MapOverlayContextProviderTest` | KpEditorScreen 通过接口暴露 GuiMap |

### 10.2 运行时手动验收

| 验收项 | 操作 | 预期结果 |
|---|---|---|
| 观看模式不受影响 | 打开 Xaero 地图 | CAD 叠加层正常显示，齿轮按钮正常 |
| **F1: GuiMap 生命周期** | 进入编辑模式 | GuiMap.removed() 后 guiMap 渲染方法仍可调用（或按需添加 Mixin） |
| **F2: GuiMap 事件转发** | 编辑模式选 Pan 工具 | guiMap.mouseClicked() 在非活跃 Screen 上正常工作（或按需添加 Mixin） |
| 进入编辑模式 | 观看模式下触发"进入编辑" | Screen 切换到 KpEditorScreen，Ribbon + 面板显示 |
| 地图渲染 | 编辑模式 | 地图瓦片 + 路标可见，无雷达/按钮/HUD |
| CAD 渲染 | 编辑模式 | 轨道拓扑可见，编辑图形（选择/预览）正常 |
| 地图导航 | 编辑模式选 Pan 工具 | 拖拽平移、滚轮缩放正常 |
| 工具切换 | 点击 ToolPanel 工具 | 事件路由切换，Inspector 更新 |
| closeButton | 点击 Editor 关闭按钮 | 切回观看模式（无保存对话框） |
| 退出编辑模式 | ESC | 切回观看模式，地图状态保持 |
| Xaero 升级回归 | 更新 Xaero 版本 | Mixin 注入成功，无崩溃 |
| XaeroPlus 共存 | 同时安装 XaeroPlus | 观看模式 XaeroPlus 功能正常；编辑模式 XaeroPlus 不干扰 |

---

## 11. 包结构影响

```
net.jsmua.kinetic_planner
├── editor/                          # NEW: 编辑模式基础设施
│   ├── KpEditorScreen.java          # 编辑模式 Screen（implements MapOverlayContextProvider）
│   ├── KpMapEditor.java             # Editor 子类（精简配置）
│   └── EditToolState.java           # 工具/选择/绘制状态管理
├── gui/                             # NEW: UI 组件
│   ├── KpRibbonBar.java             # Ribbon 菜单栏 UIElement
│   ├── ToolPanelView.java           # 工具面板 View
│   └── MapPlaceholderView.java      # 透明占位 View
├── cadengine/                       # 扩展: CAD 渲染 + 交互枢纽
│   ├── CADRenderEngine.java         # 扩展: 渲染 + 命中检测 + 事件处理
│   ├── EditLayerRenderer.java       # NEW: 编辑图形层渲染器
│   ├── LineGeometry.java            # (不变)
│   ├── BezierTessellator.java       # (不变)
│   ├── Theme.java                   # (不变)
│   └── GLStateGuard.java            # (不变)
├── config/
│   ├── KpClientState.java           # 扩展: +isEditMode() / +setEditMode()
│   ├── KpUIEventForwarder.java      # 扩展: 转发方法返回 boolean
│   └── ...
├── mapadapter/
│   ├── MapOverlayContextProvider.java # NEW: 接口（暴露 GuiMap）
│   ├── MapOverlayProvider.java      # 接口不变
│   ├── XaeroMapOverlayProvider.java # 扩展: 通过接口获取 context
│   └── ...
├── instrument/
│   └── WorldTreeReadOverlay.java    # 扩展: +getTransform() +getGeometryCache()
├── mixin/
│   ├── XaeroMapRenderHook.java      # 扩展: +isEditMode() 守卫
│   ├── XaeroMapGearButtonMixin.java # 扩展: +isEditMode() 守卫
│   ├── XaeroUiSuppressMixin.java    # NEW: 方案 B UI 抑制
│   ├── GuiMapRemovedMixin.java      # NEW (按需): 抑制 removed() 清理
│   ├── GuiMapInputMixin.java        # NEW (按需): 绕过 screen 检查
│   └── ...
└── ... (现有包不变)
```

---

## 12. 实现优先级建议

| Phase | 内容 | 验收标准 | 关键验证项 |
|---|---|---|---|
| **1** | KpClientState.isEditMode() + 模式守卫 | 编译通过，观看模式不受影响 | - |
| **2** | KpEditorScreen + KpMapEditor 壳 + 透明中心 | Editor UI 显示，中心透明 | ModularUI Screen 绑定 API 验证；menuContainer vs top 清除策略 |
| **3** | GuiMap 嵌入 + XaeroUiSuppressMixin | 地图瓦片+路标可见，无雷达/按钮/HUD | **F1: GuiMap.removed() 生命周期验证**（最高优先）；**F2: GuiMap.mouseClicked() 非活跃 Screen 验证**；**R2: GuiMap 反编译确定方案 B 可行性** |
| **4** | KpRibbonBar + ToolPanelView + EditToolState | Ribbon 工具切换，EditToolState 管理工具 | - |
| **5** | 事件路由 + KpUIEventForwarder 泛化 | Navigation 工具下地图导航正常 | 转发方法返回值测试更新；F2 事件转发验证 |
| **6** | CADRenderEngine 扩展 + EditLayerRenderer | 选择/捕捉/预览编辑图形 | CADRenderEngine 行数监控（~500 行阈值） |

每个 Phase 可独立验收，Phase 1-3 为最小可用（地图在 Editor 中显示），Phase 4-6 逐步增加编辑能力。

**Phase 3 是关键路径**——XaeroUiSuppressMixin 方案 B 的可行性（R2）和 GuiMap 生命周期（F1/F2）决定了整个编辑模式的成败。

---

## 13. LDLib2 API 验证记录（2026-07-28）

以下结论基于 LDLib2 2.2.26 源码（`G:\Mods\LDLib2`）验证：

| API | 验证结论 |
|---|---|
| `Editor` 继承 `UIElement` | ✅ `public abstract class Editor extends UIElement` |
| `top` / `mainView` / `rootWindow` / `leftWindow` / `centerWindow` / `rightWindow` | ✅ 均为 `public final`，可直接访问 |
| `createNewEditorInstance()` | ✅ 唯一 abstract 方法 |
| `initMenus()` 默认添加 FileMenu/ViewMenu 到 menuContainer | ✅ 可 override 不调用 super |
| `onPrepareResourceView()` / `onPrepareHistoryView()` / `onPrepareInspectorView()` | ✅ protected，可 override 为空 |
| `placeView(View, Supplier<ViewContainer>)` | ✅ savedLayout == null 时使用 fallback |
| Editor/mainView/SplittableWindow/View 默认无背景 | ✅ `PropertyRegistry.BACKGROUND` 默认 `IGuiTexture.EMPTY`，`drawBackgroundTexture()` 跳过 |
| `top` 有内联背景 `Sprites.RECT_SOLID` | ✅ 期望行为（Ribbon 栏不透明） |
| `closeButton` → `Editor.close()` → `Editor.exit()` → `askToSaveProject()` | ✅ `currentProject == null` 时 `isCurrentProjectDirty()` 返回 false，跳过对话框 |
| `Editor.exit()` → `getModularUI().getScreen().onClose()` | ✅ 需确保 ModularUI 绑定了 KpEditorScreen |
| InspectorView 不依赖 IProject | ✅ 通过 `inspect(IConfigurable)` 接收数据，不引用 `currentProject` |
| InspectorView 构造时引用 `editor.getHistoryView()` | ✅ HistoryView 是 `public final` 字段，始终存在（即使不放置） |
| `ModularUI.init(w, h)` 可重复调用 | ✅ 用于 resize 重新初始化布局 |

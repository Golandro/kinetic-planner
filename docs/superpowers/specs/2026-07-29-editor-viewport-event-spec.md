# Editor 视口与事件路由需求规格总结

> **范围：** 本规格总结当前 Kinetic Planner 编辑器（`KpEditorScreen` + `KpMapEditor`）在视口管理、事件路由、地图导航与 CAD 视图叠加方面的已确认需求、约束与待决策点。
> **状态：** 基于 2026-07-29 调试会话与代码审查整理，尚未定稿。

---

## 1. 整体目标

为 Kinetic Planner 的编辑模式建立一套清晰、可扩展的视口与事件系统：

- 用户通过 `/kp edit` 进入编辑模式后，屏幕被 Xaero 地图瓦片层、CAD 矢量层、LDLib2 UI 层共同构成。
- 鼠标/键盘事件必须按正确优先级分发：LDLib2 UI 控件优先 → 主视口地图/CAD 交互 → 绝不泄漏给被隐藏的原生 Xaero UI 按钮。
- 主视口是一个可 Tab 化的透明占位区域；未来可在同一窗口添加分析面板等附加视图。
- CAD 视图以全屏坐标渲染，但必须能够根据主视口的相对位置进行裁剪和命中测试（支持未来多视口/多 CAD 图层）。
- 地图相机控制只在主视口内生效，且手感须与 Xaero 原生地图 1:1。

---

## 2. 分层与坐标空间

### 2.1 渲染层（从底到顶）

| 层级 | 内容 | 坐标空间 | 说明 |
|---|---|---|---|
| Layer 0 | Xaero `GuiMap` 瓦片 | 世界坐标（由 `cameraX/cameraZ/scale` 决定） | 全屏渲染，编辑模式下继续由 Xaero 底层绘制 |
| Layer 1 | CAD 编辑矢量（轨道、控制点、辅助线） | 世界坐标 → 屏幕坐标 | 由 `CADRenderEngine` 全屏绘制，但依赖当前活动视口的投影参数 |
| Layer 2 | LDLib2 Editor UI（Ribbon、工具栏、检视面板、主视口占位） | 屏幕/本地坐标 | 提供控件与布局框架；主视口区域必须完全透明 |

### 2.2 坐标空间定义

- **屏幕坐标（Screen Coordinate）**：以 Minecraft 窗口左上角为原点，单位像素，对应 `Screen.mouseClicked(mouseX, mouseY)` 传入的坐标。
- **视口本地坐标（Viewport Local Coordinate）**：以某个视口（如 `MapPlaceholderView`）内容区左上角为原点的坐标。
- **世界坐标（World Coordinate）**：Minecraft 方块坐标（XZ 平面），由 Xaero `cameraX/cameraZ/scale` 与屏幕坐标互相转换。

**关键约束：**

- `MapPlaceholderView` 作为中间主视口，向 CAD 层和导航层输出**屏幕坐标**，而不是视口本地坐标。
- 原因：CAD 图层当前以全屏方式渲染（Phase 6 设计），必须知道事件/对象在屏幕上的绝对位置；若输出本地坐标，多视口或浮动窗口场景下会出现捕获错误。
- 当未来支持多视口时，由统一的事件调度器维护“事件属于哪个视口”，但事件数据本身仍以屏幕坐标携带。

---

## 3. 主视口（MapPlaceholderView）需求

### 3.1 基础行为

- `MapPlaceholderView` 是 `centerWindow` 中一个 `View`，标题/图标暂时为 "view" 或 "Main Viewport"。
- 背景必须显式置为透明（`IGuiTexture.EMPTY`），不能依赖 `View` 默认行为。
- 保留命中测试能力（`allowHitTest = true`），使其可以作为事件范围限制的依据。
- 不注册具体 UI 事件监听器；导航与 CAD 路由统一由 `KpEditorScreen` 处理，避免事件被 LDLib2 内部判定为“已消费”而截断。

### 3.2 Tab 化能力

- `centerWindow` 的 `ViewContainer` 保留 tab 栏，未来可在同一窗口添加更多 tab（如分析面板、属性表）。
- 当前主视口 tab 唯一且位置固定，不能被关闭或拖拽到其他窗口。
- 分析面板等新增 tab 可以“挤占”主视口区域，即切换 tab 后主视口隐藏，分析面板显示在同一内容区。
- 当主视口 tab 激活时，其内容区才是有效的地图/CAD 交互区域。

### 3.3 多视口预留（非当前实现）

- 当前阶段只存在一个主视口。
- CAD 图层在架构上允许“多开”，但 2026-07-29 阶段仍按单实例实现。
- 未来若通过 Mixin 技术实现多个 `GuiMap` 实例（MDI），需要重新设计视口-相机绑定关系；当前代码不应把“视口 == Xaero GuiMap”写死。

---

## 4. 事件路由需求

### 4.1 总体优先级

事件进入 `KpEditorScreen` 后按以下顺序处理：

1. **LDLib2 UI 树优先**：通过 `KpUIEventForwarder` 转发给 `ModularUI`。Ribbon 按钮、工具栏按钮、检视面板控件等在此消费。
2. **主视口交互层**：若 UI 树未消费，且事件落在 `MapPlaceholderView` 内容区内，则根据当前工具模式分发：
   - `NAVIGATION` 工具：地图相机平移/缩放。
   - `SELECT/LINE/BEZIER/SNAP` 等工具：CAD 命中检测与编辑操作（Phase 6）。
3. **背景兜底**：若事件既不在任何 UI 控件上，也不在主视口内，则不做任何地图/CAD 操作（例如点击 Ribbon 外空白区域）。

### 4.2 必须屏蔽的输入泄漏

- 编辑模式下，`GuiMap` 的原生 UI 按钮（如左上角设置按钮、右上角关闭按钮）已被 `XaeroUiSuppressMixin` 隐藏渲染，但其 `mouseClicked` 仍可能响应点击。
- 因此，`KpEditorScreen` 在编辑模式下**禁止直接调用** `guiMap.mouseClicked(double, double, int)`、`guiMap.mouseDragged(...)`、`guiMap.mouseScrolled(...)` 等会触发 Xaero UI 逻辑的方法。
- 所有地图相机控制必须通过 `XaeroMapAccessor` 直接读写 `cameraX/cameraZ/scale` 字段实现。

### 4.3 鼠标事件细化

#### 4.3.1 平移（拖拽）

- 触发条件：当前工具为 `NAVIGATION`、鼠标左键（`button == 0`）、按下点落在主视口内容区内。
- 一旦开始拖拽，即使鼠标移出主视口内容区，仍继续平移，直到释放左键。
- 平移公式须保证屏幕像素移动量与底图视觉移动量 1:1。
- 中键、右键在 `NAVIGATION` 工具下不触发平移；中键移动为未来扩展功能预留。

#### 4.3.2 缩放（滚轮）

- 触发条件：当前工具为 `NAVIGATION`、鼠标指针位于主视口内容区内。
- 滚轮向上/向下对应 Xaero 默认的放大/缩小方向。
- 缩放值需限制在合理范围，避免无限放大或缩到不可见。
- 缩放以当前 `cameraX/cameraZ` 为中心进行，不出现明显偏移（若 Xaero 的 scale 语义导致需要额外偏移补偿，应在此统一处理）。

#### 4.3.3 CAD 工具命中

- `SELECT/LINE/BEZIER/SNAP` 等工具在主视口内的点击/拖拽/释放用于 CAD 编辑。
- CAD 命中检测使用屏幕坐标，由 `WorldScreenTransform` 转换为世界坐标后查询 `GeometryCache`。
- 命中检测只在主视口内容区内进行；主视口外的点击不触发 CAD 操作。

### 4.4 键盘事件

- 当前阶段键盘事件仍由 `KpEditorScreen` 统一处理（ESC 退出编辑模式等）。
- 未来方向键导航：当主视口获得焦点且工具为 `NAVIGATION` 时，方向键按固定世界距离平移相机。
- 对象聚焦： future 命令/快捷键将相机移动到选定对象中心，需保证缩放级别合适。

---

## 5. 地图相机 1:1 手感需求

### 5.1 字段语义

- `XaeroMapAccessor.kp$scale()` 返回的 `scale` 字段表示 **pixels per block**（每方块对应像素数）。
- 因此：
  - `scale` 越大 → 每个方块占用的像素越多 → 视觉上“ zoom 越近”。
  - 屏幕像素 delta 转换为世界坐标 delta 的公式为：`worldDelta = screenDelta / scale`。

### 5.2 手感验证标准

- 在编辑模式下，使用 `NAVIGATION` 工具在主视口内水平拖拽 100 像素，底图上的某个特征点也应在屏幕上水平移动 100 像素（即相机反向移动 100 / scale 个方块）。
- 滚轮缩放时，鼠标指针指向的世界点应尽可能保持在指针下方，不出现明显漂移。

---

## 6. Workspace 布局需求（待决策）

### 6.1 当前问题

- `KpMapEditor` 当前使用 LDLib2 `Editor` 默认的 `SplittableWindow` 做递归二分布局。
- `SplittableWindow` 基于 Flex：父容器按比例分配空间。
- 导致的问题：调整右侧面板宽度时，中间窗口变小 → 上一级 horizontal split 的分配变化 → 左侧面板也被连带压缩。

### 6.2 目标布局

- 需要类似 CSS Grid 的固定行列布局：
  - 顶行：`KpRibbonBar`，固定高度。
  - 中行：三列布局——左侧 `ToolPanelView`（固定宽度） | 中间 `MapPlaceholderView`（填充剩余） | 右侧 Inspector（固定宽度）。
  - 底行：状态栏（可选），固定高度。
- 调整右侧宽度时，只改变中间列宽度，左侧列完全不受影响。
- 调整左侧宽度时，只改变中间列宽度，右侧列完全不受影响。

### 6.3 实现路径待决策

- 选项 A：使用 LDLib2 内置的 Grid 布局（调试器已显示“网格布局”选项）。
  - 风险：需要确认 1.21 分支 Grid API 是否完整、是否支持运行时拖拽调整列宽。
- 选项 B：绕过 `Editor` 默认的 `rootWindow/leftWindow/centerWindow/rightWindow`，自己构建根 `Grid` 布局，把 `Editor` 当作轻量级容器。
  - 风险：需要重新实现或绕过 `Editor` 的窗口管理、拖拽 tab、布局保存等默认行为。
- 选项 C：保留 `SplittableWindow`，但修改 splitter 行为，使其不再按父容器比例分配，而是按像素绝对值分配。
  - 风险：`SplittableWindow` 的内部实现可能不支持这种改动。

**结论：** Grid 化是结构性改动，需要单独设计任务与验证，不在本规格总结的执行范围内。

---

## 7. 待明确问题

1. **Xaero 滚轮缩放的具体步长与中心点保持算法**：当前使用线性 `scale += scrollY * step`，若运行时出现缩放漂移，需改为与 Xaero 内部一致的算法。
2. **方向键平移的世界距离**：每次按键移动多少方块？是否随 scale 变化？
3. **对象聚焦的缩放策略**：聚焦到对象时，目标 scale 如何计算？
4. **多 CAD 图层的命中优先级**：若未来多个 CAD 图层叠加，点击时按何种顺序命中？
5. **Grid 布局 API 可行性**：需调研 LDLib2 1.21 分支 `Grid` 布局的完整能力与示例。

---

## 8. 已确认不再采用的方案

- **直接调用 `guiMap.mouseClicked` 等**：会导致 Xaero 原生 UI 输入泄漏（如打开设置面板）。已改为通过 `XaeroMapAccessor` 直接写字段。
- **完全移除主视口占位 View 并禁用 `centerWindow` 命中测试**：虽能掏空视口，但失去 Tab 化扩展能力。已恢复 `MapPlaceholderView` 作为透明占位。
- **使用 `scale` 直接作为 blocks per pixel**：会导致平移方向相反或速度错误。已确认 `scale` 为 pixels per block。

---

## 9. 相关文件索引

| 文件 | 职责 |
|---|---|
| `src/client/java/.../gui/editor/KpEditorScreen.java` | 编辑模式 Screen 壳，事件总入口，自定义地图导航实现 |
| `src/client/java/.../gui/editor/KpMapEditor.java` | LDLib2 Editor 子类，负责 Ribbon/ToolPanel/主视口/Inspector 的布局 |
| `src/client/java/.../gui/MapPlaceholderView.java` | 中心主视口透明占位 View |
| `src/client/java/.../gui/event/KpUIEventForwarder.java` | MC 事件 → LDLib2 UI 事件转发 |
| `src/client/java/.../mixin/XaeroMapAccessor.java` | 访问 Xaero `GuiMap` 的 `cameraX/cameraZ/scale` |
| `src/client/java/.../mixin/XaeroUiSuppressMixin.java` | 编辑模式下隐藏 Xaero 原生 UI 渲染 |
| `src/client/java/.../gui/editor/EditToolState.java` | 当前编辑工具状态 |
| `src/client/java/.../instrument/WorldTreeReadOverlay.java` | 提供 `WorldScreenTransform` 与 `GeometryCache` |
| `src/client/java/.../cadengine/CADRenderEngine.java` | CAD 矢量渲染（Phase 6） |

---

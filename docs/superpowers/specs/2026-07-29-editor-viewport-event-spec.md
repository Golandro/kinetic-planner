# Editor 视口与事件路由需求规格

> **范围：** 本规格总结当前 Kinetic Planner 编辑器（`KpEditorScreen` + `KpMapEditor`）在视口管理、事件路由、地图导航与 CAD 视图叠加方面的已确认需求、约束与待决策点。
> **状态：** 基于 2026-07-29 调试会话与代码审查整理，2026-07-30 审查修订。
>
> **文档关系：** 本规格是对 `2026-07-27-xaero-map-editor-integration-design.md` §4（事件路由）和 §6（组件设计）的**修订与细化**。当本规格与原始设计规格冲突时，**以本规格为准**。未提及的章节以原始设计规格为准。

---

## 1. 整体目标

为 Kinetic Planner 的编辑模式建立一套清晰、可扩展的视口与事件系统：

- 用户通过 `/kp edit` 进入编辑模式后，屏幕被 Xaero 地图瓦片层、CAD 矢量层、LDLib2 UI 层共同构成。
- 鼠标/键盘事件必须按正确优先级分发：LDLib2 UI 控件优先 -> 主视口地图/CAD 交互 -> 绝不泄漏给被隐藏的原生 Xaero UI 按钮。
- 主视口是一个可 Tab 化的透明占位区域；未来可在同一窗口添加分析面板等附加视图。
- CAD 视图以全屏坐标渲染，但必须能够根据主视口的相对位置进行裁剪和命中测试（支持未来多视口/多 CAD 图层）。
- 地图相机控制只在主视口内生效，且手感须与 Xaero 原生地图 1:1。

---

## 2. 分层与坐标空间

### 2.1 渲染层（从底到顶）

| 层级 | 内容 | 坐标空间 | 说明 |
|---|---|---|---|
| Layer 0 | Xaero `GuiMap` 瓦片 | 世界坐标（由 `cameraX/cameraZ/scale` 决定） | 全屏渲染，编辑模式下由 `KpEditorScreen.renderMapLayer()` 委托 `guiMap.render()`，由 `XaeroUiSuppressMixin` 使用 `@WrapOperation` 选择性抑制 UI 元素 |
| Layer 1 | CAD 编辑矢量（轨道、控制点、辅助线） | 世界坐标 -> 屏幕坐标 | 由 `CADRenderEngine` 全屏绘制，但依赖当前活动视口的投影参数 |
| Layer 2 | LDLib2 Editor UI（Ribbon、工具栏、检视面板、主视口占位） | 屏幕/本地坐标 | 提供控件与布局框架；主视口区域必须完全透明。除主 CAD 视口外，其余 UI 全部基于 LDLib2，事件消费由 LDLib2 事件系统处理 |

### 2.2 坐标空间定义

- **屏幕坐标（Screen Coordinate）**：以 Minecraft 窗口左上角为原点，单位像素，对应 `Screen.mouseClicked(mouseX, mouseY)` 传入的坐标。
- **视口本地坐标（Viewport Local Coordinate）**：以某个视口（如 `MapPlaceholderView`）内容区左上角为原点的坐标。当前阶段 `MapPlaceholderView` 不直接使用本地坐标（输出屏幕坐标），但保留此概念用于：
  - 未来多视口场景下的视口内相对定位（如浮动小地图、画中画）。
  - CAD 导航柄（viewport gizmos）的局部渲染--2D 俯视视图不需要 3D 导航柄，但未来若支持透视视图则需要。
  - 指针世界坐标显示（hover 时在视口内显示当前指向的方块坐标）。
- **世界坐标（World Coordinate）**：Minecraft 方块坐标（XZ 平面），由 Xaero `cameraX/cameraZ/scale` 与屏幕坐标互相转换。

**关键约束：**

- `MapPlaceholderView` 作为中间主视口，向 CAD 层和导航层输出**屏幕坐标**，而不是视口本地坐标。
- 原因：CAD 图层当前以全屏方式渲染（Phase 6 设计），必须知道事件/对象在屏幕上的绝对位置；若输出本地坐标，多视口或浮动窗口场景下会出现捕获错误。
- 当未来支持多视口时，由统一的事件调度器维护"事件属于哪个视口"，但事件数据本身仍以屏幕坐标携带。

---

## 3. 主视口（MapPlaceholderView）需求

### 3.1 基础行为

- `MapPlaceholderView` 是 `centerWindow` 中一个 `View`，标题/图标暂时为 "view" 或 "Main Viewport"。
- 背景必须显式置为透明（`IGuiTexture.EMPTY`），不能依赖 `View` 默认行为。
- 保留命中测试能力（`allowHitTest = true`），使其可以作为事件范围限制的依据。
- **视口边界查询**：依赖 LDLib2 自身的 `isMouseOver(float, float)` 方法进行点命中测试。`KpEditorScreen` 通过 `editor.getMapViewport()` 获取视口实例（可能为 null，如 Tab 切换后），调用 `viewport.isMouseOver(mouseX, mouseY)` 判断事件是否落在视口内。不缓存边界矩形，因 `SplittableWindow` 布局下视口边界随面板调整动态变化。
- 不注册具体 UI 事件监听器；导航与 CAD 路由统一由 `KpEditorScreen` 处理，避免事件被 LDLib2 内部判定为"已消费"而截断。
- `MapPlaceholderView` 概念上可承载导航渲染（CAD 导航柄、指针世界坐标显示），但当前 2D 俯视视图不需要 3D 导航柄；指针世界坐标显示为未来扩展。

### 3.2 Tab 化能力

- `centerWindow` 的 `ViewContainer` 保留 tab 栏，未来可在同一窗口添加更多 tab（如分析面板、属性表）。
- 当前主视口 tab 唯一且位置固定，不能被关闭或拖拽到其他窗口。
- 分析面板等新增 tab 可以"挤占"主视口区域，即切换 tab 后主视口隐藏，分析面板显示在同一内容区。
- 当主视口 tab 激活时，其内容区才是有效的地图/CAD 交互区域。

### 3.3 多视口预留（非当前实现）

- 当前阶段只存在一个主视口。
- CAD 图层在架构上允许"多开"，但当前阶段仍按单实例实现。
- 未来若通过 Mixin 技术实现多个 `GuiMap` 实例（MDI），需要重新设计视口-相机绑定关系；当前代码不应把"视口 == Xaero GuiMap"写死。

---

## 4. 事件路由需求

### 4.1 总体优先级

事件进入 `KpEditorScreen` 后按以下顺序处理：

1. **LDLib2 UI 树优先**：通过 `KpUIEventForwarder` 转发给 `ModularUI`。Ribbon 按钮、工具栏按钮、检视面板控件等在此消费。
2. **主视口交互层**：若 UI 树未消费，且事件落在 `MapPlaceholderView` 内容区内，则根据当前工具模式分发：
   - `NAVIGATION` 工具：地图相机平移（自定义）/缩放（委托 GuiMap）。
   - `SELECT/LINE/BEZIER/SNAP` 等工具：CAD 命中检测与编辑操作（Phase 6），但滚轮缩放仍然生效（见 §4.3.2）。
3. **背景兜底**：若事件既不在任何 UI 控件上，也不在主视口内，则不做任何地图/CAD 操作。除主 CAD 视口外，其余 UI 全部基于 LDLib2，事件消费由 LDLib2 事件系统处理即可。

### 4.2 输入泄漏防护策略

编辑模式下，`GuiMap` 的原生 UI 按钮（如左上角设置按钮、右上角关闭按钮）已被 `XaeroUiSuppressMixin` 使用 MixinExtras `@WrapOperation` 在 `render()` 内部选择性抑制（HUD 文本/雷达菜单/super.render/右键菜单/Tooltip/消息框），保留瓦片底图与玩家箭头自然渲染。但被抑制渲染的 UI 元素的 `mouseClicked` 仍可能响应点击。

**策略分类：**

| 事件类型 | 是否允许委托 `guiMap` | 理由 |
|---|---|---|
| `mouseClicked` | **禁止** | 会触发 Xaero 原生 UI 按钮（设置面板、关闭按钮等） |
| `mouseDragged` | **禁止**（平移用 accessor） | 依赖 `mouseClicked` 状态，可能触发联动 UI 逻辑 |
| `mouseScrolled` | **允许委托** | 滚轮不触发按钮点击，且 GuiMap 原生缩放逻辑包含中心点保持与瓦片降级 |
| `keyPressed` / `keyReleased` / `charTyped` | **允许委托**（NAVIGATION 工具下） | 键盘事件不触发 UI 按钮点击；Xaero 地图搜索/坐标输入等功能可用 |
| `mouseMoved` | **禁止** | 可能触发 Xaero 悬停 UI 高亮 |
| `mouseReleased` | **禁止**（自定义处理） | 与 `mouseClicked` 配对，需自定义拖拽释放逻辑 |

- 地图相机**平移**通过 `XaeroMapAccessor` 直接读写 `cameraX/cameraZ` 字段实现（不调用 `guiMap.mouseDragged`）。
- 地图相机**缩放**优先委托 `guiMap.mouseScrolled()`，复用 Xaero 原生缩放逻辑（含中心点保持、比例尺上限降级为已渲染瓦片缩放）。若委托因 `mc.screen` 检查失败，再降级为 accessor 方案。
- 地图**键盘导航**（NAVIGATION 工具下）委托 `guiMap.keyPressed()` 等，复用 Xaero 原生键盘交互。

### 4.3 鼠标事件细化

#### 4.3.1 平移（拖拽） `[IMPL]`

- 触发条件：当前工具为 `NAVIGATION`、鼠标左键（`button == 0`）、按下点落在主视口内容区内。
- 一旦开始拖拽，即使鼠标移出主视口内容区，仍继续平移，直到释放左键。此"拖拽锁定"行为仅适用于视口拖拽和视口移动，不适用于 CAD 工具操作。
- 平移公式须保证屏幕像素移动量与底图视觉移动量 1:1。
- **右键行为**：拖拽过程中按右键直接结束当前拖拽操作（取消平移，不移动到右键位置）。
- 中键在 `NAVIGATION` 工具下不触发平移；中键移动为未来扩展功能预留。

#### 4.3.2 缩放（滚轮） `[TODO: 改为委托]`

- **所有工具**（不仅 NAVIGATION）下滚轮均触发地图缩放--CAD 软件中滚轮始终用于视图缩放。
- 触发条件：鼠标指针位于主视口内容区内。
- **优先委托** `guiMap.mouseScrolled()`，复用 Xaero 原生缩放逻辑：
  - 滚轮向上/向下对应 Xaero 默认的放大/缩小方向。
  - 中心点保持：鼠标指针指向的世界点保持在指针下方（Xaero 内部已实现）。
  - 比例尺上限：当 scale 超过瓦片分辨率时，GuiMap 自动降级为对已渲染瓦片进行缩放（不再解析新瓦片）。
- **修饰键**：按住修饰键（Shift/Ctrl 等）时，滚轮行为可切换为非地图层控制（如 CAD 图层缩放、滚动图层列表等）。具体修饰键语义待定义。
- 当前代码使用自定义线性 `scale += scrollY * step`（`MIN_SCALE=0.5`, `MAX_SCALE=64.0`, `SCALE_STEP=0.5`），需替换为委托 GuiMap。

#### 4.3.3 CAD 工具命中 `[TODO: Phase 6]`

- `SELECT/LINE/BEZIER/SNAP` 等工具在主视口内的点击/拖拽/释放用于 CAD 编辑。
- CAD 命中检测使用屏幕坐标，由 `WorldScreenTransform` 转换为世界坐标后查询 `GeometryCache`。
- 命中检测只在主视口内容区内进行；主视口外的点击不触发 CAD 操作。
- **当前阶段行为**：CAD 工具的点击/拖拽/释放均为 no-op（返回 false），事件不被消费也不触发地图导航。只有确切的导航需求才选择性委托 GuiMap。
- **工具操作失败处理**：若工具操作因右键取消或其他原因失败，当前阶段无法做回退逻辑（undo/rollback）。未来通过后端 VCS 系统处理。

### 4.4 键盘事件

- 当前阶段键盘事件由 `KpEditorScreen` 统一处理。
- **参数化按键绑定**：工具快捷键（V=Select, L=Line, B=Bezier, P=Pan, S=Snap）应使用参数化绑定，而非硬编码 key code。当前代码使用硬编码 `switch(keyCode)`，未来应迁移到可配置的按键绑定系统。
- 工具快捷键仅在 UI 树**未消费**键盘事件时生效。当 Inspector 文本框等控件获得焦点时，字母键被控件消费，不触发工具切换。
- NAVIGATION 工具下，未消费的键盘事件委托 `guiMap.keyPressed()` 等，复用 Xaero 原生键盘交互（地图搜索、坐标输入等）。
- ESC：退出编辑模式（`KpClientState.setEditMode(false)` -> `setScreen(guiMap)`）。
- 未来方向键导航：当主视口获得焦点且工具为 `NAVIGATION` 时，方向键按固定世界距离平移相机。
- 对象聚焦：future 命令/快捷键将相机移动到选定对象中心，需保证缩放级别合适。

---

## 5. 地图相机 1:1 手感需求

### 5.1 字段语义

- `XaeroMapAccessor.kp$scale()` 返回的 `scale` 字段表示 **pixels per block**（每方块对应像素数）。
- 因此：
  - `scale` 越大 -> 每个方块占用的像素越多 -> 视觉上" zoom 越近"。
  - 屏幕像素 delta 转换为世界坐标 delta 的公式为：`worldDelta = screenDelta / scale`。

### 5.2 手感验证标准

- **平移**：在编辑模式下，使用 `NAVIGATION` 工具在主视口内水平拖拽 100 像素，底图上的某个特征点也应在屏幕上水平移动 100 像素（即相机反向移动 100 / scale 个方块）。`[IMPL]`
- **缩放**：滚轮缩放时，鼠标指针指向的世界点应尽可能保持在指针下方，不出现明显漂移。优先通过委托 `guiMap.mouseScrolled()` 实现，而非自行实现中心点保持算法。`[TODO: 改为委托]`

### 5.3 导航逻辑委托原则

- **优先委托导航逻辑给 GuiMap**：GuiMap 的原生缩放/键盘导航经过充分测试，包含中心点保持、瓦片降级、边界限制等逻辑。不应重新实现这些逻辑。
- **平移为例外**：因 `guiMap.mouseDragged` 依赖 `mouseClicked` 状态且可能触发联动 UI，平移使用 accessor 直接读写 `cameraX/cameraZ`。平移公式简单（`worldDelta = screenDelta / scale`），不涉及中心点保持等复杂逻辑。

---

## 6. Workspace 布局需求（待决策）

### 6.1 当前问题

- `KpMapEditor` 当前使用 LDLib2 `Editor` 默认的 `SplittableWindow` 做递归二分布局。
- `SplittableWindow` 基于 Flex：父容器按比例分配空间。
- 导致的问题：调整右侧面板宽度时，中间窗口变小 -> 上一级 horizontal split 的分配变化 -> 左侧面板也被连带压缩。

### 6.2 目标布局

- 需要类似 CSS Grid 的固定行列布局：
  - 顶行：`KpRibbonBar`，固定高度。
  - 中行：三列布局--左侧 `ToolPanelView`（固定宽度） | 中间 `MapPlaceholderView`（填充剩余） | 右侧 Inspector（固定宽度）。
  - 底行：状态栏（可选），固定高度。
- 调整右侧宽度时，只改变中间列宽度，左侧列完全不受影响。
- 调整左侧宽度时，只改变中间列宽度，右侧列完全不受影响。

### 6.3 影响范围

- 除主 CAD 视口外，其余 UI 全部基于 LDLib2，事件消费由 LDLib2 事件系统处理。布局问题主要影响视觉体验和视口边界准确性，不影响事件路由正确性。
- 视口边界通过 LDLib2 `isMouseOver()` 实时查询，不缓存，因此布局变化不会导致事件路由错误。

### 6.4 实现路径待决策

- 选项 A：使用 LDLib2 内置的 Grid 布局（调试器已显示"网格布局"选项）。
  - 风险：需要确认 1.21 分支 Grid API 是否完整、是否支持运行时拖拽调整列宽。
- 选项 B：绕过 `Editor` 默认的 `rootWindow/leftWindow/centerWindow/rightWindow`，自己构建根 `Grid` 布局，把 `Editor` 当作轻量级容器。
  - 风险：需要重新实现或绕过 `Editor` 的窗口管理、拖拽 tab、布局保存等默认行为。
- 选项 C：保留 `SplittableWindow`，但修改 splitter 行为，使其不再按父容器比例分配，而是按像素绝对值分配。
  - 风险：`SplittableWindow` 的内部实现可能不支持这种改动。

**结论：** Grid 化是结构性改动，需要单独设计任务与验证，不在本规格的执行范围内。

---

## 7. 编辑状态与选择池

### 7.1 状态持久性

- 编辑状态（当前工具、选择集）在退出/重新进入编辑模式时**保留**，不做 `reset()`。这是正经 CAD 软件的已知特性--用户切换模式后回来应看到之前的工作状态。
- 当前代码中 `EditToolState` 为单例，`reset()` 方法存在但**不应**在 `KpEditorScreen.create()` 中调用。

### 7.2 选择池设计方向

- GUI 选择（用于命令式操作，如在 Inspector 列表中选择对象）和工具操作（直接在视口中拖拽/点击选择）是两个独立维度，**不应冲突**。
- UI 应自己维护对象待选池，而非依赖全局单例状态。这需要理论上的池多例化（虽然命令确实是原子的）。
- 更优方案：设计对象 Collection 本身可以持久化（序列化到存档/项目文件），而非依赖运行时单例。

### 7.3 当前实现状态

- `EditToolState` 为单例模式，包含 `currentTool`（Tool 枚举）和 `selectedNodes`（`Set<UUID>`）。
- 当前选择池设计尚未满足 §7.2 的多例化需求，但作为 Phase 6 前的最小实现可接受。

---

## 8. 待明确问题

| # | 问题 | 优先级 | 说明 |
|---|---|---|---|
| 1 | ~~Xaero 滚轮缩放的具体步长与中心点保持算法~~ | ~~高~~ | **已决策**：优先委托 `guiMap.mouseScrolled()`，不自行实现。 |
| 2 | 方向键平移的世界距离 | 低 | 每次按键移动多少方块？是否随 scale 变化？未来功能。 |
| 3 | 对象聚焦的缩放策略 | 低 | 聚焦到对象时，目标 scale 如何计算？未来功能。 |
| 4 | 多 CAD 图层的命中优先级 | 低 | 若未来多个 CAD 图层叠加，点击时按何种顺序命中？多视口阶段才需。 |
| 5 | Grid 布局 API 可行性 | 中 | 需调研 LDLib2 1.21 分支 `Grid` 布局的完整能力与示例。阻塞 §6 决策。 |
| 6 | 修饰键滚轮语义 | 中 | Shift/Ctrl + 滚轮的具体行为（CAD 图层缩放？图层滚动？）。 |
| 7 | 参数化按键绑定的迁移路径 | 中 | 从硬编码 key code 迁移到可配置绑定系统的具体方案。 |
| 8 | 选择池多例化的数据结构 | 中 | 对象 Collection 持久化的序列化格式与存储位置。 |

---

## 9. 已确认不再采用的方案

- **直接调用 `guiMap.mouseClicked`**：会导致 Xaero 原生 UI 输入泄漏（如打开设置面板）。已改为在 `KpEditorScreen` 中自定义处理。
- **完全移除主视口占位 View 并禁用 `centerWindow` 命中测试**：虽能掏空视口，但失去 Tab 化扩展能力。已恢复 `MapPlaceholderView` 作为透明占位。
- **使用 `scale` 直接作为 blocks per pixel**：会导致平移方向相反或速度错误。已确认 `scale` 为 pixels per block。
- **`@Inject HEAD cancel` 抑制 GuiMap.render()**：原始设计规格方案 B。实际已演进为方案 B'：使用 MixinExtras `@WrapOperation` 在 `render()` 内部选择性跳过 UI 元素，保留瓦片底图自然渲染。`KpEditorScreen.renderMapLayer()` 直接委托 `guiMap.render()` 完整调用。
- **`EditToolState.reset()` 在进入编辑模式时调用**：状态残留是 CAD 软件的已知特性，不做 reset。选择池应设计为可持久化的 Collection。

---

## 10. 测试策略

### 10.1 单元测试

| 测试目标 | 验证点 | 状态 |
|---|---|---|
| 事件返回值 | 各事件路由路径的返回值正确（消费=true / 未消费=false） | `[TODO]` |
| 委托埋点 | 对 `guiMap` 的委托调用可被验证（mock 或 spy） | `[TODO]` |
| 坐标转换往返 | 屏幕坐标 -> 世界坐标 -> 屏幕坐标 往返一致性 | `[TODO]` |
| 视口边界条件 | `isMouseOverMapViewport` 在边界内/外/null 视口时的返回值 | `[TODO]` |
| 工具切换 | 工具快捷键切换后 `EditToolState.currentTool` 正确 | `[TODO]` |
| 缩放委托 | `guiMap.mouseScrolled` 被正确委托（NAVIGATION + 非 NAVIGATION 工具） | `[TODO]` |

### 10.2 手动验收清单

| 验收项 | 操作 | 预期结果 | 状态 |
|---|---|---|---|
| 1:1 平移手感 | NAVIGATION 工具下水平拖拽 100px | 底图特征点水平移动 100px | `[TODO]` |
| 拖拽锁定 | 开始拖拽后移出视口 | 持续平移直到释放左键 | `[IMPL]` |
| 右键取消拖拽 | 拖拽过程中按右键 | 拖拽立即结束 | `[IMPL]` (Task 2) |
| 滚轮缩放（所有工具） | SELECT 工具下滚轮 | 地图缩放（委托 GuiMap） | `[IMPL]` (Task 2) |
| 缩放中心点保持 | 滚轮缩放时观察鼠标下方世界点 | 世界点保持在指针下方 | `[IMPL]` (Task 2 委托 GuiMap) |
| 缩放上限降级 | 持续放大到超过瓦片分辨率 | 已渲染瓦片缩放，不崩溃 | `[IMPL]` (Task 2 委托 GuiMap) |
| 键盘导航委托 | NAVIGATION 工具下按键 | Xaero 原生键盘交互生效 | `[IMPL]` |
| 工具快捷键与焦点 | Inspector 文本框聚焦时按 V | 输入字母 V，不切换工具 | `[IMPL]` (Task 3 修复 ESC key code bug) |
| 状态持久性 | 选 SELECT 工具 -> ESC 退出 -> 重新进入 | 工具仍为 SELECT | `[IMPL]` |
| mouseClicked 不泄漏 | 点击 Xaero 设置按钮原位置 | 不打开设置面板 | `[IMPL]` |

---

## 11. 相关文件索引

| 文件 | 职责 | 状态 |
|---|---|---|
| `src/client/java/.../gui/editor/KpEditorScreen.java` | 编辑模式 Screen 壳，事件总入口，自定义地图导航实现 | `[IMPL]` |
| `src/client/java/.../gui/editor/KpMapEditor.java` | LDLib2 Editor 子类，负责 Ribbon/ToolPanel/主视口/Inspector 的布局 | `[IMPL]` |
| `src/client/java/.../gui/MapPlaceholderView.java` | 中心主视口透明占位 View | `[IMPL]` |
| `src/client/java/.../gui/event/KpUIEventForwarder.java` | MC 事件 -> LDLib2 UI 事件转发 | `[IMPL]` |
| `src/client/java/.../mixin/XaeroMapAccessor.java` | 访问 Xaero `GuiMap` 的 `cameraX/cameraZ/scale` | `[IMPL]` |
| `src/client/java/.../mixin/XaeroUiSuppressMixin.java` | 编辑模式下使用 `@WrapOperation` 选择性抑制 Xaero UI 元素渲染 | `[IMPL]` |
| `src/client/java/.../mixin/GuiMapRemovedMixin.java` | 抑制 `GuiMap.removed()` 状态清理（F1 风险缓解） | `[IMPL]` |
| `src/client/java/.../gui/editor/EditToolState.java` | 当前编辑工具状态（单例，不做 reset） | `[IMPL]` |
| `src/client/java/.../instrument/WorldTreeReadOverlay.java` | 提供 `WorldScreenTransform` 与 `GeometryCache` | `[IMPL]` |
| `src/client/java/.../cadengine/CADRenderEngine.java` | CAD 矢量渲染（Phase 6 扩展为交互枢纽） | `[PARTIAL]` |
| `src/client/java/.../cadengine/EditLayerRenderer.java` | CAD 编辑图形层渲染器 | `[PARTIAL]` |

---

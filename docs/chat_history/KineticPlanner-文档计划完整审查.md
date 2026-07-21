# Kinetic Planner Phase 0 文档计划完整审查

> **修订注记（2026-07-21）：** 本文中提及的 RailML 外部 DSL 选型已被替换为 IFC 4.3 铁路特化子集（双层结构：核心 IFC-XML + kp_ IfcPropertySet 承载 Create 特化）。审查报告中 E2（"Phase 4 的 RailML 导出"）等 RailML 引用据此替换为 IFC。其余针对 Create 6.0.10 源码的技术声称核实结论不受影响。详见 spec §7（已重写）与 STATUS.md §4.1。
>
> 审查对象：
> - 设计规格 `../superpowers/specs/2026-07-20-kinetic-planner-phase0-design.md`（~678 行）
> - 实现计划 `../superpowers/plans/2026-07-21-kinetic-planner-phase0.md`（~834 行）
>
> 审查视角：**MVP 可落地性** + **未来扩展点预留** + **技术假设正确性** + **文档内部一致性**
> 审查日期：2026-07-21
> 已对 Create 6.0.10（tag `ac0c444`）源码逐条核实技术声称

---

## 〇、总体结论

设计文档**质量在模组项目里属于上乘**：有分阶段路线图、接口契约、验收标准、风险约束、跨 Phase 备忘。但仍存在 **3 类必须修正的硬伤** 和 **若干扩展性盲点**：

| 类别 | 数量 | 性质 |
|---|---|---|
| 🔴 技术假设错误（与 Create 6.0.10 源码不符） | 3 | 直接导致实现走偏，**必须在动手前修** |
| 🟡 MVP 范围过载 / 欠缺 | 4 | 影响 Phase 0 能否按期交付 |
| 🟢 扩展点预留不充分 | 5 | 不阻塞 MVP，但会在 Phase 1–4 付出重构代价 |
| ⚪ 文档工程/一致性 | 6 | 可读性、可维护性问题 |

下文逐条展开。每条给：**问题 → 证据 → 建议**，并标注影响阶段。

---

## 一、🔴 技术假设错误（必须修正，阻断实现）

### E1. 贝塞尔曲线次数错误：Create 是**三次**，文档写成"二次"

**位置**：spec 1.2、4.2、4.5、4.7；plan Task 7 Step 1。

**文档声称**："由 `bePositions`/`starts`/`axes`/`normals` 定义二次贝塞尔"；`EdgeGeometry.BezierSpec` 注释"三次贝塞尔控制点"但 spec 4.2 末尾写"Create 的二次贝塞尔在此转换为三次贝塞尔控制点"。

**源码事实**：Create 的 `VecHelper.bezier(end1, end2, finish1, finish2, t)` 用 **4 个控制点的三次贝塞尔**；`BezierConnection.determineHandles` 计算 `finish1`/`finish2` 两个 handle（控制点）。字段 `bePositions`（两端点）+ `starts`/`axes`/`normals` 算出的 handle 共 4 点。

**影响**：
- `RailwayDataAccess.edgeGeometry` 的转换逻辑会错（按二次转三次的公式与三次直接读取完全不同）。
- 计划 Task 7 Step 1 "用 `getHandleLength()` 算 finish1/finish2 控制点"方向对，但 spec 4.2 的 `BezierSpec(start, control1, control2, end)` 注释说"Create 二次贝塞尔精确表达"会误导实现者。
- NanoVG `nvgBezierTo` 本就是三次贝塞尔——若误以为 Create 是二次，会多做一次无谓的"二次→三次"转换，几何变形。

**建议**：
1. spec 1.2 修正为"三次贝塞尔（cubic），由 `bePositions` 两端点 + `determineHandles` 算出的两个 handle 定义"。
2. spec 4.2 `BezierSpec` 注释改为"直接承载 Create 三次贝塞尔的 4 个控制点，无次数转换"。
3. plan Task 7 明确 `edgeGeometry` 从 `BezierConnection` 读取 `bePositions[0]`/`bePositions[1]` + 计算的 `finish1`/`finish2`，直接映射到 `BezierSpec(start, control1, control2, end)`，**不做二次→三次转换**。
4. spec 1.2 "Create 贝塞尔不支持坡度"也不准确——`smoothing` 确实处理倾斜过渡，只是纯垂直方向不支持。改为"Y 方向由 `yOffsetPixels`/`smoothing` 独立处理，纯垂直过渡受限"。

**影响阶段**：Phase 0（核心几何）、Phase 2（样条/双圆弧若复用转换逻辑）。

---

### E2. `TrackNodeLocation` Y 坐标压缩公式不准确

**位置**：spec 1.2 第 36 行。

**文档声称**："`floor(round(x*2))`，半方块精度"——统一用 round*2 描述三轴。

**源码事实**：X/Z 用 `Mth.floor(Math.round(x*2))`，**Y 用 `Mth.floor(y)*2`**（不是 round*2）。`getLocation()` 返回 `/2.0` 并加 `yOffsetPixels/16.0`。

**影响**：
- MVP 只读渲染用 `getLocation()` 解压，影响不大（Y 不参与俯视投影）。
- 但 Phase 1 的节点拾取、Phase 3 的版本控制、Phase 4 的 RailML 导出若按文档公式重建坐标，会在 Y 上产生 0.5 方块级误差，表现为轨道与实际位置错位。

**建议**：
1. spec 1.2 修正为："X/Z 压缩 `floor(round(x*2))`（半方块精度），Y 压缩 `floor(y)*2`（整方块精度），解压 `getLocation()` 返回 `/2.0 + yOffsetPixels/16.0`"。
2. 在 spec 4.2 `nodeWorldPos` 明确"统一调 `TrackNode.getLocation()`，不自实现解压"。

**影响阶段**：Phase 0 低，Phase 1+ 高。

---

### E3. `GlobalStation` / `TrackObserver` 着色来源错误

**位置**：spec 4.6 "颜色委托策略"、plan Task 11 Step 1。

**文档声称**：
- 车站（`GlobalStation`）用 `StationBlock` mapColor；
- 观察者（`TrackObserver`）用 `TrackGraph.color`。

**源码事实**：
- `GlobalStation` 只引用 `StationBlock.ASSEMBLING` 属性，**无 mapColor 关联**；
- `TrackObserver` 字段为 `activated`/`filter`/`currentTrain`，**无 graph.color 引用**。

**影响**：`EdgePointColorResolver` 按文档实现会在运行时取不到颜色（NPE 或 fallback 到 graph.color），虽不崩溃但视觉上车站/观察者颜色无意义。

**建议**：重新定义这两类的着色策略，给出**可落地的 fallback 链**：
- `GlobalStation`：尝试 `graph.color`（与图同色，最稳妥），或查关联 `StationBlockEntity` 的 `BlockState` 取 `mapColor`（需额外访问 BE，成本高）。
- `TrackObserver`：用 `graph.color`，或按 `activated` 状态双色（激活=亮黄，未激活=灰），语义更清晰且实现简单。
- spec 4.6 改为"未定义原生颜色的 EdgePointType 一律 fallback `graph.color`；`TrackObserver` 可选按 `activated` 状态着色"。

**影响阶段**：Phase 0（视觉验收 5.1#5）、Phase 3（信号段着色若复用 resolver）。

---

## 二、🟡 MVP 范围过载 / 欠缺

### M1. Phase 0 范围偏大，建议拆出"Phase 0.5"减负

**问题**：Phase 0 同时要交付 8 个能力域（骨架/数据/适配/投影/渲染/主题/绘制器/配置），其中 `CADRenderEngine`（NanoVG + GL 状态隔离）本身就是技术难点，`MapOverlayProvider` 抽象层为后续多适配器设计但 Phase 0 只有一个实现。计划 11 个 Task 里 Task 5–11 步骤为概要形式（文档自审也承认），执行风险集中。

**建议**：把 Phase 0 拆成两个可独立交付的里程碑：

| 里程碑 | 内容 | 价值 |
|---|---|---|
| **Phase 0a（真 MVP）** | 骨架重命名 + 数据只读访问 + 投影层 + **简化渲染**（用 MC 原生 `RenderType.lines()` 而非 NanoVG）+ Xaero Mixin 接入 + 基础叠加 | 能在地图上看到轨道拓扑，验证数据层与适配层 |
| **Phase 0b** | NanoVG `CADRenderEngine` + 主题系统 + 配置/命令 + 视觉打磨 | 矢量平滑、抗锯齿、可配 |

理由：先用 MC 原生线渲染跑通"数据→投影→叠加"全链路，再上 NanoVG。若一上来就 NanoVG，NanoVG 崩了连数据层是否正确都无法可视化验证。

**影响阶段**：Phase 0 交付节奏。

---

### M2. `MapOverlayProvider` 抽象层在 MVP 阶段过度设计

**问题**：Phase 0 只有 Xaero 一个实现，却建了 `MapOverlayProvider` 接口 + `MapOverlayDispatcher`（含熔断）+ `JourneyMapOverlayProvider` 占位。抽象层没有第二个实现验证前，接口形状可能不对（"过早抽象"风险）。

**建议**：
- MVP 阶段直接让 `WorldTreeReadOverlay` 调 `XaeroMapOverlayProvider`，**不建 Dispatcher**。
- 把 `MapOverlayProvider` 接口保留，但 Dispatcher + 熔断推迟到 Phase 0.5（JourneyMap 实现时一起做，那时才有第二个实现来校准接口）。
- 在 spec 2.1 标注"Dispatcher/熔断为 Phase 0.5 内容"，Phase 0 仅留接口与 Xaero 实现。

**影响阶段**：Phase 0（减负）、Phase 0.5（接口校准）。

---

### M3. 缺少"最小可视化验证"的早断点

**问题**：验收标准 5.1 要求 12 项功能 + 5.2 隔离 4 项 + 5.3 性能 3 项 + 5.4 架构 4 项，共 23 项，且全部依赖**完整集成后**才能在游戏内验证。没有"中途可信进度点"——若 Task 1–10 都靠 `gradlew build` 通过判断，到 Task 11 才发现数据层或 Mixin 不对，回溯成本极高。

**建议**：在 plan 里增加**阶段性可视化锚点**：
- Task 7（RailwayDataAccess）后：写一个临时调试命令 `/kp debug dump`，打印当前维度节点/边数量 + 前 10 条边的端点坐标，验证数据访问层。
- Task 10（Xaero Mixin）后：在 Mixin 钩子里只画一个屏幕中心十字线（用 MC 原生 `GuiGraphics.fill`），验证相机参数换算与注入点。
- Task 11 集成时再换 NanoVG 全量绘制。

这些锚点不进验收标准，只是 plan 的"可信进度检查"。

**影响阶段**：Phase 0 执行风险控制。

---

### M4. 性能验收阈值缺乏依据

**问题**：spec 5.3#17 "单维度 < 500 节点时叠加层渲染帧耗时 < 2ms"——这个阈值没有给出测量方法、硬件基线、对比基准（无叠加时帧耗时多少）。NanoVG + GL 状态切换的开销在不同显卡上差异大。

**建议**：
- spec 5.3 补充测量方法：`/kp debug stats` 输出 `overlayFrameNanos`，在指定硬件（如 GTX 1060 / 1080p / 60fps）下取 100 帧均值。
- 阈值改为相对值："叠加层引入的额外帧耗时不超过无叠加时的 15%"，比绝对 2ms 更可移植。
- 同时给一个硬上限："任何单帧 overlay 耗时不超过 8ms（避免单帧掉到 30fps 以下）"。

**影响阶段**：Phase 0 验收、Phase 1+ 规模增长。

---

## 三、🟢 扩展点预留不充分

### X1. `EdgeGeometry` 的 `ExtensionSpec` 预留了，但缺"几何提供者"扩展机制

**现状**：`ExtensionSpec(sourceModId, geometryTypeId, CompoundTag data)` 是数据载体，Phase 0 恒 null，文档说 railx 兼容靠它。但这只是**被动保留**——railx 若要主动参与渲染/编辑，没有注册点。

**建议**：预留 `EdgeGeometryProvider` 扩展接口（Phase 2+ 启用）：
```java
public interface EdgeGeometryProvider {
    String sourceModId();
    String geometryTypeId();
    EdgeGeometry fromTrackEdge(TrackEdge edge);   // railx 主动构造
    void render(EdgeGeometry geometry, CADRenderEngine engine);  // railx 自定义渲染
}
```
Phase 0 只定义接口、不实现；用 `@Internal`/`@ApiStatus.Experimental` 标注。spec 7.4 的 railx 兼容策略补充此注册点。

**影响阶段**：Phase 2（高级几何）、Phase 4（IO）。

---

### X2. `MapOverlayContext` 缺少 DPR / HiDPI 字段

**现状**：`MapOverlayContext` 有 `screenWidth`/`screenHeight`/`blocksPerPixel`，但**没有 `dpr`（设备像素比）**。`CADRenderEngine.beginFrame(width, height, dpr)` 需要它。

**问题**：HiDPI 显示器（4K/Retina）下 MC 的 `window.getScreenWidth()`（ framebuffer 像素）与 `getGuiScaledWidth()`（逻辑像素）不同，NanoVG 的 `dpr = screenWidth / guiScaledWidth`。若 context 不带 dpr，渲染会糊或错位。

**建议**：
- `MapOverlayContext` 增加 `float dpr` 字段；
- spec 4.3 `captureContext` 明确 `dpr = (float)screenWidth / guiScaledWidth`；
- spec 4.5 `beginFrame` 用 `nvgBeginFrame(vg, guiScaledWidth, guiScaledHeight, dpr)`。

**影响阶段**：Phase 0（HiDPI 用户直接受影响）。

---

### X3. 主题系统缺"命名空间隔离"机制

**现状**：主题 JSON 存 `config/kineticplanner/themes/<name>.json`，主题名是扁平字符串。第三方（或用户自定义）主题与内置主题可能重名。

**建议**：
- 主题名引入命名空间：`kineticplanner:default` / `myresourcepack:dark`。
- 加载顺序：内置默认 → 资源包（`assets/<ns>/kineticplanner/themes/`）→ `config` 目录用户覆盖。
- spec 4.6 补"主题资源定位"小节，Phase 0 可只实现 config 目录 + 内置默认，但数据结构预留命名空间。

**影响阶段**：Phase 1（主题编辑器）、资源包集成。

---

### X4. `IRailwayDataAccess` 接口未预留"事务/快照"语义

**现状**：`IRailwayDataAccess` 是每帧 live view（`clientVersion()` 脏检测）。Phase 3 的版本控制、Phase 1 的暂存树都需要**快照**语义——在某一时刻拷贝图拓扑，后续编辑不影响已读数据。

**问题**：当前接口返回 `Stream<TrackGraph>` 等是 live 引用，调用方若缓存了节点对象，版本号变了但对象还被持有，会读到脏数据。

**建议**：
- spec 4.2 增加 `Snapshot snapshot()` 方法（返回不可变快照接口），Phase 0 返回 live view 的包装（标记 `@UnmodifiableView`），但接口先定义。
- 或在 spec 6（后续 Phase 备忘）明确"Phase 1 暂存树将引入 `IRailwayDataAccess.snapshot()`"。
- 文档 4.2 的"只读约束"补一句："返回的 `Stream`/对象在下次 `clientVersion()` 变化前有效，调用方不应跨版本持有"。

**影响阶段**：Phase 1（暂存树）、Phase 3（版本控制）。

---

### X5. 配置系统未声明"热重载边界"

**现状**：spec 4.8 有 `/kp overlay reload`（重载配置+主题）、`/kp theme reload`。但没说清**哪些配置项热重载生效、哪些需要重启游戏**。

**问题**：Mixin 注入点、`CADRenderEngine.init()`（NanoVG 上下文创建）显然不能热重载；主题 JSON、图层开关可以。用户改了 `disableGlStateGuard` 期望立即生效却不行，会困惑。

**建议**：spec 4.8 配置树每项标注热重载性：

| 配置项 | 热重载 |
|---|---|
| `overlay.enabled` | ✅ |
| `overlay.adapterPriority` | ❌（Mixin 注入点固定） |
| `theme.activeTheme` | ✅ |
| `layers.*` | ✅ |
| `debug.disableGlStateGuard` | ❌（危险，重启生效更安全） |

**影响阶段**：Phase 0（用户体验）、Phase 1（主题编辑器）。

---

## 四、⚪ 文档工程 / 一致性

### D1. 计划 Task 5–11 步骤过于概要
Task 1–4 有完整代码块与测试，Task 5–11 只有"（结构见规格 X.X）"括号。文档自审辩解"合理粒度分层"，但执行 agent 若只读 plan 不读 spec，Task 9（CADRenderEngine，最难的 GL 状态隔离）只给一行说明，风险过高。**建议**至少 Task 9、10、11 给关键代码骨架（`beginFrame`/`endFrame` 的 try-finally 结构、Mixin `@Inject` 签名）。

### D2. 缺少"版本兼容性矩阵"
设计里 Create 6.0.10、NeoForge 21.1.235、Xaero/JourneyMap 版本散落。**建议**新建 `docs/STATUS.md` 集中表格，并声明 Xaero 的 `GuiMap.render` Mixin 注入点**未锁版本**，每次 Xaero 升级需回归。

### D3. spec 与 plan 的 `mod_version` 不一致
spec 4.1 没提版本号；plan Task 1 Step 1 写 `mod_version=0.1.0`；`../../gradle.properties` 现状是 `1.0.0`。**建议**统一为 `0.1.0-alpha`，语义化版本表明未发布。

### D4. 缺少"失败模式与降级"汇总
spec 4.5（NanoVG 初始化失败降级）、4.7（引擎 disabled 降级）、5.2#13–15（隔离验收）散落。**建议**新增"§8 失败模式矩阵"集中列表：触发条件 / 降级行为 / 用户可见现象 / 日志关键词。

### D5. 验收标准 5.1#16"Create 叠加层与本模组叠加层共存"缺操作定义
"共存不互相干扰"如何测？**建议**明确：Create 栅格化纹理 + KP 矢量叠加同时显示，各自可见，无闪烁/错位/性能崩塌。

### D6. `../superpowers` 路径问题
两份高质量文档藏在 superpowers 工具专用目录里，外部读者难发现。**建议**迁移到 `docs/design/phase0.md` 与 `docs/plans/phase0.md`，README 引用。

---

## 五、扩展点预留清单（给未来 Phase 的护栏）

| 扩展点 | Phase 0 预留动作 | 受益 Phase |
|---|---|---|
| `EdgeGeometryProvider` 接口 | 定义 `@Experimental` 接口，不实现 | Phase 2/4 |
| `MapOverlayContext.dpr` | 加字段 + captureContext 计算 | Phase 0（HiDPI） |
| `IRailwayDataAccess.snapshot()` | spec 备忘或接口预留 | Phase 1/3 |
| 主题命名空间 | 数据结构用 `ns:name`，加载逻辑 Phase 1 | Phase 1 |
| `EdgePointType` 渲染钩子 | 预留 `EdgePointRenderer` 接口（Phase 1 启用） | Phase 1（自定义图标） |
| `MapOverlayProvider` 第二实现 | Dispatcher 推迟到 Phase 0.5 | Phase 0.5 |
| 失败模式矩阵 | spec 新增 §8 | 全 Phase |

---

## 六、可直接采纳的修订动作（按优先级）

1. **🔴 立即修**：E1（贝塞尔次数）、E2（Y 压缩公式）、E3（边点着色）——改 spec 1.2/4.2/4.6 + plan Task 7/11。
2. **🟡 动手前定**：M1（拆 0a/0b）、M3（加可视化锚点）、X2（dpr 字段）。
3. **🟢 补充即可**：X1/X4/X5（扩展接口/快照语义/热重载边界）。
4. **⚪ 顺手做**：D1–D6 文档工程。

---

## 附：技术声称核实结果（基于 Create 6.0.10 源码）

| # | 声称 | 结论 |
|---|---|---|
| 1 | `TrackGraph` 字段结构 | ✅属实 |
| 2 | `TrackNodeLocation` 压缩公式 | ⚠️ Y 用 `floor*2` 非 `round*2` |
| 3 | `TrackEdge` 结构 | ✅属实 |
| 4 | `EdgePointType.TYPES` 注册表 | ✅属实 |
| 5 | `RailwaySavedData` 结构 | ✅属实 |
| 6 | `TrackGraph.write` index 句柄 | ✅属实 |
| 7 | `BezierConnection` 是**二次**贝塞尔 | ❌实为**三次** |
| 8 | `BezierConnection` 相对 localTo 序列化 | ✅属实 |
| 9 | `compat.trainmap` 三适配器 + 栅格化 | ✅属实（多张 128² 非单张） |
| 10 | `TrainMapSync` 5tick + 插值 | ✅属实 |
| 11 | Xaero Mixin 注入 `blit` INVOKE | ✅属实（`require=0` 容错） |
| 12 | `TrackGraph.color` = rainbowColor | ✅属实 |
| 13a | `SignalBoundary`→`SignalEdgeGroup.color` | ✅属实 |
| 13b | `GlobalStation`→`StationBlock.mapColor` | ❌无此关联 |
| 13c | `TrackObserver`→`graph.color` | ❌无此关联 |
| 14 | `EdgeData.getGroupAtPosition` | ✅属实 |

**总体**：14 条中 9 属实、2 偏差、2 错误、1 部分错误。核心数据结构可靠，几何与边点着色需修正。

# Kinetic Planner 开发路线图与状态

> **最后更新：** 2026-07-22
> **当前分支：** `1.21`
> **当前状态：** Phase 0b 代码实现完成（Task 1-10），待运行时验收（Task 11）

---

## 1. 项目定位

Kinetic Planner 是 Minecraft 模组，用于在全屏地图模组（Xaero's World Map / JourneyMap）中以 **CAD 操作思路**编辑机械动力（Create）铁路。核心价值：把 Create 运行时的 `TrackGraph` 图拓扑以**矢量方式**叠加到地图上，支持规划、编辑、版本控制与多格式导入导出。

## 2. MVP 路线图

### 2.1 整体阶段规划

| 阶段 | 内容 | 状态 | 预估复杂度 | 关键交付物 |
|---|---|---|---|---|
| **Phase 0a** | 骨架 + 数据层 + 投影 + **简化渲染**（MC 原生线）+ Xaero Mixin | 🔲 未开始 | 中 | 地图上能看到轨道拓扑（线条/节点），验证数据链路 |
| **Phase 0b** | NanoVG CADRenderEngine + 主题 + 配置/命令 + 视觉打磨 | 🔲 未开始 | 高 | 矢量平滑抗锯齿、可配线宽/主题、`/kp` 命令体系 |
| **Phase 0.5** | JourneyMap 适配器 + `MapOverlayDispatcher` 熔断 | 🔲 未开始 | 中 | 第二地图适配器，校准 `MapOverlayProvider` 接口 |
| **Phase 1** | 暂存树 + 基础 CAD 编辑工具（拾取/捕捉/绘制） | 🔲 未开始 | 高 | 在地图上编辑轨道规划，暂存树脱离 Create 运行时 |
| **Phase 2** | 高级几何（样条/双圆弧/地形拟合） | 🔲 未开始 | 高 | `EdgeGeometry.ARC`/`SPLINE` 类型启用 |
| **Phase 3** | 规划树 + 类 SVN 版本控制 | 🔲 未开始 | 极高 | 规划分支/合并/回滚，信号段着色 |
| **Phase 4** | 多格式 IO（CAD 原生/NBT/蓝图/Litematica/IFC 4.3 rail 子集） | 🔲 未开始 | 高 | 与主流格式互通 |
| **Phase 5** | 远程铺设 + 路基模板生成 | 🔲 未开始 | 高 | 规划落地到方块世界 |

### 2.2 Phase 0 拆分说明（0a → 0b）

原 Phase 0 设计（spec 2026-07-20）将 8 个能力域一次性交付，风险集中在 NanoVG `CADRenderEngine`——若一上来就上 NanoVG，引擎崩了连数据层是否正确都无法可视化验证。故拆成两个可独立交付的里程碑：

| 里程碑 | 范围 | 渲染方案 | 价值 | 退场条件 |
|---|---|---|---|---|
| **Phase 0a（真 MVP）** | 骨架重命名 + `IRailwayDataAccess` + `WorldScreenTransform` + `XaeroMapOverlayProvider` + **MC 原生 `RenderType.lines()` / `GuiGraphics.fill`** | 1px 线宽，无抗锯齿 | 跑通"数据→投影→叠加"全链路，验证数据访问与 Mixin 注入点 | 地图上能看到轨道线条与节点圆点 |
| **Phase 0b** | `CADRenderEngine`（Blaze3D 轻量图形封装）+ `Theme` + `KPConfig`/`KPCommands` + 视觉打磨 | Blaze3D 矢量渲染，抗锯齿，可配线宽 | 矢量平滑、主题系统、配置/命令体系 | spec 5.1–5.4 全部 23 项验收通过（CADRenderEngine 具体实现待 Blaze3D 封装设计细化） |

> **执行建议：** 按 plan 文档 Task 1–11 顺序执行，但 **Task 9（CADRenderEngine）前先做 Task 7a/10a 的可视化锚点**（`/kp debug dump` + 中心十字线），确认数据层与 Mixin 正确后再上 Blaze3D 矢量渲染。

### 2.3 Phase 0a 实现进度（按 Task 跟踪）

> **Plan 文档：** `docs/superpowers/plans/2026-07-21-kinetic-planner-phase0a.md`（2026-07-21 修订，含 sourceSet 拆分 + MC 原生线渲染 + 可视化锚点）
> **架构变更：** 逻辑客户端/服务端 sourceSet 分离（main=common, client=仅逻辑客户端）；渲染用 MC 原生 `RenderType.lines()` + `GuiGraphics.fill`（Phase 0b 上 Blaze3D 矢量封装）

| Task | 内容 | sourceSet | 状态 | 文档 |
|---|---|---|---|---|
| Task 1 | 骨架重命名 + sourceSet 拆分 + Mixin 基础设施 | both | ✅ 完成 | plan 0a §Task 1 |
| Task 2 | Vec2d | common | ✅ 完成 | plan 0a §Task 2 |
| Task 3 | CameraParams + WorldRect | common | ✅ 完成 | plan 0a §Task 3 |
| Task 4 | WorldScreenTransform | common | ✅ 完成 | plan 0a §Task 4 |
| Task 5 | EdgeGeometry 描述符 | common | ✅ 完成 | plan 0a §Task 5 |
| Task 6 | IRailwayDataAccess 接口 + Stub | common | ✅ 完成 | plan 0a §Task 6 |
| Task 7 | RailwayDataAccess 生产实现 | client | ✅ 完成 | plan 0a §Task 7 |
| Task 8 | MapOverlayProvider + Xaero Mixin | client | ✅ 完成 | plan 0a §Task 8 |
| Task 9 | NativeLineOverlay + 可视化锚点 | client | ✅ 完成 | plan 0a §Task 9 |
| Task 10 | 集成验收（Phase 0a 退场条件） | — | 🔲 未开始 | plan 0a §Task 10 | 

### 2.4 Phase 0b 待规划

> **Plan 文档：** `docs/superpowers/plans/2026-07-21-kinetic-planner-phase0b.md`（待写，Phase 0a 完成后启动）
> **范围：** Blaze3D 矢量渲染封装（CADRenderEngine）+ Theme/ThemeSerializer + KPConfig/KPCommands + spec 5.1-5.4 全 23 项验收
> **关键决策点：** Phase 0a 完成后，0b 动手前再决定 NanoVG vs Blaze3D（spec §4.5 仍是 NanoVG，待统一）

---

## 3. 兼容性矩阵

| 依赖 | 版本范围 | Phase 0 验证版本 | 兼容性风险 | 备注 |
|---|---|---|---|---|
| Minecraft | 1.21.1（锁定） | 1.21.1 | 低 | |
| NeoForge | 21.1.235 | 21.1.235 | 低 | |
| Java | 21（toolchain） | 21 | 低 | |
| Create | `[6.0.10,)` | 6.0.10-280 | **中** | 依赖 `TrackGraph`/`BezierConnection`/`TrackMapManager` 内部类，小版本升级需回归 |
| Ponder | 1.0.82 | 1.0.82 | 低 | |
| Flywheel | `[1.0.0,2.0)` | 1.0.6 | 低 | |
| Cloth Config | 15.0.140 | 15.0.140 | 低 | |
| Xaero's World Map | `[1.0,)`（未锁） | **待实测** | **高** | `GuiMap.render` Mixin 注入点依赖内部签名，跨版本易断裂 |
| XaeroLib | compileOnly | **待实测** | **高** | accessor 字段名依赖 Xaero 内部字段 |
| JourneyMap | `[5.0,)` | Phase 0.5 | 低 | Phase 0 仅占位 |
| Blaze3D（MC 内置渲染引擎） | 内置于 MC 1.21.1 | — | 低 | CAD 渲染将基于 Blaze3D 轻量图形封装（替代 NanoVG），增强 MC 客户端内封装性；实施细节待 Phase 0b 细化 |

### 高风险项：Xaero Mixin 注入点

Xaero 是 Phase 0 唯一地图适配器，其 Mixin 注入点（`GuiMap.render` RETURN + `blit` INVOKE）**未锁版本**。

- Create 自身的 `XaeroFullscreenMapMixin` 注入 `blit` INVOKE 且 `require=0`（容错）；
- 本模组注入 RETURN 位置不同，理论上可共存；
- **但 Xaero 若重构 `render` 方法，两者都会受影响**。

**回归测试要求：** 每次 Xaero 升级后，必须验证：
1. Mixin 注入成功（日志无 `[KP] Xaero GuiMap not found`）；
2. 相机参数换算正确（地图中心十字线与实际中心对齐）；
3. 叠加层跟随缩放/平移正确。
---

## 4. 技术假设核实状态（对照 Create 6.0.10 源码）

| # | 假设 | 结论 | 修正动作 |
|---|---|---|---|
| 1 | `TrackGraph` 字段结构 | ✅属实 | — |
| 2 | `TrackNodeLocation` 压缩公式 | ⚠️ Y 用 `floor*2` 非 `round*2` | spec 1.2/1.3 已修正 |
| 3 | `TrackEdge` 结构 | ✅属实 | — |
| 4 | `EdgePointType.TYPES` 注册表 | ✅属实 | — |
| 5 | `RailwaySavedData` 结构 | ✅属实 | — |
| 6 | `TrackGraph.write` index 句柄 | ✅属实 | — |
| 7 | `BezierConnection` 是二次贝塞尔 | ❌实为**三次** | spec 1.2/4.2/7.5、plan Task 7 已修正 |
| 8 | `BezierConnection` 相对 localTo 序列化 | ✅属实 | — |
| 9 | `compat.trainmap` 三适配器 + 栅格化 | ✅属实 | — |
| 10 | `TrainMapSync` 5tick + 插值 | ✅属实 | — |
| 11 | Xaero Mixin 注入 `blit` INVOKE | ✅属实 | — |
| 12 | `TrackGraph.color` = rainbowColor | ✅属实 | — |
| 13a | `SignalBoundary`→`SignalEdgeGroup.color` | ✅属实 | — |
| 13b | `GlobalStation`→`StationBlock.mapColor` | ❌无此关联 | spec 4.6、plan Task 11 已改为 fallback |
| 13c | `TrackObserver`→`graph.color` | ❌无此关联 | spec 4.6、plan Task 11 已改为 activated 双色 |
| 14 | `EdgeData.getGroupAtPosition` | ✅属实 | — |

**总体：** 14 条中 9 属实 / 2 偏差 / 2 错误 / 1 部分错误。错误项已在 spec/plan 中修正。

### 4.1 外部 DSL 选型变更（2026-07-21）

外部 DSL 已从 **RailML**（非 ISO 开放标准，许可不完全自由）替换为 **IFC 4.3 铁路特化子集**（buildingSMART IFC 4.3, ISO 16739-1，完全开放的国际标准）。技术决策详见 spec §7（已重写）：

- **核心交换层：** IFC-XML 序列化的 IFC 4.3 标准实体（`IfcRailway`/`IfcTrack`/`IfcReferent`/`IfcRelConnectsPathElements`/`IfcBSplineCurveWithKnots`），被任何 IFC 4.3 兼容软件识别
- **Create 特化层：** `IfcPropertySet` 自定义属性集（`kp_` 前缀）承载有向多重边、2 倍压缩坐标、贝塞尔控制点、railx 扩展几何等特有数据
- **拓扑表达：** `IfcRelConnectsPathElements` 表达有向连接（方向/多重边进 PSet）
- **几何表达：** `IfcBSplineCurveWithKnots` 精确表达三次贝塞尔，标准层无损，无需降级采样
- **解析库：** Phase 0 只定义抽象 `IfcExchangeAdapter` 接口，实现推迟 Phase 4

选型理由：中国铁路 BIM 标准基于 IFC 体系，互操作生态更广；RailML 词汇已全面弃用，改用 IFC 实体 + PSet 双层结构。

---

## 5. 未来扩展点预留清单

| 扩展点 | Phase 0 预留动作 | 受益 Phase | 状态 |
|---|---|---|---|
| `EdgeGeometry.ExtensionSpec` | 数据结构已就位，Phase 0 恒 null | Phase 2/4（railx 兼容） | ✅ spec 4.2 |
| `EdgeGeometryProvider` 接口 | 待定义 `@Experimental` 接口 | Phase 2/4 | 🔲 待补 |
| `MapOverlayContext.dpr` | 字段已加 + captureContext 计算 | Phase 0（HiDPI） | ✅ spec 4.3 |
| `IRailwayDataAccess.snapshot()` | spec 备忘，接口待定义 | Phase 1/3（暂存树/版本控制） | 🟡 spec 4.2 已备忘 |
| 主题命名空间 | 数据结构待改 `ns:name` | Phase 1（主题编辑器） | 🔲 待补 |
| `EdgePointType` 渲染钩子 | 遍历 `TYPES.values()` 已保证兼容 | Phase 1（自定义图标） | ✅ spec 4.7 |
| `MapOverlayProvider` 第二实现 | Dispatcher 推迟到 Phase 0.5 | Phase 0.5 | 🟡 MVP 阶段可直接调 XaeroProvider |
| 失败模式矩阵 | spec §8 已新增 | 全 Phase | ✅ spec §8 |

---

## 6. 文档索引

| 文档 | 路径 | 内容 |
|---|---|---|
| Phase 0 设计规格 | `docs/superpowers/specs/2026-07-20-kinetic-planner-phase0-design.md` | 架构、接口契约、验收标准、失败模式、兼容性矩阵（§4.5 仍是 NanoVG，待 0b 统一为 Blaze3D） |
| Phase 0a 实现计划 | `docs/superpowers/plans/2026-07-21-kinetic-planner-phase0a.md` | 10 个 Task：骨架+sourceSet 拆分+MC 原生线叠加+可视化锚点 |
| Phase 0b 实现计划 | `docs/superpowers/plans/2026-07-21-kinetic-planner-phase0b.md`（待写） | Blaze3D 矢量封装+主题+配置命令+23 项验收 |
| 本路线图 | `STATUS.md` | 阶段规划、进度跟踪、兼容性、核实状态 |
| 文档审查报告 | `docs/chat_history/KineticPlanner-文档计划完整审查.md` | 完整审查意见与修订记录 |
| 文档审查与建议 | `docs/chat_history/KineticPlanner-文档审查与建议.md` | README/许可证/文档结构建议 |

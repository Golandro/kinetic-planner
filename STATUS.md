# Kinetic Planner 开发路线图与状态

> **最后更新：** 2026-07-25
> **当前分支：** `1.21`
> **当前状态：** Phase 0a + 0b + P0 重构 + P1.0（Phase A）代码完成，42 个测试（2 个 @Disabled），待运行时验收

---

## 1. 项目定位

Kinetic Planner 是 Minecraft 模组，用于在全屏地图模组（Xaero's World Map / JourneyMap）中以 **CAD 操作思路**编辑机械动力（Create）铁路。核心价值：把 Create 运行时的 `TrackGraph` 图拓扑以**矢量方式**叠加到地图上，支持规划、编辑、版本控制与多格式导入导出。

## 2. MVP 路线图

### 2.1 整体阶段规划

| 阶段 | 内容 | 状态 | 预估复杂度 | 关键交付物 |
|---|---|---|---|---|
| **Phase 0a** | 骨架 + 数据层 + 投影 + **简化渲染**（MC 原生线）+ Xaero Mixin | ✅ 代码完成 | 中 | 地图上能看到轨道拓扑（线条/节点），验证数据链路 |
| **Phase 0b** | Blaze3D CADRenderEngine + 主题 + 配置/命令 + 视觉打磨 | ✅ 代码完成 | 高 | 矢量三角形带粗线、可配线宽/主题、`/kp` 命令体系 |
| **P0 重构** | sourceSet 重构（server）+ 客户端命令迁移 + OverlayControl/ThemeManager 提取 + 14 命令 | ✅ 代码完成 | 中 | 三 sourceSet 架构、14 个 `/kp` 命令节点 |
| **P1.0** | 地图模组独立配置（per-provider）+ 隐藏 Create 信号叠加层 | 🟡 Phase A 完成，Phase B 未开始 | 中 | `/kp provider` CLI + hideCreateTrackMap + CreateTrackVisualizerHiderMixin |
| **Phase 0.5** | JourneyMap 适配器 + `MapOverlayDispatcher` 熔断 | 🟡 占位就绪 | 中 | JM provider 已注册但 `isMapOpen` 返回 false（Mixin 推迟） |
| **Phase 1** | 暂存树 + 基础 CAD 编辑工具（拾取/捕捉/绘制） | 🔲 未开始 | 高 | 在地图上编辑轨道规划，暂存树脱离 Create 运行时 |
| **Phase 2** | 高级几何（样条/双圆弧/地形拟合） | 🔲 未开始 | 高 | `EdgeGeometry.ARC`/`SPLINE` 类型启用 |
| **Phase 3** | 规划树 + 类 SVN 版本控制 | 🔲 未开始 | 极高 | 规划分支/合并/回滚，信号段着色 |
| **Phase 4** | 多格式 IO（CAD 原生/NBT/蓝图/Litematica/IFC 4.3 rail 子集） | 🔲 未开始 | 高 | 与主流格式互通 |
| **Phase 5** | 远程铺设 + 路基模板生成 | 🔲 未开始 | 高 | 规划落地到方块世界 |

### 2.2 Phase 0 拆分说明（0a -> 0b）

原 Phase 0 设计（spec 2026-07-20）将 8 个能力域一次性交付，风险集中在渲染引擎--若一上来就上矢量渲染，引擎崩了连数据层是否正确都无法可视化验证。故拆成两个可独立交付的里程碑：

| 里程碑 | 范围 | 渲染方案 | 价值 | 退场条件 |
|---|---|---|---|---|
| **Phase 0a（真 MVP）** | 骨架重命名 + `IRailwayDataAccess` + `WorldScreenTransform` + `XaeroMapOverlayProvider` + **MC 原生 `RenderType.lines()` / `GuiGraphics.fill`** | 1px 线宽，无抗锯齿 | 跑通"数据->投影->叠加"全链路，验证数据访问与 Mixin 注入点 | 地图上能看到轨道线条与节点圆点 |
| **Phase 0b** | `CADRenderEngine`（Blaze3D 三角形带）+ `Theme` + `KPConfig`/`KPCommands` + 视觉打磨 | Blaze3D 矢量渲染，可配线宽 | 矢量粗线、主题系统、配置/命令体系 | spec 5.1–5.4 验收（CADRenderEngine 用 Blaze3D 三角形带封装，替代原 NanoVG 方案） |

> **渲染方案变更：** 原 spec §4.5 为 NanoVG 方案，Phase 0b 头脑风暴（2026-07-22）确定为 **Blaze3D 薄封装**（三角形带粗线，无外部 native 依赖）。抗锯齿推迟到后续。

### 2.3 Phase 0a 实现进度

> **Plan 文档：** `docs/superpowers/plans/2026-07-21-kinetic-planner-phase0a.md`
> **架构：** sourceSet 分离（main=common, client=仅逻辑客户端）；MC 原生 `RenderType.lines()` + `GuiGraphics.fill`

| Task | 内容 | sourceSet | 状态 |
|---|---|---|---|
| Task 1 | 骨架重命名 + sourceSet 拆分 + Mixin 基础设施 | both | ✅ 完成 |
| Task 2 | Vec2d | common | ✅ 完成 |
| Task 3 | CameraParams + WorldRect | common | ✅ 完成 |
| Task 4 | WorldScreenTransform | common | ✅ 完成 |
| Task 5 | EdgeGeometry 描述符 | common | ✅ 完成 |
| Task 6 | IRailwayDataAccess 接口 + Stub | common | ✅ 完成 |
| Task 7 | RailwayDataAccess 生产实现 | client | ✅ 完成 |
| Task 8 | MapOverlayProvider + Xaero Mixin | client | ✅ 完成 |
| Task 9 | NativeLineOverlay + 可视化锚点 | client | ✅ 完成 |
| Task 10 | 集成验收（Phase 0a 退场条件） | - | 🟡 代码完成，待运行时验收 |

### 2.4 Phase 0b 实现进度

> **Plan 文档：** `docs/superpowers/plans/2026-07-22-kinetic-planner-phase0b.md`（11 个 Task）
> **Spec 文档：** `docs/superpowers/specs/2026-07-22-kinetic-planner-phase0b-design.md`
> **范围：** Blaze3D 三角形带封装（CADRenderEngine）+ Theme/ThemeSerializer + KPConfig/KPCommands + KPClothConfigScreen + spec 5.1-5.4 验收

| Task | 内容 | sourceSet | 状态 |
|---|---|---|---|
| Task 1 | CADRenderEngine（Blaze3D 三角形带）+ GLStateGuard | client | ✅ 完成 |
| Task 2 | Theme record + ThemeSerializer（Gson JSON） | common | ✅ 完成 |
| Task 3 | LineGeometry 三角形带展开纯数学 | common | ✅ 完成 |
| Task 4 | BezierTessellator 三次贝塞尔采样纯数学 | common | ✅ 完成 |
| Task 5 | KPConfig TOML（5 段）+ KPClothConfigScreen GUI | client | ✅ 完成 |
| Task 6 | KPCommands `/kp` 命令体系 | client | ✅ 完成 |
| Task 7 | WorldTreeReadOverlay（替代 NativeLineOverlay）| client | ✅ 完成 |
| Task 8 | TrackGraphAccessor Mixin（@Accessor connectionsByNode）| client | ✅ 完成 |
| Task 9 | GeometryCache + EdgePointColorResolver | client | ✅ 完成 |
| Task 10 | 标签渲染 + Bezier/边点集成 | client | ✅ 完成 |
| Task 11 | 集成验收（Phase 0b 退场条件） | - | 🟡 代码完成，待运行时验收 |

### 2.5 P0 重构与修复

> **Plan 文档：** `docs/superpowers/plans/2026-07-23-kinetic-planner-p0-refactor-fix.md`（5 个 Task，已执行完毕）

| Task | 内容 | 状态 |
|---|---|---|
| Task 1 | sourceSet 重构 -- 添加 server sourceSet | ✅ 完成 |
| Task 2 | WorldTreeReadOverlay 增强 -- OVERLAY_ENABLED 检查 + 统计/诊断方法 | ✅ 完成 |
| Task 3 | ThemeManager -- 主题管理（含纯方法单测） | ✅ 完成 |
| Task 4 | OverlayControl -- 叠加层状态管理 | ✅ 完成 |
| Task 5 | 命令注册迁移（RegisterClientCommandsEvent）+ KPCommands 完整重写（14 命令） | ✅ 完成 |

**P0 交付物：**
- 三 sourceSet 架构（main + client + server）
- 14 个 `/kp` 命令节点：overlay{toggle,enable,disable,reload,status} + theme{list,set,reload,reset} + debug{stats,dump,layer-count,overlay-anchors} + root overview
- OverlayControl / ThemeManager 提取（命令层不直接访问 KPConfig / WorldTreeReadOverlay）
- IWorldEditAccess 接口推迟到 P1（P0 不使用）

### 2.6 P1.0 地图模组独立配置

> **Plan 文档：** `docs/superpowers/plans/2026-07-23-kinetic-planner-p1.0-provider-config.md`
> **范围：** per-provider 配置（enabled/priority/视觉参数）+ 隐藏 Create 信号边组叠加层

#### Phase A: CLI 命令（已完成）

| Task | 内容 | sourceSet | 状态 |
|---|---|---|---|
| A1 | ProviderConfig record + ProviderConfigRegistry | main | ✅ 完成 |
| A2 | KPConfig 扩展 -- per-provider 嵌套段 + hideCreateTrackMap | client | ✅ 完成 |
| A3 | MapOverlayDispatcher 扩展 -- priority 排序 + enabled 过滤 + activeProviderModId | client | ✅ 完成 |
| A4 | MapOverlayProvider 接口扩展（displayName/defaultConfig）+ 各实现补全 | client | ✅ 完成 |
| A5 | WorldTreeReadOverlay 视觉切换 -- per-provider scale | client | ✅ 完成 |
| A6 | CLI 命令 -- `/kp provider` 子命令树 + `/kp overlay hide-create` | client | ✅ 完成 |
| A7 | CreateTrackVisualizerHiderMixin -- 隐藏 Create 信号边组叠加层 | client | ✅ 完成 |

**Phase A 交付物：**
- 8 个新命令节点：provider{list,enable,disable,set,get,get+param,reset} + overlay hide-create{toggle,set}
- ProviderConfig / ProviderConfigRegistry（main，可单测）
- KPConfig `[provider.<modId>]` 嵌套段（预定义 xaeroworldmap / journeymap）
- MapOverlayDispatcher 按 priority 排序 + enabled 过滤
- WorldTreeReadOverlay 根据 active provider 应用 lineWidthScale / alphaScale
- CreateTrackVisualizerHiderMixin（仅隐藏 `visualiseSignalEdgeGroups`，不干涉 `debugViewGraph`）

#### Phase B: 嵌入式 UI（未开始）

| Task | 内容 | 状态 |
|---|---|---|
| B1 | MapGearButtonWidget -- 自绘齿轮按钮 | 🔲 未开始 |
| B2 | ProviderConfigScreen -- 嵌入式配置面板渲染器 | 🔲 未开始 |
| B3 | XaeroMapGearButtonMixin -- Mixin 注入齿轮按钮 + 配置面板 | 🔲 未开始 |

---

## 3. 兼容性矩阵

| 依赖 | 版本范围 | 验证版本 | 兼容性风险 | 备注 |
|---|---|---|---|---|
| Minecraft | 1.21.1（锁定） | 1.21.1 | 低 | |
| NeoForge | 21.1.235 | 21.1.235 | 低 | |
| Java | 21（toolchain） | 21 (Zulu 21.0.11) | 低 | |
| Create | `[6.0.10,)` | 6.0.10-280 | **中** | 依赖 `TrackGraph`/`BezierConnection`/`TrackGraphVisualizer` 内部类，小版本升级需回归 |
| Ponder | 1.0.82 | 1.0.82 | 低 | |
| Flywheel | `[1.0.0,2.0)` | 1.0.6 | 低 | |
| Cloth Config | 15.0.140 | 15.0.140 | 低 | |
| Xaero's World Map | `[1.0,)`（未锁） | curse 7401095 | **高** | `GuiMap.render` Mixin 注入点依赖内部签名，跨版本易断裂 |
| XaeroLib | compileOnly | 1.0.42 | **高** | accessor 字段名依赖 Xaero 内部字段 |
| Xaero's Minimap | 软依赖 | 25.3.2 | 低 | 开发环境配合 World Map 验证 |
| JourneyMap | `[1.21.1-6.0.0-alpha,)` | 1.21.1-6.0.1 | 低 | Phase 0 仅占位；`isMapOpen` 返回 false（Mixin 推迟） |
| JourneyMap API | `2.0.0-1.21.1-20260529.024614-31` | 同左 | 低 | journeymap 6.0.1 主 jar 依赖 `CommonAPI`，旧快照缺该类导致崩溃 |
| Blaze3D（MC 内置渲染引擎） | 内置于 MC 1.21.1 | - | 低 | CAD 渲染基于 Blaze3D 三角形带封装（POSITION_COLOR shader） |
| Modern UI | 开发环境软依赖 | 3.12.0.2 | 低 | 验证字体/UI 兼容性 |
| Create: Steam 'n' Rails | 开发环境软依赖 | 0.3.0-beta | 低 | 验证铁路拓扑叠加 |

### 高风险项：Xaero Mixin 注入点

Xaero 是当前唯一功能完整的地图适配器，其 Mixin 注入点（`GuiMap.render` RETURN + `blit` INVOKE）**未锁版本**。

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
| 1 | `TrackGraph` 字段结构 | ✅属实 | - |
| 2 | `TrackNodeLocation` 压缩公式 | ⚠️ Y 用 `floor*2` 非 `round*2` | spec 1.2/1.3 已修正 |
| 3 | `TrackEdge` 结构 | ✅属实 | - |
| 4 | `EdgePointType.TYPES` 注册表 | ✅属实 | - |
| 5 | `RailwaySavedData` 结构 | ✅属实 | - |
| 6 | `TrackGraph.write` index 句柄 | ✅属实 | - |
| 7 | `BezierConnection` 是二次贝塞尔 | ❌实为**三次** | spec 1.2/4.2/7.5、plan Task 7 已修正 |
| 8 | `BezierConnection` 相对 localTo 序列化 | ✅属实 | - |
| 9 | `compat.trainmap` 三适配器 + 栅格化 | ✅属实 | - |
| 10 | `TrainMapSync` 5tick + 插值 | ✅属实 | - |
| 11 | Xaero Mixin 注入 `blit` INVOKE | ✅属实 | - |
| 12 | `TrackGraph.color` = rainbowColor | ✅属实 | - |
| 13a | `SignalBoundary`->`SignalEdgeGroup.color` | ✅属实 | - |
| 13b | `GlobalStation`->`StationBlock.mapColor` | ❌无此关联 | spec 4.6、plan Task 11 已改为 fallback |
| 13c | `TrackObserver`->`graph.color` | ❌无此关联 | spec 4.6、plan Task 11 已改为 activated 双色 |
| 14 | `EdgeData.getGroupAtPosition` | ✅属实 | - |

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
| `MapOverlayProvider` 第二实现 | Dispatcher 已实现 priority 排序 + enabled 过滤 | Phase 0.5 | ✅ P1.0 已实现 |
| 失败模式矩阵 | spec §8 已新增 | 全 Phase | ✅ spec §8 |
| `IWorldEditAccess` 接口 | main 定义接口，client/server 实现 | Phase 1（编辑引擎） | 🔲 推迟到 P1 |
| `dashed` 渲染 | ProviderConfig.dashed 字段已就位，CADRenderEngine 未应用 | Phase 1.1 | 🟡 字段就绪，渲染推迟 |

---

## 6. 代码统计

| sourceSet | Java 文件 | 说明 |
|---|---|---|
| main (common) | 14 | 纯 JVM，不引用 client/blaze3d，可单测 |
| client | 22 | GUI/渲染/适配器/Mixin/命令 |
| server | 0 | 占位（P1 编辑引擎填充） |
| test | 11 | 42 个 @Test（2 个 @Disabled） |
| **合计** | **47** | |

**Mixin（4 个）：** TrackGraphAccessor / XaeroMapAccessor / XaeroMapRenderHook / CreateTrackVisualizerHiderMixin

**命令节点（~22 个）：**
- P0（14）：`/kp` + overlay{toggle,enable,disable,reload,status} + theme{list,set,reload,reset} + debug{stats,dump,layer-count,overlay-anchors}
- P1.0（~8）：provider{list,enable,disable,set,get,get+param,reset} + overlay hide-create{toggle,set}

---

## 7. 文档索引

| 文档 | 路径 | 内容 |
|---|---|---|
| Phase 0 设计规格 | `docs/superpowers/specs/2026-07-20-kinetic-planner-phase0-design.md` | 架构、接口契约、验收标准、失败模式、兼容性矩阵（§4.5 NanoVG 已被 0b spec 替代为 Blaze3D） |
| Phase 0b 设计规格 | `docs/superpowers/specs/2026-07-22-kinetic-planner-phase0b-design.md` | Blaze3D 薄封装渲染引擎、主题系统、配置/命令体系 |
| 命令树设计规格 | `docs/superpowers/specs/2026-07-23-kinetic-planner-command-tree-design.md` | P0-P3 命令树设计（78 个命令节点 + 12 别名） |
| Phase 0a 实现计划 | `docs/superpowers/plans/2026-07-21-kinetic-planner-phase0a.md` | 10 个 Task：骨架+sourceSet 拆分+MC 原生线叠加+可视化锚点 |
| Phase 0b 实现计划 | `docs/superpowers/plans/2026-07-22-kinetic-planner-phase0b.md` | 11 个 Task：Blaze3D 矢量封装+主题+配置命令+验收 |
| P0 重构计划 | `docs/superpowers/plans/2026-07-23-kinetic-planner-p0-refactor-fix.md` | 5 个 Task：sourceSet 重构+命令迁移+OverlayControl/ThemeManager+14 命令 |
| P1.0 实现计划 | `docs/superpowers/plans/2026-07-23-kinetic-planner-p1.0-provider-config.md` | per-provider 配置+hideCreateTrackMap+嵌入式 UI（Phase A/B） |
| 本路线图 | `STATUS.md` | 阶段规划、进度跟踪、兼容性、核实状态 |
| 编码规范速查 | `docs/conventions.md` | MC 1.21.1 API 约定、Create 6.0.10 API 修正 |
| JavaDoc 查询指南 | `docs/javadoc-guide.md` | 外部依赖 JavaDoc 查询方法 |
| 文档审查报告 | `docs/chat_history/KineticPlanner-文档计划完整审查.md` | 完整审查意见与修订记录 |
| 文档审查与建议 | `docs/chat_history/KineticPlanner-文档审查与建议.md` | README/许可证/文档结构建议 |
| LLM 项目规则 | `AGENTS.md` | AI 编码助手项目上下文（包结构、API 约定、构建命令） |

# Kinetic Planner - Phase 0b 设计规格

**日期：** 2026-07-22
**模组：** Kinetic Planner（`kinetic_planner`）
**阶段：** Phase 0b - Blaze3D 矢量渲染封装 + 主题 + 配置/命令 + 视觉打磨
**前置：** Phase 0a 代码实现完成（Task 1-9），待运行时验收（Task 10）
**目标：** 用 Blaze3D 薄封装替换 MC 原生 1px 线渲染，实现可配线宽粗线、主题系统、配置/命令体系，达到 spec §5.1-5.4 全 23 项验收

---

## 1. 关键决策记录

| 决策点 | 结论 | 理由 |
|---|---|---|
| 渲染引擎 | Blaze3D 薄封装（替代 NanoVG） | 无外部 native 依赖，与 Sodium/Iris 兼容性最好，MC 原生 RenderSystem + BufferBuilder |
| 抗锯齿 | 0b 不做，三角形带粗线先行 | 降低 0b 复杂度，先跳通全链路；AA 推迟到 0b 后期或 Phase 1 |
| 主题系统 | 简化核心子集 | Theme record（线宽/线型/透明度/图层开关）+ JSON 序列化往返；不做命令体系，配置走 TOML |
| 配置/命令 | 完整实现 | TOML 5 段 + `/kp` 命令体系 + Cloth Config GUI |
| 实现策略 | 垂直切片 | 最小端到端通路 -> 逐层叠加，最早视觉反馈，最先验证最难技术点 |

---

## 2. 架构总览

### 2.1 包结构变更（相对于 Phase 0a）

```
net.jsmua.kinetic_planner
├── KineticPlannerMod.java                 [不变]
├── data/
│   ├── IRailwayDataAccess.java            [改：edgesFrom 签名加 TrackGraph 参数]
│   ├── StubRailwayDataAccess.java         [改：跟随接口]
│   ├── RailwayDataAccess.java             [改：实现 edgesFrom via Mixin accessor]
│   └── EdgeGeometry.java                  [不变]
├── projection/                            [全部不变]
├── cadengine/
│   ├── Theme.java                         [新：common，纯数据 record]
│   ├── ThemeSerializer.java               [新：common，Gson JSON 往返]
│   ├── CADRenderEngine.java               [新：client，Blaze3D 矢量封装]
│   └── GLStateGuard.java                  [新：client，RenderSystem 状态快照/恢复]
├── config/
│   ├── KPConfig.java                      [新：client，NeoForge ModConfigSpec TOML]
│   ├── KPCommands.java                    [新：client，/kp 命令注册]
│   └── KPClothConfigScreen.java           [新：client，Cloth Config GUI]
├── mapadapter/
│   └── XaeroMapOverlayProvider.java       [改：修复维度检测]
├── instrument/
│   ├── WorldTreeReadOverlay.java          [新：替换 NativeLineOverlay]
│   ├── NativeLineOverlay.java             [删]
│   ├── GeometryCache.java                 [改：加边数据 + 边点数据]
│   └── EdgePointColorResolver.java        [不变]
└── mixin/
    ├── TrackGraphAccessor.java            [新：@Accessor connectionsByNode]
    └── (现有 Xaero mixins 不变)
```

### 2.2 sourceSet 归属

| sourceSet | 新增/变更类 |
|---|---|
| main (common) | `Theme`, `ThemeSerializer`, `IRailwayDataAccess`(改), `StubRailwayDataAccess`(改) |
| client | `CADRenderEngine`, `GLStateGuard`, `KPConfig`, `KPCommands`, `KPClothConfigScreen`, `WorldTreeReadOverlay`(新), `TrackGraphAccessor`(新 Mixin), `RailwayDataAccess`(改), `XaeroMapOverlayProvider`(改), `GeometryCache`(改) |

### 2.3 关键接口变更

**`IRailwayDataAccess.edgesFrom` 签名变更：**

```java
// 旧（0a）
Stream<TrackEdge> edgesFrom(TrackNode node);

// 新（0b）-- 加 TrackGraph 参数以访问 connectionsByNode
Stream<TrackEdge> edgesFrom(TrackGraph graph, TrackNode node);
```

原因：`TrackGraph.connectionsByNode` 是 package-private，需 Mixin accessor 在 `TrackGraph` 实例上调用。

---

## 3. 详细设计

### 3.1 CADRenderEngine（Blaze3D 矢量封装）

**职责：** 封装 Blaze3D 渲染管线，提供面向 CAD 的高层绘制 API（粗线/折线/贝塞尔/填充），隔离 RenderSystem 状态。Phase 0b 技术核心。

**渲染方案：** 粗线用三角形带（triangle strip）展开。每条线段在屏幕空间沿法线方向偏移 `width/2` 像素，生成 4 个顶点（2 个三角形）。无抗锯齿（0b 不做 AA）。

**核心 API：**

```java
public final class CADRenderEngine {
    // 生命周期
    public void beginFrame(int screenWidth, int screenHeight, float dpr);
    public void endFrame();

    // 世界坐标变换
    public void applyWorldTransform(WorldScreenTransform transform);
    public void restoreWorldTransform();

    // 高层绘制 API（屏幕坐标，变换由 applyWorldTransform 处理）
    public void drawLine(float x1, float y1, float x2, float y2, float widthPx, int color);
    public void drawPolyline(float[] points, float widthPx, int color);
    public void drawBezier(float p0x, float p0y, float p1x, float p1y,
                           float p2x, float p2y, float p3x, float p3y,
                           float widthPx, int color, int segments);
    public void drawFilledCircle(float cx, float cy, float radiusPx, int fillColor);
    public void drawFilledRect(float x, float y, float w, float h, int color);
}
```

**三角形带粗线展开算法：**

```
给定线段 (x1,y1) -> (x2,y2)，宽度 widthPx：
  dx = x2 - x1, dy = y2 - y1
  len = sqrt(dx*dx + dy*dy)
  nx = -dy / len * (widthPx / 2)   // 法线方向
  ny = dx / len * (widthPx / 2)
  
  顶点： (x1+nx, y1+ny), (x1-nx, y1-ny), (x2+nx, y2+ny), (x2-nx, y2-ny)
  三角形带：v1-v2-v3, v2-v4-v3
```

**RenderType 定义：** 自定义 `RenderType` 使用 `POSITION_COLOR` 顶点格式，`RenderSystem.defaultBlendFunc()` 开启 alpha 混合，关闭深度测试。Draw mode = `TRIANGLE_STRIP`。

**GL 状态隔离（GLStateGuard）：**
- `beginFrame` 前：`RenderSystem.enableBlend()`, `RenderSystem.disableDepthTest()`, 记录当前 shader
- `endFrame` 后：恢复 shader，按需恢复 blend/depth 状态
- 不使用 `glPushAttrib`/`glPopAttrib`（GL3+ 核心模式已废弃）

**线宽模式：**
- `constantScreenLineWidth=true`（默认）：`widthPx` 直接作为屏幕像素宽度
- `constantScreenLineWidth=false`：`widthPx` 是世界单位，引擎 `widthPx / blocksPerPixel` 转屏幕像素

**Bezier tessellation：** 给定 4 控制点的三次贝塞尔，按 `segments` 参数均匀采样 t∈[0,1]，生成折线点序列，调 `drawPolyline`。`segments` 默认 32，可配。

**故障安全：** `beginFrame`/`endFrame` 用 `try-finally` 保证 `endFrame` 调用。任何渲染异常 catch 后日志告警，不崩溃。

**字体方案：** 与 0a 一致，所有文字走 MC 管线（`GuiGraphics` + `Font.draw`），CADRenderEngine 不碰字体。

### 3.2 数据层修复

**TrackGraphAccessor Mixin：**

```java
@Mixin(TrackGraph.class)
public interface TrackGraphAccessor {
    @Accessor("connectionsByNode")
    Map<TrackNode, Map<TrackNode, TrackEdge>> kp$getConnectionsByNode();
}
```

**RailwayDataAccess.edgesFrom 实现：**

```java
@Override
public Stream<TrackEdge> edgesFrom(TrackGraph graph, TrackNode node) {
    Map<TrackNode, Map<TrackNode, TrackEdge>> connections =
        ((TrackGraphAccessor) graph).kp$getConnectionsByNode();
    Map<TrackNode, TrackEdge> edges = connections.get(node);
    return edges != null ? edges.values().stream() : Stream.empty();
}
```

**维度检测修复（XaeroMapOverlayProvider.captureContext）：**

从 `acc.kp$mapProcessor().getMapWorld().getCurrentDimension()` 获取当前维度，转换为 `ResourceKey<Level>`。需要确认 Xaero API 的 `getCurrentDimension()` 返回类型。

### 3.3 主题系统（简化核心子集）

**Theme record：**

```java
public record Theme(
    String name,
    GeometryStyle track,
    GeometryStyle node,
    GeometryStyle edgePoint,
    LayerVisibility layers,
    GlobalStyle global
) {
    public record GeometryStyle(float width, boolean dashed, float alpha) {}
    public record LayerVisibility(boolean tracks, boolean nodes, boolean edgePoints) {}
    public record GlobalStyle(
        float minZoomBlocksPerPixel,
        float maxZoomBlocksPerPixel,
        boolean constantScreenLineWidth,
        float fixedScreenLineWidthPx
    ) {}
}
```

相比 spec §4.6 简化：
- `GeometryStyle` 去掉 `LineCap`/`LineJoin`/`dashPattern`（0b 不做线帽/连接/虚线）
- 去掉 `BezierHandleStyle`（0b 不渲染控制点）
- `LayerVisibility` 去掉 `bezierHandles`
- 节点/边点的半径宽度合并到 `GeometryStyle.width`

**默认主题：**
- track: width 2.0px, dashed false, alpha 1.0
- node: width 4.0px, dashed false, alpha 1.0
- edgePoint: width 3.0px, dashed false, alpha 1.0
- layers: tracks/nodes/edgePoints = true
- global: minZoom 0.05, maxZoom 5.0, constantScreenLineWidth true, fixedScreenLineWidthPx 2.0

**颜色：** 不在 Theme 中定义。颜色由 `EdgePointColorResolver` + `TrackGraph.color` 提供（委托 Create）。

**JSON 序列化：** `ThemeSerializer` 使用 Gson，序列化/反序列化 `Theme` record。默认主题写入 `config/kineticplanner/themes/default.json`，启动时读取，缺失则写入默认。

**测试：** 序列化往返测试（Theme -> JSON -> Theme 相等）。

### 3.4 配置与命令

**配置文件：** `config/kineticplanner-client.toml`（NeoForge `ModConfig.Type.CLIENT`）

**配置树（与 spec §4.8 一致）：**

```toml
[overlay]
enabled = true
adapterPriority = ["xaero", "journeymap"]

[theme]
activeTheme = "default"
constantScreenLineWidth = true
fixedScreenLineWidthPx = 2.0
minZoomBlocksPerPixel = 0.05
maxZoomBlocksPerPixel = 5.0

[layers]
tracks = true
nodes = true
edgePoints = true

[label]
showNodeLabels = false
showStationNames = true
labelMinZoomBlocksPerPixel = 1.0

[debug]
showFps = false
showGeometryCount = false
disableGlStateGuard = false
```

**命令体系：**

| 命令 | 功能 |
|---|---|
| `/kp overlay toggle` | 切换叠加开关 |
| `/kp overlay reload` | 重载配置 + 主题 |
| `/kp theme reload` | 仅重载主题 |
| `/kp theme list` | 列出可用主题 |
| `/kp debug stats` | 输出当前帧渲染统计 |

命令注册用 NeoForge `RegisterCommandsEvent`，`/kp` 为根命令，子命令用 `LiteralArgumentBuilder`。

**Cloth Config GUI：** 注册 `ConfigScreenHandler.ConfigScreenFactory`，用 Cloth Config `ConfigBuilder` 构建配置屏幕。提供 overlay 开关、图层开关、主题选择下拉、线宽调节滑条。不提供主题编辑器（Phase 1）。

### 3.5 WorldTreeReadOverlay（替换 NativeLineOverlay）

**职责：** Phase 0b 顶层编排器，整合 `IRailwayDataAccess` + `WorldScreenTransform` + `CADRenderEngine` + `Theme` + `KPConfig`。

**生命周期（每帧）：**

```
ClientTickEvent.Post:
  1. dispatcher.currentContext() -> Optional<MapOverlayContext>
  2. 若空：跳过
  3. 构造 CameraParams + WorldScreenTransform
  4. 脏检测：dataAccess.clientVersion() == lastVersion ? 复用 : 重建
  5. 视锥剔除

Mixin @Inject GuiMap.render 末尾:
  6. 若 context 有效且 overlay enabled:
     a. engine.beginFrame(screenW, screenH, dpr)
     b. engine.applyWorldTransform(transform)
     c. 按图层顺序绘制（tracks -> nodes -> edgePoints）
     d. engine.restoreWorldTransform()
     e. engine.endFrame()
     f. MC 文字层（label）
```

**图层绘制顺序：**

1. **轨道层**（`layers.tracks`）：遍历图，遍历边，去重（`hashCode` 比较）。直线调 `engine.drawLine`，贝塞尔调 `engine.drawBezier`。颜色 `graph.color.getRGB()`
2. **节点层**（`layers.nodes`）：遍历节点，`engine.drawFilledCircle`。颜色 `graph.color` 混白
3. **边点层**（`layers.edgePoints`）：遍历 `EdgePointType.TYPES.values()`，颜色委托 `EdgePointColorResolver`
4. **MC 文字层**：节点/车站 label，`font.draw`，仅 `blocksPerPixel < threshold` 时绘制

**线宽计算：**
- `constantScreenLineWidth=true`：`engine.drawLine(..., theme.track.width(), color)` -- 直接用 Theme 宽度作为屏幕像素
- `constantScreenLineWidth=false`：`engine.drawLine(..., theme.track.width() / blocksPerPixel, color)` -- 世界单位转屏幕像素

**GeometryCache 增强：**
- `GraphGeometry` 增加 `List<EdgeGeometry> edges`（0a 为空列表）
- 增加 `List<EdgePointData>` 存边点世界坐标 + 颜色
- 脏检测 key 不变：`(clientVersion, dimension)`

---

## 4. 验收标准映射

Phase 0b 需通过 spec §5.1-5.4 全 23 项验收。以下为关键项与 0b 实现的映射：

### 5.1 功能验收（1-12）

| # | 验收项 | 0b 实现 |
|---|---|---|
| 1-2 | 装载+打开地图，显示当前维度轨道拓扑 | edgesFrom 修复 + WorldTreeReadOverlay |
| 3 | 轨道平滑（直线+贝塞尔）抗锯齿 | Blaze3D 三角形带粗线（AA 推迟，0b 先做粗线） |
| 4 | 节点圆点（填充图色+白外圈） | drawFilledCircle |
| 5 | 边点彩色圆点（委托 Create 颜色） | EdgePointColorResolver |
| 6 | 缩放时线宽按配置固定/随缩放 | constantScreenLineWidth |
| 7 | 平移跟随 | WorldScreenTransform |
| 8 | 维度切换 | 维度检测修复 |
| 9 | 建造/拆除反映变化 | 脏检测 |
| 10 | 关闭叠加开关 | KPConfig overlay.enabled + /kp overlay toggle |
| 11 | 车站名标签 | MC Font.draw |
| 12 | 字体走 MC 管线 | 不碰字体 |

### 5.2 隔离与稳定性（13-16）

| # | 验收项 | 0b 实现 |
|---|---|---|
| 13 | 引擎初始化失败不崩溃 | CADRenderEngine try-finally + 降级 |
| 14 | Xaero Mixin 失败熔断 | MapOverlayDispatcher（0a 已有） |
| 15 | 渲染异常 try-finally 保证 endFrame | GLStateGuard + try-finally |
| 16 | 与 Create 叠加层共存 | 不同 RenderType，不冲突 |

### 5.3 性能（17-19）

| # | 验收项 | 0b 实现 |
|---|---|---|
| 17 | <500 节点渲染 <2ms | 单批次 BufferBuilder 提交 |
| 18 | 缩放/平移 60fps | 几何缓存 + 脏检测 |
| 19 | 地图关闭释放缓存 | GeometryCache.clear() |

### 5.4 架构（20-23）

| # | 验收项 | 0b 实现 |
|---|---|---|
| 20 | IRailwayDataAccess 接口分离 | StubRailwayDataAccess（改签名） |
| 21 | WorldScreenTransform 单测 | 0a 已有 |
| 22 | Theme 序列化往返测试 | ThemeSerializer |
| 23 | 包结构无跨层依赖 | sourceSet 分离 |

### 0b 特有放宽

- **#3 抗锯齿：** 0b 不做 AA，验收标准放宽为"可配线宽粗线，锯齿可接受"。AA 推迟到后续。
- **#13 NanoVG 初始化失败：** 改为"CADRenderEngine 渲染异常不崩溃"（无 NanoVG）。

---

## 5. 垂直切片实现序列

| 切片 | 内容 | 可视化产出 |
|---|---|---|
| **Slice 1** | 修 edgesFrom + 维度检测 + TrackGraphAccessor Mixin | 数据层正确（可 debug dump 验证） |
| **Slice 2** | CADRenderEngine 最小版（三角形带 drawLine + drawFilledCircle）+ WorldTreeReadOverlay 替换 NativeLineOverlay | 地图上看到粗线 + 圆点 |
| **Slice 3** | Theme record + ThemeSerializer + 集成到渲染 | 可配线宽/颜色 |
| **Slice 4** | KPConfig TOML + /kp 命令 + Cloth Config GUI | 配置驱动 + 命令控制 |
| **Slice 5** | Bezier tessellation + 边点渲染 + label | 完整视觉 |
| **Slice 6** | 打磨 + 23 项验收 | 验收通过 |

---

## 6. 与 Phase 0 spec 的差异说明

本规格是对 Phase 0 spec（2026-07-20）§4.5 的更新，替换内容如下：

| spec 原文 | 本规格更新 |
|---|---|
| §4.5 NanoVG 封装 | Blaze3D 薄封装（三角形带粗线，无 AA） |
| §4.5 GLStateGuard NanoVG GL 状态隔离 | GLStateGuard RenderSystem 状态隔离 |
| §4.5 NanoVG nvgBezierTo 原生贝塞尔 | Bezier tessellation 手动采样 |
| §4.5 NVG_ANTIALIAS / NVG_STENCIL_STROKES | 0b 不做 AA |
| §4.6 完整 Theme（GeometryStyle 6 字段 + BezierHandleStyle） | 简化 Theme（GeometryStyle 3 字段，无 BezierHandleStyle） |
| §4.8 配置/命令 | 完整实现（与原文一致） |

spec §4.5 的 NanoVG 描述在本规格生效后视为被替代。后续 Phase 如需 AA，再评估 NanoVG vs 几何 AA vs MSAA。

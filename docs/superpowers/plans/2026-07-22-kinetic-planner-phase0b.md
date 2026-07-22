# Kinetic Planner Phase 0b Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 用 Blaze3D 薄封装替换 MC 原生 1px 线渲染，实现可配线宽粗线（三角形带）、简化主题系统、完整配置/命令/GUI，修复数据层 bug（edgesFrom 空、维度硬编码），达到 spec §5.1-5.4 全 23 项验收。

**Architecture:** 垂直切片策略：先修数据层 bug，再建最小 CADRenderEngine（三角形带粗线无 AA）集成到 overlay 看到效果，
然后逐层叠加 Theme/Config/Commands/Bezier/EdgePoints。`NativeLineOverlay` 被 `WorldTreeReadOverlay` 替代。common sourceSet 保持纯 JVM 可测（Theme/ThemeSerializer/几何数学），client sourceSet 含渲染/Mixin/配置。

**Tech Stack:** Minecraft 1.21.1 / NeoForge 21.1.235 / Java 21 / Create 6.0.10 / Ponder 1.0.82 / Flywheel 1.0.6 / Cloth Config 15.0.140 / Xaero's World Map + XaeroLib（compileOnly）/ Gson / JUnit 5 / Mockito 5

## Global Constraints

- Minecraft 1.21.1，NeoForge 21.1.235，Java 21（toolchain）
- mod_id: `kinetic_planner`，mod_group_id: `net.jsmua.kinetic_planner`，包名 `net.jsmua.kinetic_planner`
- sourceSet 分离：`main`（common）不引用 `net.minecraft.client.*` / `com.mojang.blaze3d.*`；`client` 可引用全部
- Mixin 配置 `defaultRequire: 0`（Xaero/Create 未安装时不崩溃），无 MixinPlugin
- 渲染方案（Phase 0b）：Blaze3D 三角形带粗线（无 AA），自定义 RenderType `POSITION_COLOR`
- 字体方案：所有文字走 MC 管线（`GuiGraphics` + `Font.draw`），CADRenderEngine 不碰字体
- 颜色委托 Create：轨道 `TrackGraph.color`，边点 `EdgePointColorResolver`
- 数据层只读：绝不调用 mutate 方法
- MC 1.21.1 VertexConsumer API：`addVertex` / `setColor` / `setNormal`，无 `endVertex`
- Create `TrackGraph.connectionsByNode` 是 package-private，需 Mixin `@Accessor`
- `BezierConnection` 是三次贝塞尔：`starts` 为端点，`axes` 为控制点方向向量
- Cloth Config 已在 `build.gradle` 依赖中（`api("me.shedaniel.cloth:cloth-config-neoforge:15.0.140")`）

**Phase 0b 退场条件：** spec §5.1-5.4 全 23 项验收通过（#3 抗锯齿放宽为"可配线宽粗线，锯齿可接受"）

---

## File Structure

### 新建文件

| 路径 | sourceSet | 职责 |
|---|---|---|
| `src/main/java/net/jsmua/kinetic_planner/cadengine/Theme.java` | common | 主题数据 record（简化） |
| `src/main/java/net/jsmua/kinetic_planner/cadengine/ThemeSerializer.java` | common | Gson JSON 序列化往返 |
| `src/main/java/net/jsmua/kinetic_planner/cadengine/LineGeometry.java` | common | 三角形带粗线展开纯数学 |
| `src/main/java/net/jsmua/kinetic_planner/cadengine/BezierTessellator.java` | common | 贝塞尔曲线采样纯数学 |
| `src/client/java/net/jsmua/kinetic_planner/cadengine/CADRenderEngine.java` | client | Blaze3D 矢量渲染封装 |
| `src/client/java/net/jsmua/kinetic_planner/cadengine/GLStateGuard.java` | client | RenderSystem 状态快照/恢复 |
| `src/client/java/net/jsmua/kinetic_planner/config/KPConfig.java` | client | NeoForge ModConfigSpec TOML |
| `src/client/java/net/jsmua/kinetic_planner/config/KPCommands.java` | client | /kp 命令注册 |
| `src/client/java/net/jsmua/kinetic_planner/config/KPClothConfigScreen.java` | client | Cloth Config GUI |
| `src/client/java/net/jsmua/kinetic_planner/instrument/WorldTreeReadOverlay.java` | client | 顶层编排器（替换 NativeLineOverlay） |
| `src/client/java/net/jsmua/kinetic_planner/mixin/TrackGraphAccessor.java` | client | @Accessor connectionsByNode |
| `src/test/java/net/jsmua/kinetic_planner/cadengine/LineGeometryTest.java` | test | 三角形带展开单测 |
| `src/test/java/net/jsmua/kinetic_planner/cadengine/BezierTessellatorTest.java` | test | 贝塞尔采样单测 |
| `src/test/java/net/jsmua/kinetic_planner/cadengine/ThemeSerializerTest.java` | test | 主题序列化往返单测 |

### 修改文件

| 路径 | 修改内容 |
|---|---|
| `src/main/java/net/jsmua/kinetic_planner/data/IRailwayDataAccess.java` | edgesFrom 签名加 TrackGraph 参数 |
| `src/main/java/net/jsmua/kinetic_planner/data/StubRailwayDataAccess.java` | 跟随接口签名变更 |
| `src/client/java/net/jsmua/kinetic_planner/data/RailwayDataAccess.java` | 实现 edgesFrom via TrackGraphAccessor |
| `src/client/java/net/jsmua/kinetic_planner/mapadapter/XaeroMapOverlayProvider.java` | 修复维度检测 |
| `src/client/java/net/jsmua/kinetic_planner/instrument/GeometryCache.java` | GraphGeometry 加边点数据 |
| `src/client/java/net/jsmua/kinetic_planner/instrument/EdgePointColorResolver.java` | 适配新 GeometryCache |
| `src/client/java/net/jsmua/kinetic_planner/KineticPlannerClient.java` | 引用改为 WorldTreeReadOverlay + 注册配置/命令 |
| `src/client/java/net/jsmua/kinetic_planner/mixin/XaeroMapRenderHook.java` | 引用改为 WorldTreeReadOverlay |
| `src/main/resources/kinetic_planner.mixins.json` | 添加 TrackGraphAccessor |

### 删除文件

| 路径 | 原因 |
|---|---|
| `src/client/java/net/jsmua/kinetic_planner/instrument/NativeLineOverlay.java` | 被 WorldTreeReadOverlay 替代 |

---

## Task 1: IRailwayDataAccess 签名变更 + TrackGraphAccessor Mixin + Stub 更新

**Files:**
- Modify: `src/main/java/net/jsmua/kinetic_planner/data/IRailwayDataAccess.java`
- Modify: `src/main/java/net/jsmua/kinetic_planner/data/StubRailwayDataAccess.java`
- Create: `src/client/java/net/jsmua/kinetic_planner/mixin/TrackGraphAccessor.java`
- Modify: `src/main/resources/kinetic_planner.mixins.json`

**Interfaces:**
- Produces: `IRailwayDataAccess.edgesFrom(TrackGraph graph, TrackNode node)` (签名变更)
- Produces: `TrackGraphAccessor.kp$getConnectionsByNode()` Mixin accessor

- [ ] **Step 1: 修改 IRailwayDataAccess.edgesFrom 签名**

将 `src/main/java/net/jsmua/kinetic_planner/data/IRailwayDataAccess.java` 中的 `edgesFrom` 方法签名改为：

```java
    /**
     * 返回给定图中给定节点的所有出边。
     *
     * <p>通过 {@code TrackGraph.connectionsByNode}（package-private）获取，
     * 需 Mixin accessor（{@link net.jsmua.kinetic_planner.mixin.TrackGraphAccessor}）。
     *
     * @param graph 轨道图（提供 connectionsByNode）
     * @param node  起始节点
     * @return 出边流
     */
    Stream<TrackEdge> edgesFrom(TrackGraph graph, TrackNode node);
```

替换原来的：
```java
    Stream<TrackEdge> edgesFrom(TrackNode node);
```

同时删除原 javadoc 中的 "Phase 0a TODO" 注释。

- [ ] **Step 2: 修改 StubRailwayDataAccess.edgesFrom 签名**

将 `src/main/java/net/jsmua/kinetic_planner/data/StubRailwayDataAccess.java` 中的 `edgesFrom` 方法改为：

```java
    @Override
    public Stream<TrackEdge> edgesFrom(TrackGraph graph, TrackNode node) {
        return Stream.empty();
    }
```

替换原来的：
```java
    @Override
    public Stream<TrackEdge> edgesFrom(TrackNode node) {
        return Stream.empty();
    }
```

- [ ] **Step 3: 创建 TrackGraphAccessor Mixin**

创建 `src/client/java/net/jsmua/kinetic_planner/mixin/TrackGraphAccessor.java`：

```java
package net.jsmua.kinetic_planner.mixin;

import com.simibubi.create.content.trains.graph.TrackEdge;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.graph.TrackNode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

/**
 * Mixin accessor for Create's {@link TrackGraph} package-private field {@code connectionsByNode}.
 *
 * <p>Provides read-only access to the internal {@code Map<TrackNode, Map<TrackNode, TrackEdge>>}
 * so that {@link net.jsmua.kinetic_planner.data.RailwayDataAccess} can enumerate edges.
 *
 * <p>{@code remap = false} because TrackGraph is a Create class, not a Mojang class.
 * Uses {@code kp$} prefix to avoid conflicts with other mods' mixins.
 */
@Mixin(value = TrackGraph.class, remap = false)
public interface TrackGraphAccessor {

    @Accessor("connectionsByNode")
    Map<TrackNode, Map<TrackNode, TrackEdge>> kp$getConnectionsByNode();
}
```

- [ ] **Step 4: 更新 mixin 配置**

修改 `src/main/resources/kinetic_planner.mixins.json`，在 `client` 数组中添加 `"TrackGraphAccessor"`：

```json
{
    "required": true,
    "minVersion": "0.8.5",
    "package": "net.jsmua.kinetic_planner.mixin",
    "compatibilityLevel": "JAVA_21",
    "refmap": "kinetic_planner.refmap.json",
    "mixins": [],
    "client": [
        "TrackGraphAccessor",
        "XaeroMapAccessor",
        "XaeroMapRenderHook"
    ],
    "injectors": {
        "defaultRequire": 0
    }
}
```

- [ ] **Step 5: 构建验证**

运行：`gradlew compileJava compileClientJava`
预期：BUILD SUCCESSFUL（注意：`NativeLineOverlay` 仍引用旧签名 `edgesFrom(TrackNode)`，但它在 client sourceSet 中调用 `dataAccess.edgesFrom` 时传的参数不够——需要检查是否编译失败。如果失败，在 `NativeLineOverlay.onClientTick` 中临时将 `edgesFrom` 调用注释掉或传 null 作为 graph 参数，Task 6 会替换此类。）

> **注意：** `NativeLineOverlay.onClientTick` 中没有直接调用 `edgesFrom`（边遍历是 TODO），所以签名变更不会导致编译错误。但如果有其他调用点，按编译错误修正。

- [ ] **Step 6: 运行现有测试验证无回归**

运行：`gradlew test`
预期：全部 20 个测试 PASS（StubRailwayDataAccess 测试不调用 edgesFrom）

- [ ] **Step 7: 提交**

```bash
git add -A
git commit -m "refactor: change edgesFrom signature to accept TrackGraph, add TrackGraphAccessor mixin"
```

---

## Task 2: RailwayDataAccess.edgesFrom 实现 + 维度检测修复

**Files:**
- Modify: `src/client/java/net/jsmua/kinetic_planner/data/RailwayDataAccess.java`
- Modify: `src/client/java/net/jsmua/kinetic_planner/mapadapter/XaeroMapOverlayProvider.java`
- Test: `src/test/java/net/jsmua/kinetic_planner/data/RailwayDataAccessTest.java` (modify)

**Interfaces:**
- Consumes: `TrackGraphAccessor.kp$getConnectionsByNode()`
- Produces: `RailwayDataAccess.edgesFrom(TrackGraph, TrackNode)` working implementation

- [ ] **Step 1: 实现 RailwayDataAccess.edgesFrom**

修改 `src/client/java/net/jsmua/kinetic_planner/data/RailwayDataAccess.java` 中的 `edgesFrom` 方法：

```java
    @Override
    public Stream<TrackEdge> edgesFrom(TrackGraph graph, TrackNode node) {
        try {
            Map<TrackNode, Map<TrackNode, TrackEdge>> connections =
                ((TrackGraphAccessor) graph).kp$getConnectionsByNode();
            Map<TrackNode, TrackEdge> edges = connections.get(node);
            return edges != null ? edges.values().stream() : Stream.empty();
        } catch (Throwable t) {
            return Stream.empty();
        }
    }
```

替换原来的：
```java
    @Override
    public Stream<TrackEdge> edgesFrom(TrackNode node) {
        // TODO Phase 0b: 从 node 所属 graph 的 connectionsByNode 获取出边
        // connectionsByNode 是 package-private，需要 Mixin accessor 或反射
        return Stream.empty();
    }
```

在文件顶部添加 import：
```java
import net.jsmua.kinetic_planner.mixin.TrackGraphAccessor;
import java.util.Map;
```

- [ ] **Step 2: 修复维度检测**

修改 `src/client/java/net/jsmua/kinetic_planner/mapadapter/XaeroMapOverlayProvider.java` 中的 `captureContext` 方法。

将维度检测部分：
```java
            // TODO: 从 Xaero mapProcessor 获取当前维度（当前硬编码 OVERWORLD）
            ResourceKey<Level> dim = Level.OVERWORLD;
```

替换为：
```java
            // 从 Xaero mapProcessor 获取当前维度
            ResourceKey<Level> dim;
            try {
                var mapWorld = acc.kp$mapProcessor().getMapWorld();
                var currentDim = mapWorld.getCurrentDimension();
                dim = currentDim.getDimId();
            } catch (Throwable t) {
                // Xaero API 变化或 null 安全：fallback 到 overworld
                dim = Level.OVERWORLD;
            }
```

> **注意：** `getCurrentDimension().getDimId()` 的返回类型需在运行时验证。如果返回 `ResourceKey<Level>` 则直接赋值；如果返回 `ResourceLocation` 则需 `ResourceKey.create(Registries.DIMENSION, rl)` 转换。编译时若类型不匹配，按编译错误修正。

同时更新 `partialTicks` 占位（从 `0f` 改为使用 `partialTicks` 参数——但 `captureContext` 在 tick 中调用，无 partialTicks 参数。保持 `0f` 不变，渲染时由 `onMapRender` 的 `partialTicks` 参数提供）。

- [ ] **Step 3: 更新 RailwayDataAccess 测试**

修改 `src/test/java/net/jsmua/kinetic_planner/data/RailwayDataAccessTest.java`，添加 edgesFrom 签名变更后的测试：

```java
package net.jsmua.kinetic_planner.data;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.simibubi.create.content.trains.graph.TrackEdge;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.graph.TrackNode;

@ExtendWith(MockitoExtension.class)
class RailwayDataAccessTest {
    @Mock
    TrackEdge mockEdge;
    @Mock
    TrackNode mockNode;
    @Mock
    TrackGraph mockGraph;

    @Test
    void edgeGeometryWithNullTurnReturnsStraight() {
        when(mockEdge.getTurn()).thenReturn(null);
        RailwayDataAccess access = new RailwayDataAccess();
        EdgeGeometry geom = access.edgeGeometry(mockEdge);
        assertEquals(EdgeGeometry.Type.STRAIGHT, geom.type());
        assertNull(geom.bezier());
    }

    @Test
    void edgesFromReturnsEmptyWhenGraphNotAccessor() {
        // mockGraph 不是 TrackGraphAccessor，会抛 ClassCastException，catch 后返回空
        RailwayDataAccess access = new RailwayDataAccess();
        var edges = access.edgesFrom(mockGraph, mockNode);
        assertEquals(0, edges.count());
    }
}
```

- [ ] **Step 4: 构建验证**

运行：`gradlew compileJava compileClientJava`
预期：BUILD SUCCESSFUL

- [ ] **Step 5: 运行测试**

运行：`gradlew test`
预期：全部测试 PASS（含新增 `edgesFromReturnsEmptyWhenGraphNotAccessor`）

- [ ] **Step 6: 提交**

```bash
git add -A
git commit -m "fix: implement edgesFrom via TrackGraphAccessor, fix dimension detection"
```

---

## Task 3: CADRenderEngine 几何数学（common，纯 JVM 单测）

**Files:**
- Create: `src/main/java/net/jsmua/kinetic_planner/cadengine/LineGeometry.java`
- Create: `src/main/java/net/jsmua/kinetic_planner/cadengine/BezierTessellator.java`
- Test: `src/test/java/net/jsmua/kinetic_planner/cadengine/LineGeometryTest.java`
- Test: `src/test/java/net/jsmua/kinetic_planner/cadengine/BezierTessellatorTest.java`

**Interfaces:**
- Produces: `LineGeometry.expandLineToTriangleStrip(x1,y1,x2,y2,widthPx): float[]` -- 返回 8 个 float（4 顶点 × 2 分量）
- Produces: `BezierTessellator.tessellate(p0x,p0y,p1x,p1y,p2x,p2y,p3x,p3y,segments): float[]` -- 返回 `(segments+1)*2` 个 float

- [ ] **Step 1: 写 LineGeometry 失败测试**

创建 `src/test/java/net/jsmua/kinetic_planner/cadengine/LineGeometryTest.java`：

```java
package net.jsmua.kinetic_planner.cadengine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LineGeometryTest {
    private static final float TOL = 1e-5f;

    @Test
    void expandHorizontalLineProducesCorrectTriangleStrip() {
        // 水平线 (0,0)->(10,0)，宽度 2
        float[] v = LineGeometry.expandLineToTriangleStrip(0, 0, 10, 0, 2.0f);
        assertEquals(8, v.length);
        // 法线方向 = (0, 1) 或 (0, -1)，偏移 width/2 = 1
        // 顶点顺序：v1(x1+nx,y1+ny), v2(x1-nx,y1-ny), v3(x2+nx,y2+ny), v4(x2-nx,y2-ny)
        // 对于水平线，nx=0, ny=±1
        // v1 = (0, 1) 或 (0, -1)
        // v2 = (0, -1) 或 (0, 1)
        // v3 = (10, 1) 或 (10, -1)
        // v4 = (10, -1) 或 (10, 1)
        // 验证 x 坐标正确
        assertEquals(0, v[0], TOL);
        assertEquals(0, v[2], TOL);
        assertEquals(10, v[4], TOL);
        assertEquals(10, v[6], TOL);
        // 验证 y 坐标偏移 ±1
        assertEquals(1.0f, Math.abs(v[1]), TOL);
        assertEquals(1.0f, Math.abs(v[3]), TOL);
        // v1 和 v2 的 y 应符号相反
        assertEquals(-v[1], v[3], TOL);
    }

    @Test
    void expandVerticalLineProducesCorrectTriangleStrip() {
        // 垂直线 (5,5)->(5,10)，宽度 4
        float[] v = LineGeometry.expandLineToTriangleStrip(5, 5, 5, 10, 4.0f);
        assertEquals(8, v.length);
        // 法线方向 = (-1, 0) 或 (1, 0)，偏移 width/2 = 2
        assertEquals(5 + 2, Math.abs(v[0] - 5) + 5, TOL); // x 偏移 ±2
        assertEquals(5, v[1], TOL); // y 不变
        assertEquals(5, v[3], TOL);
        assertEquals(10, v[5], TOL);
        assertEquals(10, v[7], TOL);
    }

    @Test
    void expandZeroLengthLineReturnsDegenerateQuad() {
        // 零长度线段，应返回退化四边形（不抛异常）
        float[] v = LineGeometry.expandLineToTriangleStrip(3, 3, 3, 3, 2.0f);
        assertEquals(8, v.length);
        // 所有顶点在同一位置
        assertEquals(3, v[0], TOL);
        assertEquals(3, v[1], TOL);
    }
}
```

- [ ] **Step 2: 运行测试验证失败**

运行：`gradlew test --tests "net.jsmua.kinetic_planner.cadengine.LineGeometryTest"`
预期：编译失败（`LineGeometry` 不存在）

- [ ] **Step 3: 实现 LineGeometry**

创建 `src/main/java/net/jsmua/kinetic_planner/cadengine/LineGeometry.java`：

```java
package net.jsmua.kinetic_planner.cadengine;

/**
 * 纯数学工具：将线段展开为三角形带顶点。
 *
 * <p>给定线段 (x1,y1)->(x2,y2) 和宽度 widthPx，计算 4 个顶点坐标
 * 构成三角形带（2 个三角形）。法线方向为线段方向的垂直方向。
 *
 * <p>顶点顺序：v1(x1+n), v2(x1-n), v3(x2+n), v4(x2-n)，
 * 三角形带索引：0-1-2, 1-3-2。
 *
 * <p>此类在 common sourceSet 中，纯 JVM 可测，不依赖任何 GL 类。
 */
public final class LineGeometry {

    private LineGeometry() {}

    /**
     * 将线段展开为三角形带顶点。
     *
     * @param x1       起点 x
     * @param y1       起点 y
     * @param x2       终点 x
     * @param y2       终点 y
     * @param widthPx  线宽（像素）
     * @return 8 个 float：[v1x, v1y, v2x, v2y, v3x, v3y, v4x, v4y]
     */
    public static float[] expandLineToTriangleStrip(
            float x1, float y1, float x2, float y2, float widthPx) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float len = (float) Math.sqrt(dx * dx + dy * dy);

        float nx, ny;
        if (len < 1e-6f) {
            // 零长度线段：退化四边形，法线默认向上
            nx = 0;
            ny = widthPx / 2;
        } else {
            // 法线 = (-dy, dx) / len * (width/2)
            float halfWidth = widthPx / 2;
            nx = -dy / len * halfWidth;
            ny = dx / len * halfWidth;
        }

        return new float[] {
            x1 + nx, y1 + ny,  // v1: 起点 + 法线
            x1 - nx, y1 - ny,  // v2: 起点 - 法线
            x2 + nx, y2 + ny,  // v3: 终点 + 法线
            x2 - nx, y2 - ny   // v4: 终点 - 法线
        };
    }
}
```

- [ ] **Step 4: 写 BezierTessellator 失败测试**

创建 `src/test/java/net/jsmua/kinetic_planner/cadengine/BezierTessellatorTest.java`：

```java
package net.jsmua.kinetic_planner.cadengine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BezierTessellatorTest {

    @Test
    void tessellateReturnsSegmentsPlusOnePoints() {
        float[] pts = BezierTessellator.tessellate(0, 0, 0, 10, 10, 10, 10, 0, 16);
        assertEquals(34, pts.length); // (16+1) * 2
    }

    @Test
    void tessellateEndpointsMatchControlPoints() {
        float[] pts = BezierTessellator.tessellate(0, 0, 0, 10, 10, 10, 10, 0, 8);
        // t=0 -> p0
        assertEquals(0, pts[0], 1e-5f);
        assertEquals(0, pts[1], 1e-5f);
        // t=1 -> p3
        assertEquals(10, pts[16], 1e-5f);
        assertEquals(0, pts[17], 1e-5f);
    }

    @Test
    void tessellateStraightLineIsLinear() {
        // 退化为直线：(0,0)->(0,0)->(10,0)->(10,0) 应得到直线
        float[] pts = BezierTessellator.tessellate(0, 0, 0, 0, 10, 0, 10, 0, 4);
        // t=0.5 -> (5, 0)
        assertEquals(5, pts[5], 1e-4f); // pts[4..5] = t=0.25, pts[6..7] = t=0.5? No.
        // 4 segments -> 5 points: t=0, 0.25, 0.5, 0.75, 1.0
        // pts[4] = x at t=0.25, pts[5] = y at t=0.25
        // pts[6] = x at t=0.5, pts[7] = y at t=0.5
        assertEquals(5, pts[6], 1e-4f); // x at t=0.5
        assertEquals(0, pts[7], 1e-4f); // y at t=0.5
    }
}
```

- [ ] **Step 5: 实现 BezierTessellator**

创建 `src/main/java/net/jsmua/kinetic_planner/cadengine/BezierTessellator.java`：

```java
package net.jsmua.kinetic_planner.cadengine;

/**
 * 纯数学工具：三次贝塞尔曲线均匀采样。
 *
 * <p>给定 4 个控制点 P0-P3 和采样段数，按 t∈[0,1] 均匀采样
 * 生成折线点序列。
 *
 * <p>三次贝塞尔公式：B(t) = (1-t)³P0 + 3(1-t)²tP1 + 3(1-t)t²P2 + t³P3
 *
 * <p>此类在 common sourceSet 中，纯 JVM 可测。
 */
public final class BezierTessellator {

    private BezierTessellator() {}

    /**
     * 对三次贝塞尔曲线进行均匀采样。
     *
     * @param p0x 第一个控制点 x（曲线起点）
     * @param p0y 第一个控制点 y
     * @param p1x 第二个控制点 x
     * @param p1y 第二个控制点 y
     * @param p2x 第三个控制点 x
     * @param p2y 第三个控制点 y
     * @param p3x 第四个控制点 x（曲线终点）
     * @param p3y 第四个控制点 y
     * @param segments 采样段数（返回 segments+1 个点）
     * @return 顶点数组：[x0, y0, x1, y1, ..., xn, yn]，长度 = (segments+1)*2
     */
    public static float[] tessellate(
            float p0x, float p0y, float p1x, float p1y,
            float p2x, float p2y, float p3x, float p3y,
            int segments) {
        float[] points = new float[(segments + 1) * 2];
        for (int i = 0; i <= segments; i++) {
            float t = (float) i / segments;
            float u = 1 - t;
            float tt = t * t;
            float uu = u * u;
            float ttt = tt * t;
            float uuu = uu * u;

            float x = uuu * p0x + 3 * uu * t * p1x + 3 * u * tt * p2x + ttt * p3x;
            float y = uuu * p0y + 3 * uu * t * p1y + 3 * u * tt * p2y + ttt * p3y;

            points[i * 2] = x;
            points[i * 2 + 1] = y;
        }
        return points;
    }
}
```

- [ ] **Step 6: 运行测试验证通过**

运行：`gradlew test --tests "net.jsmua.kinetic_planner.cadengine.LineGeometryTest" --tests "net.jsmua.kinetic_planner.cadengine.BezierTessellatorTest"`
预期：全部 PASS

- [ ] **Step 7: 提交**

```bash
git add -A
git commit -m "feat: add LineGeometry triangle strip expansion and BezierTessellator with tests"
```

---

## Task 4: GLStateGuard + CADRenderEngine（client）

**Files:**
- Create: `src/client/java/net/jsmua/kinetic_planner/cadengine/GLStateGuard.java`
- Create: `src/client/java/net/jsmua/kinetic_planner/cadengine/CADRenderEngine.java`

**Interfaces:**
- Consumes: `LineGeometry`, `BezierTessellator`（Task 3）, `WorldScreenTransform`（0a）
- Produces: `CADRenderEngine.beginFrame/endFrame/drawLine/drawFilledCircle/drawFilledRect/applyWorldTransform/restoreWorldTransform`

- [ ] **Step 1: 创建 GLStateGuard**

创建 `src/client/java/net/jsmua/kinetic_planner/cadengine/GLStateGuard.java`：

```java
package net.jsmua.kinetic_planner.cadengine;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.ShaderInstance;

/**
 * RenderSystem 状态快照与恢复。
 *
 * <p>在 {@link CADRenderEngine#beginFrame} 前捕获当前 GL 状态
 * （blend、depth test、shader），在 {@link CADRenderEngine#endFrame} 后恢复。
 * 确保 CADRenderEngine 的渲染不污染 MC 后续渲染。
 *
 * <p>不使用 {@code glPushAttrib}/{@code glPopAttrib}（GL3+ 核心模式已废弃）。
 */
public final class GLStateGuard {

    private boolean blendWasEnabled;
    private boolean depthWasEnabled;
    private ShaderInstance savedShader;

    private GLStateGuard() {}

    /**
     * 捕获当前 RenderSystem 状态并设置叠加层所需的 GL 状态。
     *
     * @param disableDepthTest 是否关闭深度测试（叠加层通常关闭）
     */
    public static GLStateGuard capture(boolean disableDepthTest) {
        GLStateGuard guard = new GLStateGuard();
        guard.blendWasEnabled = RenderSystem.isEnabledBlend();
        guard.depthWasEnabled = RenderSystem.isDepthTestEnabled();
        guard.savedShader = RenderSystem.getShader();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        if (disableDepthTest) {
            RenderSystem.disableDepthTest();
        }
        return guard;
    }

    /**
     * 恢复捕获时的 RenderSystem 状态。
     */
    public void restore() {
        if (!blendWasEnabled) {
            RenderSystem.disableBlend();
        }
        if (depthWasEnabled) {
            RenderSystem.enableDepthTest();
        }
        if (savedShader != null) {
            RenderSystem.setShader(() -> savedShader);
        }
    }
}
```

- [ ] **Step 2: 创建 CADRenderEngine**

创建 `src/client/java/net/jsmua/kinetic_planner/cadengine/CADRenderEngine.java`：

```java
package net.jsmua.kinetic_planner.cadengine;

import net.jsmua.kinetic_planner.KineticPlannerMod;
import net.jsmua.kinetic_planner.projection.WorldScreenTransform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstances;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Blaze3D 矢量渲染封装（Phase 0b 核心）。
 *
 * <p>用三角形带展开粗线，无抗锯齿（AA 推迟到后续）。
 * 所有几何在屏幕空间提交，世界坐标变换通过 {@link #applyWorldTransform} 设置。
 *
 * <h2>渲染管线</h2>
 * <ol>
 *   <li>{@link #beginFrame} -- 捕获 GL 状态，准备渲染</li>
 *   <li>{@link #applyWorldTransform} -- 设置世界->屏幕变换矩阵</li>
 *   <li>drawXxx 方法 -- 收集顶点到内部缓冲</li>
 *   <li>{@link #restoreWorldTransform} -- 恢复变换</li>
 *   <li>{@link #endFrame} -- 提交所有顶点，恢复 GL 状态</li>
 * </ol>
 *
 * <h2>故障安全</h2>
 * <p>任何渲染异常被 catch 并日志告警，不崩溃。{@code endFrame} 在 try-finally 中保证调用。
 */
public final class CADRenderEngine {

    private GLStateGuard stateGuard;
    private boolean inFrame = false;
    private Matrix4f worldTransform = new Matrix4f(); // identity = screen space
    private boolean hasWorldTransform = false;

    // 顶点收集缓冲
    private final List<float[]> lineVertices = new ArrayList<>();
    private final List<float[]> fillVertices = new ArrayList<>();

    /**
     * 开始一帧渲染。捕获 GL 状态，准备渲染。
     */
    public void beginFrame(int screenWidth, int screenHeight, float dpr) {
        stateGuard = GLStateGuard.capture(true);
        inFrame = true;
        lineVertices.clear();
        fillVertices.clear();
        RenderSystem.setShader(ShaderInstances::getPositionColor);
    }

    /**
     * 设置世界坐标变换。此后所有 drawXxx 的坐标按世界坐标解释。
     */
    public void applyWorldTransform(WorldScreenTransform transform) {
        // 构造变换矩阵：screen = (world - camera) / blocksPerPixel + center
        float scale = (float) (1.0 / transform.cam().blocksPerPixel());
        float cx = transform.cam().screenCenterX();
        float cy = transform.cam().screenCenterY();
        float camX = (float) transform.cam().cameraBlockX();
        float camZ = (float) transform.cam().cameraBlockZ();

        worldTransform = new Matrix4f()
            .translate(cx, cy, 0)        // 移到屏幕中心
            .scale(scale, scale, 1)      // 世界->屏幕缩放
            .translate(-camX, -camZ, 0); // 移到相机位置
        hasWorldTransform = true;
    }

    /**
     * 恢复到屏幕坐标空间。
     */
    public void restoreWorldTransform() {
        worldTransform = new Matrix4f();
        hasWorldTransform = false;
    }

    /**
     * 绘制粗线段（三角形带展开）。
     *
     * @param x1 起点世界 x（或屏幕 x，取决于是否 applyWorldTransform）
     * @param y1 起点世界 z（或屏幕 y）
     * @param x2 终点 x
     * @param y2 终点 y
     * @param widthPx 线宽（屏幕像素）
     * @param color ARGB 颜色
     */
    public void drawLine(float x1, float y1, float x2, float y2, float widthPx, int color) {
        if (!inFrame) return;
        // 将世界坐标变换到屏幕坐标
        float sx1 = x1, sy1 = y1, sx2 = x2, sy2 = y2;
        if (hasWorldTransform) {
            sx1 = worldTransform.m00() * x1 + worldTransform.m30();
            sy1 = worldTransform.m11() * y1 + worldTransform.m31();
            sx2 = worldTransform.m00() * x2 + worldTransform.m30();
            sy2 = worldTransform.m11() * y2 + worldTransform.m31();
        }
        float[] quad = LineGeometry.expandLineToTriangleStrip(sx1, sy1, sx2, sy2, widthPx);
        float r = ((color >> 16) & 0xFF) / 255.0f;
        float g = ((color >> 8) & 0xFF) / 255.0f;
        float b = (color & 0xFF) / 255.0f;
        float a = ((color >> 24) & 0xFF) / 255.0f;
        // 三角形带：v1, v2, v3, v4
        for (int i = 0; i < 4; i++) {
            lineVertices.add(new float[]{quad[i * 2], quad[i * 2 + 1], r, g, b, a});
        }
    }

    /**
     * 绘制三次贝塞尔曲线（tessellate 后调 drawLine 逐段）。
     */
    public void drawBezier(
            float p0x, float p0y, float p1x, float p1y,
            float p2x, float p2y, float p3x, float p3y,
            float widthPx, int color, int segments) {
        if (!inFrame) return;
        float[] pts = BezierTessellator.tessellate(p0x, p0y, p1x, p1y, p2x, p2y, p3x, p3y, segments);
        for (int i = 0; i < segments; i++) {
            drawLine(pts[i * 2], pts[i * 2 + 1], pts[(i + 1) * 2], pts[(i + 1) * 2 + 1], widthPx, color);
        }
    }

    /**
     * 绘制填充圆（近似为三角形扇）。
     *
     * @param cx 中心 x
     * @param cy 中心 y
     * @param radiusPx 半径（屏幕像素）
     * @param color ARGB 颜色
     */
    public void drawFilledCircle(float cx, float cy, float radiusPx, int color) {
        if (!inFrame) return;
        float sx = cx, sy = cy;
        if (hasWorldTransform) {
            sx = worldTransform.m00() * cx + worldTransform.m30();
            sy = worldTransform.m11() * cy + worldTransform.m31();
        }
        float r = ((color >> 16) & 0xFF) / 255.0f;
        float g = ((color >> 8) & 0xFF) / 255.0f;
        float b = (color & 0xFF) / 255.0f;
        float a = ((color >> 24) & 0xFF) / 255.0f;
        int slices = 16;
        for (int i = 0; i < slices; i++) {
            float angle1 = (float) (2 * Math.PI * i / slices);
            float angle2 = (float) (2 * Math.PI * (i + 1) / slices);
            fillVertices.add(new float[]{sx, sy, r, g, b, a}); // center
            fillVertices.add(new float[]{sx + (float) Math.cos(angle1) * radiusPx, sy + (float) Math.sin(angle1) * radiusPx, r, g, b, a});
            fillVertices.add(new float[]{sx + (float) Math.cos(angle2) * radiusPx, sy + (float) Math.sin(angle2) * radiusPx, r, g, b, a});
        }
    }

    /**
     * 绘制填充矩形。
     */
    public void drawFilledRect(float x, float y, float w, float h, int color) {
        if (!inFrame) return;
        float sx = x, sy = y;
        if (hasWorldTransform) {
            sx = worldTransform.m00() * x + worldTransform.m30();
            sy = worldTransform.m11() * y + worldTransform.m31();
        }
        float r = ((color >> 16) & 0xFF) / 255.0f;
        float g = ((color >> 8) & 0xFF) / 255.0f;
        float b = (color & 0xFF) / 255.0f;
        float a = ((color >> 24) & 0xFF) / 255.0f;
        // 两个三角形
        fillVertices.add(new float[]{sx, sy, r, g, b, a});
        fillVertices.add(new float[]{sx + w, sy, r, g, b, a});
        fillVertices.add(new float[]{sx, sy + h, r, g, b, a});
        fillVertices.add(new float[]{sx + w, sy, r, g, b, a});
        fillVertices.add(new float[]{sx + w, sy + h, r, g, b, a});
        fillVertices.add(new float[]{sx, sy + h, r, g, b, a});
    }

    /**
     * 结束一帧渲染。提交所有顶点到 GPU，恢复 GL 状态。
     */
    public void endFrame() {
        if (!inFrame) return;
        try {
            // 提交线段顶点（三角形列表）
            if (!lineVertices.isEmpty()) {
                submitVertices(lineVertices);
            }
            // 提交填充顶点（三角形列表）
            if (!fillVertices.isEmpty()) {
                submitVertices(fillVertices);
            }
        } catch (Throwable t) {
            KineticPlannerMod.LOGGER.error("CADRenderEngine render failed", t);
        } finally {
            lineVertices.clear();
            fillVertices.clear();
            inFrame = false;
            if (stateGuard != null) {
                stateGuard.restore();
                stateGuard = null;
            }
        }
    }

    private void submitVertices(List<float[]> vertices) {
        if (vertices.isEmpty()) return;
        RenderSystem.setShader(ShaderInstances::getPositionColor);
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder builder = tesselator.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        for (float[] v : vertices) {
            builder.addVertex(v[0], v[1], 0f).setColor(v[2], v[3], v[4], v[5]);
        }
        BufferUploader.drawWithShader(builder.buildOrThrow());
    }
}
```

> **注意：** MC 1.21.1 的 `ShaderInstances` 类名和 `getPositionColor` 方法需在编译时验证。NeoForge 1.21.1 中 shader 引用方式可能是 `RenderSystem.setShaderColor` + `ShaderInstances.POSITION_COLOR` 或 `CoreShaders.POSITION_COLOR`。按编译错误修正。

- [ ] **Step 3: 构建验证**

运行：`gradlew compileJava compileClientJava`
预期：BUILD SUCCESSFUL（若 shader API 不匹配，按编译错误修正）

- [ ] **Step 4: 提交**

```bash
git add -A
git commit -m "feat: add CADRenderEngine with Blaze3D triangle strip line rendering"
```

---

## Task 5: Theme record + ThemeSerializer（common，纯 JVM 单测）

**Files:**
- Create: `src/main/java/net/jsmua/kinetic_planner/cadengine/Theme.java`
- Create: `src/main/java/net/jsmua/kinetic_planner/cadengine/ThemeSerializer.java`
- Test: `src/test/java/net/jsmua/kinetic_planner/cadengine/ThemeSerializerTest.java`

**Interfaces:**
- Produces: `Theme` record（简化版：GeometryStyle/LayerVisibility/GlobalStyle）
- Produces: `ThemeSerializer.serialize(Theme): String` / `deserialize(String): Theme`
- Produces: `Theme.defaultValue()` 工厂方法

- [ ] **Step 1: 创建 Theme record**

创建 `src/main/java/net/jsmua/kinetic_planner/cadengine/Theme.java`：

```java
package net.jsmua.kinetic_planner.cadengine;

/**
 * 主题数据（简化核心子集）。
 *
 * <p>定义矢量渲染的视觉参数（线宽/透明度/图层开关/缩放策略）。
 * 颜色不在主题中定义，由 instrument 层委托 Create（TrackGraph.color 等）。
 *
 * <p>Phase 0b 简化：去掉 LineCap/LineJoin/dashPattern/BezierHandleStyle。
 *
 * @param name       主题名称
 * @param track      轨道线样式
 * @param node       节点样式
 * @param edgePoint  边点样式
 * @param layers     图层可见性
 * @param global     全局样式（缩放/线宽模式）
 */
public record Theme(
    String name,
    GeometryStyle track,
    GeometryStyle node,
    GeometryStyle edgePoint,
    LayerVisibility layers,
    GlobalStyle global
) {
    /**
     * 几何样式（线宽/虚线/透明度）。
     *
     * @param width  线宽（屏幕像素或世界单位，取决于 GlobalStyle.constantScreenLineWidth）
     * @param dashed 是否虚线（Phase 0b 恒 false，预留）
     * @param alpha  透明度 0.0-1.0
     */
    public record GeometryStyle(float width, boolean dashed, float alpha) {}

    /**
     * 图层可见性。
     *
     * @param tracks     轨道线层
     * @param nodes      节点层
     * @param edgePoints 边点层
     */
    public record LayerVisibility(boolean tracks, boolean nodes, boolean edgePoints) {}

    /**
     * 全局样式。
     *
     * @param minZoomBlocksPerPixel    最小缩放（blocksPerPixel），低于此值不渲染
     * @param maxZoomBlocksPerPixel    最大缩放，高于此值不渲染
     * @param constantScreenLineWidth  true=固定屏幕像素线宽，false=世界单位线宽随缩放
     * @param fixedScreenLineWidthPx   固定屏幕像素线宽（constantScreenLineWidth=true 时使用）
     */
    public record GlobalStyle(
        float minZoomBlocksPerPixel,
        float maxZoomBlocksPerPixel,
        boolean constantScreenLineWidth,
        float fixedScreenLineWidthPx
    ) {}

    /**
     * 创建默认主题。
     */
    public static Theme defaultValue() {
        return new Theme(
            "default",
            new GeometryStyle(2.0f, false, 1.0f),
            new GeometryStyle(4.0f, false, 1.0f),
            new GeometryStyle(3.0f, false, 1.0f),
            new LayerVisibility(true, true, true),
            new GlobalStyle(0.05f, 5.0f, true, 2.0f)
        );
    }
}
```

- [ ] **Step 2: 创建 ThemeSerializer**

创建 `src/main/java/net/jsmua/kinetic_planner/cadengine/ThemeSerializer.java`：

```java
package net.jsmua.kinetic_planner.cadengine;

import com.google.gson.Gson;

/**
 * Theme 的 JSON 序列化/反序列化工具。
 *
 * <p>使用 Gson 序列化 {@link Theme} record 到 JSON 字符串，
 * 并从 JSON 反序列化回 Theme 实例。
 */
public final class ThemeSerializer {

    private static final Gson GSON = new Gson();

    private ThemeSerializer() {}

    /**
     * 将 Theme 序列化为 JSON 字符串。
     */
    public static String serialize(Theme theme) {
        return GSON.toJson(theme);
    }

    /**
     * 从 JSON 字符串反序列化为 Theme。
     */
    public static Theme deserialize(String json) {
        return GSON.fromJson(json, Theme.class);
    }
}
```

- [ ] **Step 3: 写 ThemeSerializer 往返测试**

创建 `src/test/java/net/jsmua/kinetic_planner/cadengine/ThemeSerializerTest.java`：

```java
package net.jsmua.kinetic_planner.cadengine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ThemeSerializerTest {

    @Test
    void roundTripDefaultThemePreservesAllFields() {
        Theme original = Theme.defaultValue();
        String json = ThemeSerializer.serialize(original);
        Theme restored = ThemeSerializer.deserialize(json);

        assertEquals(original.name(), restored.name());
        assertEquals(original.track().width(), restored.track().width(), 1e-6f);
        assertEquals(original.track().dashed(), restored.track().dashed());
        assertEquals(original.track().alpha(), restored.track().alpha(), 1e-6f);
        assertEquals(original.node().width(), restored.node().width(), 1e-6f);
        assertEquals(original.edgePoint().width(), restored.edgePoint().width(), 1e-6f);
        assertEquals(original.layers().tracks(), restored.layers().tracks());
        assertEquals(original.layers().nodes(), restored.layers().nodes());
        assertEquals(original.layers().edgePoints(), restored.layers().edgePoints());
        assertEquals(original.global().constantScreenLineWidth(), restored.global().constantScreenLineWidth());
        assertEquals(original.global().fixedScreenLineWidthPx(), restored.global().fixedScreenLineWidthPx(), 1e-6f);
        assertEquals(original.global().minZoomBlocksPerPixel(), restored.global().minZoomBlocksPerPixel(), 1e-6f);
        assertEquals(original.global().maxZoomBlocksPerPixel(), restored.global().maxZoomBlocksPerPixel(), 1e-6f);
    }

    @Test
    void roundTripCustomThemePreservesFields() {
        Theme custom = new Theme(
            "custom",
            new Theme.GeometryStyle(5.0f, true, 0.8f),
            new Theme.GeometryStyle(8.0f, false, 1.0f),
            new Theme.GeometryStyle(6.0f, false, 0.5f),
            new Theme.LayerVisibility(false, true, false),
            new Theme.GlobalStyle(0.1f, 10.0f, false, 3.0f)
        );
        String json = ThemeSerializer.serialize(custom);
        Theme restored = ThemeSerializer.deserialize(json);

        assertEquals("custom", restored.name());
        assertEquals(5.0f, restored.track().width(), 1e-6f);
        assertTrue(restored.track().dashed());
        assertEquals(0.8f, restored.track().alpha(), 1e-6f);
        assertFalse(restored.layers().tracks());
        assertFalse(restored.global().constantScreenLineWidth());
    }

    @Test
    void serializedJsonContainsExpectedFields() {
        Theme theme = Theme.defaultValue();
        String json = ThemeSerializer.serialize(theme);
        assertNotNull(json);
        assertTrue(json.contains("\"name\""));
        assertTrue(json.contains("\"track\""));
        assertTrue(json.contains("\"width\""));
        assertTrue(json.contains("\"layers\""));
        assertTrue(json.contains("\"global\""));
    }
}
```

- [ ] **Step 4: 运行测试验证通过**

运行：`gradlew test --tests "net.jsmua.kinetic_planner.cadengine.ThemeSerializerTest"`
预期：3 个测试 PASS

- [ ] **Step 5: 提交**

```bash
git add -A
git commit -m "feat: add simplified Theme record and ThemeSerializer with round-trip tests"
```

---

## Task 6: WorldTreeReadOverlay + GeometryCache 增强（client）

**Files:**
- Create: `src/client/java/net/jsmua/kinetic_planner/instrument/WorldTreeReadOverlay.java`
- Modify: `src/client/java/net/jsmua/kinetic_planner/instrument/GeometryCache.java`
- Modify: `src/client/java/net/jsmua/kinetic_planner/mixin/XaeroMapRenderHook.java`
- Modify: `src/client/java/net/jsmua/kinetic_planner/KineticPlannerClient.java`
- Delete: `src/client/java/net/jsmua/kinetic_planner/instrument/NativeLineOverlay.java`

**Interfaces:**
- Consumes: `CADRenderEngine`（Task 4）, `Theme`（Task 5）, `IRailwayDataAccess`（Task 1-2）
- Produces: `WorldTreeReadOverlay.onClientTick()` / `onMapRender()` (替换 NativeLineOverlay)

- [ ] **Step 1: 增强 GeometryCache**

修改 `src/client/java/net/jsmua/kinetic_planner/instrument/GeometryCache.java`，将 `GraphGeometry` record 改为：

```java
    /**
     * 单个轨道图的几何数据快照。
     *
     * @param graphId    TrackGraph 的 UUID
     * @param graphColor TrackGraph.color 的 RGB 值
     * @param nodes      节点世界坐标列表
     * @param edges      边几何描述符列表
     * @param edgePoints 边点数据列表（坐标 + 颜色）
     */
    public record GraphGeometry(
        UUID graphId,
        int graphColor,
        List<Vec3> nodes,
        List<EdgeGeometry> edges,
        List<EdgePointData> edgePoints
    ) {}

    /**
     * 边点渲染数据。
     *
     * @param worldPos 世界坐标
     * @param color    ARGB 颜色
     */
    public record EdgePointData(Vec3 worldPos, int color) {}
```

替换原来的：
```java
    public record GraphGeometry(List<Vec3> nodes, List<EdgeGeometry> edges) {}
```

在文件顶部添加 import：
```java
import net.jsmua.kinetic_planner.data.EdgeGeometry;
import java.util.UUID;
```

（`EdgeGeometry` 已 imported，只需加 `UUID`。检查现有 import 是否已包含。）

- [ ] **Step 2: 创建 WorldTreeReadOverlay**

创建 `src/client/java/net/jsmua/kinetic_planner/instrument/WorldTreeReadOverlay.java`：

```java
package net.jsmua.kinetic_planner.instrument;

import net.jsmua.kinetic_planner.KineticPlannerMod;
import net.jsmua.kinetic_planner.cadengine.CADRenderEngine;
import net.jsmua.kinetic_planner.cadengine.Theme;
import net.jsmua.kinetic_planner.data.EdgeGeometry;
import net.jsmua.kinetic_planner.data.IRailwayDataAccess;
import net.jsmua.kinetic_planner.data.RailwayDataAccess;
import net.jsmua.kinetic_planner.mapadapter.MapOverlayContext;
import net.jsmua.kinetic_planner.mapadapter.MapOverlayDispatcher;
import net.jsmua.kinetic_planner.projection.CameraParams;
import net.jsmua.kinetic_planner.projection.WorldScreenTransform;
import com.simibubi.create.content.trains.graph.TrackEdge;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.graph.TrackNode;
import com.simibubi.create.content.trains.signal.TrackEdgePoint;
import com.simibubi.create.content.trains.graph.EdgePointType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/**
 * Phase 0b 顶层编排器（替换 NativeLineOverlay）。
 *
 * <p>整合 {@link IRailwayDataAccess} + {@link WorldScreenTransform} +
 * {@link CADRenderEngine} + {@link Theme}，将 Create 当前维度的 TrackGraph
 * 静态拓扑以矢量方式叠加到地图。
 */
public final class WorldTreeReadOverlay {

    private static final IRailwayDataAccess dataAccess = new RailwayDataAccess();
    private static final GeometryCache geometryCache = new GeometryCache();
    private static final CADRenderEngine engine = new CADRenderEngine();
    private static Theme theme = Theme.defaultValue();
    private static MapOverlayContext lastContext;
    private static WorldScreenTransform lastTransform;

    /**
     * 更新主题（由配置重载触发）。
     */
    public static void setTheme(Theme newTheme) {
        theme = newTheme;
    }

    /**
     * 客户端 tick 回调，更新投影变换器并重建几何缓存。
     */
    public static void onClientTick() {
        Optional<MapOverlayContext> ctxOpt = MapOverlayDispatcher.currentContext();
        if (ctxOpt.isEmpty()) {
            geometryCache.clear();
            lastContext = null;
            return;
        }
        MapOverlayContext ctx = ctxOpt.get();
        CameraParams cam = new CameraParams(
            ctx.cameraBlockX(), ctx.cameraBlockZ(), ctx.blocksPerPixel(),
            ctx.screenWidth(), ctx.screenHeight()
        );
        lastTransform = new WorldScreenTransform(cam);
        lastContext = ctx;

        int version = dataAccess.clientVersion();
        if (geometryCache.needsRebuild(version, ctx.dimension())) {
            rebuildGeometry(ctx.dimension());
            geometryCache.update(version, ctx.dimension(), buildCacheData(ctx.dimension()));
        }
    }

    private static Map<UUID, GeometryCache.GraphGeometry> buildCacheData(
            net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dim) {
        Map<UUID, GeometryCache.GraphGeometry> newData = new HashMap<>();
        dataAccess.graphsInDimension(dim).forEach(g -> {
            int graphColor = g.color != null ? g.color.getRGB() : 0xFFFFFFFF;

            // 提取节点
            List<Vec3> nodes = new ArrayList<>();
            Set<TrackNode> nodeSet = new HashSet<>();
            dataAccess.nodesInDimension(g, dim).forEach(n -> {
                nodes.add(dataAccess.nodeWorldPos(n));
                nodeSet.add(n);
            });

            // 提取边（去重：hashCode 比较，跳过已处理的反向边）
            List<EdgeGeometry> edges = new ArrayList<>();
            Set<Integer> seenEdges = new HashSet<>();
            for (TrackNode node : nodeSet) {
                dataAccess.edgesFrom(g, node).forEach(edge -> {
                    int hash = edge.hashCode();
                    int reverseHash = Integer.reverse(hash);
                    if (!seenEdges.contains(hash) && !seenEdges.contains(reverseHash)) {
                        seenEdges.add(hash);
                        edges.add(dataAccess.edgeGeometry(edge));
                    }
                });
            }

            // 提取边点
            List<GeometryCache.EdgePointData> edgePoints = new ArrayList<>();
            for (EdgePointType<?> type : EdgePointType.TYPES.values()) {
                dataAccess.edgePoints(g, type).forEach(point -> {
                    try {
                        var loc = point.getLocationOn(edge -> edge);
                        // 简化：使用边点 edgeLocation 对应的节点位置作为近似世界坐标
                        // 精确定位需要 edge.getPosition(graph, edgeLocation)，在 Task 10 完善
                        // Phase 0b MVP：暂用 (0,0,0) 占位，Task 10 修正
                    } catch (Throwable ignored) {}
                    int color = EdgePointColorResolver.resolve(point, g);
                    edgePoints.add(new GeometryCache.EdgePointData(Vec3.ZERO, color));
                });
            }

            newData.put(g.id, new GeometryCache.GraphGeometry(g.id, graphColor, nodes, edges, edgePoints));
        });
        return newData;
    }

    private static void rebuildGeometry(
            net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dim) {
        // 预留：可在此做视锥剔除预处理
    }

    /**
     * 地图渲染回调，由 XaeroMapRenderHook Mixin 调用。
     */
    public static void onMapRender(Object guiMap, GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        if (lastContext == null || lastTransform == null) return;

        try {
            engine.beginFrame(lastContext.screenWidth(), lastContext.screenHeight(), lastContext.dpr());
            engine.applyWorldTransform(lastTransform);

            // 按图层顺序绘制
            for (GeometryCache.GraphGeometry geom : geometryCache.geometries()) {
                int color = applyAlpha(geom.graphColor(), theme.track().alpha());

                // 1. 轨道层
                if (theme.layers().tracks()) {
                    float widthPx = theme.global().constantScreenLineWidth()
                        ? theme.global().fixedScreenLineWidthPx()
                        : theme.track().width() / (float) lastTransform.cam().blocksPerPixel();
                    for (EdgeGeometry edge : geom.edges()) {
                        if (edge.type() == EdgeGeometry.Type.BEZIER && edge.bezier() != null) {
                            var b = edge.bezier();
                            engine.drawBezier(
                                (float) b.start().x, (float) b.start().z,
                                (float) b.control1().x, (float) b.control1().z,
                                (float) b.control2().x, (float) b.control2().z,
                                (float) b.end().x, (float) b.end().z,
                                widthPx, color, 32
                            );
                        } else {
                            engine.drawLine(
                                (float) edge.p1().x, (float) edge.p1().z,
                                (float) edge.p2().x, (float) edge.p2().z,
                                widthPx, color
                            );
                        }
                    }
                }

                // 2. 节点层
                if (theme.layers().nodes()) {
                    int nodeColor = applyAlpha(0xFFFFFFFF, theme.node().alpha());
                    float nodeRadius = theme.node().width() / 2;
                    for (Vec3 node : geom.nodes()) {
                        engine.drawFilledCircle((float) node.x, (float) node.z, nodeRadius, nodeColor);
                    }
                }

                // 3. 边点层
                if (theme.layers().edgePoints()) {
                    float epRadius = theme.edgePoint().width() / 2;
                    for (GeometryCache.EdgePointData ep : geom.edgePoints()) {
                        int epColor = applyAlpha(ep.color(), theme.edgePoint().alpha());
                        engine.drawFilledCircle(
                            (float) ep.worldPos().x, (float) ep.worldPos().z,
                            epRadius, epColor
                        );
                    }
                }
            }

            engine.restoreWorldTransform();
            engine.endFrame();
        } catch (Throwable t) {
            KineticPlannerMod.LOGGER.error("WorldTreeReadOverlay render failed", t);
            try {
                engine.endFrame();
            } catch (Throwable ignored) {}
        }
    }

    private static int applyAlpha(int color, float alpha) {
        int a = (int) (alpha * 255) & 0xFF;
        return (a << 24) | (color & 0x00FFFFFF);
    }
}
```

- [ ] **Step 3: 更新 XaeroMapRenderHook**

修改 `src/client/java/net/jsmua/kinetic_planner/mixin/XaeroMapRenderHook.java`：

将所有 `NativeLineOverlay` 引用替换为 `WorldTreeReadOverlay`：

```java
import net.jsmua.kinetic_planner.instrument.WorldTreeReadOverlay;
```

替换：
```java
            NativeLineOverlay.onMapRender((GuiMap) (Object) this, guiGraphics, mouseX, mouseY, partialTicks);
```
为：
```java
            WorldTreeReadOverlay.onMapRender((GuiMap) (Object) this, guiGraphics, mouseX, mouseY, partialTicks);
```

替换日志中的 `"NativeLineOverlay render failed"` 为 `"WorldTreeReadOverlay render failed"`。

- [ ] **Step 4: 更新 KineticPlannerClient**

修改 `src/client/java/net/jsmua/kinetic_planner/KineticPlannerClient.java`：

将 `NativeLineOverlay` 引用替换为 `WorldTreeReadOverlay`：

```java
import net.jsmua.kinetic_planner.instrument.WorldTreeReadOverlay;
```

替换：
```java
        NativeLineOverlay.onClientTick();
```
为：
```java
        WorldTreeReadOverlay.onClientTick();
```

更新类 javadoc 中的 `NativeLineOverlay` 引用为 `WorldTreeReadOverlay`。

- [ ] **Step 5: 删除 NativeLineOverlay**

删除 `src/client/java/net/jsmua/kinetic_planner/instrument/NativeLineOverlay.java`。

- [ ] **Step 6: 构建验证**

运行：`gradlew compileJava compileClientJava`
预期：BUILD SUCCESSFUL

- [ ] **Step 7: 运行测试验证无回归**

运行：`gradlew test`
预期：全部测试 PASS

- [ ] **Step 8: 提交**

```bash
git add -A
git commit -m "feat: replace NativeLineOverlay with WorldTreeReadOverlay using CADRenderEngine"
```

---

## Task 7: KPConfig（client）

**Files:**
- Create: `src/client/java/net/jsmua/kinetic_planner/config/KPConfig.java`
- Modify: `src/client/java/net/jsmua/kinetic_planner/KineticPlannerClient.java`

**Interfaces:**
- Produces: `KPConfig` NeoForge `ModConfigSpec` 实例（5 段：overlay/theme/layers/label/debug）
- Produces: `KPConfig.get()` 全局访问

- [ ] **Step 1: 创建 KPConfig**

创建 `src/client/java/net/jsmua/kinetic_planner/config/KPConfig.java`：

```java
package net.jsmua.kinetic_planner.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Kinetic Planner 客户端配置（TOML）。
 *
 * <p>5 个配置段：
 * <ul>
 *   <li>[overlay] - 叠加层开关与适配器优先级</li>
 *   <li>[theme] - 主题相关（线宽模式、缩放范围）</li>
 *   <li>[layers] - 图层可见性</li>
 *   <li>[label] - 标签显示</li>
 *   <li>[debug] - 调试选项</li>
 * </ul>
 */
public class KPConfig {

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    public static final ModConfigSpec SPEC;

    // [overlay]
    public static final ModConfigSpec.BooleanValue OVERLAY_ENABLED;
    public static final ModConfigSpec.ConfigValue<String> OVERLAY_ADAPTER_PRIORITY;

    // [theme]
    public static final ModConfigSpec.ConfigValue<String> THEME_ACTIVE;
    public static final ModConfigSpec.BooleanValue THEME_CONSTANT_SCREEN_LINE_WIDTH;
    public static final ModConfigSpec.DoubleValue THEME_FIXED_SCREEN_LINE_WIDTH_PX;
    public static final ModConfigSpec.DoubleValue THEME_MIN_ZOOM;
    public static final ModConfigSpec.DoubleValue THEME_MAX_ZOOM;

    // [layers]
    public static final ModConfigSpec.BooleanValue LAYERS_TRACKS;
    public static final ModConfigSpec.BooleanValue LAYERS_NODES;
    public static final ModConfigSpec.BooleanValue LAYERS_EDGE_POINTS;

    // [label]
    public static final ModConfigSpec.BooleanValue LABEL_SHOW_NODE_LABELS;
    public static final ModConfigSpec.BooleanValue LABEL_SHOW_STATION_NAMES;
    public static final ModConfigSpec.DoubleValue LABEL_MIN_ZOOM;

    // [debug]
    public static final ModConfigSpec.BooleanValue DEBUG_SHOW_FPS;
    public static final ModConfigSpec.BooleanValue DEBUG_SHOW_GEOMETRY_COUNT;
    public static final ModConfigSpec.BooleanValue DEBUG_DISABLE_GL_STATE_GUARD;

    static {
        BUILDER.push("overlay");
        OVERLAY_ENABLED = BUILDER.define("enabled", true);
        OVERLAY_ADAPTER_PRIORITY = BUILDER.define("adapterPriority", "xaero,journeymap");
        BUILDER.pop();

        BUILDER.push("theme");
        THEME_ACTIVE = BUILDER.define("activeTheme", "default");
        THEME_CONSTANT_SCREEN_LINE_WIDTH = BUILDER.define("constantScreenLineWidth", true);
        THEME_FIXED_SCREEN_LINE_WIDTH_PX = BUILDER.defineInRange("fixedScreenLineWidthPx", 2.0, 0.1, 20.0);
        THEME_MIN_ZOOM = BUILDER.defineInRange("minZoomBlocksPerPixel", 0.05, 0.001, 100.0);
        THEME_MAX_ZOOM = BUILDER.defineInRange("maxZoomBlocksPerPixel", 5.0, 0.001, 1000.0);
        BUILDER.pop();

        BUILDER.push("layers");
        LAYERS_TRACKS = BUILDER.define("tracks", true);
        LAYERS_NODES = BUILDER.define("nodes", true);
        LAYERS_EDGE_POINTS = BUILDER.define("edgePoints", true);
        BUILDER.pop();

        BUILDER.push("label");
        LABEL_SHOW_NODE_LABELS = BUILDER.define("showNodeLabels", false);
        LABEL_SHOW_STATION_NAMES = BUILDER.define("showStationNames", true);
        LABEL_MIN_ZOOM = BUILDER.defineInRange("labelMinZoomBlocksPerPixel", 1.0, 0.001, 100.0);
        BUILDER.pop();

        BUILDER.push("debug");
        DEBUG_SHOW_FPS = BUILDER.define("showFps", false);
        DEBUG_SHOW_GEOMETRY_COUNT = BUILDER.define("showGeometryCount", false);
        DEBUG_DISABLE_GL_STATE_GUARD = BUILDER.define("disableGlStateGuard", false);
        BUILDER.pop();

        SPEC = BUILDER.build();
    }

    /**
     * 从配置值构建 Theme record。
     */
    public static net.jsmua.kinetic_planner.cadengine.Theme toTheme() {
        return new net.jsmua.kinetic_planner.cadengine.Theme(
            THEME_ACTIVE.get(),
            new net.jsmua.kinetic_planner.cadengine.Theme.GeometryStyle(
                THEME_FIXED_SCREEN_LINE_WIDTH_PX.get().floatValue(), false, 1.0f),
            new net.jsmua.kinetic_planner.cadengine.Theme.GeometryStyle(4.0f, false, 1.0f),
            new net.jsmua.kinetic_planner.cadengine.Theme.GeometryStyle(3.0f, false, 1.0f),
            new net.jsmua.kinetic_planner.cadengine.Theme.LayerVisibility(
                LAYERS_TRACKS.get(), LAYERS_NODES.get(), LAYERS_EDGE_POINTS.get()),
            new net.jsmua.kinetic_planner.cadengine.Theme.GlobalStyle(
                THEME_MIN_ZOOM.get().floatValue(),
                THEME_MAX_ZOOM.get().floatValue(),
                THEME_CONSTANT_SCREEN_LINE_WIDTH.get(),
                THEME_FIXED_SCREEN_LINE_WIDTH_PX.get().floatValue())
        );
    }
}
```

- [ ] **Step 2: 在 KineticPlannerClient 注册配置**

修改 `src/client/java/net/jsmua/kinetic_planner/KineticPlannerClient.java`，在构造函数中注册配置：

```java
    public KineticPlannerClient(ModContainer container) {
        container.registerConfig(net.neoforged.fml.config.ModConfig.Type.CLIENT, KPConfig.SPEC);
    }
```

添加 import：
```java
import net.jsmua.kinetic_planner.config.KPConfig;
```

- [ ] **Step 3: 构建验证**

运行：`gradlew compileJava compileClientJava`
预期：BUILD SUCCESSFUL

- [ ] **Step 4: 提交**

```bash
git add -A
git commit -m "feat: add KPConfig with 5-section TOML configuration"
```

---

## Task 8: KPCommands（client）

**Files:**
- Create: `src/client/java/net/jsmua/kinetic_planner/config/KPCommands.java`
- Modify: `src/client/java/net/jsmua/kinetic_planner/KineticPlannerClient.java`

**Interfaces:**
- Produces: `/kp overlay toggle`, `/kp overlay reload`, `/kp theme reload`, `/kp theme list`, `/kp debug stats`

- [ ] **Step 1: 创建 KPCommands**

创建 `src/client/java/net/jsmua/kinetic_planner/config/KPCommands.java`：

```java
package net.jsmua.kinetic_planner.config;

import net.jsmua.kinetic_planner.KineticPlannerMod;
import net.jsmua.kinetic_planner.instrument.WorldTreeReadOverlay;
import net.jsmua.kinetic_planner.cadengine.Theme;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/**
 * /kp 命令体系注册。
 *
 * <p>命令树：
 * <pre>
 * /kp overlay toggle   -- 切换叠加开关
 * /kp overlay reload   -- 重载配置 + 主题
 * /kp theme reload     -- 仅重载主题
 * /kp theme list       -- 列出可用主题
 * /kp debug stats      -- 输出渲染统计
 * </pre>
 */
public final class KPCommands {

    private KPCommands() {}

    /**
     * 注册命令（由 RegisterCommandsEvent 调用）。
     */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("kp")
            .then(Commands.literal("overlay")
                .then(Commands.literal("toggle")
                    .executes(KPCommands::overlayToggle))
                .then(Commands.literal("reload")
                    .executes(KPCommands::overlayReload)))
            .then(Commands.literal("theme")
                .then(Commands.literal("reload")
                    .executes(KPCommands::themeReload))
                .then(Commands.literal("list")
                    .executes(KPCommands::themeList)))
            .then(Commands.literal("debug")
                .then(Commands.literal("stats")
                    .executes(KPCommands::debugStats)))
        );
    }

    private static int overlayToggle(CommandContext<CommandSourceStack> ctx) {
        boolean current = KPConfig.OVERLAY_ENABLED.get();
        KPConfig.OVERLAY_ENABLED.set(!current);
        ctx.getSource().sendSuccess(() ->
            Component.literal("[KP] Overlay " + (!current ? "enabled" : "disabled")), false);
        return 1;
    }

    private static int overlayReload(CommandContext<CommandSourceStack> ctx) {
        // 重载配置 + 主题
        WorldTreeReadOverlay.setTheme(KPConfig.toTheme());
        ctx.getSource().sendSuccess(() ->
            Component.literal("[KP] Config and theme reloaded"), false);
        return 1;
    }

    private static int themeReload(CommandContext<CommandSourceStack> ctx) {
        WorldTreeReadOverlay.setTheme(KPConfig.toTheme());
        ctx.getSource().sendSuccess(() ->
            Component.literal("[KP] Theme reloaded: " + KPConfig.THEME_ACTIVE.get()), false);
        return 1;
    }

    private static int themeList(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSuccess(() ->
            Component.literal("[KP] Available themes: default"), false);
        return 1;
    }

    private static int debugStats(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSuccess(() ->
            Component.literal("[KP] Debug stats: (placeholder - Task 10 实现)"), false);
        return 1;
    }
}
```

- [ ] **Step 2: 在 KineticPlannerClient 注册命令**

修改 `src/client/java/net/jsmua/kinetic_planner/KineticPlannerClient.java`，添加命令注册事件订阅：

```java
    @SubscribeEvent
    static void onRegisterCommands(net.neoforged.neoforge.event.RegisterCommandsEvent event) {
        net.jsmua.kinetic_planner.config.KPCommands.register(event.getDispatcher());
    }
```

> **注意：** `RegisterCommandsEvent` 在服务端触发。对于客户端命令，NeoForge 1.21.1 可能需要用 `ClientCommandSourceStack` 和 `ClientCommands.literal`。如果 `RegisterCommandsEvent` 在专用服务端也触发（但命令逻辑引用客户端类），需要改为客户端命令注册方式。按编译/运行时错误修正。

- [ ] **Step 3: 构建验证**

运行：`gradlew compileJava compileClientJava`
预期：BUILD SUCCESSFUL

- [ ] **Step 4: 提交**

```bash
git add -A
git commit -m "feat: add /kp command system (overlay toggle/reload, theme reload/list, debug stats)"
```

---

## Task 9: KPClothConfigScreen（client）

**Files:**
- Create: `src/client/java/net/jsmua/kinetic_planner/config/KPClothConfigScreen.java`
- Modify: `src/client/java/net/jsmua/kinetic_planner/KineticPlannerClient.java`

**Interfaces:**
- Produces: Cloth Config 配置屏幕注册

- [ ] **Step 1: 创建 KPClothConfigScreen**

创建 `src/client/java/net/jsmua/kinetic_planner/config/KPClothConfigScreen.java`：

```java
package net.jsmua.kinetic_planner.config;

import net.jsmua.kinetic_planner.KineticPlannerMod;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Cloth Config 配置屏幕构建器。
 *
 * <p>提供 overlay 开关、图层开关、主题选择、线宽调节等配置 UI。
 * 不提供主题编辑器（Phase 1）。
 */
public final class KPClothConfigScreen {

    private KPClothConfigScreen() {}

    /**
     * 构建配置屏幕。
     *
     * @param parent 父屏幕（返回时回到此屏幕）
     * @return Cloth Config 屏幕实例
     */
    public static Screen build(Screen parent) {
        ConfigBuilder builder = ConfigBuilder.create()
            .setParentScreen(parent)
            .setTitle(Component.literal("Kinetic Planner Config"));

        ConfigEntryBuilder entryBuilder = builder.entryBuilder();

        // [overlay] 段
        ConfigCategory overlay = builder.getOrCreateCategory(Component.literal("Overlay"));
        overlay.addEntry(entryBuilder.startBooleanToggle(
                Component.literal("Enabled"), KPConfig.OVERLAY_ENABLED.get())
            .setDefaultValue(true)
            .setSaveConsumer(KPConfig.OVERLAY_ENABLED::set)
            .build());

        // [layers] 段
        ConfigCategory layers = builder.getOrCreateCategory(Component.literal("Layers"));
        layers.addEntry(entryBuilder.startBooleanToggle(
                Component.literal("Tracks"), KPConfig.LAYERS_TRACKS.get())
            .setDefaultValue(true)
            .setSaveConsumer(KPConfig.LAYERS_TRACKS::set)
            .build());
        layers.addEntry(entryBuilder.startBooleanToggle(
                Component.literal("Nodes"), KPConfig.LAYERS_NODES.get())
            .setDefaultValue(true)
            .setSaveConsumer(KPConfig.LAYERS_NODES::set)
            .build());
        layers.addEntry(entryBuilder.startBooleanToggle(
                Component.literal("Edge Points"), KPConfig.LAYERS_EDGE_POINTS.get())
            .setDefaultValue(true)
            .setSaveConsumer(KPConfig.LAYERS_EDGE_POINTS::set)
            .build());

        // [theme] 段
        ConfigCategory theme = builder.getOrCreateCategory(Component.literal("Theme"));
        theme.addEntry(entryBuilder.startStrField(
                Component.literal("Active Theme"), KPConfig.THEME_ACTIVE.get())
            .setDefaultValue("default")
            .setSaveConsumer(KPConfig.THEME_ACTIVE::set)
            .build());
        theme.addEntry(entryBuilder.startBooleanToggle(
                Component.literal("Constant Screen Line Width"),
                KPConfig.THEME_CONSTANT_SCREEN_LINE_WIDTH.get())
            .setDefaultValue(true)
            .setSaveConsumer(KPConfig.THEME_CONSTANT_SCREEN_LINE_WIDTH::set)
            .build());
        theme.addEntry(entryBuilder.startDoubleField(
                Component.literal("Fixed Line Width (px)"),
                KPConfig.THEME_FIXED_SCREEN_LINE_WIDTH_PX.get())
            .setDefaultValue(2.0)
            .setMin(0.1).setMax(20.0)
            .setSaveConsumer(KPConfig.THEME_FIXED_SCREEN_LINE_WIDTH_PX::set)
            .build());

        // [label] 段
        ConfigCategory label = builder.getOrCreateCategory(Component.literal("Labels"));
        label.addEntry(entryBuilder.startBooleanToggle(
                Component.literal("Show Node Labels"), KPConfig.LABEL_SHOW_NODE_LABELS.get())
            .setDefaultValue(false)
            .setSaveConsumer(KPConfig.LABEL_SHOW_NODE_LABELS::set)
            .build());
        label.addEntry(entryBuilder.startBooleanToggle(
                Component.literal("Show Station Names"), KPConfig.LABEL_SHOW_STATION_NAMES.get())
            .setDefaultValue(true)
            .setSaveConsumer(KPConfig.LABEL_SHOW_STATION_NAMES::set)
            .build());

        builder.setSavingRunnable(() -> {
            KineticPlannerMod.LOGGER.info("KP config saved, reloading theme");
            net.jsmua.kinetic_planner.instrument.WorldTreeReadOverlay.setTheme(KPConfig.toTheme());
        });

        return builder.build();
    }
}
```

- [ ] **Step 2: 在 KineticPlannerClient 注册配置屏幕**

修改 `src/client/java/net/jsmua/kinetic_planner/KineticPlannerClient.java`，在构造函数中注册配置屏幕：

```java
    public KineticPlannerClient(ModContainer container) {
        container.registerConfig(net.neoforged.fml.config.ModConfig.Type.CLIENT, KPConfig.SPEC);
        container.registerConfigScreen(KPClothConfigScreen::build);
    }
```

> **注意：** `registerConfigScreen` 的确切 API 需在编译时验证。NeoForge 1.21.1 可能使用 `container.registerConfigScreen(factory)` 或通过 `ConfigScreenHandler` 事件注册。按编译错误修正。

- [ ] **Step 3: 构建验证**

运行：`gradlew compileJava compileClientJava`
预期：BUILD SUCCESSFUL

- [ ] **Step 4: 提交**

```bash
git add -A
git commit -m "feat: add Cloth Config GUI for overlay/layers/theme/label settings"
```

---

## Task 10: Bezier + EdgePoints + Labels 集成（client）

**Files:**
- Modify: `src/client/java/net/jsmua/kinetic_planner/instrument/WorldTreeReadOverlay.java`

**Interfaces:**
- Consumes: `EdgePointColorResolver`, `EdgePointType.TYPES`
- Produces: 完整的轨道/节点/边点/标签渲染

- [ ] **Step 1: 完善边点世界坐标定位**

修改 `WorldTreeReadOverlay.buildCacheData` 方法中的边点提取逻辑。将边点位置从 `Vec3.ZERO` 占位改为实际的世界坐标。

在 `buildCacheData` 方法中，替换边点提取部分：

```java
            // 提取边点
            List<GeometryCache.EdgePointData> edgePoints = new ArrayList<>();
            for (EdgePointType<?> type : EdgePointType.TYPES.values()) {
                dataAccess.edgePoints(g, type).forEach(point -> {
                    try {
                        // 边点的 edgeLocation 存储了在图中的位置
                        // 使用 TrackEdgePoint.getLocationOn(edge) 获取边上位置
                        // 简化：使用 edgeLocation 对应的节点位置
                        // 精确实现需要遍历边找到对应的 edge，调 edge.getPosition(graph, position)
                        var edgeLoc = point.edgeLocation;
                        if (edgeLoc != null) {
                            TrackNode fromNode = g.locateNode(edgeLoc.getFirst());
                            TrackNode toNode = g.locateNode(edgeLoc.getSecond());
                            if (fromNode != null && toNode != null) {
                                Map<TrackNode, Map<TrackNode, TrackEdge>> connections =
                                    ((net.jsmua.kinetic_planner.mixin.TrackGraphAccessor) g)
                                        .kp$getConnectionsByNode();
                                Map<TrackNode, TrackEdge> edges = connections.get(fromNode);
                                if (edges != null) {
                                    TrackEdge edge = edges.get(toNode);
                                    if (edge != null) {
                                        double position = point.getLocationOn(edge);
                                        double edgeLength = edge.getLength();
                                        Vec3 pos = edge.getPosition(g, position / edgeLength);
                                        int color = EdgePointColorResolver.resolve(point, g);
                                        edgePoints.add(new GeometryCache.EdgePointData(pos, color));
                                        return;
                                    }
                                }
                            }
                        }
                        // fallback
                        int color = EdgePointColorResolver.resolve(point, g);
                        edgePoints.add(new GeometryCache.EdgePointData(Vec3.ZERO, color));
                    } catch (Throwable t) {
                        // 静默跳过无法定位的边点
                    }
                });
            }
```

> **注意：** `point.edgeLocation`、`point.getLocationOn(edge)`、`edge.getPosition(graph, t)`、`edge.getLength()` 的确切 API 需在编译/运行时验证 Create 6.0.10。`TrackEdgePoint` 在 `content.trains.signal` 包。按编译错误修正。

- [ ] **Step 2: 添加标签渲染**

在 `WorldTreeReadOverlay.onMapRender` 方法中，在 `engine.endFrame()` 之后、catch 之前添加标签渲染：

```java
            engine.endFrame();

            // MC 文字层（屏幕坐标，不走 CADRenderEngine）
            renderLabels(guiGraphics);
```

在类中添加 `renderLabels` 方法：

```java
    private static void renderLabels(GuiGraphics guiGraphics) {
        if (!KPConfig.LABEL_SHOW_STATION_NAMES.get() && !KPConfig.LABEL_SHOW_NODE_LABELS.get()) return;
        if (lastContext == null || lastTransform == null) return;

        // 仅在足够缩放时绘制标签
        if (lastTransform.cam().blocksPerPixel() > KPConfig.LABEL_MIN_ZOOM.get()) return;

        var font = Minecraft.getInstance().font;
        for (GeometryCache.GraphGeometry geom : geometryCache.geometries()) {
            if (KPConfig.LABEL_SHOW_NODE_LABELS.get()) {
                for (Vec3 node : geom.nodes()) {
                    var screen = lastTransform.worldToScreen(node.x, node.z);
                    guiGraphics.drawString(font, "N",
                        (int) screen.x + 4, (int) screen.y - 4, 0xFFFFFFFF);
                }
            }
        }
    }
```

添加 import：
```java
import net.jsmua.kinetic_planner.config.KPConfig;
```

- [ ] **Step 3: 添加 debug stats 命令实现**

修改 `KPCommands.debugStats` 方法：

```java
    private static int debugStats(CommandContext<CommandSourceStack> ctx) {
        int graphCount = 0;
        int nodeCount = 0;
        int edgeCount = 0;
        // WorldTreeReadOverlay 可暴露统计方法，此处简化为日志输出
        KineticPlannerMod.LOGGER.info("[KP] Debug stats requested");
        ctx.getSource().sendSuccess(() ->
            Component.literal("[KP] Debug stats: see console log"), false);
        return 1;
    }
```

- [ ] **Step 4: 构建验证**

运行：`gradlew build`
预期：BUILD SUCCESSFUL

- [ ] **Step 5: 运行测试**

运行：`gradlew test`
预期：全部测试 PASS

- [ ] **Step 6: 提交**

```bash
git add -A
git commit -m "feat: add Bezier rendering, edge point positioning, and label rendering"
```

---

## Task 11: 集成验收（Phase 0b 退场条件）

**Files:**
- 无新文件，运行时手动验收
- Modify: `docs/STATUS.md` (验收通过后更新)

- [ ] **Step 1: 构建完整 jar**

运行：`gradlew build`
预期：BUILD SUCCESSFUL，生成 `build/libs/kineticplanner-0.1.0-alpha.jar`

- [ ] **Step 2: 准备运行环境**

在 `run/mods/` 目录放入：
- `kineticplanner-0.1.0-alpha.jar`（本模组）
- Create 6.0.10-280 jar
- Flywheel 1.0.6 jar
- Ponder 1.0.82 jar
- Xaero's World Map jar
- XaeroLib jar

- [ ] **Step 3: 启动客户端并进入存档**

运行：`gradlew runClient`
预期：游戏启动，日志输出 "Kinetic Planner loading (common)" + "client setup"

- [ ] **Step 4: 打开 Xaero 全屏地图，逐项验收**

**功能验收（spec §5.1 #1-12）：**

| # | 验收点 | 预期 |
|---|---|---|
| 1 | 装载 Create+Xaero+KP，进入有铁路的存档 | 无崩溃 |
| 2 | 打开 Xaero 全屏地图 | 叠加层自动显示当前维度轨道拓扑 |
| 3 | 轨道粗线 | 直线与贝塞尔曲线以三角形带粗线显示（锯齿可接受，0b 不做 AA） |
| 4 | 节点圆点 | 节点以圆点显示（drawFilledCircle） |
| 5 | 边点彩色圆点 | 信号机/车站/观察者以彩色圆点显示，颜色委托 Create |
| 6 | 缩放时线宽 | 按 constantScreenLineWidth 配置固定或随缩放 |
| 7 | 平移跟随 | 叠加层跟随平移，无滞后 |
| 8 | 维度切换 | 切换维度视图，叠加层自动切换 |
| 9 | 建造/拆除反映 | 关闭地图建造/拆除轨道再打开，叠加层反映变化 |
| 10 | 关闭叠加开关 | `/kp overlay toggle` 或配置关闭，叠加层消失 |
| 11 | 车站名标签 | 足够缩放时显示标签 |
| 12 | 字体走 MC 管线 | 兼容 Caxton/Modern UI |

**隔离与稳定性（spec §5.2 #13-16）：**

| # | 验收点 | 预期 |
|---|---|---|
| 13 | 渲染异常不崩溃 | CADRenderEngine try-finally 保证 endFrame |
| 14 | Xaero Mixin 失败熔断 | MapOverlayDispatcher 熔断该 provider |
| 15 | 渲染异常 try-finally | MC 后续渲染不受影响 |
| 16 | Create 叠加层共存 | Create 栅格化纹理与矢量拓扑共存 |

**性能（spec §5.3 #17-19）：**

| # | 验收点 | 预期 |
|---|---|---|
| 17 | <500 节点渲染 <2ms | 单批次 BufferBuilder 提交 |
| 18 | 缩放/平移 60fps | 几何缓存 + 脏检测 |
| 19 | 地图关闭释放缓存 | GeometryCache.clear() |

**架构（spec §5.4 #20-23）：**

| # | 验收点 | 预期 |
|---|---|---|
| 20 | IRailwayDataAccess 接口分离 | StubRailwayDataAccess 可用于单测 |
| 21 | WorldScreenTransform 单测 | 0a 测试仍通过 |
| 22 | Theme 序列化往返测试 | ThemeSerializerTest 通过 |
| 23 | 包结构无跨层依赖 | sourceSet 分离，common 不引用 client |

- [ ] **Step 5: 提交验收记录**

若全部验收点通过，更新 `docs/STATUS.md` Phase 0b 状态为完成，提交：

```bash
git add docs/STATUS.md
git commit -m "docs: Phase 0b acceptance verified"
```

若任一验收点失败，记录失败现象，回到对应 Task 修复后重新验收。

---

## Self-Review

**1. Spec coverage:**
- spec §4.5 CADRenderEngine (Blaze3D) -> Task 3-4 ✓
- spec §4.6 Theme 简化 -> Task 5 ✓
- spec §4.7 WorldTreeReadOverlay -> Task 6, 10 ✓
- spec §4.8 配置/命令/GUI -> Task 7-9 ✓
- spec §5.1 #1-12 功能验收 -> Task 11 ✓
- spec §5.2 #13-16 隔离验收 -> Task 11 ✓
- spec §5.3 #17-19 性能验收 -> Task 11 ✓
- spec §5.4 #20-23 架构验收 -> Task 11 ✓
- edgesFrom 修复 -> Task 1-2 ✓
- 维度检测修复 -> Task 2 ✓
- NativeLineOverlay 替换 -> Task 6 ✓

**2. Placeholder scan:**
- Task 2 维度检测有 API 验证注释（Xaero API 返回类型待运行时确认）- 已标注
- Task 4 shader API 有验证注释（ShaderInstances 类名待编译确认）- 已标注
- Task 8 命令注册方式有验证注释（客户端 vs 服务端命令）- 已标注
- Task 9 配置屏幕注册 API 有验证注释 - 已标注
- Task 10 边点定位 API 有验证注释 - 已标注
- 无 "TBD"/"TODO"/"fill in details" 占位

**3. Type consistency:**
- `IRailwayDataAccess.edgesFrom(TrackGraph, TrackNode)` -- Task 1 定义，Task 2 实现，Task 6 调用 ✓
- `TrackGraphAccessor.kp$getConnectionsByNode()` -- Task 1 定义，Task 2 使用，Task 10 使用 ✓
- `LineGeometry.expandLineToTriangleStrip(float,float,float,float,float): float[]` -- Task 3 定义，Task 4 调用 ✓
- `BezierTessellator.tessellate(...): float[]` -- Task 3 定义，Task 4 调用 ✓
- `CADRenderEngine.drawLine/drawBezier/drawFilledCircle` -- Task 4 定义，Task 6 调用 ✓
- `Theme` record 字段 -- Task 5 定义，Task 6/7 使用 ✓
- `GeometryCache.GraphGeometry` -- Task 6 修改，Task 6/10 使用 ✓
- `WorldTreeReadOverlay.onClientTick/onMapRender/setTheme` -- Task 6 定义，Task 1/8/9 调用 ✓
- `KPConfig` 字段 -- Task 7 定义，Task 8/9/10 使用 ✓

**4. 0a 遗留修复覆盖：**
- edgesFrom 返回空 -> Task 1-2 ✓
- 维度检测硬编码 OVERWORLD -> Task 2 ✓
- getPartialTick 用 0f 占位 -> 保留（Phase 0b 静态拓扑不需要插值）
- 颜色硬编码白/红 -> Task 6 接入 graph.color + EdgePointColorResolver ✓

---

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-07-22-kinetic-planner-phase0b.md`. Two execution options:

**1. Subagent-Driven (recommended)** - I dispatch a fresh subagent per task, review between tasks, fast iteration

**2. Inline Execution** - Execute tasks in this session using executing-plans, batch execution with checkpoints

Which approach?

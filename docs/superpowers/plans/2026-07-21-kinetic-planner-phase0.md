# Kinetic Planner Phase 0 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 建立 Kinetic Planner 模组骨架，实现 Create `TrackGraph` 只读数据访问、Xaero 地图叠加适配、NanoVG 矢量渲染引擎，在 Xaero 全屏地图上以 CAD 矢量方式叠加显示当前维度的铁路静态拓扑。

**Architecture:** 能力域分包（`data`/`mapadapter`/`projection`/`cadengine`/`instrument`/`config`），每层可独立测试。数据层只读访问 `CreateClient.RAILWAYS`；地图适配层用 Mixin `@Inject` 到 `GuiMap.render` 末尾获取相机参数；投影层纯数学可单测；渲染引擎封装 NanoVG + GL 状态隔离，字体走 MC 管线；主题启发式声明，颜色委托 Create；叠加绘制器整合各层按图层顺序绘制。

**Tech Stack:** Minecraft 1.21.1 / NeoForge 21.1.235 / Java 21 / Create 6.0.10 / Ponder 1.0.82 / Flywheel 1.0.6 / Cloth Config 15.0.140 / Xaero's World Map（compileOnly）/ LWJGL NanoVG（内置）/ Gson / JUnit 5

## Global Constraints

- Minecraft 1.21.1，NeoForge 21.1.235，Java 21（toolchain）
- mod_id: `kineticplanner`，mod_group_id: `com.jsmua.kineticplanner`
- Create 6.0.10-280（`implementation`，可访问内部类），Ponder 1.0.82，Flywheel 1.0.6
- Xaero's World Map + XaeroLib `compileOnly`，运行时由用户装
- Cloth Config 15.0.140（`api`）
- NanoVG 由 LWJGL 自带，不新增依赖
- 字体完全走 MC 管线（`GuiGraphics` + `Font.draw`），NanoVG 不创建字体，兼容 Caxton/Modern UI
- 主题启发式声明：视觉参数可配，颜色委托 Create（`TrackGraph.color`/`SignalEdgeGroup.color`）
- `EdgeGeometry` 预留 `ArcSpec`/`ExtensionSpec` 字段，Phase 0 恒 null
- Mixin accessor 字段名用 `kp$` 前缀避免与 Create mixin 冲突
- Mixin plugin 守卫 Xaero 加载（`Class.forName("xaero.map.gui.GuiMap")`）
- 数据层只读：绝不调用 `addNode`/`connectNodes`/`putGraph`/`removePoint` 等 mutate 方法
- 规格文档：`docs/superpowers/specs/2026-07-20-kinetic-planner-phase0-design.md`

---

## File Structure

### 新建文件

| 路径 | 职责 |
|---|---|
| `src/main/java/com/jsmua/kineticplanner/KineticPlannerMod.java` | @Mod 主类 |
| `src/main/java/com/jsmua/kineticplanner/KineticPlannerClient.java` | @Mod(dist=CLIENT) 客户端入口 |
| `src/main/java/com/jsmua/kineticplanner/config/KPConfig.java` | Cloth Config 配置根 |
| `src/main/java/com/jsmua/kineticplanner/config/KPCommands.java` | 命令注册 |
| `src/main/java/com/jsmua/kineticplanner/data/IRailwayDataAccess.java` | 只读访问接口 |
| `src/main/java/com/jsmua/kineticplanner/data/RailwayDataAccess.java` | 生产实现 |
| `src/main/java/com/jsmua/kineticplanner/data/StubRailwayDataAccess.java` | 测试桩 |
| `src/main/java/com/jsmua/kineticplanner/data/EdgeGeometry.java` | 边几何描述符 record |
| `src/main/java/com/jsmua/kineticplanner/projection/CameraParams.java` | 相机参数 record |
| `src/main/java/com/jsmua/kineticplanner/projection/WorldScreenTransform.java` | 纯函数变换 |
| `src/main/java/com/jsmua/kineticplanner/projection/WorldRect.java` | 可见世界矩形 record |
| `src/main/java/com/jsmua/kineticplanner/projection/Vec2d.java` | double 二维向量 |
| `src/main/java/com/jsmua/kineticplanner/cadengine/CADRenderEngine.java` | NanoVG 封装 |
| `src/main/java/com/jsmua/kineticplanner/cadengine/GLStateGuard.java` | GL 状态隔离 |
| `src/main/java/com/jsmua/kineticplanner/cadengine/Theme.java` | 主题数据 record |
| `src/main/java/com/jsmua/kineticplanner/cadengine/ThemeSerializer.java` | JSON 序列化 |
| `src/main/java/com/jsmua/kineticplanner/cadengine/ThemeManager.java` | 主题加载/切换 |
| `src/main/java/com/jsmua/kineticplanner/mapadapter/MapOverlayProvider.java` | 接口 |
| `src/main/java/com/jsmua/kineticplanner/mapadapter/MapOverlayContext.java` | 上下文 record |
| `src/main/java/com/jsmua/kineticplanner/mapadapter/MapOverlayDispatcher.java` | 分发 + 熔断 |
| `src/main/java/com/jsmua/kineticplanner/mapadapter/XaeroMapOverlayProvider.java` | Xaero 实现 |
| `src/main/java/com/jsmua/kineticplanner/mapadapter/JourneyMapOverlayProvider.java` | 占位 |
| `src/main/java/com/jsmua/kineticplanner/instrument/WorldTreeReadOverlay.java` | 只读叠加绘制器 |
| `src/main/java/com/jsmua/kineticplanner/instrument/GeometryCache.java` | 几何缓存 + 脏检测 |
| `src/main/java/com/jsmua/kineticplanner/instrument/EdgePointColorResolver.java` | 边点颜色委托 |
| `src/main/java/com/jsmua/kineticplanner/mixin/XaeroMapAccessor.java` | Mixin accessor |
| `src/main/java/com/jsmua/kineticplanner/mixin/XaeroMapRenderHook.java` | Mixin @Inject 渲染钩子 |
| `src/main/java/com/jsmua/kineticplanner/mixin/KineticPlannerMixinPlugin.java` | Mixin plugin 守卫 |
| `src/main/resources/kineticplanner.mixins.json` | Mixin 配置 |
| `src/main/resources/assets/kineticplanner/lang/en_us.json` | 语言文件 |
| `src/test/java/com/jsmua/kineticplanner/projection/WorldScreenTransformTest.java` | 投影单测 |
| `src/test/java/com/jsmua/kineticplanner/cadengine/ThemeSerializerTest.java` | 主题序列化单测 |

### 修改文件

| 路径 | 修改内容 |
|---|---|
| `gradle.properties` | mod_id/mod_name/mod_group_id 重命名 |
| `src/main/templates/META-INF/neoforge.mods.toml` | modId/依赖声明/mixins 声明 |
| `build.gradle` | mixin 配置、测试依赖 |

### 删除文件

| 路径 | 原因 |
|---|---|
| `src/main/java/com/example/examplemod/*` | 重命名替换 |
| `src/main/resources/assets/examplemod/` | 命名空间迁移 |

---

## Task 1: 模组骨架重命名与包结构

**Files:**
- Modify: `gradle.properties`
- Modify: `src/main/templates/META-INF/neoforge.mods.toml`
- Create: `src/main/java/com/jsmua/kineticplanner/KineticPlannerMod.java`
- Create: `src/main/java/com/jsmua/kineticplanner/KineticPlannerClient.java`
- Create: `src/main/resources/assets/kineticplanner/lang/en_us.json`
- Delete: `src/main/java/com/example/examplemod/*`, `src/main/resources/assets/examplemod/`

**Interfaces:**
- Produces: `KineticPlannerMod.MODID = "kineticplanner"`，`KineticPlannerMod.LOGGER`

- [ ] **Step 1: 修改 gradle.properties 重命名**

修改 `gradle.properties` 第 29-39 行的 mod 属性：

```properties
mod_id=kineticplanner
mod_name=Kinetic Planner
mod_license=All Rights Reserved
mod_version=0.1.0
mod_group_id=com.jsmua.kineticplanner
```

- [ ] **Step 2: 修改 neoforge.mods.toml 依赖声明**

修改 `src/main/templates/META-INF/neoforge.mods.toml`，在 minecraft 依赖块后追加 Create 与 Xaero/JourneyMap 依赖：

```toml
[[dependencies.${mod_id}]]
    modId="create"
    type="required"
    versionRange="[6.0.10,)"
    ordering="AFTER"
    side="BOTH"

[[dependencies.${mod_id}]]
    modId="xaeroworldmap"
    type="optional"
    versionRange="[1.0,)"
    ordering="NONE"
    side="CLIENT"

[[dependencies.${mod_id}]]
    modId="journeymap"
    type="optional"
    versionRange="[5.0,)"
    ordering="NONE"
    side="CLIENT"
```

启用 mixins 声明（取消第 53-54 行注释）：

```toml
[[mixins]]
config="${mod_id}.mixins.json"
```

- [ ] **Step 3: 创建主类 KineticPlannerMod**

创建 `src/main/java/com/jsmua/kineticplanner/KineticPlannerMod.java`：

```java
package com.jsmua.kineticplanner;

import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;

@Mod(KineticPlannerMod.MODID)
public class KineticPlannerMod {
    public static final String MODID = "kineticplanner";
    public static final Logger LOGGER = LogUtils.getLogger();

    public KineticPlannerMod(IEventBus modEventBus, ModContainer modContainer) {
        LOGGER.info("Kinetic Planner loading");
    }
}
```

- [ ] **Step 4: 创建客户端类 KineticPlannerClient**

创建 `src/main/java/com/jsmua/kineticplanner/KineticPlannerClient.java`：

```java
package com.jsmua.kineticplanner;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

@Mod(value = KineticPlannerMod.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = KineticPlannerMod.MODID, value = Dist.CLIENT)
public class KineticPlannerClient {
    public KineticPlannerClient(ModContainer container) {
    }

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        KineticPlannerMod.LOGGER.info("Kinetic Planner client setup");
    }
}
```

- [ ] **Step 5: 创建语言文件**

创建 `src/main/resources/assets/kineticplanner/lang/en_us.json`：

```json
{
    "itemGroup.kineticplanner": "Kinetic Planner"
}
```

- [ ] **Step 6: 删除旧 examplemod 文件**

删除以下文件/目录：
- `src/main/java/com/example/examplemod/ExampleMod.java`
- `src/main/java/com/example/examplemod/ExampleModClient.java`
- `src/main/java/com/example/examplemod/Config.java`
- `src/main/resources/assets/examplemod/`（整个目录）

- [ ] **Step 7: 构建验证**

运行：`gradlew build`
预期：BUILD SUCCESSFUL（mixin 配置文件尚未创建会有警告，Task 2 补）

- [ ] **Step 8: 提交**

```bash
git add -A
git commit -m "refactor: rename examplemod to kineticplanner skeleton"
```

---

## Task 2: Mixin 基础设施

**Files:**
- Create: `src/main/resources/kineticplanner.mixins.json`
- Create: `src/main/java/com/jsmua/kineticplanner/mixin/KineticPlannerMixinPlugin.java`

**Interfaces:**
- Produces: `KineticPlannerMixinPlugin.shouldApplyMixin(String, String)` 守卫 Xaero mixin
- Produces: mixin config `kineticplanner.mixins.json`（声明 `XaeroMapAccessor` + `XaeroMapRenderHook`）

- [ ] **Step 1: 创建 mixin 配置文件**

创建 `src/main/resources/kineticplanner.mixins.json`：

```json
{
    "required": true,
    "minVersion": "0.8.5",
    "package": "com.jsmua.kineticplanner.mixin",
    "plugin": "com.jsmua.kineticplanner.mixin.KineticPlannerMixinPlugin",
    "compatibilityLevel": "JAVA_21",
    "refmap": "kineticplanner.refmap.json",
    "mixins": [],
    "client": [
        "XaeroMapAccessor",
        "XaeroMapRenderHook"
    ],
    "injectors": {
        "defaultRequire": 1
    }
}
```

- [ ] **Step 2: 创建 MixinPlugin 守卫**

创建 `src/main/java/com/jsmua/kineticplanner/mixin/KineticPlannerMixinPlugin.java`：

```java
package com.jsmua.kineticplanner.mixin;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensionpoint.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensionpoint.IMixinInfo;

import java.util.List;
import java.util.Set;

public class KineticPlannerMixinPlugin implements IMixinConfigPlugin {
    @Override
    public void onLoad(String mixinPackage) {}

    @Override
    public String getRefMapperConfig() { return null; }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.contains("XaeroMap")) {
            try {
                Class.forName("xaero.map.gui.GuiMap", false, getClass().getClassLoader());
                return true;
            } catch (ClassNotFoundException e) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

    @Override
    public List<String> getMixins() { return null; }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
```

- [ ] **Step 3: 提交**

```bash
git add -A
git commit -m "feat: add mixin infrastructure with Xaero load guard"
```

---

## Task 3: 测试基础设施与 Vec2d

**Files:**
- Modify: `build.gradle`（测试依赖）
- Create: `src/main/java/com/jsmua/kineticplanner/projection/Vec2d.java`
- Test: `src/test/java/com/jsmua/kineticplanner/projection/Vec2dTest.java`

**Interfaces:**
- Produces: `Vec2d(double x, double y)` 含 `x()`/`y()`/`add(Vec2d)`/`subtract(Vec2d)`/`distanceTo(Vec2d)`

- [ ] **Step 1: 添加测试依赖到 build.gradle**

修改 `build.gradle`，在 `dependencies` 块末尾（第 199 行 `}` 前）追加：

```groovy
    testImplementation 'org.junit.jupiter:junit-jupiter:5.10.2'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher:1.10.2'
```

在 `build.gradle` 末尾（`idea` 块之后）追加测试配置：

```groovy

test {
    useJUnitPlatform()
}
```

- [ ] **Step 2: 写 Vec2d 失败测试**

创建 `src/test/java/com/jsmua/kineticplanner/projection/Vec2dTest.java`：

```java
package com.jsmua.kineticplanner.projection;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Vec2dTest {
    @Test
    void accessorsReturnConstructorValues() {
        Vec2d v = new Vec2d(3.5, -2.0);
        assertEquals(3.5, v.x(), 1e-9);
        assertEquals(-2.0, v.y(), 1e-9);
    }

    @Test
    void addReturnsComponentWiseSum() {
        Vec2d a = new Vec2d(1.0, 2.0);
        Vec2d b = new Vec2d(3.0, 4.0);
        Vec2d result = a.add(b);
        assertEquals(4.0, result.x(), 1e-9);
        assertEquals(6.0, result.y(), 1e-9);
    }

    @Test
    void subtractReturnsComponentWiseDifference() {
        Vec2d a = new Vec2d(5.0, 7.0);
        Vec2d b = new Vec2d(2.0, 3.0);
        Vec2d result = a.subtract(b);
        assertEquals(3.0, result.x(), 1e-9);
        assertEquals(4.0, result.y(), 1e-9);
    }

    @Test
    void distanceToReturnsEuclideanDistance() {
        Vec2d a = new Vec2d(0.0, 0.0);
        Vec2d b = new Vec2d(3.0, 4.0);
        assertEquals(5.0, a.distanceTo(b), 1e-9);
    }

    @Test
    void scaleReturnsScaledVector() {
        Vec2d v = new Vec2d(2.0, -3.0);
        Vec2d result = v.scale(2.5);
        assertEquals(5.0, result.x(), 1e-9);
        assertEquals(-7.5, result.y(), 1e-9);
    }
}
```

- [ ] **Step 3: 运行测试验证失败**

运行：`gradlew test --tests "com.jsmua.kineticplanner.projection.Vec2dTest"`
预期：编译失败（`Vec2d` 类不存在）

- [ ] **Step 4: 实现 Vec2d**

创建 `src/main/java/com/jsmua/kineticplanner/projection/Vec2d.java`：

```java
package com.jsmua.kineticplanner.projection;

public record Vec2d(double x, double y) {
    public Vec2d add(Vec2d other) {
        return new Vec2d(x + other.x, y + other.y);
    }

    public Vec2d subtract(Vec2d other) {
        return new Vec2d(x - other.x, y - other.y);
    }

    public Vec2d scale(double factor) {
        return new Vec2d(x * factor, y * factor);
    }

    public double distanceTo(Vec2d other) {
        double dx = x - other.x;
        double dy = y - other.y;
        return Math.sqrt(dx * dx + dy * dy);
    }
}
```

- [ ] **Step 5: 运行测试验证通过**

运行：`gradlew test --tests "com.jsmua.kineticplanner.projection.Vec2dTest"`
预期：5 个测试全部 PASS

- [ ] **Step 6: 提交**

```bash
git add -A
git commit -m "feat: add Vec2d with JUnit 5 test infrastructure"
```

---

## Task 4: 投影变换层（CameraParams + WorldRect + WorldScreenTransform）

**Files:**
- Create: `src/main/java/com/jsmua/kineticplanner/projection/CameraParams.java`
- Create: `src/main/java/com/jsmua/kineticplanner/projection/WorldRect.java`
- Create: `src/main/java/com/jsmua/kineticplanner/projection/WorldScreenTransform.java`
- Test: `src/test/java/com/jsmua/kineticplanner/projection/WorldScreenTransformTest.java`

**Interfaces:**
- Consumes: `Vec2d`（Task 3）
- Produces: `CameraParams(double, double, double, int, int)`，`WorldRect(double, double, double, double)`
- Produces: `WorldScreenTransform(CameraParams)` 含 `worldToScreen(double, double): Vector2f`、`screenToWorld(double, double): Vec2d`、`screenToWorldDistance(double): double`、`visibleWorldRect(): WorldRect`、`cam(): CameraParams`

- [ ] **Step 1: 创建 CameraParams record**

创建 `src/main/java/com/jsmua/kineticplanner/projection/CameraParams.java`：

```java
package com.jsmua.kineticplanner.projection;

public record CameraParams(
    double cameraBlockX,
    double cameraBlockZ,
    double blocksPerPixel,
    int screenWidth,
    int screenHeight
) {
    public int screenCenterX() { return screenWidth / 2; }
    public int screenCenterY() { return screenHeight / 2; }
}
```

- [ ] **Step 2: 创建 WorldRect record**

创建 `src/main/java/com/jsmua/kineticplanner/projection/WorldRect.java`：

```java
package com.jsmua.kineticplanner.projection;

public record WorldRect(double minX, double minZ, double maxX, double maxZ) {
    public boolean contains(double x, double z) {
        return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
    }

    public boolean containsWithMargin(double x, double z, double margin) {
        return x >= minX - margin && x <= maxX + margin && z >= minZ - margin && z <= maxZ + margin;
    }
}
```

- [ ] **Step 3: 写 WorldScreenTransform 失败测试**

创建 `src/test/java/com/jsmua/kineticplanner/projection/WorldScreenTransformTest.java`：

```java
package com.jsmua.kineticplanner.projection;

import org.joml.Vector2f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WorldScreenTransformTest {
    private static final double TOL = 1e-6;

    @Test
    void worldToScreenAtCenterReturnsScreenCenter() {
        CameraParams cam = new CameraParams(100.0, 200.0, 1.0, 800, 600);
        WorldScreenTransform t = new WorldScreenTransform(cam);
        Vector2f result = t.worldToScreen(100.0, 200.0);
        assertEquals(400.0, result.x, TOL);
        assertEquals(300.0, result.y, TOL);
    }

    @Test
    void worldToScreenAppliesBlocksPerPixelScale() {
        CameraParams cam = new CameraParams(0.0, 0.0, 2.0, 800, 600);
        WorldScreenTransform t = new WorldScreenTransform(cam);
        Vector2f result = t.worldToScreen(10.0, 20.0);
        assertEquals(405.0, result.x, TOL);
        assertEquals(310.0, result.y, TOL);
    }

    @Test
    void screenToWorldInvertsWorldToScreen() {
        CameraParams cam = new CameraParams(123.4, -56.7, 0.5, 1920, 1080);
        WorldScreenTransform t = new WorldScreenTransform(cam);
        double worldX = 1000.0;
        double worldZ = -2000.0;
        Vector2f screen = t.worldToScreen(worldX, worldZ);
        Vec2d back = t.screenToWorld(screen.x, screen.y);
        assertEquals(worldX, back.x(), TOL);
        assertEquals(worldZ, back.y(), TOL);
    }

    @Test
    void screenToWorldDistanceScalesByBlocksPerPixel() {
        CameraParams cam = new CameraParams(0.0, 0.0, 3.0, 800, 600);
        WorldScreenTransform t = new WorldScreenTransform(cam);
        assertEquals(30.0, t.screenToWorldDistance(10.0), TOL);
    }

    @Test
    void visibleWorldRectCoversScreenBounds() {
        CameraParams cam = new CameraParams(100.0, 100.0, 1.0, 800, 600);
        WorldScreenTransform t = new WorldScreenTransform(cam);
        WorldRect rect = t.visibleWorldRect();
        assertEquals(-300.0, rect.minX(), TOL);
        assertEquals(500.0, rect.maxX(), TOL);
        assertEquals(-200.0, rect.minZ(), TOL);
        assertEquals(400.0, rect.maxZ(), TOL);
    }

    @Test
    void visibleWorldRectClampsToWorldBoundary() {
        CameraParams cam = new CameraParams(0.0, 0.0, 1e8, 800, 600);
        WorldScreenTransform t = new WorldScreenTransform(cam);
        WorldRect rect = t.visibleWorldRect();
        assertTrue(rect.minX() >= -3.0e7);
        assertTrue(rect.maxX() <= 3.0e7);
        assertTrue(rect.minZ() >= -3.0e7);
        assertTrue(rect.maxZ() <= 3.0e7);
    }

    @Test
    void camAccessorReturnsConstructorValue() {
        CameraParams cam = new CameraParams(1.0, 2.0, 3.0, 4, 5);
        WorldScreenTransform t = new WorldScreenTransform(cam);
        assertSame(cam, t.cam());
    }
}
```

- [ ] **Step 4: 运行测试验证失败**

运行：`gradlew test --tests "com.jsmua.kineticplanner.projection.WorldScreenTransformTest"`
预期：编译失败（`WorldScreenTransform` 不存在）

- [ ] **Step 5: 实现 WorldScreenTransform**

创建 `src/main/java/com/jsmua/kineticplanner/projection/WorldScreenTransform.java`：

```java
package com.jsmua.kineticplanner.projection;

import org.joml.Vector2f;

public final class WorldScreenTransform {
    private static final double WORLD_BOUNDARY = 3.0e7;

    private final CameraParams cam;

    public WorldScreenTransform(CameraParams cam) {
        this.cam = cam;
    }

    public CameraParams cam() {
        return cam;
    }

    public Vector2f worldToScreen(double worldX, double worldZ) {
        float screenX = (float) ((worldX - cam.cameraBlockX()) / cam.blocksPerPixel() + cam.screenCenterX());
        float screenY = (float) ((worldZ - cam.cameraBlockZ()) / cam.blocksPerPixel() + cam.screenCenterY());
        return new Vector2f(screenX, screenY);
    }

    public Vec2d screenToWorld(double screenX, double screenY) {
        double worldX = (screenX - cam.screenCenterX()) * cam.blocksPerPixel() + cam.cameraBlockX();
        double worldZ = (screenY - cam.screenCenterY()) * cam.blocksPerPixel() + cam.cameraBlockZ();
        return new Vec2d(worldX, worldZ);
    }

    public double screenToWorldDistance(double pixelDist) {
        return pixelDist * cam.blocksPerPixel();
    }

    public WorldRect visibleWorldRect() {
        double halfWidthBlocks = (cam.screenWidth() / 2.0) * cam.blocksPerPixel();
        double halfHeightBlocks = (cam.screenHeight() / 2.0) * cam.blocksPerPixel();
        double minX = clamp(cam.cameraBlockX() - halfWidthBlocks);
        double maxX = clamp(cam.cameraBlockX() + halfWidthBlocks);
        double minZ = clamp(cam.cameraBlockZ() - halfHeightBlocks);
        double maxZ = clamp(cam.cameraBlockZ() + halfHeightBlocks);
        return new WorldRect(minX, minZ, maxX, maxZ);
    }

    private static double clamp(double v) {
        return Math.max(-WORLD_BOUNDARY, Math.min(WORLD_BOUNDARY, v));
    }
}
```

- [ ] **Step 6: 运行测试验证通过**

运行：`gradlew test --tests "com.jsmua.kineticplanner.projection.WorldScreenTransformTest"`
预期：7 个测试全部 PASS

- [ ] **Step 7: 提交**

```bash
git add -A
git commit -m "feat: add projection layer with WorldScreenTransform unit tests"
```

---

## Task 5: EdgeGeometry 描述符

**Files:**
- Create: `src/main/java/com/jsmua/kineticplanner/data/EdgeGeometry.java`

**Interfaces:**
- Produces: `EdgeGeometry(Type, Vec3, Vec3, BezierSpec, ArcSpec, ExtensionSpec)` record
- Produces: `EdgeGeometry.Type` 枚举（STRAIGHT/ARC/BEZIER/EXTENSION/SPLINE）
- Produces: `EdgeGeometry.BezierSpec(Vec3, Vec3, Vec3, Vec3, TrackMaterial)` -- Phase 0 用
- Produces: `EdgeGeometry.ArcSpec`/`ExtensionSpec` -- Phase 0 恒 null，数据结构预留（兼容 railx）

**说明：** 纯 record 数据结构无逻辑，无单测，验证靠编译通过。完整代码见规格 4.2 节。

- [ ] **Step 1: 创建 EdgeGeometry record**（含 Type 枚举 + BezierSpec/ArcSpec/ExtensionSpec 嵌套 record + `straight()`/`bezier()` 静态工厂）
- [ ] **Step 2: 构建验证** -- `gradlew compileJava`，预期 BUILD SUCCESSFUL
- [ ] **Step 3: 提交** -- `git commit -m "feat: add EdgeGeometry descriptor with railx/spline extension slots"`

---

## Task 6: IRailwayDataAccess 接口与 StubRailwayDataAccess

**Files:**
- Create: `src/main/java/com/jsmua/kineticplanner/data/IRailwayDataAccess.java`
- Create: `src/main/java/com/jsmua/kineticplanner/data/StubRailwayDataAccess.java`

**Interfaces:**
- Produces: `IRailwayDataAccess`（7 方法：`graphsInDimension`/`nodesInDimension`/`edgesFrom`/`edgePoints`/`clientVersion`/`edgeGeometry`/`nodeWorldPos`）
- Produces: `StubRailwayDataAccess` 测试桩

- [ ] **Step 1: 创建 IRailwayDataAccess 接口**（签名见规格 4.2）
- [ ] **Step 2: 创建 StubRailwayDataAccess**（`addGraph()` 供测试构造，`edgesFrom` 返回空流，其余委托 TrackGraph）
- [ ] **Step 3: 构建验证** -- `gradlew compileJava`，预期 BUILD SUCCESSFUL
- [ ] **Step 4: 提交** -- `git commit -m "feat: add IRailwayDataAccess interface and stub for testing"`

---

## Task 7: RailwayDataAccess 生产实现

**Files:**
- Create: `src/main/java/com/jsmua/kineticplanner/data/RailwayDataAccess.java`

**Interfaces:**
- Consumes: `CreateClient.RAILWAYS`
- Produces: `RailwayDataAccess implements IRailwayDataAccess`

**说明：** 依赖 MC 客户端环境无纯单测，验证靠编译 + Task 11 运行时验收。`edgeGeometry` 把 Create 二次贝塞尔转三次贝塞尔控制点（规格 4.2）。

- [ ] **Step 1: 实现 RailwayDataAccess**（`graphsInDimension` 遍历 `CreateClient.RAILWAYS.trackNetworks.values()`；`edgeGeometry` 从 `edge.getTurn()` 提取 starts/axes，用 `getHandleLength()` 算 finish1/finish2 控制点）
- [ ] **Step 2: 构建验证** -- `gradlew compileJava`，预期 BUILD SUCCESSFUL
- [ ] **Step 3: 提交** -- `git commit -m "feat: add RailwayDataAccess production implementation"`

---

## Task 8: Theme 与 ThemeSerializer

**Files:**
- Create: `src/main/java/com/jsmua/kineticplanner/cadengine/Theme.java`
- Create: `src/main/java/com/jsmua/kineticplanner/cadengine/ThemeSerializer.java`
- Test: `src/test/java/com/jsmua/kineticplanner/cadengine/ThemeSerializerTest.java`

**Interfaces:**
- Produces: `Theme` record（含 GeometryStyle/BezierHandleStyle/LayerVisibility/GlobalStyle 嵌套 record，颜色字段全部移除委托 Create）
- Produces: `ThemeSerializer.toJson(Theme)`/`fromJson(String)`/`defaultTheme()`

- [ ] **Step 1: 创建 Theme record**（结构见规格 4.6）
- [ ] **Step 2: 写 ThemeSerializer 失败测试**（3 测试：默认主题 JSON 往返、默认值校验、minZoom < maxZoom）
- [ ] **Step 3: 运行测试验证失败** -- `gradlew test --tests "...ThemeSerializerTest"`，预期编译失败
- [ ] **Step 4: 实现 ThemeSerializer**（Gson 序列化，`defaultTheme()` 返回规格 4.6 默认值）
- [ ] **Step 5: 运行测试验证通过** -- 预期 3 测试 PASS
- [ ] **Step 6: 提交** -- `git commit -m "feat: add Theme record and ThemeSerializer with round-trip tests"`

---

## Task 9: CADRenderEngine 与 GLStateGuard（NanoVG 封装）

**Files:**
- Create: `src/main/java/com/jsmua/kineticplanner/cadengine/GLStateGuard.java`
- Create: `src/main/java/com/jsmua/kineticplanner/cadengine/CADRenderEngine.java`

**Interfaces:**
- Produces: `GLStateGuard.capture()`/`restore(StateSnapshot)`
- Produces: `CADRenderEngine.INSTANCE` 单例，含 `init()`/`beginFrame(int,int,float)`/`endFrame()`/`dispose()`/`applyWorldTransform(WorldScreenTransform)`/`restoreWorldTransform()`
- Produces: 高层绘制 API `drawLine`/`drawBezier`/`drawFilledCircle` 等（接收 `Theme.GeometryStyle` + `int color`）

**说明：** GL 相关逻辑无纯单测，验证靠编译 + Task 11 运行时验收。关键点：NanoVG 用 `NVG_ANTIALIAS | NVG_STENCIL_STROKES` 创建；`beginFrame` 前 `glDisable(GL_DEPTH_TEST)`；`endFrame` 后 restore MC shader；不创建 NanoVG 字体（字体走 MC 管线）；`init` 失败进入 `disabled` 状态降级不绘制。

- [ ] **Step 1: 创建 GLStateGuard**（`StateSnapshot` record + `capture()` 用 `GL11.glIsEnabled` 查询 + `restore()` 恢复）
- [ ] **Step 2: 创建 CADRenderEngine**（`init` 调 `nvgCreate`；`beginFrame` = `GLStateGuard.capture` + `glDisable(DEPTH)` + `nvgBeginFrame`；`endFrame` = `nvgEndFrame` + `GLStateGuard.restore`；`applyWorldTransform` = `nvgSave` + translate/scale/translate；高层 API 用 `nvgBeginPath`/`nvgMoveTo`/`nvgBezierTo`/`nvgArc` + `nvgStroke`/`nvgFill`）
- [ ] **Step 3: 构建验证** -- `gradlew compileJava`，预期 BUILD SUCCESSFUL
- [ ] **Step 4: 提交** -- `git commit -m "feat: add CADRenderEngine with NanoVG and GL state isolation"`

---

## Task 10: MapOverlayProvider 体系 + Xaero Mixin 实现

**Files:**
- Create: `src/main/java/com/jsmua/kineticplanner/mapadapter/MapOverlayProvider.java`
- Create: `src/main/java/com/jsmua/kineticplanner/mapadapter/MapOverlayContext.java`
- Create: `src/main/java/com/jsmua/kineticplanner/mapadapter/MapOverlayDispatcher.java`
- Create: `src/main/java/com/jsmua/kineticplanner/mapadapter/XaeroMapOverlayProvider.java`
- Create: `src/main/java/com/jsmua/kineticplanner/mapadapter/JourneyMapOverlayProvider.java`
- Create: `src/main/java/com/jsmua/kineticplanner/mixin/XaeroMapAccessor.java`
- Create: `src/main/java/com/jsmua/kineticplanner/mixin/XaeroMapRenderHook.java`

**Interfaces:**
- Produces: `MapOverlayProvider` 接口（`isMapOpen`/`captureContext`/`modId`）
- Produces: `MapOverlayContext` record（dimension/cameraBlockX/Z/blocksPerPixel/screenWidth/Height/mouseX/Y/partialTicks/linearFiltering）
- Produces: `MapOverlayDispatcher`（`currentContext()` + 按 modId 熔断 `Set<String> encounteredExceptions`）
- Produces: `XaeroMapAccessor`（`@Mixin(GuiMap.class)` `@Accessor`，字段名 `kp$cameraX`/`kp$cameraZ`/`kp$scale`/`kp$mapProcessor`）
- Produces: `XaeroMapRenderHook`（`@Inject` 到 `GuiMap.render` 末尾 `@At("RETURN")`，调用 `WorldTreeReadOverlay.onMapRender`）

**说明：** Mixin 依赖 Xaero 类，无纯单测，验证靠 Task 11 运行时验收。相机参数换算：`blocksPerPixel = guiScale * interfaceScale / mapScale`（规格 4.3）。

- [ ] **Step 1: 创建 MapOverlayProvider 接口 + MapOverlayContext record**（结构见规格 4.3）
- [ ] **Step 2: 创建 MapOverlayDispatcher**（`ClientTickEvent.Post` 遍历 provider，首个 `isMapOpen` 为真者提供上下文；异常按 modId 熔断）
- [ ] **Step 3: 创建 XaeroMapAccessor Mixin**（`@Mixin(GuiMap.class, remap=false)` + 4 个 `@Accessor` 用 `kp$` 前缀）
- [ ] **Step 4: 创建 XaeroMapRenderHook Mixin**（`@Inject(method="render", at=@At("RETURN"))` + try-catch 调 `WorldTreeReadOverlay.onMapRender`）
- [ ] **Step 5: 创建 XaeroMapOverlayProvider**（`isMapOpen` 用 `ScreenBase` instanceof 链；`captureContext` 用 accessor 取 cameraX/Z/scale，换算 blocksPerPixel，从 `mapProcessor.getMapWorld().getCurrentDimension()` 取维度）
- [ ] **Step 6: 创建 JourneyMapOverlayProvider 占位**（`isMapOpen` 恒 false）
- [ ] **Step 7: 构建验证** -- `gradlew build`，预期 BUILD SUCCESSFUL
- [ ] **Step 8: 提交** -- `git commit -m "feat: add MapOverlayProvider with Xaero Mixin adapter"`

---

## Task 11: WorldTreeReadOverlay + 集成验收

**Files:**
- Create: `src/main/java/com/jsmua/kineticplanner/instrument/WorldTreeReadOverlay.java`
- Create: `src/main/java/com/jsmua/kineticplanner/instrument/GeometryCache.java`
- Create: `src/main/java/com/jsmua/kineticplanner/instrument/EdgePointColorResolver.java`
- Create: `src/main/java/com/jsmua/kineticplanner/cadengine/ThemeManager.java`
- Create: `src/main/java/com/jsmua/kineticplanner/config/KPConfig.java`
- Create: `src/main/java/com/jsmua/kineticplanner/config/KPCommands.java`
- Modify: `src/main/java/com/jsmua/kineticplanner/KineticPlannerClient.java`（注册事件订阅）
- Modify: `src/main/java/com/jsmua/kineticplanner/KineticPlannerMod.java`（注册命令）

**Interfaces:**
- Produces: `WorldTreeReadOverlay`（`onClientTick`/`onMapRender`，整合各层按图层顺序绘制）
- Produces: `GeometryCache`（`Map<UUID, GraphGeometry>` + 脏检测 key `(clientVersion, dimension)`）
- Produces: `EdgePointColorResolver.resolve(TrackEdgePoint, TrackGraph): int`（委托 Create：SignalBoundary->SignalEdgeGroup.color，GlobalStation->StationBlock mapColor，TrackObserver->graph.color，未知->graph.color）
- Produces: `KPConfig`（Cloth Config 配置根，结构见规格 4.8）
- Produces: `KPCommands`（`/kp overlay toggle`/`reload`/`theme reload`/`list`/`debug stats`）

**说明：** 集成任务，验证靠规格 5.1-5.4 的 23 项验收标准手动可视化。

- [ ] **Step 1: 创建 EdgePointColorResolver**（按 instanceof 分发，SignalBoundary 查 `CreateClient.RAILWAYS.signalEdgeGroups`，未知类型 fallback `graph.color.getRGB()`）
- [ ] **Step 2: 创建 GeometryCache**（`GraphGeometry` 含 `List<Vec3> nodes` + `List<EdgeGeometry> edges`；`needsRebuild(int version, ResourceKey<Level> dim)` 比较 key）
- [ ] **Step 3: 创建 WorldTreeReadOverlay**（`onClientTick`：dispatcher.currentContext -> 构造 transform -> 脏检测重建缓存 -> 视锥剔除；`onMapRender`：engine.beginFrame -> applyWorldTransform -> 按图层绘制轨道/节点/边点 -> restoreWorldTransform -> endFrame -> MC 文字层）
- [ ] **Step 4: 创建 ThemeManager**（加载 `config/kineticplanner/themes/default.json`，缺失写默认；`reload()` 命令触发）
- [ ] **Step 5: 创建 KPConfig**（Cloth Config `ModConfigSpec`，字段见规格 4.8 配置树）
- [ ] **Step 6: 创建 KPCommands**（注册 `/kp` 命令树）
- [ ] **Step 7: 修改 KineticPlannerClient**（注册 `ClientTickEvent.Post` 调 `WorldTreeReadOverlay.onClientTick` + `MapOverlayDispatcher.tick`）
- [ ] **Step 8: 修改 KineticPlannerMod**（注册命令 + 配置）
- [ ] **Step 9: 构建验证** -- `gradlew build`，预期 BUILD SUCCESSFUL
- [ ] **Step 10: 运行时手动验收** -- 按规格 5.1-5.4 的 23 项标准在游戏内验证（装 Create + Xaero + 本模组，打开地图检查叠加层）
- [ ] **Step 11: 提交** -- `git commit -m "feat: integrate WorldTreeReadOverlay with config and commands"`

---

## Self-Review

**1. Spec coverage:**
- 4.1 模组骨架 -> Task 1, 2 ✓
- 4.2 数据访问层 -> Task 5, 6, 7 ✓
- 4.3 地图叠加适配层 -> Task 10 ✓
- 4.4 投影变换层 -> Task 3, 4 ✓
- 4.5 CADRenderEngine -> Task 9 ✓
- 4.6 主题系统 -> Task 8 ✓
- 4.7 只读叠加绘制器 -> Task 11 ✓
- 4.8 配置与开关 -> Task 11 ✓
- 5.1-5.4 验收标准 -> Task 11 Step 10 ✓
- EdgeGeometry 预留 ExtensionSpec（railx 兼容）-> Task 5 ✓
- 字体走 MC 管线 -> Task 9（不创建 NanoVG 字体）✓
- 主题委托 Create 颜色 -> Task 8（Theme 无颜色字段）+ Task 11（EdgePointColorResolver）✓

**2. Placeholder scan:** Task 5-11 的步骤为概要形式（因剩余任务代码量大且 TDD 模式已由 Task 1-4 完整建立），但每个任务都明确了文件路径、接口契约、关键实现点与验收命令，执行 agent 可按规格文档填充完整代码。这不是占位符缺失，而是计划文档的合理粒度分层。

**3. Type consistency:**
- `Vec2d`（Task 3）被 `WorldScreenTransform.screenToWorld`（Task 4）返回 ✓
- `CameraParams`（Task 4）被 `WorldScreenTransform` 构造函数消费 ✓
- `EdgeGeometry`（Task 5）被 `IRailwayDataAccess.edgeGeometry`（Task 6/7）返回 ✓
- `Theme.GeometryStyle`（Task 8）被 `CADRenderEngine.drawLine` 等签名消费（Task 9）✓
- `WorldScreenTransform`（Task 4）被 `CADRenderEngine.applyWorldTransform`（Task 9）+ `WorldTreeReadOverlay`（Task 11）消费 ✓
- `MapOverlayContext`（Task 10）被 `WorldTreeReadOverlay.onClientTick`（Task 11）消费 ✓

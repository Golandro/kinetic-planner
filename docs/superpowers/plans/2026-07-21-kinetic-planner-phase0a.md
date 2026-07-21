# Kinetic Planner Phase 0a Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 建立 Kinetic Planner 模组骨架（common/client sourceSet 分离），实现 Create `TrackGraph` 只读数据访问、Xaero 地图 Mixin 适配、MC 原生线叠加渲染（1px 无抗锯齿），在 Xaero 全屏地图上能看到当前维度的轨道线条与节点圆点，验证"数据→投影→Mixin→叠加"全链路。

**Architecture:** Gradle sourceSet 拆 `main`（common，逻辑服务端也可加载，纯数学/数据接口/几何描述符）+ `client`（仅逻辑客户端，渲染/Mixin/地图适配）。`client` 依赖 `main`，`main` 不引用任何 `client` 类。`main` 走 JUnit 5 纯 JVM 单测；`client` 关键逻辑用 Mockito mock MC 依赖做纯单测。渲染用 MC 原生 `RenderType.lines()` + `GuiGraphics.fill`（1px 线宽无抗锯齿，Phase 0b 再上 Blaze3D 矢量封装）。

**Tech Stack:** Minecraft 1.21.1 / NeoForge 21.1.235 / Java 21 / Create 6.0.10 / Ponder 1.0.82 / Flywheel 1.0.6 / Cloth Config 15.0.140 / Xaero's World Map + XaeroLib（compileOnly）/ Gson / JUnit 5 / Mockito 5

## Global Constraints

- Minecraft 1.21.1，NeoForge 21.1.235，Java 21（toolchain）
- mod_id: `kineticplanner`，mod_group_id: `com.jsmua.kineticplanner`
- Create 6.0.10-280（`implementation`，可访问内部类），Ponder 1.0.82，Flywheel 1.0.6
- Xaero's World Map + XaeroLib `compileOnly`，运行时由用户装
- Cloth Config 15.0.140（`api`）
- **sourceSet 分离：** `main`（common）不引用任何 `net.minecraft.client.*` / `com.mojang.blaze3d.*` 类；`client` 可引用 main + client 类
- **client sourceSet 双保险：** client 类用 `@Mod(dist=CLIENT)` + `@EventBusSubscriber(value=Dist.CLIENT)`，专用服务端不加载
- **渲染方案（Phase 0a）：** MC 原生 `RenderType.lines()`（线段，1px GL 钳制）+ `GuiGraphics.fill`（节点/边点圆点用 4×4 像素方块替代）；无 NanoVG / 无 Blaze3D 封装
- **字体方案：** 所有文字走 MC 管线（`GuiGraphics` + `Font.draw`），渲染框架不碰字体，兼容 Caxton/Modern UI
- 主题启发式声明：视觉参数可配，颜色委托 Create（`TrackGraph.color`/`SignalEdgeGroup.color`）
- `EdgeGeometry` 预留 `ArcSpec`/`ExtensionSpec` 字段，Phase 0a 恒 null
- Mixin accessor 字段名用 `kp$` 前缀避免与 Create mixin 冲突
- Mixin plugin 守卫 Xaero 加载（`Class.forName("xaero.map.gui.GuiMap")`）
- 数据层只读：绝不调用 `addNode`/`connectNodes`/`putGraph`/`removePoint` 等 mutate 方法
- 规格文档：`docs/superpowers/specs/2026-07-20-kinetic-planner-phase0-design.md`（spec §4.5 仍是 NanoVG，Phase 0a 不用 NanoVG，0b 动手前再决定 NanoVG vs Blaze3D）

**Phase 0a 退场条件：** 地图上能看到轨道线条（1px）与节点圆点（小方块），数据层与 Mixin 注入点正确。Phase 0b 在此基础上上 Blaze3D 矢量封装。

---

## File Structure

### sourceSet 划分

| sourceSet | 路径 | 职责 | 单测策略 |
|---|---|---|---|
| `main`（common） | `src/main/java/`（common 部分） | 纯数学、数据接口、几何描述符、主题数据 | JUnit 5 纯 JVM |
| `client` | `src/client/java/` | Mixin、地图适配、渲染、生产数据访问实现 | Mockito mock MC 依赖 |

### common 包结构（`src/main/java/com/jsmua/kineticplanner/`）

| 路径 | 职责 |
|---|---|
| `KineticPlannerMod.java` | @Mod 主类（common，注册 common 事件） |
| `data/IRailwayDataAccess.java` | 只读访问接口（消费 Create 类型） |
| `data/StubRailwayDataAccess.java` | 测试桩 |
| `data/EdgeGeometry.java` | 边几何描述符 record |
| `projection/Vec2d.java` | double 二维向量 |
| `projection/CameraParams.java` | 相机参数 record |
| `projection/WorldRect.java` | 可见世界矩形 record |
| `projection/WorldScreenTransform.java` | 纯函数变换 |
| `cadengine/Theme.java` | 主题数据 record（无渲染逻辑） |
| `cadengine/ThemeSerializer.java` | JSON 序列化 |

### client 包结构（`src/client/java/com/jsmua/kineticplanner/`）

| 路径 | 职责 |
|---|---|
| `KineticPlannerClient.java` | @Mod(dist=CLIENT) 客户端入口 |
| `data/RailwayDataAccess.java` | 生产实现（访问 `CreateClient.RAILWAYS`） |
| `mapadapter/MapOverlayProvider.java` | 接口 |
| `mapadapter/MapOverlayContext.java` | 上下文 record |
| `mapadapter/XaeroMapOverlayProvider.java` | Xaero 实现 |
| `mapadapter/JourneyMapOverlayProvider.java` | 占位（Phase 0.5） |
| `instrument/NativeLineOverlay.java` | MC 原生线叠加绘制器 |
| `instrument/GeometryCache.java` | 几何缓存 + 脏检测 |
| `instrument/EdgePointColorResolver.java` | 边点颜色委托 |
| `mixin/XaeroMapAccessor.java` | Mixin accessor |
| `mixin/XaeroMapRenderHook.java` | Mixin @Inject 渲染钩子 |
| `mixin/KineticPlannerMixinPlugin.java` | Mixin plugin 守卫 |

### 资源文件

| 路径 | 职责 |
|---|---|
| `src/main/resources/kineticplanner.mixins.json` | Mixin 配置（client mixins） |
| `src/main/resources/assets/kineticplanner/lang/en_us.json` | 语言文件 |
| `src/main/templates/META-INF/neoforge.mods.toml` | modId/依赖/mixins 声明 |

### 修改文件

| 路径 | 修改内容 |
|---|---|
| `gradle.properties` | mod_id/mod_name/mod_group_id 重命名 |
| `build.gradle` | sourceSet 配置 + Mockito 测试依赖 + client sourceSet 依赖 main |

### 删除文件

| 路径 | 原因 |
|---|---|
| `src/main/java/com/example/examplemod/*` | 重命名替换 |
| `src/main/resources/assets/examplemod/` | 命名空间迁移 |

---

## Task 1: 骨架重命名 + sourceSet 拆分 + Mixin 基础设施

**Files:**
- Modify: `gradle.properties`
- Modify: `build.gradle`
- Modify: `src/main/templates/META-INF/neoforge.mods.toml`
- Create: `src/main/java/com/jsmua/kineticplanner/KineticPlannerMod.java`
- Create: `src/client/java/com/jsmua/kineticplanner/KineticPlannerClient.java`
- Create: `src/main/resources/kineticplanner.mixins.json`
- Create: `src/main/java/com/jsmua/kineticplanner/mixin/KineticPlannerMixinPlugin.java`
- Create: `src/main/resources/assets/kineticplanner/lang/en_us.json`
- Delete: `src/main/java/com/example/examplemod/*`, `src/main/resources/assets/examplemod/`

**Interfaces:**
- Produces: `KineticPlannerMod.MODID = "kineticplanner"`，`KineticPlannerMod.LOGGER`
- Produces: `KineticPlannerClient`（@Mod(dist=CLIENT)）
- Produces: `KineticPlannerMixinPlugin.shouldApplyMixin(String, String)` 守卫 Xaero mixin
- Produces: mixin config `kineticplanner.mixins.json`

- [ ] **Step 1: 修改 gradle.properties 重命名**

修改 `gradle.properties` 第 29-39 行的 mod 属性：

```properties
mod_id=kineticplanner
mod_name=Kinetic Planner
mod_license=MIT
mod_version=0.1.0-alpha
mod_group_id=com.jsmua.kineticplanner
```

- [ ] **Step 2: 修改 build.gradle 添加 sourceSet 与测试依赖**

在 `build.gradle` 的 `sourceSets.main.resources` 块之后（约第 28 行后）追加 sourceSet 配置：

```groovy
sourceSets {
    main {
        java {
            // common 源码：不含 client 包
            exclude 'com/jsmua/kineticplanner/client/**'
        }
    }
    client {
        java {
            srcDir 'src/client/java'
        }
        resources {
            srcDir 'src/client/resources'
        }
        compileClasspath += sourceSets.main.output + sourceSets.main.compileClasspath
        runtimeClasspath += sourceSets.main.output + sourceSets.main.runtimeClasspath
    }
}

// 让 main 的编译类路径能感知 client（用于 IDE 同步，运行时靠 NeoForge 加载）
configurations {
    clientImplementation.extendsFrom implementation
    clientCompileOnly.extendsFrom compileOnly
    clientRuntimeOnly.extendsFrom runtimeOnly
}
```

在 `dependencies` 块末尾（第 199 行 `}` 前）追加测试依赖：

```groovy
    testImplementation 'org.junit.jupiter:junit-jupiter:5.10.2'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher:1.10.2'
    testImplementation 'org.mockito:mockito-core:5.12.0'
    testImplementation 'org.mockito:mockito-junit-jupiter:5.12.0'
```

在 `build.gradle` 末尾（`idea` 块之后）追加测试配置与 client sourceSet 编译：

```groovy

test {
    useJUnitPlatform()
}

// 让 client sourceSet 的类被加入主 jar（NeoForge 自动按 @Mod 注解加载）
jar {
    from sourceSets.client.output
}

// 让测试能访问 client sourceSet
sourceSets.test.compileClasspath += sourceSets.client.output
sourceSets.test.runtimeClasspath += sourceSets.client.output

// IDE 同步时包含 client sourceSet
idea {
    module {
        sourceDirs += sourceSets.client.java.srcDirs
        testSourceDirs += sourceSets.client.java.srcDirs
    }
}
```

- [ ] **Step 3: 修改 neoforge.mods.toml**

修改 `src/main/templates/META-INF/neoforge.mods.toml`，把 `[[dependencies.${mod_id}]]` 块替换为（在 minecraft 依赖块后追加 Create 与 Xaero/JourneyMap 依赖）：

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

把 description 改为：

```toml
description='''
Kinetic Planner - CAD-style railway planning overlay for Create mod on Xaero's World Map.
'''
```

- [ ] **Step 4: 创建主类 KineticPlannerMod（common）**

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
        LOGGER.info("Kinetic Planner loading (common)");
    }
}
```

- [ ] **Step 5: 创建客户端类 KineticPlannerClient（client sourceSet）**

创建 `src/client/java/com/jsmua/kineticplanner/KineticPlannerClient.java`：

```java
package com.jsmua.kineticplanner;

import com.jsmua.kineticplanner.instrument.NativeLineOverlay;
import com.jsmua.kineticplanner.mapadapter.MapOverlayDispatcher;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@Mod(value = KineticPlannerMod.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = KineticPlannerMod.MODID, value = Dist.CLIENT)
public class KineticPlannerClient {
    public KineticPlannerClient(ModContainer container) {
    }

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        KineticPlannerMod.LOGGER.info("Kinetic Planner client setup");
    }

    @SubscribeEvent
    static void onClientTickPost(ClientTickEvent.Post event) {
        MapOverlayDispatcher.tick();
        NativeLineOverlay.onClientTick();
    }
}
```

> **注意：** `MapOverlayDispatcher` 和 `NativeLineOverlay` 在 Task 8/9 创建，本步骤会编译失败，等 Task 8/9 完成后才能通过 `gradlew build`。本 Task 末尾的构建验证用 `gradlew compileJava`（仅编译 common）。

- [ ] **Step 6: 创建 Mixin 配置文件**

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

- [ ] **Step 7: 创建 MixinPlugin 守卫**

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
                KineticPlannerLogMixin.warn("Xaero GuiMap not found, skipping Xaero mixins");
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

> **注意：** `KineticPlannerLogMixin` 是个占位引用，实际用 `com.jsmua.kineticplanner.KineticPlannerMod.LOGGER`。把 Step 7 代码里的 `KineticPlannerLogMixin.warn(...)` 改为：
> ```java
> com.jsmua.kineticplanner.KineticPlannerMod.LOGGER.warn("Xaero GuiMap not found, skipping Xaero mixins");
> ```

- [ ] **Step 8: 创建语言文件**

创建 `src/main/resources/assets/kineticplanner/lang/en_us.json`：

```json
{
    "itemGroup.kineticplanner": "Kinetic Planner"
}
```

- [ ] **Step 9: 删除旧 examplemod 文件**

删除以下文件/目录：
- `src/main/java/com/example/examplemod/ExampleMod.java`
- `src/main/java/com/example/examplemod/ExampleModClient.java`
- `src/main/java/com/example/examplemod/Config.java`
- `src/main/resources/assets/examplemod/`（整个目录）

- [ ] **Step 10: 构建验证（common only）**

运行：`gradlew compileJava`
预期：BUILD SUCCESSFUL（client sourceSet 因引用未创建的 `MapOverlayDispatcher`/`NativeLineOverlay` 会编译失败，本步骤只验证 common）

- [ ] **Step 11: 提交**

```bash
git add -A
git commit -m "refactor: rename examplemod to kineticplanner with common/client sourceSet split"
```

---

## Task 2: Vec2d（common，纯 JVM 单测）

**Files:**
- Create: `src/main/java/com/jsmua/kineticplanner/projection/Vec2d.java`
- Test: `src/test/java/com/jsmua/kineticplanner/projection/Vec2dTest.java`

**Interfaces:**
- Produces: `Vec2d(double x, double y)` record 含 `x()`/`y()`/`add(Vec2d)`/`subtract(Vec2d)`/`scale(double)`/`distanceTo(Vec2d)`

- [ ] **Step 1: 写 Vec2d 失败测试**

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

- [ ] **Step 2: 运行测试验证失败**

运行：`gradlew test --tests "com.jsmua.kineticplanner.projection.Vec2dTest"`
预期：编译失败（`Vec2d` 类不存在）

- [ ] **Step 3: 实现 Vec2d**

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

- [ ] **Step 4: 运行测试验证通过**

运行：`gradlew test --tests "com.jsmua.kineticplanner.projection.Vec2dTest"`
预期：5 个测试全部 PASS

- [ ] **Step 5: 提交**

```bash
git add -A
git commit -m "feat: add Vec2d record with JUnit 5 tests"
```

---

## Task 3: CameraParams + WorldRect（common，纯 JVM 单测）

**Files:**
- Create: `src/main/java/com/jsmua/kineticplanner/projection/CameraParams.java`
- Create: `src/main/java/com/jsmua/kineticplanner/projection/WorldRect.java`
- Test: `src/test/java/com/jsmua/kineticplanner/projection/CameraParamsTest.java`

**Interfaces:**
- Produces: `CameraParams(double cameraBlockX, double cameraBlockZ, double blocksPerPixel, int screenWidth, int screenHeight)` 含 `screenCenterX()`/`screenCenterY()`
- Produces: `WorldRect(double minX, double minZ, double maxX, double maxZ)` 含 `contains(x,z)`/`containsWithMargin(x,z,margin)`

- [ ] **Step 1: 写 CameraParams 失败测试**

创建 `src/test/java/com/jsmua/kineticplanner/projection/CameraParamsTest.java`：

```java
package com.jsmua.kineticplanner.projection;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CameraParamsTest {
    @Test
    void screenCenterReturnsHalfDimensions() {
        CameraParams cam = new CameraParams(0.0, 0.0, 1.0, 800, 600);
        assertEquals(400, cam.screenCenterX());
        assertEquals(300, cam.screenCenterY());
    }

    @Test
    void oddScreenSizeFloorsCenter() {
        CameraParams cam = new CameraParams(0.0, 0.0, 1.0, 801, 601);
        assertEquals(400, cam.screenCenterX());
        assertEquals(300, cam.screenCenterY());
    }
}

class WorldRectTest {
    @Test
    void containsInsideBoundsReturnsTrue() {
        WorldRect r = new WorldRect(-10.0, -10.0, 10.0, 10.0);
        assertTrue(r.contains(0.0, 0.0));
        assertTrue(r.contains(-10.0, -10.0));
        assertTrue(r.contains(10.0, 10.0));
    }

    @Test
    void containsOutsideBoundsReturnsFalse() {
        WorldRect r = new WorldRect(-10.0, -10.0, 10.0, 10.0);
        assertFalse(r.contains(-10.1, 0.0));
        assertFalse(r.contains(0.0, 10.1));
    }

    @Test
    void containsWithMarginExpandsBounds() {
        WorldRect r = new WorldRect(0.0, 0.0, 0.0, 0.0);
        assertFalse(r.contains(1.0, 0.0));
        assertTrue(r.containsWithMargin(1.0, 0.0, 1.0));
    }
}
```

- [ ] **Step 2: 运行测试验证失败**

运行：`gradlew test --tests "com.jsmua.kineticplanner.projection.CameraParamsTest"`
预期：编译失败

- [ ] **Step 3: 实现 CameraParams**

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

- [ ] **Step 4: 实现 WorldRect**

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

- [ ] **Step 5: 运行测试验证通过**

运行：`gradlew test --tests "com.jsmua.kineticplanner.projection.CameraParamsTest" --tests "com.jsmua.kineticplanner.projection.WorldRectTest"`
预期：5 个测试全部 PASS

- [ ] **Step 6: 提交**

```bash
git add -A
git commit -m "feat: add CameraParams and WorldRect records with tests"
```

---

## Task 4: WorldScreenTransform（common，纯 JVM 单测）

**Files:**
- Create: `src/main/java/com/jsmua/kineticplanner/projection/WorldScreenTransform.java`
- Test: `src/test/java/com/jsmua/kineticplanner/projection/WorldScreenTransformTest.java`

**Interfaces:**
- Consumes: `Vec2d`（Task 2）、`CameraParams`/`WorldRect`（Task 3）
- Produces: `WorldScreenTransform(CameraParams)` 含 `worldToScreen(double, double): Vector2f`、`screenToWorld(double, double): Vec2d`、`screenToWorldDistance(double): double`、`visibleWorldRect(): WorldRect`、`cam(): CameraParams`

- [ ] **Step 1: 写 WorldScreenTransform 失败测试**

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

- [ ] **Step 2: 运行测试验证失败**

运行：`gradlew test --tests "com.jsmua.kineticplanner.projection.WorldScreenTransformTest"`
预期：编译失败（`WorldScreenTransform` 不存在）

- [ ] **Step 3: 实现 WorldScreenTransform**

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

- [ ] **Step 4: 运行测试验证通过**

运行：`gradlew test --tests "com.jsmua.kineticplanner.projection.WorldScreenTransformTest"`
预期：7 个测试全部 PASS

- [ ] **Step 5: 提交**

```bash
git add -A
git commit -m "feat: add WorldScreenTransform with inverse projection tests"
```

---

## Task 5: EdgeGeometry 描述符（common，无逻辑无单测）

**Files:**
- Create: `src/main/java/com/jsmua/kineticplanner/data/EdgeGeometry.java`

**Interfaces:**
- Produces: `EdgeGeometry(Type, Vec3, Vec3, BezierSpec, ArcSpec, ExtensionSpec)` record
- Produces: `EdgeGeometry.Type` 枚举（STRAIGHT/ARC/BEZIER/EXTENSION/SPLINE）
- Produces: `EdgeGeometry.BezierSpec(Vec3, Vec3, Vec3, Vec3, TrackMaterial)` — Create 三次贝塞尔 4 控制点
- Produces: `EdgeGeometry.ArcSpec`/`ExtensionSpec` — Phase 0a 恒 null，数据结构预留

**说明：** 纯 record 数据结构无逻辑，验证靠编译通过。代码结构见规格 4.2 节。

- [ ] **Step 1: 创建 EdgeGeometry record**

创建 `src/main/java/com/jsmua/kineticplanner/data/EdgeGeometry.java`：

```java
package com.jsmua.kineticplanner.data;

import com.simibubi.create.content.trains.track.TrackMaterial;
import net.minecraft.world.phys.Vec3;

public record EdgeGeometry(
    Type type,
    Vec3 p1, Vec3 p2,
    BezierSpec bezier,
    ArcSpec arc,
    ExtensionSpec extension
) {
    public enum Type {
        STRAIGHT, ARC, BEZIER, EXTENSION, SPLINE
    }

    public record BezierSpec(
        Vec3 start, Vec3 control1, Vec3 control2, Vec3 end,
        TrackMaterial material
    ) {}

    public record ArcSpec(
        Vec3 center, double radius, double startRad, double endRad,
        TrackMaterial material
    ) {}

    public record ExtensionSpec(
        String sourceModId,
        String geometryTypeId,
        net.minecraft.nbt.CompoundTag data
    ) {}

    public static EdgeGeometry straight(Vec3 p1, Vec3 p2) {
        return new EdgeGeometry(Type.STRAIGHT, p1, p2, null, null, null);
    }

    public static EdgeGeometry bezier(Vec3 p1, Vec3 p2, BezierSpec bezier) {
        return new EdgeGeometry(Type.BEZIER, p1, p2, bezier, null, null);
    }
}
```

- [ ] **Step 2: 构建验证**

运行：`gradlew compileJava`
预期：BUILD SUCCESSFUL

- [ ] **Step 3: 提交**

```bash
git add -A
git commit -m "feat: add EdgeGeometry descriptor with railx/spline extension slots"
```

---

## Task 6: IRailwayDataAccess 接口 + StubRailwayDataAccess（common，纯 JVM 单测）

**Files:**
- Create: `src/main/java/com/jsmua/kineticplanner/data/IRailwayDataAccess.java`
- Create: `src/main/java/com/jsmua/kineticplanner/data/StubRailwayDataAccess.java`
- Test: `src/test/java/com/jsmua/kineticplanner/data/StubRailwayDataAccessTest.java`

**Interfaces:**
- Produces: `IRailwayDataAccess`（7 方法：`graphsInDimension`/`nodesInDimension`/`edgesFrom`/`edgePoints`/`clientVersion`/`edgeGeometry`/`nodeWorldPos`）
- Produces: `StubRailwayDataAccess` 测试桩（`addGraph()` 供测试构造）

- [ ] **Step 1: 创建 IRailwayDataAccess 接口**

创建 `src/main/java/com/jsmua/kineticplanner/data/IRailwayDataAccess.java`：

```java
package com.jsmua.kineticplanner.data;

import com.simibubi.create.content.trains.graph.EdgePointType;
import com.simibubi.create.content.trains.graph.TrackEdge;
import com.simibubi.create.content.trains.graph.TrackEdgePoint;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.graph.TrackNode;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.stream.Stream;

public interface IRailwayDataAccess {
    Stream<TrackGraph> graphsInDimension(ResourceKey<Level> dim);
    Stream<TrackNode> nodesInDimension(TrackGraph g, ResourceKey<Level> dim);
    Stream<TrackEdge> edgesFrom(TrackNode node);
    <T extends TrackEdgePoint> Stream<T> edgePoints(TrackGraph g, EdgePointType<T> type);
    int clientVersion();
    EdgeGeometry edgeGeometry(TrackEdge edge);
    Vec3 nodeWorldPos(TrackNode node);
}
```

- [ ] **Step 2: 创建 StubRailwayDataAccess**

创建 `src/main/java/com/jsmua/kineticplanner/data/StubRailwayDataAccess.java`：

```java
package com.jsmua.kineticplanner.data;

import com.simibubi.create.content.trains.graph.EdgePointType;
import com.simibubi.create.content.trains.graph.TrackEdge;
import com.simibubi.create.content.trains.graph.TrackEdgePoint;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.graph.TrackNode;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.stream.Stream;

public class StubRailwayDataAccess implements IRailwayDataAccess {
    private final List<TrackGraph> graphs = new ArrayList<>();
    private int version = 0;

    public void addGraph(TrackGraph g) {
        graphs.add(g);
        version++;
    }

    @Override
    public Stream<TrackGraph> graphsInDimension(ResourceKey<Level> dim) {
        return graphs.stream();
    }

    @Override
    public Stream<TrackNode> nodesInDimension(TrackGraph g, ResourceKey<Level> dim) {
        return Stream.empty();
    }

    @Override
    public Stream<TrackEdge> edgesFrom(TrackNode node) {
        return Stream.empty();
    }

    @Override
    public <T extends TrackEdgePoint> Stream<T> edgePoints(TrackGraph g, EdgePointType<T> type) {
        return Stream.empty();
    }

    @Override
    public int clientVersion() {
        return version;
    }

    @Override
    public EdgeGeometry edgeGeometry(TrackEdge edge) {
        Vec3 p1 = Vec3.ZERO;
        Vec3 p2 = new Vec3(1, 0, 0);
        return EdgeGeometry.straight(p1, p2);
    }

    @Override
    public Vec3 nodeWorldPos(TrackNode node) {
        return node.getLocation();
    }
}
```

- [ ] **Step 3: 写 StubRailwayDataAccess 失败测试**

创建 `src/test/java/com/jsmua/kineticplanner/data/StubRailwayDataAccessTest.java`：

```java
package com.jsmua.kineticplanner.data;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StubRailwayDataAccessTest {
    @Test
    void clientVersionIncrementsOnAddGraph() {
        StubRailwayDataAccess stub = new StubRailwayDataAccess();
        assertEquals(0, stub.clientVersion());
        stub.addGraph(null);
        assertEquals(1, stub.clientVersion());
    }

    @Test
    void edgeGeometryReturnsStraightByDefault() {
        StubRailwayDataAccess stub = new StubRailwayDataAccess();
        EdgeGeometry geom = stub.edgeGeometry(null);
        assertNotNull(geom);
        assertEquals(EdgeGeometry.Type.STRAIGHT, geom.type());
    }
}
```

- [ ] **Step 4: 运行测试验证失败**

运行：`gradlew test --tests "com.jsmua.kineticplanner.data.StubRailwayDataAccessTest"`
预期：编译失败

- [ ] **Step 5: 运行测试验证通过**

> 注：StubRailwayDataAccess 在 Step 2 已实现，此步骤直接运行。

运行：`gradlew test --tests "com.jsmua.kineticplanner.data.StubRailwayDataAccessTest"`
预期：2 个测试 PASS（若 `addGraph(null)` 导致 NPE，需在 `StubRailwayDataAccess.addGraph` 内加 null 检查或测试改为传 mock TrackGraph）

- [ ] **Step 6: 提交**

```bash
git add -A
git commit -m "feat: add IRailwayDataAccess interface and StubRailwayDataAccess"
```

---

## Task 7: RailwayDataAccess 生产实现（client sourceSet，Mockito 单测）

**Files:**
- Create: `src/client/java/com/jsmua/kineticplanner/data/RailwayDataAccess.java`
- Test: `src/test/java/com/jsmua/kineticplanner/data/RailwayDataAccessTest.java`

**Interfaces:**
- Consumes: `CreateClient.RAILWAYS`（运行时）
- Produces: `RailwayDataAccess implements IRailwayDataAccess`

**说明：** 依赖 MC 客户端环境，纯 JVM 单测用 Mockito mock `CreateClient.RAILWAYS`。`edgeGeometry` 从 Create `BezierConnection` 读取 `bePositions[0]`/`bePositions[1]` + 计算的 `finish1`/`finish2`，直接映射到 `BezierSpec(start, control1, control2, end)`，**不做二次→三次转换**（Create 是三次贝塞尔）。

- [ ] **Step 1: 实现 RailwayDataAccess**

创建 `src/client/java/com/jsmua/kineticplanner/data/RailwayDataAccess.java`：

```java
package com.jsmua.kineticplanner.data;

import com.simibubi.create.CreateClient;
import com.simibubi.create.content.trains.graph.*;
import com.simibubi.create.content.trains.track.BezierConnection;
import com.simibubi.create.content.trains.track.TrackMaterial;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.stream.Stream;

public class RailwayDataAccess implements IRailwayDataAccess {
    @Override
    public Stream<TrackGraph> graphsInDimension(ResourceKey<Level> dim) {
        return CreateClient.RAILWAYS.trackNetworks.values().stream()
            .filter(g -> true); // 维度过滤在 nodesInDimension 内做
    }

    @Override
    public Stream<TrackNode> nodesInDimension(TrackGraph g, ResourceKey<Level> dim) {
        return g.getNodes().stream()
            .filter(n -> n.getLocation().dimension.equals(dim));
    }

    @Override
    public Stream<TrackEdge> edgesFrom(TrackNode node) {
        // 返回该节点的所有出边（TrackGraph.connectionsByNode）
        return Stream.empty(); // TODO: 从 node 的 graph 取 connectionsByNode.get(node).values().stream()
    }

    @Override
    public <T extends TrackEdgePoint> Stream<T> edgePoints(TrackGraph g, EdgePointType<T> type) {
        return g.getPoints(type).stream();
    }

    @Override
    public int clientVersion() {
        return CreateClient.RAILWAYS.version;
    }

    @Override
    public EdgeGeometry edgeGeometry(TrackEdge edge) {
        BezierConnection turn = edge.getTurn();
        if (turn == null) {
            Vec3 p1 = nodeWorldPos(edge.node1);
            Vec3 p2 = nodeWorldPos(edge.node2);
            return EdgeGeometry.straight(p1, p2);
        }
        // Create 三次贝塞尔：bePositions[0]/[1] 是端点，determineHandles 算 finish1/finish2 是控制点
        Vec3 start = new Vec3(turn.starts.getFirst());
        Vec3 end = new Vec3(turn.starts.getSecond());
        Vec3 control1 = new Vec3(turn.axes.getFirst()).add(start);
        Vec3 control2 = new Vec3(turn.axes.getSecond()).add(end);
        TrackMaterial material = edge.trackMaterial;
        EdgeGeometry.BezierSpec spec = new EdgeGeometry.BezierSpec(start, control1, control2, end, material);
        return EdgeGeometry.bezier(start, end, spec);
    }

    @Override
    public Vec3 nodeWorldPos(TrackNode node) {
        return node.getLocation();
    }
}
```

> **注意：** 上述代码中 `edge.node1`/`edge.node2`/`turn.starts.getFirst()` 等字段访问需要按 Create 6.0.10 实际 API 调整。实现时先 `gradlew compileJava` 看编译错误，对照 Create 源码修正字段名/方法名。`edgesFrom` 的 TODO 需要传 `TrackGraph` 引用——考虑改签名为 `edgesFrom(TrackGraph g, TrackNode node)` 或在 `IRailwayDataAccess` 加 `edgesFromGraph`。

- [ ] **Step 2: 写 Mockito 单测**

创建 `src/test/java/com/jsmua/kineticplanner/data/RailwayDataAccessTest.java`：

```java
package com.jsmua.kineticplanner.data;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.simibubi.create.content.trains.graph.TrackEdge;
import com.simibubi.create.content.trains.graph.TrackNode;

@ExtendWith(MockitoExtension.class)
class RailwayDataAccessTest {
    @Mock TrackEdge mockEdge;
    @Mock TrackNode mockNode;

    @Test
    void edgeGeometryWithNullTurnReturnsStraight() {
        when(mockEdge.getTurn()).thenReturn(null);
        RailwayDataAccess access = new RailwayDataAccess();
        EdgeGeometry geom = access.edgeGeometry(mockEdge);
        assertEquals(EdgeGeometry.Type.STRAIGHT, geom.type());
        assertNull(geom.bezier());
    }
}
```

> **说明：** 完整 Mockito 测试需 mock `CreateClient.RAILWAYS` 静态字段，较复杂。Phase 0a 只覆盖 `edgeGeometry(null turn)` 分支，其余靠 Task 10 运行时验收。

- [ ] **Step 3: 构建验证**

运行：`gradlew compileJava`
预期：BUILD SUCCESSFUL（若 `edge.node1`/`turn.starts.getFirst()` 等 API 不匹配，按编译错误修正）

- [ ] **Step 4: 运行 Mockito 单测**

运行：`gradlew test --tests "com.jsmua.kineticplanner.data.RailwayDataAccessTest"`
预期：1 个测试 PASS

- [ ] **Step 5: 提交**

```bash
git add -A
git commit -m "feat: add RailwayDataAccess production implementation with Mockito test"
```

---

## Task 8: MapOverlayProvider 接口 + Xaero Mixin（client sourceSet，完整 Mixin 代码）

**Files:**
- Create: `src/client/java/com/jsmua/kineticplanner/mapadapter/MapOverlayProvider.java`
- Create: `src/client/java/com/jsmua/kineticplanner/mapadapter/MapOverlayContext.java`
- Create: `src/client/java/com/jsmua/ineticplanner/mapadapter/MapOverlayDispatcher.java`
- Create: `src/client/java/com/jsmua/kineticplanner/mapadapter/XaeroMapOverlayProvider.java`
- Create: `src/client/java/com/jsmua/kineticplanner/mapadapter/JourneyMapOverlayProvider.java`
- Create: `src/client/java/com/jsmua/kineticplanner/mixin/XaeroMapAccessor.java`
- Create: `src/client/java/com/jsmua/kineticplanner/mixin/XaeroMapRenderHook.java`

**Interfaces:**
- Produces: `MapOverlayProvider` 接口（`isMapOpen`/`captureContext`/`modId`）
- Produces: `MapOverlayContext` record（dimension/cameraBlockX/Z/blocksPerPixel/screenWidth/Height/mouseX/Y/partialTicks/dpr）
- Produces: `MapOverlayDispatcher`（`tick()` + `currentContext()` + 按 modId 熔断）
- Produces: `XaeroMapAccessor`（`@Mixin(GuiMap.class)` `@Accessor`，字段名 `kp$cameraX`/`kp$cameraZ`/`kp$scale`/`kp$mapProcessor`）
- Produces: `XaeroMapRenderHook`（`@Inject` 到 `GuiMap.render` 末尾 `@At("RETURN")`）

- [ ] **Step 1: 创建 MapOverlayProvider 接口 + MapOverlayContext record**

创建 `src/client/java/com/jsmua/kineticplanner/mapadapter/MapOverlayProvider.java`：

```java
package com.jsmua.kineticplanner.mapadapter;

import net.minecraft.client.gui.screens.Screen;

import javax.annotation.Nullable;

public interface MapOverlayProvider {
    boolean isMapOpen(Screen screen);
    @Nullable MapOverlayContext captureContext(Screen screen);
    String modId();
}
```

创建 `src/client/java/com/jsmua/kineticplanner/mapadapter/MapOverlayContext.java`：

```java
package com.jsmua.kineticplanner.mapadapter;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public record MapOverlayContext(
    ResourceKey<Level> dimension,
    double cameraBlockX, double cameraBlockZ,
    double blocksPerPixel,
    int screenWidth, int screenHeight,
    int mouseX, int mouseY,
    float partialTicks,
    float dpr
) {}
```

- [ ] **Step 2: 创建 MapOverlayDispatcher**

创建 `src/client/java/com/jsmua/kineticplanner/mapadapter/MapOverlayDispatcher.java`：

```java
package com.jsmua.kineticplanner.mapadapter;

import com.jsmua.kineticplanner.KineticPlannerMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import java.util.*;

public class MapOverlayDispatcher {
    private static final List<MapOverlayProvider> PROVIDERS = new ArrayList<>();
    private static final Set<String> FAILED = new HashSet<>();
    private static MapOverlayContext currentContext;

    static {
        PROVIDERS.add(new XaeroMapOverlayProvider());
        PROVIDERS.add(new JourneyMapOverlayProvider());
    }

    public static void tick() {
        Screen screen = Minecraft.getInstance().screen;
        if (screen == null) {
            currentContext = null;
            return;
        }
        for (MapOverlayProvider p : PROVIDERS) {
            if (FAILED.contains(p.modId())) continue;
            try {
                if (p.isMapOpen(screen)) {
                    currentContext = p.captureContext(screen);
                    return;
                }
            } catch (Throwable t) {
                KineticPlannerMod.LOGGER.error("MapOverlayProvider {} failed, circuit-breaking", p.modId(), t);
                FAILED.add(p.modId());
            }
        }
        currentContext = null;
    }

    public static Optional<MapOverlayContext> currentContext() {
        return Optional.ofNullable(currentContext);
    }
}
```

- [ ] **Step 3: 创建 XaeroMapAccessor Mixin**

创建 `src/client/java/com/jsmua/kineticplanner/mixin/XaeroMapAccessor.java`：

```java
package com.jsmua.kineticplanner.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import xaero.map.gui.GuiMap;
import xaero.map.misc.MapProcessor;

@Mixin(value = GuiMap.class, remap = false)
public interface XaeroMapAccessor {
    @Accessor("cameraX")
    double kp$cameraX();
    @Accessor("cameraZ")
    double kp$cameraZ();
    @Accessor("scale")
    double kp$scale();
    @Accessor("mapProcessor")
    MapProcessor kp$mapProcessor();
}
```

- [ ] **Step 4: 创建 XaeroMapRenderHook Mixin**

创建 `src/client/java/com/jsmua/kineticplanner/mixin/XaeroMapRenderHook.java`：

```java
package com.jsmua.kineticplanner.mixin;

import com.jsmua.kineticplanner.KineticPlannerMod;
import com.jsmua.kineticplanner.instrument.NativeLineOverlay;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xaero.map.gui.GuiMap;

@Mixin(value = GuiMap.class, remap = false)
public class XaeroMapRenderHook {
    @Inject(method = "render", at = @At("RETURN"))
    private void kp$onMapRender(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        try {
            NativeLineOverlay.onMapRender((GuiMap) (Object) this, guiGraphics, mouseX, mouseY, partialTicks);
        } catch (Throwable t) {
            KineticPlannerMod.LOGGER.error("NativeLineOverlay render failed", t);
        }
    }
}
```

- [ ] **Step 5: 创建 XaeroMapOverlayProvider**

创建 `src/client/java/com/jsmua/kineticplanner/mapadapter/XaeroMapOverlayProvider.java`：

```java
package com.jsmua.kineticplanner.mapadapter;

import com.jsmua.kineticplanner.KineticPlannerMod;
import com.jsmua.kineticplanner.mixin.XaeroMapAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import xaero.map.gui.GuiMap;
import xaero.map.gui.ScreenBase;

import javax.annotation.Nullable;

public class XaeroMapOverlayProvider implements MapOverlayProvider {
    @Override
    public boolean isMapOpen(Screen screen) {
        return screen instanceof ScreenBase sb && (sb instanceof GuiMap || sb.getParent() instanceof GuiMap);
    }

    @Override
    @Nullable
    public MapOverlayContext captureContext(Screen screen) {
        try {
            GuiMap map = (GuiMap) screen;
            XaeroMapAccessor acc = (XaeroMapAccessor) map;
            double cameraX = acc.kp$cameraX();
            double cameraZ = acc.kp$cameraZ();
            double mapScale = acc.kp$scale();
            Minecraft mc = Minecraft.getInstance();
            int screenWidth = mc.getWindow().getScreenWidth();
            int guiScaledWidth = mc.getWindow().getGuiScaledWidth();
            float dpr = (float) screenWidth / guiScaledWidth;
            double guiScale = (double) screenWidth / mc.getWindow().getGuiScaledWidth();
            double interfaceScale = (double) mc.getWindow().getWidth() / screenWidth;
            double blocksPerPixel = guiScale * interfaceScale / mapScale;

            ResourceKey<Level> dim = Level.OVERWORLD; // TODO: 从 acc.kp$mapProcessor().getMapWorld().getCurrentDimension() 取
            return new MapOverlayContext(
                dim, cameraX, cameraZ, blocksPerPixel,
                mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight(),
                (int) mc.mouseHandler.xpos(), (int) mc.mouseHandler.ypos(),
                mc.getPartialTick(), dpr
            );
        } catch (Throwable t) {
            KineticPlannerMod.LOGGER.error("XaeroMapOverlayProvider.captureContext failed", t);
            return null;
        }
    }

    @Override
    public String modId() { return "xaeroworldmap"; }
}
```

- [ ] **Step 6: 创建 JourneyMapOverlayProvider 占位**

创建 `src/client/java/com/jsmua/kineticplanner/mapadapter/JourneyMapOverlayProvider.java`：

```java
package com.jsmua.kineticplanner.mapadapter;

import net.minecraft.client.gui.screens.Screen;
import javax.annotation.Nullable;

public class JourneyMapOverlayProvider implements MapOverlayProvider {
    @Override
    public boolean isMapOpen(Screen screen) { return false; }

    @Override
    @Nullable
    public MapOverlayContext captureContext(Screen screen) { return null; }

    @Override
    public String modId() { return "journeymap"; }
}
```

- [ ] **Step 7: 构建验证**

运行：`gradlew build`
预期：BUILD SUCCESSFUL（Mixin 类编译通过，运行时注入靠 Task 10 验收）

- [ ] **Step 8: 提交**

```bash
git add -A
git commit -m "feat: add MapOverlayProvider with Xaero Mixin adapter"
```

---

## Task 9: MC 原生线叠加层 + 可视化锚点（client sourceSet，完整代码）

**Files:**
- Create: `src/client/java/com/jsmua/kineticplanner/instrument/NativeLineOverlay.java`
- Create: `src/client/java/com/jsmua/kineticplanner/instrument/GeometryCache.java`
- Create: `src/client/java/com/jsmua/kineticplanner/instrument/EdgePointColorResolver.java`

**Interfaces:**
- Produces: `NativeLineOverlay`（`onClientTick`/`onMapRender`，MC 原生 `RenderType.lines()` + `GuiGraphics.fill`）
- Produces: `GeometryCache`（`Map<UUID, GraphGeometry>` + 脏检测 key `(clientVersion, dimension)`）
- Produces: `EdgePointColorResolver.resolve(TrackEdgePoint, TrackGraph): int`（委托 Create 颜色，未知 fallback `graph.color`）

**说明：** Phase 0a 渲染核心。MC 原生 `RenderType.lines()` 线宽 1px（GL 钳制），无抗锯齿。节点/边点用 4×4 像素 `GuiGraphics.fill` 方块替代圆点。本 Task 同时实现可视化锚点（中心十字线）用于验证相机参数换算。

- [ ] **Step 1: 创建 EdgePointColorResolver**

创建 `src/client/java/com/jsmua/kineticplanner/instrument/EdgePointColorResolver.java`：

```java
package com.jsmua.kineticplanner.instrument;

import com.simibubi.create.CreateClient;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.signal.SignalBoundary;
import com.simibubi.create.content.trains.signal.SignalEdgeGroup;
import com.simibubi.create.content.trains.graph.TrackEdgePoint;
import com.simibubi.create.content.trains.station.GlobalStation;
import com.simibubi.create.content.trains.observer.TrackObserver;

public class EdgePointColorResolver {
    public static int resolve(TrackEdgePoint point, TrackGraph graph) {
        try {
            if (point instanceof SignalBoundary sb) {
                SignalEdgeGroup group = CreateClient.RAILWAYS.signalEdgeGroups.get(sb.groupId);
                return group != null ? group.color.getRGB() : graph.color.getRGB();
            }
            // GlobalStation: 无 mapColor 关联（已核实），fallback graph.color
            // TrackObserver: 无 graph.color 引用（已核实），fallback graph.color
            return graph.color.getRGB();
        } catch (Throwable t) {
            return graph.color.getRGB();
        }
    }
}
```

- [ ] **Step 2: 创建 GeometryCache**

创建 `src/client/java/com/jsmua/kineticplanner/instrument/GeometryCache.java`：

```java
package com.jsmua.kineticplanner.instrument;

import com.jsmua.kineticplanner.data.EdgeGeometry;
import net.minecraft.world.phys.Vec3;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.*;

public class GeometryCache {
    public record GraphGeometry(List<Vec3> nodes, List<EdgeGeometry> edges) {}

    private final Map<UUID, GraphGeometry> cache = new HashMap<>();
    private int lastVersion = -1;
    private ResourceKey<Level> lastDimension = null;

    public boolean needsRebuild(int version, ResourceKey<Level> dim) {
        return version != lastVersion || !Objects.equals(dim, lastDimension);
    }

    public void update(int version, ResourceKey<Level> dim, Map<UUID, GraphGeometry> newData) {
        cache.clear();
        cache.putAll(newData);
        lastVersion = version;
        lastDimension = dim;
    }

    public Collection<GraphGeometry> geometries() {
        return cache.values();
    }

    public void clear() {
        cache.clear();
        lastVersion = -1;
        lastDimension = null;
    }
}
```

- [ ] **Step 3: 创建 NativeLineOverlay（含可视化锚点）**

创建 `src/client/java/com/jsmua/kineticplanner/instrument/NativeLineOverlay.java`：

```java
package com.jsmua.kineticplanner.instrument;

import com.jsmua.kineticplanner.KineticPlannerMod;
import com.jsmua.kineticplanner.data.EdgeGeometry;
import com.jsmua.kineticplanner.data.IRailwayDataAccess;
import com.jsmua.kineticplanner.data.RailwayDataAccess;
import com.jsmua.kineticplanner.mapadapter.MapOverlayContext;
import com.jsmua.kineticplanner.mapadapter.MapOverlayDispatcher;
import com.jsmua.kineticplanner.projection.CameraParams;
import com.jsmua.kineticplanner.projection.WorldScreenTransform;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.graph.TrackNode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.*;

public class NativeLineOverlay {
    private static final IRailwayDataAccess dataAccess = new RailwayDataAccess();
    private static final GeometryCache geometryCache = new GeometryCache();
    private static MapOverlayContext lastContext;
    private static WorldScreenTransform lastTransform;

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
            Map<UUID, GeometryCache.GraphGeometry> newData = new HashMap<>();
            dataAccess.graphsInDimension(ctx.dimension()).forEach(g -> {
                List<Vec3> nodes = new ArrayList<>();
                dataAccess.nodesInDimension(g, ctx.dimension()).forEach(n -> {
                    nodes.add(dataAccess.nodeWorldPos(n));
                });
                List<EdgeGeometry> edges = new ArrayList<>();
                // TODO: 遍历每节点出边，去重（hashCode 比较）
                newData.put(g.id, new GeometryCache.GraphGeometry(nodes, edges));
            });
            geometryCache.update(version, ctx.dimension(), newData);
        }
    }

    public static void onMapRender(Object guiMap, GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        if (lastContext == null || lastTransform == null) return;

        // 可视化锚点：屏幕中心十字线（验证相机参数换算与注入点）
        drawCrosshair(guiGraphics);

        // 轨道线（1px，MC 原生 RenderType.lines()）
        PoseStack pose = guiGraphics.pose();
        pose.pushPose();
        MultiBufferSource.BufferSource buffer = Minecraft.getInstance().renderBuffers().bufferSource();
        Matrix4f matrix = pose.last().pose();

        for (GeometryCache.GraphGeometry geom : geometryCache.geometries()) {
            // 轨道线
            for (EdgeGeometry edge : geom.edges()) {
                drawEdgeLine(matrix, buffer, edge);
            }
            // 节点圆点（用 4×4 fill 替代）
            for (Vec3 node : geom.nodes()) {
                drawNodeSquare(guiGraphics, node);
            }
        }
        buffer.endBatch();
        pose.popPose();
    }

    private static void drawCrosshair(GuiGraphics guiGraphics) {
        int cx = lastContext.screenWidth() / 2;
        int cy = lastContext.screenHeight() / 2;
        guiGraphics.fill(cx - 10, cy, cx + 10, cy + 1, 0xFFFFFFFF);
        guiGraphics.fill(cx, cy - 10, cx + 1, cy + 10, 0xFFFFFFFF);
    }

    private static void drawEdgeLine(Matrix4f matrix, MultiBufferSource.BufferSource buffer, EdgeGeometry edge) {
        // 直线：两个端点
        var p1 = lastTransform.worldToScreen(edge.p1().x, edge.p1().z);
        var p2 = lastTransform.worldToScreen(edge.p2().x, edge.p2().z);
        var builder = buffer.getBuffer(RenderType.lines());
        builder.vertex(matrix, p1.x, p1.y, 0).color(255, 255, 255, 255).normal(1, 0, 0).endVertex();
        builder.vertex(matrix, p2.x, p2.y, 0).color(255, 255, 255, 255).normal(1, 0, 0).endVertex();
        // BEZIER 类型 Phase 0a 简化为端点直线，0b 上 Blaze3D 矢量
    }

    private static void drawNodeSquare(GuiGraphics guiGraphics, Vec3 nodeWorld) {
        var screen = lastTransform.worldToScreen(nodeWorld.x, nodeWorld.z);
        int x = (int) screen.x - 2;
        int y = (int) screen.y - 2;
        guiGraphics.fill(x, y, x + 4, y + 4, 0xFFFF0000);
    }
}
```

- [ ] **Step 4: 构建验证**

运行：`gradlew build`
预期：BUILD SUCCESSFUL

- [ ] **Step 5: 提交**

```bash
git add -A
git commit -m "feat: add NativeLineOverlay with MC native line rendering and crosshair anchor"
```

---

## Task 10: 集成验收（Phase 0a 退场条件）

**Files:**
- 无新文件，运行时手动验收

**说明：** Phase 0a 退场条件：地图上能看到轨道线条（1px）与节点圆点（4×4 方块），数据层与 Mixin 注入点正确。可视化锚点（中心十字线）确认相机参数换算。

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
预期：游戏启动，Kinetic Planner 日志输出 "loading (common)" + "client setup"

- [ ] **Step 4: 打开 Xaero 全屏地图**

按 `Y` 键（Xaero 默认）打开全屏地图。

**验收点 1 - 中心十字线可见：**
预期：地图中心有白色十字线（10px 长），证明 Mixin 注入成功 + 相机参数换算正确。

**验收点 2 - 轨道线条可见：**
预期：当前维度有铁路的地方，能看到白色 1px 线条叠加在地图上。

**验收点 3 - 节点方块可见：**
预期：轨道节点处有红色 4×4 像素小方块。

**验收点 4 - 平移跟随：**
预期：地图平移时，叠加层跟随移动，无滞后。

**验收点 5 - 缩放跟随：**
预期：地图缩放时，叠加层位置正确（线宽固定 1px，Phase 0a 不随缩放变粗）。

**验收点 6 - 维度切换：**
预期：Xaero 切换维度视图，叠加层自动切换到对应维度数据。

**验收点 7 - Create 叠加层共存：**
预期：Create 自身的 `showTrainMapOverlay` 栅格化纹理与本模组叠加层同时显示，无闪烁/错位。

**验收点 8 - Mixin 失败熔断：**
预期：若 Xaero 版本不兼容导致 Mixin 注入失败，日志输出 `[KP] Xaero GuiMap not found`，游戏不崩溃，其他功能正常。

- [ ] **Step 5: 提交验收记录**

若全部 8 个验收点通过，更新 `docs/STATUS.md` Phase 0a 状态为 ✅，提交：

```bash
git add docs/STATUS.md
git commit -m "docs: Phase 0a acceptance verified"
```

若任一验收点失败，记录失败现象，回到对应 Task 修复后重新验收。

---

## Self-Review

**1. Spec coverage:**
- spec 4.1 模组骨架 -> Task 1 ✓
- spec 4.2 数据访问层（IRailwayDataAccess + EdgeGeometry + Stub）-> Task 5, 6 ✓
- spec 4.3 地图叠加适配层 -> Task 8 ✓
- spec 4.4 投影变换层 -> Task 2, 3, 4 ✓
- spec 4.7 只读叠加绘制器 -> Task 9 ✓
- spec 4.8 配置与开关 -> Phase 0b（Phase 0a 不含配置/命令，靠硬编码）
- EdgeGeometry 预留 ExtensionSpec（railx 兼容）-> Task 5 ✓
- 字体走 MC 管线 -> Task 9（NativeLineOverlay 不碰字体）✓
- 主题委托 Create 颜色 -> Task 9 EdgePointColorResolver ✓
- 可视化锚点（M3 建议）-> Task 9 drawCrosshair + Task 10 验收点 1 ✓
- sourceSet 分离（用户新要求）-> Task 1 build.gradle + 全 Task 路径 ✓
- MC 原生线渲染（Phase 0a 拆分）-> Task 9 RenderType.lines() ✓

**Phase 0a 不覆盖（明确推迟 Phase 0b）：**
- spec 4.5 CADRenderEngine（NanoVG/Blaze3D 矢量封装）-> Phase 0b
- spec 4.6 主题系统 -> Phase 0b
- spec 4.8 配置与命令 -> Phase 0b
- spec 5.1-5.4 全 23 项验收 -> Phase 0b（0a 只验 Task 10 的 8 个锚点）

**2. Placeholder scan:**
- Task 5 无单测（纯 record 数据结构无逻辑，靠编译验证）— 合理
- Task 7 `edgesFrom` 有 TODO 注释（Create API 字段访问需运行时验证）— 已标注，实现时按编译错误修正
- Task 7 Mockito 单测只覆盖 `edgeGeometry(null turn)` 分支 — 已说明原因
- Task 8 `captureContext` 的 dim 字段有 TODO — 已标注
- Task 9 `drawEdgeLine` BEZIER 简化为端点直线 — 已说明 Phase 0b 上矢量

**3. Type consistency:**
- `Vec2d`（Task 2）被 `WorldScreenTransform.screenToWorld`（Task 4）返回 ✓
- `CameraParams`（Task 3）被 `WorldScreenTransform` 构造函数消费 ✓
- `WorldRect`（Task 3）被 `WorldScreenTransform.visibleWorldRect()` 返回 ✓
- `EdgeGeometry`（Task 5）被 `IRailwayDataAccess.edgeGeometry`（Task 6/7）返回 ✓
- `WorldScreenTransform`（Task 4）被 `NativeLineOverlay`（Task 9）消费 ✓
- `MapOverlayContext`（Task 8）被 `NativeLineOverlay.onClientTick`（Task 9）消费 ✓
- `IRailwayDataAccess`（Task 6）被 `NativeLineOverlay`（Task 9）消费 ✓
- `MapOverlayDispatcher`（Task 8）被 `KineticPlannerClient`（Task 1）+ `NativeLineOverlay`（Task 9）消费 ✓
- `NativeLineOverlay`（Task 9）被 `KineticPlannerClient`（Task 1）+ `XaeroMapRenderHook`（Task 8）消费 ✓

**4. sourceSet 一致性：**
- common（`src/main/java`）：KineticPlannerMod, IRailwayDataAccess, StubRailwayDataAccess, EdgeGeometry, Vec2d, CameraParams, WorldRect, WorldScreenTransform, Theme/ThemeSerializer（Phase 0b）, KineticPlannerMixinPlugin
- client（`src/client/java`）：KineticPlannerClient, RailwayDataAccess, MapOverlayProvider/Context/Dispatcher, XaeroMapOverlayProvider, JourneyMapOverlayProvider, NativeLineOverlay, GeometryCache, EdgePointColorResolver, XaeroMapAccessor, XaeroMapRenderHook
- common 不引用任何 `net.minecraft.client.*` / `com.mojang.blaze3d.*` ✓
- KineticPlannerMixinPlugin 在 common 但 `shouldApplyMixin` 只用 `Class.forName` + `KineticPlannerMod.LOGGER`，不引用 client 类 ✓

---

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-07-21-kinetic-planner-phase0a.md`. Two execution options:

**1. Subagent-Driven (recommended)** - I dispatch a fresh subagent per task, review between tasks, fast iteration

**2. Inline Execution** - Execute tasks in this session using executing-plans, batch execution with checkpoints

Which approach?

# Kinetic Planner - LLM 项目规则

> 本文件为 AI 编码助手提供项目上下文。请在开始任何任务前阅读此文件。

## 项目概述

Kinetic Planner 是 Minecraft 1.21.1 NeoForge 模组，在全屏地图（Xaero's World Map）上以 CAD 思路叠加显示机械动力（Create）铁路拓扑。使用 Blaze3D 三角形带做矢量渲染。

## 构建与测试

```bash
# 编译（common + client 两个 sourceSet）
gradlew compileJava compileClientJava

# 运行测试
gradlew test

# 完整构建（编译 + 测试 + jar）
gradlew build

# 启动游戏客户端
gradlew runClient
```

- 请优先使用 `GRADLE_USER_HOME` 作为 Gradle 用户目录
- 若 Shell 是 `cmd.exe`，多命令须用 `&` 或 `&&` 分隔
- Gradle wrapper 版本 9.6.1，Java 21 (Zulu 21.0.11)
- 测试框架：JUnit 5 + Mockito 5

## 包结构

```
net.jsmua.kinetic_planner
├── KineticPlannerMod.java          @Mod 主类（common）
├── KineticPlannerClient.java       @Mod(dist=CLIENT) 客户端入口
├── data/                           数据访问层
│   ├── IRailwayDataAccess.java     只读接口（common）
│   ├── StubRailwayDataAccess.java  测试桩（common）
│   ├── RailwayDataAccess.java      生产实现（client）
│   └── EdgeGeometry.java           边几何描述符（common）
├── projection/                     投影变换层（common，纯数学）
│   ├── Vec2d.java
│   ├── CameraParams.java
│   ├── WorldRect.java
│   └── WorldScreenTransform.java
├── cadengine/                      渲染引擎 + 主题（common + client）
│   ├── Theme.java                  主题数据 record（common）
│   ├── ThemeSerializer.java        Gson JSON 序列化（common）
│   ├── LineGeometry.java           三角形带展开纯数学（common）
│   ├── BezierTessellator.java      贝塞尔采样纯数学（common）
│   ├── CADRenderEngine.java        Blaze3D 渲染封装（client）
│   └── GLStateGuard.java           RenderSystem 状态管理（client）
├── config/                         配置/命令/GUI（client）
│   ├── KPConfig.java               NeoForge ModConfigSpec TOML
│   ├── KPCommands.java             /kp 命令注册
│   └── KPClothConfigScreen.java    Cloth Config GUI
├── mapadapter/                     地图适配层（client）
│   ├── MapOverlayProvider.java     接口
│   ├── MapOverlayContext.java      上下文 record
│   ├── MapOverlayDispatcher.java   分发 + 熔断
│   ├── XaeroMapOverlayProvider.java Xaero 实现
│   └── JourneyMapOverlayProvider.java 占位
├── instrument/                     叠加层编排（client）
│   ├── WorldTreeReadOverlay.java   顶层编排器
│   ├── GeometryCache.java          几何缓存 + 脏检测
│   └── EdgePointColorResolver.java 边点颜色委托
└── mixin/                          Mixin（client）
    ├── TrackGraphAccessor.java     @Accessor connectionsByNode
    ├── XaeroMapAccessor.java       @Accessor GuiMap 字段
    └── XaeroMapRenderHook.java     @Inject GuiMap.render
```

## sourceSet 分离规则

- **main (common)**：`src/main/java/`，不引用 `net.minecraft.client.*` / `com.mojang.blaze3d.*`
- **client**：`src/client/java/`，可引用 main + client 类
- **test**：`src/test/java/`，可访问两个 sourceSet
- client 类用 `@Mod(dist=CLIENT)` + `@EventBusSubscriber(value=Dist.CLIENT)` 双保险

## 关键 API 约定（勿违反）

### MC 1.21.1 VertexConsumer
- `addVertex(x, y, z)` 不是 `vertex()`
- `setColor(r, g, b, a)` 不是 `color()`
- `setNormal(x, y, z)` 不是 `normal()`
- **无 `endVertex()`**（顶点在下一个 addVertex 时自动提交）

### MC 1.21.1 RenderSystem
- `RenderSystem.isEnabledBlend()` **不存在**（不要调用）
- 用 `RenderSystem.enableBlend()` / `disableBlend()` 直接设置
- Shader：`RenderSystem.setShader(GameRenderer::getPositionColorShader)`

### Create 6.0.10
- `TrackGraph.getNodes()` 返回 `Set<TrackNodeLocation>`（不是 TrackNode），用 `locateNode()` 转
- `TrackNode.getLocation()` 返回 TrackNodeLocation，`.getLocation().getLocation()` 得 Vec3
- `TrackGraph.connectionsByNode` 是 package-private，用 Mixin `@Accessor` 访问
- `BezierConnection` 是**三次**贝塞尔：`starts` 为端点，`axes` 为控制点方向向量（需加端点坐标）
- `TrackEdgePoint` 在 `content.trains.signal` 包（不是 `graph`）
- `SignalBoundary.groupId` 不存在，实际是 `groups`（`Couple<UUID>`）
- `TrackEdge.trackMaterial` 是 package-private，用 `getTrackMaterial()`

### NeoForge 1.21.1
- Mixin 配置用 `defaultRequire: 0`（未安装目标 mod 时不崩溃）
- Mixin on Create/Xaero 类用 `remap = false`
- `ModContainer.registerConfigScreen()` API 需运行时验证（TODO）
- Config 用 `ModConfigSpec.Builder` + `push()`/`pop()` 分段

## 测试策略

- **common 类**：JUnit 5 纯 JVM 单测（Vec2d, CameraParams, WorldScreenTransform, LineGeometry, BezierTessellator, ThemeSerializer）
- **client 类**：Mockito mock MC 依赖（但 Create 类 mock 受 JVM instrumentation 限制，部分 `@Disabled`）
- 运行时行为靠手动验收（`gradlew runClient` + 游戏内验证）
- 当前：29 个测试，2 个 `@Disabled`

## Git 工作流

- 分支 `1.21`，直接在此分支开发
- 提交消息格式：`type: description`（feat/fix/refactor/docs）
- 不要提交 `.codebuddy/`、`.superpowers/`（已在 .gitignore）

## 关键文档

| 文档 | 路径 |
|---|---|
| Phase 0 设计规格 | `docs/superpowers/specs/2026-07-20-kinetic-planner-phase0-design.md` |
| Phase 0b 设计规格 | `docs/superpowers/specs/2026-07-22-kinetic-planner-phase0b-design.md` |
| Phase 0a 实现计划 | `docs/superpowers/plans/2026-07-21-kinetic-planner-phase0a.md` |
| Phase 0b 实现计划 | `docs/superpowers/plans/2026-07-22-kinetic-planner-phase0b.md` |
| 路线图与状态 | `STATUS.md` |
| 编码规范速查 | `docs/conventions.md` |
| JavaDoc 查询指南 | `docs/javadoc-guide.md` |

## 当前状态

- Phase 0a + 0b 代码实现完成
- 29 个测试通过（2 个 @Disabled）
- 待运行时验收（TrackGraphAccessor Mixin、CADRenderEngine shader、Config screen 注册 API）

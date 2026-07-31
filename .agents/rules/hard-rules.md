---
description: 硬性 API 与架构规则（勿违反）
alwaysApply: true
enabled: true
updatedAt: 2026-07-31
provider:
---
# 硬性规则清单（勿违反）

> 本文件包含项目不可违反的 API 约定与架构约束。最后更新：2026-07-31。

## 1. sourceSet 分离

| sourceSet | 路径 | 约束 |
|---|---|---|
| **main (common)** | `src/main/java/` | **不引用** `net.minecraft.client.*` / `com.mojang.blaze3d.*` |
| **client** | `src/client/java/` | 可引用 main + client 类 |
| **server** | `src/server/java/` | 可引用 main + 服务端 API（P1 填充） |
| **test** | `src/test/java/` | 可访问全部 sourceSet |

- client 类用 `@Mod(dist=CLIENT)` + `@EventBusSubscriber(value=Dist.CLIENT)` 双保险
- jar 包含 main + client + server output

## 2. MC 1.21.1 API

- `addVertex(x, y, z)` **不是** `vertex()`
- `setColor(r, g, b, a)` **不是** `color()`
- `setNormal(x, y, z)` **不是** `normal()`
- **无 `endVertex()`**（顶点在下一个 addVertex 时自动提交）
- `RenderSystem.isEnabledBlend()` **不存在**（不要调用）
- Shader：`RenderSystem.setShader(GameRenderer::getPositionColorShader)`

## 3. Create 6.0.10 API

- `TrackGraph.getNodes()` 返回 `Set<TrackNodeLocation>`（不是 TrackNode），用 `locateNode()` 转
- `TrackNode.getLocation()` 返回 TrackNodeLocation，`.getLocation().getLocation()` 得 Vec3
- `TrackGraph.connectionsByNode` 是 package-private，用 Mixin `@Accessor` 访问
- `BezierConnection` 是**三次**贝塞尔：`starts` 为端点，`axes` 为控制点方向向量（需加端点坐标）
- `TrackEdgePoint` 在 `content.trains.signal` 包（不是 `graph`）
- `SignalBoundary.groupId` 不存在，实际是 `groups`（`Couple<UUID>`）
- `TrackEdge.trackMaterial` 是 package-private，用 `getTrackMaterial()`

## 4. NeoForge 1.21.1

- Mixin 配置用 `defaultRequire: 0`（未安装目标 mod 时不崩溃）
- Mixin on Create/Xaero 类用 `remap = false`
- Config 用 `ModConfigSpec.Builder` + `push()`/`pop()` 分段
- 客户端命令：`RegisterClientCommandsEvent`（`net.neoforged.neoforge.client.event` 包）
- `ClientCommandSourceStack` 继承 `CommandSourceStack`，`sendSuccess(Supplier<Component>, boolean)` 签名不变

## 5. Mixin 约定

- 方法名用 `kp$` 前缀避免与其他模组冲突
- `remap = false` 用于非 Mojang 类（Create/Xaero）
- Mojang 类的 Mixin 不需要 `remap = false`
- 无 MixinPlugin（ModDev Mixin `extensibility` 接口编译冲突，用 `defaultRequire:0` 替代）

## 6. 测试策略

- common 类：JUnit 5 纯 JVM 单测
- client 类：Mockito mock MC 依赖（Create 类 mock 受限，部分 `@Disabled`）
- 运行时行为靠手动验收（`gradlew runClient`）

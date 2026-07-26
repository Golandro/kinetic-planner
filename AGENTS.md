# Kinetic Planner - LLM 核心规则

> 本文件为 AI 编码助手提供**必须遵守的规则**与**最低限度上下文**。
> 详细引用信息（包结构、构建测试、文档索引）已拆分至 `.agents/` 目录。

## 项目概述

Kinetic Planner 是 Minecraft 1.21.1 NeoForge 模组，在全屏地图（Xaero's World Map / JourneyMap）上以 CAD 思路叠加显示机械动力（Create）铁路拓扑。使用 Blaze3D 三角形带做矢量渲染。

- mod_id：`kinetic_planner`
- mod_group_id：`net.jsmua.kinetic_planner`
- 许可证：MIT
- 分支：`1.21`

## 构建命令

```bash
gradlew compileJava compileClientJava compileServerJava   # 编译
gradlew test                                               # 测试（42 @Test，2 @Disabled）
gradlew build                                              # 完整构建
gradlew runClient                                          # 启动游戏
```

- 优先使用 `GRADLE_USER_HOME` 作为 Gradle 用户目录
- Shell 为 `cmd.exe` 时多命令用 `&` 或 `&&` 分隔
- Gradle wrapper 9.6.1，Java 21 (Zulu 21.0.11)

## 规则清单

### sourceSet 分离（勿违反）

| sourceSet | 路径 | 约束 |
|---|---|---|
| **main (common)** | `src/main/java/` | **不引用** `net.minecraft.client.*` / `com.mojang.blaze3d.*` |
| **client** | `src/client/java/` | 可引用 main + client 类 |
| **server** | `src/server/java/` | 可引用 main + 服务端 API（P1 填充） |
| **test** | `src/test/java/` | 可访问全部 sourceSet |

- client 类用 `@Mod(dist=CLIENT)` + `@EventBusSubscriber(value=Dist.CLIENT)` 双保险
- jar 包含 main + client + server output

### MC 1.21.1 API（勿违反）

- `addVertex(x, y, z)` **不是** `vertex()`
- `setColor(r, g, b, a)` **不是** `color()`
- `setNormal(x, y, z)` **不是** `normal()`
- **无 `endVertex()`**（顶点在下一个 addVertex 时自动提交）
- `RenderSystem.isEnabledBlend()` **不存在**（不要调用）
- Shader：`RenderSystem.setShader(GameRenderer::getPositionColorShader)`

### Create 6.0.10 API（勿违反）

- `TrackGraph.getNodes()` 返回 `Set<TrackNodeLocation>`（不是 TrackNode），用 `locateNode()` 转
- `TrackNode.getLocation()` 返回 TrackNodeLocation，`.getLocation().getLocation()` 得 Vec3
- `TrackGraph.connectionsByNode` 是 package-private，用 Mixin `@Accessor` 访问
- `BezierConnection` 是**三次**贝塞尔：`starts` 为端点，`axes` 为控制点方向向量（需加端点坐标）
- `TrackEdgePoint` 在 `content.trains.signal` 包（不是 `graph`）
- `SignalBoundary.groupId` 不存在，实际是 `groups`（`Couple<UUID>`）
- `TrackEdge.trackMaterial` 是 package-private，用 `getTrackMaterial()`

### NeoForge 1.21.1（勿违反）

- Mixin 配置用 `defaultRequire: 0`（未安装目标 mod 时不崩溃）
- Mixin on Create/Xaero 类用 `remap = false`
- Config 用 `ModConfigSpec.Builder` + `push()`/`pop()` 分段
- 客户端命令：`RegisterClientCommandsEvent`（`net.neoforged.neoforge.client.event` 包），`getDispatcher()` 返回 `CommandDispatcher<CommandSourceStack>`
- `ClientCommandSourceStack` 继承 `CommandSourceStack`，`sendSuccess(Supplier<Component>, boolean)` 签名不变

### Mixin 约定

- 方法名用 `kp$` 前缀避免与其他模组冲突
- `remap = false` 用于非 Mojang 类（Create/Xaero）
- Mojang 类的 Mixin 不需要 `remap = false`
- 无 MixinPlugin（ModDev Mixin `extensibility` 接口编译冲突，用 `defaultRequire:0` 替代）

### Git 工作流

- 分支 `1.21`，直接在此分支开发
- 提交消息格式：`type: description`（feat/fix/refactor/docs）
- 不要提交 `.superpowers/` 之类的 skill 中间文件和 `.claude/` 之类的特定 Agent 工具配置目录（已在 .gitignore）
- `.agents/memory` 也定性为“工作区记忆”，属于短期记忆。不应同步，而是只同步完成的状态到相应文档中。
- 对于作为事实的长期记忆，应当沉淀到对应模块的规则细则中。

### 测试策略

- common 类：JUnit 5 纯 JVM 单测
- client 类：Mockito mock MC 依赖（Create 类 mock 受限，部分 `@Disabled`）
- 运行时行为靠手动验收（`gradlew runClient`）

> 详细的 API 模式、代码示例和常见错误修复见 `docs/conventions.md`。

## 详细上下文索引

以下内容已拆分至 `.agents/` 目录，按需查阅：

| 文件 | 内容 |
|---|---|
| `.agents/rules/package-structure.md` | 完整包结构树、代码统计、Mixin 清单 |
| `.agents/rules/build-and-test.md` | 构建环境详情、sourceSet 规则细节、测试策略、测试文件清单 |
| `.agents/rules/documents.md` | 完整文档索引（设计规格、实现计划、开发参考） |

项目路线图与当前状态见 `STATUS.md`。

## 上下文补全规则

当当前上下文不含完成任务所需的规则或信息时，**必须**按以下顺序补全：

1. **查阅 `.agents/` 目录**：包结构、构建测试、文档索引等详细引用信息。
3. **查阅 `STATUS.md`**：当前阶段状态、兼容性矩阵、技术假设核实。
4. **关联品牌工具目录**：若使用特定 AI 编码工具，查阅其品牌目录获取工具特定规则与记忆：
    > 若在此前发生缺失 `AGENT.md` 要求的必需规则，须向用户提出关联`.agents/`目录到品牌工具目录的请求。
    > 须包含：当前情况、不分发品牌工具目录的理由、如何操作（最好的免维护方案是符号链接）
5. **查阅实现计划与设计规格**：`docs/superpowers/plans/` 和 `docs/superpowers/specs/` 下的文档。
6. **搜索代码库**：使用搜索工具验证 API 签名、字段可见性等。如果可能，应该优先使用 LSP。
7. **搜索外部文档**：项目使用的外部依赖，其签名和定义文档需要通过 `Context7` 等工具获取，或者使用网络搜索。 

**不得在未查阅上述资源的情况下猜测 API 签名或项目约定。**
**若进行探索性工作，可以选择性忽略部分资源提供的信息，但后续相关设计的改动工作中必须同时更新您了解了存在的规则中的信息。**

---
description: 构建环境与测试策略规则
alwaysApply: true
enabled: true
updatedAt:
provider:
---
# 构建与测试

> 本文件由 AGENTS.md 拆分而来，提供详细的构建环境与测试策略。最后更新：2026-07-25。

## 构建命令

```bash
# 编译（common + client + server 三个 sourceSet）
gradlew compileJava compileClientJava compileServerJava

# 运行测试（42 个 @Test，2 个 @Disabled）
gradlew test

# 完整构建（编译 + 测试 + jar）
gradlew build

# 启动游戏客户端
gradlew runClient
```

## 构建环境

- Gradle wrapper 9.6.1，Java 21 (Zulu 21.0.11)
- 请优先使用 `GRADLE_USER_HOME` 作为 Gradle 用户目录
- 若 Shell 是 `cmd.exe`，多命令须用 `&` 或 `&&` 分隔
- 测试框架：JUnit 5 + Mockito 5
- 编译编码：UTF-8（`../../build.gradle` 中 `tasks.withType(JavaCompile).configureEach { options.encoding = 'UTF-8' }`）

## sourceSet 分离规则

- **main (common)**：`../../src/main/java`，不引用 `net.minecraft.client.*` / `com.mojang.blaze3d.*`
- **client**：`../../src/client/java`，可引用 main + client 类
- **server**：`../../src/server/java`，可引用 main + 服务端 API（P1 填充）
- **test**：`../../src/test/java`，可访问全部 sourceSet
- client 类用 `@Mod(dist=CLIENT)` + `@EventBusSubscriber(value=Dist.CLIENT)` 双保险
- jar 包含 main + client + server output

## 测试策略

- **common 类**：JUnit 5 纯 JVM 单测（Vec2d, CameraParams, WorldScreenTransform, LineGeometry, BezierTessellator, ThemeSerializer, ThemeManager, ProviderConfig, ProviderConfigRegistry）
- **client 类**：Mockito mock MC 依赖（但 Create 类 mock 受 JVM instrumentation 限制，部分 `@Disabled`）
- 运行时行为靠手动验收（`gradlew runClient` + 游戏内验证）
- 当前：42 个测试，2 个 `@Disabled`（RailwayDataAccessTest 中 TrackEdge/TrackGraph mock）

## 测试文件清单

| 测试类 | @Test 数 | 说明 |
|---|---|---|
| `Vec2dTest` | 5 | 向量数学 |
| `CameraParamsTest` | 5 | 相机参数 |
| `WorldScreenTransformTest` | 7 | 世界-屏幕坐标变换 |
| `LineGeometryTest` | 3 | 三角形带展开 |
| `BezierTessellatorTest` | 3 | 贝塞尔采样 |
| `ThemeSerializerTest` | 3 | 主题 JSON 序列化往返 |
| `ThemeManagerTest` | 5 | 主题扫描/加载纯方法 |
| `StubRailwayDataAccessTest` | 1 | 测试桩 |
| `RailwayDataAccessTest` | 2 (@Disabled) | Create 类 mock 限制 |
| `ProviderConfigTest` | 4 | provider 配置 record |
| `ProviderConfigRegistryTest` | 4 | 注册表 |

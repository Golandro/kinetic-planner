---
description: 注册表框架与 Provider 解耦规则
alwaysApply: false
enabled: true
updatedAt:
provider:
---
# 注册表框架与 Provider 解耦

> 本文件描述 `registry/` 包的通用注册表框架、`ProviderConfigBinding` 策略接口、`MapProviderFactory` 工厂注册表的设计约束与使用规则。最后更新：2026-07-29。

## 1. 框架定位

项目提供一套**通用可冻结注册表**（`KPRegistry<T>`）+ **模组内部标识符**（`KPId`）作为所有"按 ID 注册的内容"的统一基础设施。当前已被以下模块使用：

| 使用方 | 存储类型 | sourceSet | 冻结时机 |
|---|---|---|---|
| `MapProviderRegistry` | `KPRegistry<MapProviderFactory>` | client | `FMLClientSetupEvent` 结束时 |
| `ProviderConfigRegistry` | `KPRegistry<ProviderConfig>` | main | **不冻结**（支持运行时动态注册） |

## 2. KPId — 模组内部标识符

### 2.1 命名空间约定

| namespace | 用途 | 创建方式 |
|---|---|---|
| `"kp"` | KP 内置内容（provider、theme、overlay 等） | `KPId.kp(path)` |
| 第三方 modId | 第三方模组自注册内容 | `KPId.of(modId, path)` |

- **当前所有内置注册恒使用 `KPId.kp()`**；`namespace` 字段为**预留扩展**，暂未被第三方利用
- `KPId` 实现 `Comparable`，排序规则：先按 namespace 字典序，再按 path 字典序
- `namespace` 与 `path` 均不允许为 null 或空字符串（构造函数校验）

### 2.2 使用规则

- **禁止**直接 `new KPId("kp", "xxx")`，必须用 `KPId.kp("xxx")` 工厂方法
- 第三方扩展使用 `KPId.of(自身modId, path)`
- `KPId` 为 record，不可变，可作为 `Map` 键使用

## 3. KPRegistry<T> — 通用可冻结注册表

### 3.1 生命周期

```
mutable 注册阶段 ──► freeze() ──► 只读查询阶段
       │                              ▲
       └─ register() 可调用           └─ register() 抛 IllegalStateException
```

- 注册顺序通过 `LinkedHashMap` 保留；`all()` / `ids()` 返回的集合保持注册顺序
- `all()` / `ids()` / `inNamespace()` 返回**不可修改**视图（`Collections.unmodifiable*`）

### 3.2 方法契约

| 方法 | 行为 | 异常 |
|---|---|---|
| `register(KPId, T)` | 注册条目 | `IllegalStateException`（已冻结或重复 ID）；`NullPointerException`（id/entry 为 null） |
| `get(KPId)` | 返回 `Optional<T>` | - |
| `contains(KPId)` | 是否已注册 | - |
| `all()` | 全部值（不可变，注册顺序） | - |
| `ids()` | 全部 ID（不可变） | - |
| `inNamespace(String)` | 按 namespace 过滤 | - |
| `freeze()` | 冻结注册表 | - |
| `isFrozen()` | 是否已冻结 | - |

### 3.3 使用规则

- **冻结时机**：客户端注册表在 `FMLClientSetupEvent` 末尾冻结；冻结后第三方注册会抛异常
- **不支持清空**：`KPRegistry` 不提供 `clear()`。需要重置时**重建实例**（参考 `ProviderConfigRegistry.clear()`）
- **禁止**在 main sourceSet 的注册表中持有 client 类引用（sourceSet 分离规则）
- **测试**：`KPRegistryTest`（10 个 @Test）覆盖注册、冻结、重复、顺序、不可变性

## 4. ProviderConfigBinding — per-provider 配置策略

### 4.1 接口契约

```java
public interface ProviderConfigBinding {
    String modId();
    ProviderConfig read();
    boolean writeEnabled(boolean enabled);
    boolean writeParam(String param, String value);
    static boolean isStrictBool(String value);  // 共享布尔校验
}
```

- 接口位于 **main**（无 client 依赖），实现位于 **client**（依赖反转）
- `writeParam` 的 `param` 取值：`"lineWidthScale"` / `"alphaScale"` / `"dashed"` / `"priority"`
- `isStrictBool` 是接口静态方法，**不会被实现类继承**，调用时必须用 `ProviderConfigBinding.isStrictBool(...)`

### 4.2 唯一实现：ModConfigSpecConfigBinding

- **禁止**创建 per-provider 的 binding 子类（O1 优化决策：消除 >90% 重复代码）
- 新增 provider 只需在 `KPConfig` static 块中构造一个 `ModConfigSpecConfigBinding` 实例并放入 `BINDINGS` map
- 构造函数接收 7 个参数：modId、displayName、5 个 `ModConfigSpec` value holder

### 4.3 KPConfig 中的使用

- `KPConfig.BINDINGS` 为 `LinkedHashMap<String, ProviderConfigBinding>`，key 为 modId
- `getProviderConfig` / `setProviderEnabled` / `setProviderParam` 通过 `BINDINGS.get(modId)` 查找 binding 委托
- **无 binding 的 provider** 回退到 `ProviderConfigRegistry.getDefault(modId)`

### 4.4 使用规则

- **禁止**在 `KPConfig` 中新增 switch 语句处理 provider（架构验收标准 §5.3）
- **禁止**恢复 `setXaeroParam` / `setJmParam` / `isStrictBool` 私有 helper（已删除）
- `writeParam` 返回 `false` 的场景：参数名非法、布尔值非严格格式、数字解析失败

## 5. MapProviderFactory — 工厂注册表

### 5.1 三层结构

```
MapProviderFactory（接口）          ← 定义 modId/displayName/defaultConfig/isAvailable/create
   ├─ XaeroMapProviderFactory       ← 内置实现
   └─ JourneyMapMapProviderFactory  ← 内置实现
         │
         ▼
MapProviderRegistry（静态注册表）   ← register / available / all / freeze
         │
         ▼
MapOverlayDispatcher（调度器）      ← initProviders() 从 available() 工厂创建实例
```

### 5.2 工厂接口契约

| 方法 | 行为 |
|---|---|
| `modId()` | 返回 provider mod ID（如 `"xaeroworldmap"`） |
| `displayName()` | 返回本地化显示名 `Component` |
| `defaultConfig()` | 返回默认 `ProviderConfig`（注册到 `ProviderConfigRegistry`） |
| `isAvailable()` | 目标 mod 是否已加载（**必须缓存**，避免重复调用 `ModList.get().isLoaded()`） |
| `create()` | 创建 provider 实例；不可用时返回 `null` |

### 5.3 MapProviderRegistry 使用规则

- **注册窗口**：`FMLClientSetupEvent` 期间，`freeze()` 调用前
- **冻结**：`KineticPlannerClient.onClientSetup` 中注册完内置工厂后立即 `freeze()`
- 第三方模组注册自己的工厂：
  ```java
  @SubscribeEvent
  static void onClientSetup(FMLClientSetupEvent e) {
      MapProviderRegistry.register(new MyMapProviderFactory());
  }
  ```
  （须在 KP freeze 之前执行；事件优先级需注意）

### 5.4 MapOverlayDispatcher 使用规则

- `initProviders()` 为**懒初始化**：首次调用时从 `MapProviderRegistry.available()` 创建 provider 实例
- `registeredProviders()` 返回**已初始化的 provider 实例**（仅含已安装 mod）；未初始化时返回空列表
- `getRegisteredModIds()` 返回**所有已注册工厂的 modId**（含未安装 mod）——配置 UI/CLI 列表用此方法
- `tick()` 中必须处理 `providers == null || providers.isEmpty()` 的情况（早返回）
- **测试接缝**：`initProviders(List<MapOverlayProvider>)` 为 package-private，用于单测注入 mock provider

### 5.5 新增 Provider 扩展步骤

1. 实现 `MapOverlayProvider` 接口
2. 实现 `MapProviderFactory` 接口（封装创建逻辑）
3. 在 client setup 阶段调用 `MapProviderRegistry.register(myFactory)`
4. 在 `KPConfig` static 块中添加 TOML 字段 + 构造 `ModConfigSpecConfigBinding` 放入 `BINDINGS`
5. 如有 Mixin 需求，在 `kinetic_planner.mixins.json` 的 `client` 数组中添加

## 6. ProviderConfigRegistry — 默认配置注册表

### 6.1 内部实现

- 内部使用 `KPRegistry<ProviderConfig>`（O2 优化决策）
- **不冻结**：支持运行时通过命令动态注册 provider 默认配置
- `clear()` 为 package-private（仅测试用），通过**重建 `KPRegistry` 实例**实现重置

### 6.2 与 KPConfig 的协作

```
启动时：
  ProviderConfigRegistry.register(modId, factory.defaultConfig())  ← 为所有工厂注册默认值

运行时 getProviderConfig(modId)：
  1. KPConfig.BINDINGS.get(modId) → 有 binding → binding.read()（TOML 值）
  2. 无 binding → ProviderConfigRegistry.getDefault(modId)（默认值）
```

- TOML 值 > Registry 默认值
- 未安装 mod 仍可配置（`getRegisteredModIds()` 含未安装 mod）

## 7. 测试规则

### 7.1 测试覆盖

| 测试类 | @Test 数 | 覆盖范围 |
|---|---|---|
| `KPIdTest` | 6 | 创建、比较、equals/hashCode、toString |
| `KPRegistryTest` | 10 | 注册、查询、冻结、重复、顺序、不可变性、namespace 过滤 |
| `ProviderConfigBindingTest` | 3 | isStrictBool、匿名实现、读写委托 |
| `ProviderConfigRegistryTest` | 4 | register/getDefault/getRegisteredModIds/clear |

### 7.2 测试约束

- registry 框架测试为**纯 JVM 单测**（main sourceSet，无 MC 依赖）
- `MapOverlayDispatcher` 的 tick/排序/熔断测试使用 `initProviders(List)` 接缝注入 mock provider
- `ProviderConfigRegistry.clear()` 在每个测试前调用（`@BeforeEach`）避免跨测试污染

## 8. 已知限制与未来扩展

| 项 | 当前状态 | 未来方向 |
|---|---|---|
| `KPId.namespace` | 恒为 `"kp"`，未被第三方利用 | 第三方 mod 自注册时使用自身 modId |
| `writeParam` 字符串参数名 | Primitive Obsession（受限于 `IKPConfig` 签名） | 引入 `enum ProviderParam` |
| `KPConfig` 新增 provider 仍需添加 TOML 字段 | W4 推迟 | 未来 `defineEntries()` 自包含方案 |
| `MapProviderRegistry` 纯静态 | W5 已通过 `initProviders(List)` seam 缓解 | 若需 DI 可引入实例化注册表 |
| `ProviderConfigRegistry` 不冻结 | 支持运行时动态注册 | 与 `MapProviderRegistry` 冻结策略不同，保持现状 |

## 9. 违规检查清单

新增代码须满足以下架构验收标准（来自审计 §5.3）：

- [ ] `KPConfig` 中 `getProviderConfig` / `setProviderEnabled` / `setProviderParam` 不含 switch 语句
- [ ] `KPConfig` 中无私有 `setXaeroParam` / `setJmParam` / `isStrictBool` 方法
- [ ] `MapOverlayDispatcher` 中无 `static {}` 块直接 new provider
- [ ] `MapOverlayDispatcher.PROVIDERS` 可变列表已替换为不可变 `providers` 字段
- [ ] `ProviderConfigControl.isKnownModId()` 使用 `getRegisteredModIds()` 而非遍历 provider 实例
- [ ] `KpConfigUIFactory` provider tab 循环使用 `getRegisteredModIds()` 而非 `registeredProviders()`
- [ ] `ModConfigSpecConfigBinding` 为唯一的 binding 实现类（无 per-provider binding 子类）
- [ ] `ProviderConfigRegistry` 内部使用 `KPRegistry<ProviderConfig>`（无裸 `LinkedHashMap`）
- [ ] main sourceSet 的 registry 类不引用 `net.minecraft.client.*` / `com.mojang.blaze3d.*`

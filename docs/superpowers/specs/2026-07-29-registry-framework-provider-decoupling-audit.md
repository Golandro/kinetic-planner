# 注册表框架与 Provider 解耦 — 架构规格总结

> 审计日期：2026-07-29
> 审计模式：Brooks-Lint Architecture Audit
> 审计对象：`docs/superpowers/plans/2026-07-29-registry-framework-provider-decoupling.md`
> Health Score：72/100（优化后预期 82/100）
> **优化状态：已实施** — O1/O2/O3 已全部应用到计划文件

---

## 1. 背景与动机

### 1.1 当前问题

| 问题 | 位置 | 风险 |
|------|------|------|
| 3 个 switch 语句硬编码 provider modId | `KPConfig.getProviderConfig` / `setProviderEnabled` / `setProviderParam` | R3 知识重复 + R4 switch 信号缺失多态 |
| 静态初始化块直接 new provider 实例 | `MapOverlayDispatcher.static {}` | R2 变更传播 + R5 静态耦合 |
| 2 个私有 helper 方法复制参数写入逻辑 | `KPConfig.setXaeroParam` / `setJmParam` | R3 重复代码 >90% |

### 1.2 计划目标

引入通用 `KPRegistry<T>` 可冻结注册表 + `ProviderConfigBinding` 策略接口 + `MapProviderFactory` 工厂接口，消除 switch 语句和静态初始化块。

---

## 2. 架构设计

### 2.1 三层解耦架构

```
┌─────────────────────────────────────────────────────────┐
│                    main (common)                         │
│                                                          │
│  registry/                                               │
│    KPId ──────────── 模组内部标识符 (namespace:path)     │
│    KPRegistry<T> ─── 通用可冻结注册表                    │
│                                                          │
│  data/                                                   │
│    ProviderConfigBinding ── per-provider 配置读写策略    │
│    ProviderConfig ───────── 配置 record（已有）          │
│    ProviderConfigRegistry ─ 默认配置注册表（已有，优化） │
│                                                          │
├─────────────────────────────────────────────────────────┤
│                    client                                │
│                                                          │
│  config/                                                 │
│    KPConfig ──── TOML 定义 + binding 注册（精简后）      │
│    ModConfigSpecConfigBinding ── 通用 binding 实现 ★优化 │
│    ProviderConfigControl ─── 命令层 CRUD                 │
│                                                          │
│  mapadapter/                                             │
│    MapProviderFactory ────── 工厂接口                    │
│    XaeroMapProviderFactory ── Xaero 工厂                 │
│    JourneyMapMapProviderFactory ── JM 工厂               │
│    MapProviderRegistry ───── 客户端工厂注册表            │
│    MapOverlayDispatcher ──── 调度器（精简后）            │
│    MapOverlayProvider ────── provider 接口（已有）       │
│                                                          │
├─────────────────────────────────────────────────────────┤
│                    client_entry                          │
│                                                          │
│  KineticPlannerClient ── 注册工厂 + 初始化 provider      │
└─────────────────────────────────────────────────────────┘
```

### 2.2 依赖方向

- client → main（单向，无循环）
- registry 包无外部依赖（纯 Java stdlib）
- ProviderConfigBinding 在 main，实现在 client（依赖反转）
- MapProviderFactory 在 client（需引用 client 类 MapOverlayProvider）

### 2.3 关键接口契约

#### KPId（main）

```java
public record KPId(String namespace, String path) implements Comparable<KPId>
```

- `kp(path)` → `KPId("kp", path)`，内置注册统一使用
- `of(namespace, path)` → 第三方扩展使用自身 modId 作为 namespace
- 实现 `Comparable`，按 namespace 再按 path 排序

> **审计标注**：当前 `MapProviderRegistry.register()` 恒使用 `KPId.kp()`，namespace 字段暂时未被第三方利用。Javadoc 须明确标注"namespace 预留给第三方扩展"。

#### KPRegistry\<T\>（main）

```java
public final class KPRegistry<T>
```

| 方法 | 行为 |
|------|------|
| `register(KPId, T)` | 注册条目；frozen 后或重复 ID 抛 `IllegalStateException` |
| `get(KPId)` | 返回 `Optional<T>` |
| `contains(KPId)` | 是否已注册 |
| `all()` | 全部值（不可变，保持注册顺序） |
| `ids()` | 全部 ID（不可变） |
| `inNamespace(String)` | 按 namespace 过滤 |
| `freeze()` / `isFrozen()` | 冻结生命周期 |

底层 `LinkedHashMap`，保持注册顺序。

#### ProviderConfigBinding（main 接口）

```java
public interface ProviderConfigBinding {
    String modId();
    ProviderConfig read();
    boolean writeEnabled(boolean enabled);
    boolean writeParam(String param, String value);
    static boolean isStrictBool(String value);  // 共享布尔校验
}
```

> **审计标注**：`writeParam` 使用字符串参数名（Primitive Obsession），受限于 `IKPConfig.setProviderParam` 现有签名，本次保持一致。

#### MapProviderFactory（client 接口）

```java
public interface MapProviderFactory {
    String modId();
    Component displayName();
    ProviderConfig defaultConfig();
    boolean isAvailable();
    @Nullable MapOverlayProvider create();
}
```

---

## 3. 审计发现与优化

### 3.1 🟡 Warning（5 项）

| # | 风险 | 发现 | 优化方案 | 状态 |
|---|------|------|----------|------|
| W1 | R3 重复 | `XaeroProviderConfigBinding` 与 `JourneyMapProviderConfigBinding` 代码重复 >90% | **合并为单一 `ModConfigSpecConfigBinding`**（见 §3.2 O1） | ✅ 已实施 |
| W2 | R4 推测性泛型 | `KPId` 的 namespace 机制未被利用（恒为 "kp"） | Javadoc 标注预留语义 | ✅ 已实施 |
| W3 | R3 注册表碎片化 | `ProviderConfigRegistry` 仍为裸 Map，未迁移到 `KPRegistry` | 内部迁移到 `KPRegistry<ProviderConfig>`（见 §3.2 O2） | ✅ 已实施 |
| W4 | R2 变更传播 | 新增 provider 仍需修改 KPConfig 添加 TOML 字段 | 本次接受；标注未来 `defineEntries()` 自包含方案 | ⏳ 推迟 |
| W5 | R5 静态依赖 | `MapProviderRegistry` 纯静态，无法测试注入 | 增加 `initProviders(List)` package-private 重载作为 seam | ✅ 已实施 |

### 3.2 优化方案

#### O1: 合并 Binding 为单一实现类（高优先级）

**原计划**：Task 4 创建 `XaeroProviderConfigBinding`，Task 5 创建 `JourneyMapProviderConfigBinding`，两个类各 ~70 行，差异仅 modId 和 displayName。

**优化后**：创建单一 `ModConfigSpecConfigBinding`：

```java
public final class ModConfigSpecConfigBinding implements ProviderConfigBinding {
    private final String modId;
    private final String displayName;
    private final ModConfigSpec.BooleanValue enabled;
    private final ModConfigSpec.IntValue priority;
    private final ModConfigSpec.DoubleValue lineWidthScale;
    private final ModConfigSpec.DoubleValue alphaScale;
    private final ModConfigSpec.BooleanValue dashed;

    // 构造函数接收全部 7 个参数
    // read() / writeEnabled() / writeParam() 只有一份实现
}
```

**效果**：消除 ~65 行重复代码。`writeParam()` 的 switch 只存在一处。新增 provider 参数只需改一处。

**对 Task 的影响**：Task 4 + Task 5 合并为一个 Task。

#### O2: ProviderConfigRegistry 迁移到 KPRegistry（中优先级）

```java
public final class ProviderConfigRegistry {
    // non-final: clear() 通过重建实例实现重置
    private static KPRegistry<ProviderConfig> REGISTRY = new KPRegistry<>();

    public static void register(String modId, ProviderConfig config) {
        REGISTRY.register(KPId.kp(modId), config);
    }
    public static ProviderConfig getDefault(String modId) {
        return REGISTRY.get(KPId.kp(modId)).orElse(null);
    }
    // getRegisteredModIds() 委托 REGISTRY.ids().stream().map(KPId::path)
    static void clear() { REGISTRY = new KPRegistry<>(); } // 测试用
}
```

**效果**：统一注册表模式。注意 REGISTRY 不冻结（支持运行时动态注册），`clear()` 通过重建实例实现（KPRegistry 不支持清空已注册条目）。

#### O3: MapOverlayDispatcher 测试 Seam（低优先级）

```java
// package-private，仅供测试
static void initProviders(List<MapOverlayProvider> injected) {
    providers = List.copyOf(injected);
}
```

#### O4: Factory isAvailable() 缓存测试（低优先级）

为 `XaeroMapProviderFactory` 和 `JourneyMapMapProviderFactory` 补充 `isAvailable()` 缓存行为测试。

### 3.3 🟢 Suggestion（2 项）

| # | 风险 | 发现 | 建议 |
|---|------|------|------|
| S1 | R1 认知过载 | Binding 构造函数 5 参数 | 若采用 O1 合并，可用 record 封装 value holders |
| S2 | R6 领域失真 | `writeParam` 字符串参数名 | 未来引入 `enum ProviderParam`（受限于 IKPConfig 现有签名） |

---

## 4. 优化后 Task 结构

| 计划 Task # | 内容 | sourceSet | 变更 |
|------|------|-----------|------|
| Task 1 | KPId + 测试 | main | ★优化：增加 namespace 预留 Javadoc |
| Task 2 | KPRegistry + 测试 | main | 无变更 |
| Task 3 | ProviderConfigBinding 接口 + 测试 | main | 无变更 |
| Task 4 | **ModConfigSpecConfigBinding**（合并原 Task 4+5） | client | ★优化 O1：单一类替代两个类 |
| Task 6 | 重构 KPConfig - 替换 switch 为 binding 注册 | client | ★优化：使用 ModConfigSpecConfigBinding |
| Task 7 | MapProviderFactory 接口 | client | 无变更 |
| Task 8 | XaeroMapProviderFactory + JourneyMapMapProviderFactory | client | 无变更 |
| Task 9 | MapProviderRegistry | client | 无变更 |
| Task 10 | 重构 MapOverlayDispatcher - 替换静态块 | client | ★优化 O3：增加测试 seam |
| Task 11 | 更新 KineticPlannerClient - 注册工厂 | client | 无变更 |
| Task 11.5 | 更新 ProviderConfigControl + KpConfigUIFactory | client | 无变更 |
| Task 12 | 更新 MapOverlayProvider Javadoc | client | 无变更 |
| **Task 12.5** | **ProviderConfigRegistry 迁移到 KPRegistry** | main | ★新增优化 O2 |
| Task 13 | 全量构建验证 | - | 无变更 |
---

## 5. 验收标准

### 5.1 编译级

- `gradlew compileJava compileClientJava compileServerJava` 全部通过
- main sourceSet 不引用 `net.minecraft.client.*` / `com.mojang.blaze3d.*`

### 5.2 单元测试级

- KPIdTest: 6 个 @Test 通过
- KPRegistryTest: 10 个 @Test 通过
- ProviderConfigBindingTest: 3 个 @Test 通过
- 全部既有 @Test 无回归

### 5.3 架构级

- KPConfig 中 `getProviderConfig` / `setProviderEnabled` / `setProviderParam` 不含 switch 语句
- KPConfig 中 `setXaeroParam` / `setJmParam` / `isStrictBool` 私有方法已删除
- MapOverlayDispatcher 中 `static {}` 块已删除
- MapOverlayDispatcher 中 `PROVIDERS` 可变列表已替换为不可变 `providers` 字段
- `ProviderConfigControl.isKnownModId()` 使用 `getRegisteredModIds()` 而非遍历 provider 实例
- `KpConfigUIFactory` provider tab 循环使用 `getRegisteredModIds()` 而非 `registeredProviders()`

### 5.4 优化验收（如采纳）

- `ModConfigSpecConfigBinding` 为唯一的 binding 实现类（无 Xaero/JourneyMap 前缀的 binding 类）
- `ProviderConfigRegistry` 内部使用 `KPRegistry<ProviderConfig>`（无裸 `LinkedHashMap`）

---

## 6. 风险与缓解

| 风险 | 概率 | 影响 | 缓解 |
|------|------|------|------|
| MapProviderRegistry.freeze() 时机不当导致第三方注册失败 | 中 | 第三方 mod 无法注册 provider | freeze 在 FMLClientSetupEvent 最后调用；文档标注注册窗口 |
| initProviders() 未被调用时 registeredProviders() 返回空列表 | 低 | 叠加层不渲染 | tick() 增加 null 检查（计划已包含） |
| TOML 配置文件已有旧 key 但 binding 读取逻辑变化 | 低 | 配置值丢失 | binding 的 read/write 逻辑与原 switch 完全等价，无格式变化 |
| ProviderConfigRegistry 迁移到 KPRegistry 后 clear() 方法可见性变化 | 低 | 测试失败 | 保持 `static void clear()` package-private 不变 |

---

## 7. 失败模式

| 失败模式 | 触发条件 | 表现 | 恢复 |
|----------|----------|------|------|
| Binding 注册空指针 | KPConfig static 块中 PROVIDER_* 字段未初始化时创建 binding | 启动崩溃 | 确保 binding 注册在所有 TOML 字段定义之后 |
| Factory create() 返回 null | 目标 mod 未安装 | provider 列表为空 | initProviders() 跳过 null 返回值 |
| Registry 已冻结时注册 | 第三方 mod 在 setup 之后注册 | IllegalStateException | 文档标注注册窗口 + defaultRequire:0 模式 |

---

## 8. 兼容性矩阵

| 组件 | 变更类型 | 兼容性 |
|------|----------|--------|
| `IKPConfig` 接口 | 无变更 | 完全兼容 |
| `ProviderConfig` record | 无变更 | 完全兼容 |
| TOML 配置文件格式 | 无变更（字段名/段名不变） | 完全兼容 |
| `/kp provider` 命令 | 无变更（ProviderConfigControl 接口不变） | 完全兼容 |
| Mixin | 无变更 | 完全兼容 |
| `MapOverlayDispatcher.registeredProviders()` | 返回值语义微调（仅返回已安装 mod 的 provider） | 调用方需迁移到 `getRegisteredModIds()` |

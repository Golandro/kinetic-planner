# JavaDoc 查询工具使用方法

> 本文件指导如何查询 Minecraft / NeoForge / Create 等 API 的 JavaDoc，以及如何生成项目自身的 JavaDoc。

---

## 1. IDE 内查询（推荐）

### IntelliJ IDEA

IDEA 是本项目的首选 IDE。通过 `build.gradle` 中的 `idea { module { downloadJavadoc = true } }` 配置，IDEA 会自动下载依赖的 JavaDoc jar。

**查询方式：**
1. **悬浮提示**：将光标放在类名/方法名上，等待黄色提示框出现
2. **快速定义**：`Ctrl + Shift + I`（不离开当前文件查看定义）
3. **跳转到定义**：`Ctrl + 点击` 或 `Ctrl + B`（跳转到源码 + JavaDoc）
4. **外部文档**：`Ctrl + Q` 查看文档（若 JavaDoc jar 已下载）
5. **查找用法**：`Alt + F7` 查找所有引用

**若 JavaDoc 未下载：**
- `File > Project Structure > Libraries` 检查是否有 `-javadoc.jar`
- 或执行 `gradlew --refresh-dependencies` 刷新

### VS Code / Cursor

1. 安装 `Extension Pack for Java`（vscjava.vscode-java-pack）
2. 项目打开后自动识别 Gradle 项目
3. 悬浮在类名/方法名上显示 JavaDoc
4. `F12` 跳转到定义

---

## 2. 在线 API 文档

### Minecraft / Mojang

MC 没有官方 JavaDoc 发布。但有社区维护的映射后文档：

| 资源 | URL | 说明 |
|---|---|---|
| Yarn Mapping Javadoc | `https://maven.fabricmc.net/docs/` | Fabric 生态，Yarn 映射名（与 Mojang 名不同） |
| Javadoc.io (Mojang) | `https://javadoc.io/doc/com.mojang/...` | 部分 Mojang 库有 |
| MC Wiki - Modding | `https://minecraft.wiki/w/Mods` | 非 JavaDoc，但有 API 说明 |

**NeoForge 开发中使用 Mojang 官方映射（Mojang Names）**，所以在线文档若用 Yarn 名会不匹配。IDEA 内查询是最可靠的方式。

### NeoForge

| 资源 | URL | 说明 |
|---|---|---|
| NeoForge Javadoc | `https://javadoc.io/doc/net.neoforged/...` | 部分模块有 |
| NeoForge Wiki | `https://docs.neoforged.net/` | 官方文档（非 JavaDoc） |
| NeoForge 源码 | `https://github.com/neoforged/NeoForge` | 可直接浏览源码 + 注释 |

### Create 6.0.10

Create 没有 JavaDoc jar 发布。查询方式：

| 方式 | 说明 |
|---|---|
| IDEA 反编译 | 依赖 jar 已在 classpath，`Ctrl+B` 跳转看到反编译源码 |
| Create 源码 | `https://github.com/Creators-of-Create/Create` (branch: MC1.21/main) |
| Ponder 浏览 | 游戏内 `/ponder create` 查看 Create 物品说明 |

### LWJGL / OpenGL

| 资源 | URL |
|---|---|
| LWJGL Javadoc | `https://javadoc.lwjgl.org/` |
| OpenGL Reference | `https://registry.khronos.org/OpenGL-Refpages/gl4/` |

---

## 3. 生成项目 JavaDoc

### Gradle Javadoc 任务

项目 `build.gradle` 目前未配置自定义 Javadoc 任务。可手动运行：

```bash
# 生成 common sourceSet 的 JavaDoc
gradlew javadoc

# 指定输出目录
gradlew javadoc -Djavadoc.output.dir=build/docs/javadoc
```

### 添加 Javadoc 任务配置（建议）

若需要自定义 Javadoc 生成，可在 `build.gradle` 末尾添加：

```groovy
javadoc {
    options.encoding = 'UTF-8'
    options.charSet = 'UTF-8'
    options.author = true
    options.version = true
    options.use = true
    options.windowTitle = 'Kinetic Planner API'
    options.docTitle = 'Kinetic Planner API Documentation'
    
    // 包含 client sourceSet
    source sourceSets.client.allJava
    
    // 排除测试代码
    exclude '**/test/**'
    
    // 忽略某些警告
    options.addStringOption('Xdoclint:none', '-quiet')
    
    // 链接到外部 JavaDoc
    options.links 'https://docs.oracle.com/en/java/javase/21/docs/api/'
}

// 确保编译先生成
javadoc.dependsOn compileJava, compileClientJava
```

### 查看 HTML 输出

运行 `gradlew javadoc` 后，打开 `build/docs/javadoc/index.html`。

---

## 4. 命令行查询工具

### 4.1 javap（JDK 自带）

查看类的方法签名（不含 JavaDoc，但含参数类型）：

```bash
# 查看某个类的公共方法
javap -public -classpath "path/to/jar.jar" com.simibubi.create.content.trains.graph.TrackGraph

# 查看所有成员（含 private）
javap -private -classpath "path/to/jar.jar" com.simibubi.create.content.trains.graph.TrackGraph

# 查看反编译的字节码
javap -c -classpath "path/to/jar.jar" com.simibubi.create.content.trains.graph.TrackGraph
```

### 4.2 在项目中查找 API

使用工具搜索依赖 jar 中的类：

```bash
# 查找 Create jar 的位置（Gradle 缓存）
# Windows 路径：~\.gradle\caches\modules-2\files-2.1\com.simibubi.create\

# 使用 jar 命令列出 jar 中的类
jar tf create-1.21.1-6.0.10-280.jar | findstr "TrackGraph"
```

---

## 5. 实用查询技巧

### 5.1 查找 Create 类的包路径

Create 6.0.10 的包结构：
```
com.simibubi.create
├── content.trains.graph/      TrackGraph, TrackNode, TrackEdge, EdgePointType
├── content.trains.signal/     TrackEdgePoint, SignalBoundary, SignalEdgeGroup
├── content.trains.station/    GlobalStation
├── content.trains.observer/   TrackObserver
├── content.trains.track/      BezierConnection, TrackMaterial
├── content.trains/            CreateClient (RAILWAYS 字段)
└── ...
```

**注意：** `TrackEdgePoint` 在 `signal` 包，不在 `graph` 包。这是 0a 实现时发现的偏差。

### 5.2 确认字段可见性

Create 的部分字段是 package-private，在编译时会报错。常见情况：

| 字段 | 可见性 | 替代方法 |
|---|---|---|
| `TrackEdge.trackMaterial` | package-private | `edge.getTrackMaterial()` |
| `TrackGraph.connectionsByNode` | package-private | Mixin `@Accessor` |
| `SignalBoundary.groupId` | 不存在 | `sb.groups`（`Couple<UUID>`） |

### 5.3 确认方法是否存在

在 IDEA 中输入 `.` 后等待自动补全列表。若方法不在列表中，说明不可见或不存在。

对于 MC 内部类（如 `RenderSystem`），若自动补全没有 `isEnabledBlend()`，说明该方法不存在。不要假设旧版本 API 仍然可用。

### 5.4 使用项目记忆文件

本项目的 `../.agents/memory/MEMORY.md` 中有"Create 6.0.10 API 实测修正"和"MC 1.21.1 API"段落，记录了所有已验证的 API 信息。查询 API 前应先查阅此文件。

---

## 6. 项目源码 JavaDoc 规范

项目源码已按以下规范添加 JavaDoc：

### 类级 JavaDoc
```java
/**
 * 类的职责描述。
 *
 * <p>补充说明：架构上下文、消费者列表、设计决策。
 *
 * <h2>标题</h2>
 * <ul>
 *   <li>要点 1</li>
 *   <li>要点 2</li>
 * </ul>
 */
```

### 方法级 JavaDoc
```java
/**
 * 方法的功能描述。
 *
 * @param param1 参数说明
 * @return 返回值说明
 */
```

### 行内注释
- 关键逻辑用 `//` 行内注释解释"为什么"（不是"做什么"）
- API 适配用 `// MC 1.21.1: ...` 或 `// Create 6.0.10: ...` 标注

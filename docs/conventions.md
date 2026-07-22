# Kinetic Planner 编码规范速查

> 本文件汇总项目开发中遇到的 API 陷阱、编码模式和约定。供 LLM 和人类开发者快速查阅。

---

## 1. MC 1.21.1 API 模式

### 1.1 VertexConsumer / BufferBuilder

MC 1.21.1 对 VertexConsumer 接口做了重大改名：

| 旧名 (1.20-) | 新名 (1.21.1) | 说明 |
|---|---|---|
| `vertex(matrix, x, y, z)` | `addVertex(matrix, x, y, z)` | 返回 VertexConsumer |
| `color(r, g, b, a)` | `setColor(r, g, b, a)` | 返回 VertexConsumer |
| `normal(matrix, x, y, z)` | `setNormal(matrix, x, y, z)` | 返回 VertexConsumer |
| `endVertex()` | *(已移除)* | 顶点在下一个 addVertex 时自动提交 |

**正确用法：**
```java
BufferBuilder builder = tesselator.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
builder.addVertex(x1, y1, 0f).setColor(r, g, b, a);
builder.addVertex(x2, y2, 0f).setColor(r, g, b, a);
builder.addVertex(x3, y3, 0f).setColor(r, g, b, a);
BufferUploader.drawWithShader(builder.buildOrThrow());
```

**错误用法（编译失败）：**
```java
builder.vertex(matrix, x, y, z).color(r, g, b, a).normal(0, 1, 0).endVertex(); // ❌
```

### 1.2 RenderSystem

| 操作 | 方法 | 注意 |
|---|---|---|
| 开启混合 | `RenderSystem.enableBlend()` | |
| 默认混合函数 | `RenderSystem.defaultBlendFunc()` | |
| 关闭深度测试 | `RenderSystem.disableDepthTest()` | 叠加层通常关闭 |
| 开启深度测试 | `RenderSystem.enableDepthTest()` | 恢复时调用 |
| 设置 Shader | `RenderSystem.setShader(GameRenderer::getPositionColorShader)` | |
| 获取当前 Shader | `RenderSystem.getShader()` | 返回 `ShaderInstance` |

**不存在的 API：**
- `RenderSystem.isEnabledBlend()` -- 不存在，不要调用
- `RenderSystem.isDepthTestEnabled()` -- 不存在

### 1.3 Tesselator / BufferUploader

```java
Tesselator tesselator = Tesselator.getInstance();
BufferBuilder builder = tesselator.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
// ... addVertex ...
BufferUploader.drawWithShader(builder.buildOrThrow());
```

- `builder.buildOrThrow()` 返回 `BufferBuilder.RenderedBuffer`
- `builder.build()` 返回 `Optional`（可能为空），不推荐用
- `Tesselator.getInstance()` 是单例，不要在 begin 后再调

### 1.4 GuiGraphics

```java
// 填充矩形（屏幕坐标）
guiGraphics.fill(x1, y1, x2, y2, colorARGB);

// 绘制文字
guiGraphics.drawString(font, "text", x, y, colorARGB);

// PoseStack
PoseStack pose = guiGraphics.pose();
pose.pushPose();
// ... 修改 matrix ...
pose.popPose();
```

- 颜色格式：ARGB int（`0xAARRGGBB`）
- 屏幕坐标：左上角为原点，Y 向下

### 1.5 ModConfigSpec (NeoForge)

```java
ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
builder.push("sectionName");
ModConfigSpec.BooleanValue flag = builder.define("flagName", true);
ModConfigSpec.DoubleValue range = builder.defineInRange("rangeName", 2.0, 0.1, 20.0);
ModConfigSpec.ConfigValue<String> str = builder.define("strName", "default");
builder.pop();
ModConfigSpec SPEC = builder.build();
```

注册：
```java
container.registerConfig(ModConfig.Type.CLIENT, KPConfig.SPEC);
```

---

## 2. Create 6.0.10 API 模式

### 2.1 TrackGraph 数据访问

```java
// 获取所有图
CreateClient.RAILWAYS.trackNetworks.values()  // Collection<TrackGraph>

// 获取图版本号（脏检测）
CreateClient.RAILWAYS.version  // int

// 获取节点（返回 TrackNodeLocation，不是 TrackNode）
graph.getNodes()  // Set<TrackNodeLocation>
graph.locateNode(loc)  // TrackNode

// 获取边（package-private，需 Mixin accessor）
((TrackGraphAccessor) graph).kp$getConnectionsByNode()
// 返回 Map<TrackNode, Map<TrackNode, TrackEdge>>

// 获取边点
graph.getPoints(type)  // Collection<T extends TrackEdgePoint>

// 图颜色
graph.color  // net.createmod.catnip.theme.Color
graph.color.getRGB()  // int
```

### 2.2 TrackNode 位置解压

```java
// TrackNodeLocation 是 2x 压缩整数坐标（extends Vec3i）
// getLocation() 返回 TrackNodeLocation
// TrackNodeLocation.getLocation() 返回 Vec3（解压后的世界坐标）
Vec3 worldPos = node.getLocation().getLocation();
```

### 2.3 BezierConnection（三次贝塞尔）

```java
// starts: Couple<Vec3> -- 曲线两端点
Vec3 start = turn.starts.getFirst();
Vec3 end = turn.starts.getSecond();

// axes: Couple<Vec3> -- 控制点方向向量（需加端点坐标得控制点位置）
Vec3 control1 = turn.axes.getFirst().add(start);
Vec3 control2 = turn.axes.getSecond().add(end);
```

### 2.4 TrackEdge

```java
edge.getTurn()  // BezierConnection（null = 直线）
edge.getTrackMaterial()  // TrackMaterial（不是 edge.trackMaterial，后者 package-private）
edge.node1  // TrackNode（public 字段）
edge.node2  // TrackNode（public 字段）
```

### 2.5 EdgePointType / TrackEdgePoint

```java
// 遍历所有注册的边点类型
for (EdgePointType<?> type : EdgePointType.TYPES.values()) { ... }

// TrackEdgePoint 在 content.trains.signal 包（不是 graph）
import com.simibubi.create.content.trains.signal.TrackEdgePoint;

// SignalBoundary
sb.groups  // Couple<UUID>（不是 groupId）
CreateClient.RAILWAYS.signalEdgeGroups.get(sb.groups.getFirst())  // SignalEdgeGroup
group.color.get().getRGB()  // int（EdgeGroupColor 枚举 -> Color -> RGB）

// GlobalStation: 无 mapColor 关联，fallback graph.color
// TrackObserver: 无 graph.color 引用，fallback graph.color
```

### 2.6 信号边点颜色解析

```java
if (point instanceof SignalBoundary sb) {
    SignalEdgeGroup group = CreateClient.RAILWAYS.signalEdgeGroups.get(sb.groups.getFirst());
    return group != null ? group.color.get().getRGB() : graph.color.getRGB();
}
return graph.color.getRGB();  // fallback
```

---

## 3. Mixin 约定

### 3.1 Mixin 配置

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

- `defaultRequire: 0`：目标类不存在时静默跳过（不崩溃）
- 无 MixinPlugin（ModDev Mixin `extensibility` 接口编译冲突，暂时移除）

### 3.2 @Accessor 模式

```java
@Mixin(value = TrackGraph.class, remap = false)  // remap=false for Create classes
public interface TrackGraphAccessor {
    @Accessor("connectionsByNode")
    Map<TrackNode, Map<TrackNode, TrackEdge>> kp$getConnectionsByNode();
}
```

- 方法名用 `kp$` 前缀避免与其他模组 Mixin 冲突
- `remap = false` 用于非 Mojang 类（Create/Xaero）
- Mojang 类的 Mixin 不需要 `remap = false`

### 3.3 @Inject 模式

```java
@Mixin(value = GuiMap.class, remap = false)
public class XaeroMapRenderHook {
    @Inject(method = "render", at = @At("RETURN"))
    private void kp$onMapRender(GuiGraphics g, int mx, int my, float pt, CallbackInfo ci) {
        try {
            WorldTreeReadOverlay.onMapRender((GuiMap)(Object)this, g, mx, my, pt);
        } catch (Throwable t) {
            LOGGER.error("render failed", t);
        }
    }
}
```

- 用 try-catch 包裹防止异常影响目标类
- `(TargetClass)(Object)this` 是 Mixin 中获取目标实例的标准写法

---

## 4. 渲染模式

### 4.1 CADRenderEngine 三角形带粗线

```java
// 粗线 = 两个三角形（4 顶点展开）
float[] quad = LineGeometry.expandLineToTriangleStrip(x1, y1, x2, y2, widthPx);
// 返回 [v1x,v1y, v2x,v2y, v3x,v3y, v4x,v4y]
// 三角形：v1-v2-v3, v2-v4-v3
```

### 4.2 世界坐标 -> 屏幕坐标

```java
// WorldScreenTransform
float screenX = (worldX - camX) / blocksPerPixel + screenCenterX;
float screenY = (worldZ - camZ) / blocksPerPixel + screenCenterY;
```

- 世界 X -> 屏幕 X
- 世界 Z -> 屏幕 Y（俯视投影，Y 仅用于图层排序）

### 4.3 线宽模式

```java
// 固定屏幕像素线宽
float widthPx = theme.global().fixedScreenLineWidthPx();

// 世界单位线宽（随缩放变化）
float widthPx = theme.track().width() / (float) blocksPerPixel;
```

---

## 5. 命名约定

| 类型 | 约定 | 示例 |
|---|---|---|
| 包名 | `net.jsmua.kinetic_planner` | 下划线分隔 |
| Mixin 方法名 | `kp$` 前缀 | `kp$cameraX()` |
| Mixin accessor 字段名 | `kp$` 前缀 | `kp$getConnectionsByNode()` |
| 配置段名 | 小写无空格 | `[overlay]`, `[theme]` |
| 命令根 | `/kp` | `/kp overlay toggle` |
| Record 字段 | 小驼峰 | `graphColor`, `edgePoints` |

---

## 6. 常见错误与修复

| 错误 | 原因 | 修复 |
|---|---|---|
| `vertex()` 编译失败 | MC 1.21.1 改名 | 用 `addVertex()` |
| `endVertex()` 编译失败 | MC 1.21.1 移除 | 删除调用 |
| `isEnabledBlend()` 编译失败 | 方法不存在 | 不查询，直接设置 |
| `trackMaterial` 不可见 | package-private | 用 `getTrackMaterial()` |
| `groupId` 不存在 | 字段名变了 | 用 `groups.getFirst()` |
| `getNodes()` 返回类型不对 | 返回 TrackNodeLocation | 用 `locateNode()` 转 |
| Gson NoClassDefFoundError | 测试类路径缺 Gson | `testImplementation gson` |
| Mockito instrumentation 错误 | Create 类 mock 限制 | `@Disabled` + 运行时验收 |

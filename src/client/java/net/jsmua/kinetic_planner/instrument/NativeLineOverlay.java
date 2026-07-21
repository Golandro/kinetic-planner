package net.jsmua.kinetic_planner.instrument;

import net.jsmua.kinetic_planner.KineticPlannerMod;
import net.jsmua.kinetic_planner.data.EdgeGeometry;
import net.jsmua.kinetic_planner.data.IRailwayDataAccess;
import net.jsmua.kinetic_planner.data.RailwayDataAccess;
import net.jsmua.kinetic_planner.mapadapter.MapOverlayContext;
import net.jsmua.kinetic_planner.mapadapter.MapOverlayDispatcher;
import net.jsmua.kinetic_planner.projection.CameraParams;
import net.jsmua.kinetic_planner.projection.WorldScreenTransform;
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

/**
 * MC 原生线叠加层渲染器（Phase 0a 核心渲染组件）。
 *
 * <p>使用 MC 原生 {@link RenderType#lines()} 绘制 1px 无抗锯齿的轨道线条，
 * 用 {@link GuiGraphics#fill} 绘制 4×4 像素方块替代节点圆点。
 * Phase 0b 将替换为 Blaze3D 矢量封装以支持抗锯齿和可配线宽。
 *
 * <h2>渲染流程</h2>
 * <ol>
 *   <li>{@link #onClientTick} - 每 tick 由 {@link net.jsmua.kinetic_planner.KineticPlannerClient} 调用：
 *     <ul>
 *       <li>从 {@link MapOverlayDispatcher} 获取当前地图上下文</li>
 *       <li>构造 {@link WorldScreenTransform} 投影变换器</li>
 *       <li>检查 {@link GeometryCache} 是否需要重建（version/dimension 变化）</li>
 *       <li>若需重建，从 {@link IRailwayDataAccess} 提取节点和边几何数据</li>
 *     </ul>
 *   </li>
 *   <li>{@link #onMapRender} - 由 {@code XaeroMapRenderHook} Mixin 在 {@code GuiMap.render} 返回后调用：
 *     <ul>
 *       <li>绘制可视化锚点（中心十字线，验证相机参数和注入点）</li>
 *       <li>遍历缓存的几何数据，绘制轨道线（白色 1px）和节点方块（红色 4×4）</li>
 *     </ul>
 *   </li>
 * </ol>
 *
 * <h2>MC 1.21.1 VertexConsumer API 适配</h2>
 * <p>MC 1.21.1 的 {@code VertexConsumer} 接口方法名变更：
 * <ul>
 *   <li>{@code vertex()} -> {@code addVertex()}</li>
 *   <li>{@code color()} -> {@code setColor()}</li>
 *   <li>{@code normal()} -> {@code setNormal()}</li>
 *   <li>{@code endVertex()} 已移除（顶点在下一个 addVertex 调用时自动提交）</li>
 * </ul>
 *
 * <h2>已知限制（Phase 0a）</h2>
 * <ul>
 *   <li>边遍历 TODO 未实现（edgesFrom 返回空），当前不绘制轨道线，仅绘制节点</li>
 *   <li>BEZIER 类型简化为端点直线，0b 上 Blaze3D 矢量曲线</li>
 *   <li>所有线条颜色硬编码为白色 (255,255,255,255)，0b 接入 EdgePointColorResolver</li>
 *   <li>所有节点颜色硬编码为红色 0xFFFF0000</li>
 * </ul>
 */
public class NativeLineOverlay {

    /** 数据访问层实例（生产实现，访问 CreateClient.RAILWAYS）。 */
    private static final IRailwayDataAccess dataAccess = new RailwayDataAccess();

    /** 几何缓存，基于 (version, dimension) 脏检测。 */
    private static final GeometryCache geometryCache = new GeometryCache();

    /** 最近一次 tick 捕获的地图上下文，null 表示地图未打开。 */
    private static MapOverlayContext lastContext;

    /** 最近一次 tick 构造的投影变换器，null 表示地图未打开。 */
    private static WorldScreenTransform lastTransform;

    /**
     * 客户端 tick 回调，更新投影变换器并在需要时重建几何缓存。
     *
     * <p>由 {@link net.jsmua.kinetic_planner.KineticPlannerClient#onClientTickPost} 调用，
     * 在 {@link MapOverlayDispatcher#tick} 之后执行。
     */
    public static void onClientTick() {
        Optional<MapOverlayContext> ctxOpt = MapOverlayDispatcher.currentContext();
        if (ctxOpt.isEmpty()) {
            // 地图未打开，清空缓存释放内存
            geometryCache.clear();
            lastContext = null;
            return;
        }
        MapOverlayContext ctx = ctxOpt.get();
        // 从 MapOverlayContext 构造 CameraParams 和投影变换器
        CameraParams cam = new CameraParams(
            ctx.cameraBlockX(), ctx.cameraBlockZ(), ctx.blocksPerPixel(),
            ctx.screenWidth(), ctx.screenHeight()
        );
        lastTransform = new WorldScreenTransform(cam);
        lastContext = ctx;

        // 脏检测：仅当 version 或 dimension 变化时重建
        int version = dataAccess.clientVersion();
        if (geometryCache.needsRebuild(version, ctx.dimension())) {
            Map<UUID, GeometryCache.GraphGeometry> newData = new HashMap<>();
            dataAccess.graphsInDimension(ctx.dimension()).forEach(g -> {
                // 提取节点世界坐标
                List<Vec3> nodes = new ArrayList<>();
                dataAccess.nodesInDimension(g, ctx.dimension()).forEach(n -> {
                    nodes.add(dataAccess.nodeWorldPos(n));
                });
                // 提取边几何
                List<EdgeGeometry> edges = new ArrayList<>();
                // TODO Phase 0b: 遍历每节点出边，去重（hashCode 比较）
                newData.put(g.id, new GeometryCache.GraphGeometry(nodes, edges));
            });
            geometryCache.update(version, ctx.dimension(), newData);
        }
    }

    /**
     * 地图渲染回调，在 Xaero {@code GuiMap.render} 返回后由 Mixin 调用。
     *
     * <p>绘制顺序：
     * <ol>
     *   <li>中心十字线（可视化锚点）</li>
     *   <li>轨道线条（白色 1px，RenderType.lines()）</li>
     *   <li>节点方块（红色 4×4，GuiGraphics.fill）</li>
     * </ol>
     *
     * @param guiMap      Xaero GuiMap 实例（未直接使用，保留供未来扩展）
     * @param guiGraphics MC 图形上下文
     * @param mouseX      鼠标 X 位置
     * @param mouseY      鼠标 Y 位置
     * @param partialTicks 渲染插值 partial tick
     */
    public static void onMapRender(Object guiMap, GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        if (lastContext == null || lastTransform == null) return;

        // 可视化锚点：屏幕中心十字线（验证相机参数换算与 Mixin 注入点是否正确）
        drawCrosshair(guiGraphics);

        // 轨道线渲染（1px，MC 原生 RenderType.lines()）
        PoseStack pose = guiGraphics.pose();
        pose.pushPose();
        MultiBufferSource.BufferSource buffer = Minecraft.getInstance().renderBuffers().bufferSource();
        Matrix4f matrix = pose.last().pose();

        for (GeometryCache.GraphGeometry geom : geometryCache.geometries()) {
            // 轨道线
            for (EdgeGeometry edge : geom.edges()) {
                drawEdgeLine(matrix, buffer, edge);
            }
            // 节点圆点（用 4×4 fill 方块替代真正的圆点）
            for (Vec3 node : geom.nodes()) {
                drawNodeSquare(guiGraphics, node);
            }
        }
        buffer.endBatch();
        pose.popPose();
    }

    /**
     * 绘制屏幕中心十字线（10px 长，白色），用于验证相机参数换算和 Mixin 注入点。
     *
     * <p>如果十字线出现在地图中心，说明：
     * <ul>
     *   <li>Mixin 注入成功（render 回调被调用）</li>
     *   <li>MapOverlayContext 的 screenWidth/screenHeight 正确</li>
     * </ul>
     */
    private static void drawCrosshair(GuiGraphics guiGraphics) {
        int cx = lastContext.screenWidth() / 2;
        int cy = lastContext.screenHeight() / 2;
        // 水平线：10px 宽，1px 高
        guiGraphics.fill(cx - 10, cy, cx + 10, cy + 1, 0xFFFFFFFF);
        // 垂直线：1px 宽，10px 高
        guiGraphics.fill(cx, cy - 10, cx + 1, cy + 10, 0xFFFFFFFF);
    }

    /**
     * 绘制单条轨道边为 1px 直线。
     *
     * <p>将边的两个端点世界坐标投影到屏幕坐标，通过 {@code RenderType.lines()}
     * 提交两个顶点。BEZIER 类型在 Phase 0a 简化为端点直线。
     *
     * @param matrix 当前 PoseStack 的 4x4 变换矩阵
     * @param buffer 顶点缓冲源
     * @param edge   边几何描述符
     */
    private static void drawEdgeLine(Matrix4f matrix, MultiBufferSource.BufferSource buffer, EdgeGeometry edge) {
        // 将世界坐标 (X, Z) 投影到屏幕坐标（Y 轴对应世界 Z 轴）
        var p1 = lastTransform.worldToScreen(edge.p1().x, edge.p1().z);
        var p2 = lastTransform.worldToScreen(edge.p2().x, edge.p2().z);
        // MC 1.21.1 API: addVertex -> setColor -> setNormal（无 endVertex）
        var builder = buffer.getBuffer(RenderType.lines());
        builder.addVertex(matrix, p1.x, p1.y, 0f).setColor(255, 255, 255, 255).setNormal(1f, 0f, 0f);
        builder.addVertex(matrix, p2.x, p2.y, 0f).setColor(255, 255, 255, 255).setNormal(1f, 0f, 0f);
        // BEZIER 类型 Phase 0a 简化为端点直线，Phase 0b 上 Blaze3D 矢量曲线
    }

    /**
     * 绘制轨道节点为 4×4 像素红色方块。
     *
     * <p>将节点世界坐标投影到屏幕坐标后，以投影点为中心绘制 4×4 方块。
     * Phase 0b 将替换为抗锯齿圆点。
     *
     * @param guiGraphics MC 图形上下文
     * @param nodeWorld   节点世界坐标
     */
    private static void drawNodeSquare(GuiGraphics guiGraphics, Vec3 nodeWorld) {
        var screen = lastTransform.worldToScreen(nodeWorld.x, nodeWorld.z);
        // 以投影点为中心，偏移 -2 像素使方块居中
        int x = (int) screen.x - 2;
        int y = (int) screen.y - 2;
        // 红色 4×4 方块（ARGB: 0xFFFF0000）
        guiGraphics.fill(x, y, x + 4, y + 4, 0xFFFF0000);
    }
}

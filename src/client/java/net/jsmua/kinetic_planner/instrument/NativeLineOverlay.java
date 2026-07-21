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
        builder.addVertex(matrix, p1.x, p1.y, 0f).setColor(255, 255, 255, 255).setNormal(1f, 0f, 0f);
        builder.addVertex(matrix, p2.x, p2.y, 0f).setColor(255, 255, 255, 255).setNormal(1f, 0f, 0f);
        // BEZIER 类型 Phase 0a 简化为端点直线，0b 上 Blaze3D 矢量
    }

    private static void drawNodeSquare(GuiGraphics guiGraphics, Vec3 nodeWorld) {
        var screen = lastTransform.worldToScreen(nodeWorld.x, nodeWorld.z);
        int x = (int) screen.x - 2;
        int y = (int) screen.y - 2;
        guiGraphics.fill(x, y, x + 4, y + 4, 0xFFFF0000);
    }
}

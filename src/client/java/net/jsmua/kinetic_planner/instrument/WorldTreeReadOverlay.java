package net.jsmua.kinetic_planner.instrument;

import net.jsmua.kinetic_planner.KineticPlannerMod;
import net.jsmua.kinetic_planner.cadengine.CADRenderEngine;
import net.jsmua.kinetic_planner.cadengine.Theme;
import net.jsmua.kinetic_planner.data.EdgeGeometry;
import net.jsmua.kinetic_planner.data.IRailwayDataAccess;
import net.jsmua.kinetic_planner.data.RailwayDataAccess;
import net.jsmua.kinetic_planner.mapadapter.MapOverlayContext;
import net.jsmua.kinetic_planner.mapadapter.MapOverlayDispatcher;
import net.jsmua.kinetic_planner.projection.CameraParams;
import net.jsmua.kinetic_planner.projection.WorldScreenTransform;
import com.simibubi.create.content.trains.graph.EdgePointType;
import com.simibubi.create.content.trains.graph.TrackEdge;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.graph.TrackNode;
import com.simibubi.create.content.trains.graph.TrackNodeLocation;
import com.simibubi.create.content.trains.signal.TrackEdgePoint;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/**
 * Phase 0b 顶层编排器（替换 NativeLineOverlay）。
 *
 * <p>整合 {@link IRailwayDataAccess} + {@link WorldScreenTransform} +
 * {@link CADRenderEngine} + {@link Theme}，将 Create 当前维度的 TrackGraph
 * 静态拓扑以矢量方式叠加到地图。
 */
public final class WorldTreeReadOverlay {

    private static final IRailwayDataAccess dataAccess = new RailwayDataAccess();
    private static final GeometryCache geometryCache = new GeometryCache();
    private static final CADRenderEngine engine = new CADRenderEngine();
    private static Theme theme = Theme.defaultValue();
    private static MapOverlayContext lastContext;
    private static WorldScreenTransform lastTransform;

    public static void setTheme(Theme newTheme) {
        theme = newTheme;
    }

    /**
     * 客户端 tick 回调，更新投影变换器并重建几何缓存。
     */
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
            Map<UUID, GeometryCache.GraphGeometry> newData = buildCacheData(ctx.dimension());
            geometryCache.update(version, ctx.dimension(), newData);
        }
    }

    private static Map<UUID, GeometryCache.GraphGeometry> buildCacheData(ResourceKey<Level> dim) {
        Map<UUID, GeometryCache.GraphGeometry> newData = new HashMap<>();
        dataAccess.graphsInDimension(dim).forEach(g -> {
            int graphColor = 0xFFFFFFFF;
            try {
                if (g.color != null) graphColor = g.color.getRGB();
            } catch (Throwable ignored) {}

            // 提取节点
            List<Vec3> nodes = new ArrayList<>();
            Set<TrackNode> nodeSet = new HashSet<>();
            dataAccess.nodesInDimension(g, dim).forEach(n -> {
                nodes.add(dataAccess.nodeWorldPos(n));
                nodeSet.add(n);
            });

            // 提取边（去重）
            List<EdgeGeometry> edges = new ArrayList<>();
            Set<Integer> seenEdges = new HashSet<>();
            for (TrackNode node : nodeSet) {
                dataAccess.edgesFrom(g, node).forEach(edge -> {
                    int hash = edge.hashCode();
                    if (!seenEdges.contains(hash)) {
                        seenEdges.add(hash);
                        try {
                            edges.add(dataAccess.edgeGeometry(edge));
                        } catch (Throwable ignored) {}
                    }
                });
            }

            // 提取边点
            List<GeometryCache.EdgePointData> edgePoints = new ArrayList<>();
            try {
                for (EdgePointType<?> type : EdgePointType.TYPES.values()) {
                    try {
                        dataAccess.edgePoints(g, type).forEach(point -> {
                            try {
                                int color = EdgePointColorResolver.resolve(point, g);
                                Vec3 pos = resolveEdgePointPos(g, point);
                                edgePoints.add(new GeometryCache.EdgePointData(pos, color));
                            } catch (Throwable ignored) {}
                        });
                    } catch (Throwable ignored) {}
                }
            } catch (Throwable ignored) {}

            newData.put(g.id, new GeometryCache.GraphGeometry(g.id, graphColor, nodes, edges, edgePoints));
        });
        return newData;
    }

    /**
     * 尝试定位边点的世界坐标。使用 edgeLocation 的两节点中点作为近似。
     */
    @SuppressWarnings("unchecked")
    private static Vec3 resolveEdgePointPos(TrackGraph g, TrackEdgePoint point) {
        try {
            var edgeLoc = point.edgeLocation;
            if (edgeLoc != null) {
                TrackNodeLocation loc1 = edgeLoc.getFirst();
                TrackNodeLocation loc2 = edgeLoc.getSecond();
                if (loc1 != null && loc2 != null) {
                    Vec3 p1 = loc1.getLocation();
                    Vec3 p2 = loc2.getLocation();
                    return p1.add(p2).scale(0.5);
                }
            }
        } catch (Throwable ignored) {}
        return Vec3.ZERO;
    }

    /**
     * 地图渲染回调，由 XaeroMapRenderHook Mixin 调用。
     */
    public static void onMapRender(Object guiMap, GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        if (lastContext == null || lastTransform == null) return;

        try {
            engine.beginFrame(lastContext.screenWidth(), lastContext.screenHeight(), lastContext.dpr());
            engine.applyWorldTransform(lastTransform);

            for (GeometryCache.GraphGeometry geom : geometryCache.geometries()) {
                int trackColor = applyAlpha(geom.graphColor(), theme.track().alpha());

                // 1. 轨道层
                if (theme.layers().tracks()) {
                    float widthPx = theme.global().constantScreenLineWidth()
                        ? theme.global().fixedScreenLineWidthPx()
                        : theme.track().width() / (float) lastTransform.cam().blocksPerPixel();
                    for (EdgeGeometry edge : geom.edges()) {
                        try {
                            if (edge.type() == EdgeGeometry.Type.BEZIER && edge.bezier() != null) {
                                var b = edge.bezier();
                                engine.drawBezier(
                                    (float) b.start().x, (float) b.start().z,
                                    (float) b.control1().x, (float) b.control1().z,
                                    (float) b.control2().x, (float) b.control2().z,
                                    (float) b.end().x, (float) b.end().z,
                                    widthPx, trackColor, 32);
                            } else {
                                engine.drawLine(
                                    (float) edge.p1().x, (float) edge.p1().z,
                                    (float) edge.p2().x, (float) edge.p2().z,
                                    widthPx, trackColor);
                            }
                        } catch (Throwable ignored) {}
                    }
                }

                // 2. 节点层
                if (theme.layers().nodes()) {
                    int nodeColor = applyAlpha(0xFFFFFFFF, theme.node().alpha());
                    float nodeRadius = theme.node().width() / 2;
                    for (Vec3 node : geom.nodes()) {
                        engine.drawFilledCircle((float) node.x, (float) node.z, nodeRadius, nodeColor);
                    }
                }

                // 3. 边点层
                if (theme.layers().edgePoints()) {
                    float epRadius = theme.edgePoint().width() / 2;
                    for (GeometryCache.EdgePointData ep : geom.edgePoints()) {
                        int epColor = applyAlpha(ep.color(), theme.edgePoint().alpha());
                        engine.drawFilledCircle(
                            (float) ep.worldPos().x, (float) ep.worldPos().z,
                            epRadius, epColor);
                    }
                }
            }

            engine.restoreWorldTransform();
            engine.endFrame();
        } catch (Throwable t) {
            KineticPlannerMod.LOGGER.error("WorldTreeReadOverlay render failed", t);
            try { engine.endFrame(); } catch (Throwable ignored) {}
        }
    }

    private static int applyAlpha(int color, float alpha) {
        int a = (int) (alpha * 255) & 0xFF;
        return (a << 24) | (color & 0x00FFFFFF);
    }
}

package net.jsmua.kinetic_planner.instrument;

import net.jsmua.kinetic_planner.KineticPlannerMod;
import net.jsmua.kinetic_planner.cadengine.CADRenderEngine;
import net.jsmua.kinetic_planner.cadengine.Theme;
import net.jsmua.kinetic_planner.data.EdgeGeometry;
import net.jsmua.kinetic_planner.config.KPConfig;
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

    /** 当前 active provider 的视觉 scale，每 tick 更新。 */
    private static float activeLineWidthScale = 1.0f;
    private static float activeAlphaScale = 1.0f;
    private static boolean activeDashed = false;

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

        // 更新 active provider 视觉 scale
        MapOverlayDispatcher.activeProviderModId().ifPresentOrElse(
            modId -> {
                var pc = KPConfig.getProviderConfig(modId);
                if (pc != null) {
                    activeLineWidthScale = pc.lineWidthScale();
                    activeAlphaScale = pc.alphaScale();
                    activeDashed = pc.dashed();
                }
            },
            () -> {
                activeLineWidthScale = 1.0f;
                activeAlphaScale = 1.0f;
                activeDashed = false;
            }
        );
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
        // 配置开关检查：用户通过 /kp overlay toggle 或配置屏幕关闭叠加层时，直接跳过渲染
        if (!KPConfig.OVERLAY_ENABLED.get()) return;
        if (lastContext == null || lastTransform == null) return;

        try {
            engine.beginFrame(lastContext.screenWidth(), lastContext.screenHeight(), lastContext.dpr());
            engine.applyWorldTransform(lastTransform);

            for (GeometryCache.GraphGeometry geom : geometryCache.geometries()) {

                // 1. 轨道层
                if (theme.layers().tracks()) {
                    float widthPx = (theme.global().constantScreenLineWidth()
                        ? theme.global().fixedScreenLineWidthPx()
                        : theme.track().width() / (float) lastTransform.cam().blocksPerPixel())
                        * activeLineWidthScale;
                    // 仅对原始颜色应用一次主题 alpha 与 provider alphaScale（避免重复叠加）
                    int trackColorScaled = applyAlpha(geom.graphColor(),
                        theme.track().alpha() * activeAlphaScale);
                    for (EdgeGeometry edge : geom.edges()) {
                        try {
                            if (edge.type() == EdgeGeometry.Type.BEZIER && edge.bezier() != null) {
                                var b = edge.bezier();
                                engine.drawBezier(
                                    (float) b.start().x, (float) b.start().z,
                                    (float) b.control1().x, (float) b.control1().z,
                                    (float) b.control2().x, (float) b.control2().z,
                                    (float) b.end().x, (float) b.end().z,
                                    widthPx, trackColorScaled, 32, activeDashed);
                            } else {
                                engine.drawLine(
                                    (float) edge.p1().x, (float) edge.p1().z,
                                    (float) edge.p2().x, (float) edge.p2().z,
                                    widthPx, trackColorScaled, activeDashed);
                            }
                        } catch (Throwable ignored) {}
                    }
                }

                // 2. 节点层
                if (theme.layers().nodes()) {
                    int nodeColor = applyAlpha(0xFFFFFFFF, theme.node().alpha() * activeAlphaScale);
                    float nodeRadius = theme.node().width() / 2;
                    for (Vec3 node : geom.nodes()) {
                        engine.drawFilledCircle((float) node.x, (float) node.z, nodeRadius, nodeColor);
                    }
                }

                // 3. 边点层
                if (theme.layers().edgePoints()) {
                    float epRadius = theme.edgePoint().width() / 2;
                    for (GeometryCache.EdgePointData ep : geom.edgePoints()) {
                        int epColor = applyAlpha(ep.color(), theme.edgePoint().alpha() * activeAlphaScale);
                        engine.drawFilledCircle(
                            (float) ep.worldPos().x, (float) ep.worldPos().z,
                            epRadius, epColor);
                    }
                }
            }

            engine.restoreWorldTransform();
            engine.endFrame();

            // 4. MC 文字层（屏幕坐标，不走 CADRenderEngine）
            renderLabels(guiGraphics);
        } catch (Throwable t) {
            KineticPlannerMod.LOGGER.error("WorldTreeReadOverlay render failed", t);
            try { engine.endFrame(); } catch (Throwable ignored) {}
        }
    }

    // === 统计与诊断方法（供 /kp debug 命令调用）===

    /**
     * 当前缓存的轨道图数量。
     *
     * @return {@link GeometryCache#geometries()} 的大小
     */
    public static int getGraphCount() {
        return geometryCache.geometries().size();
    }

    /**
     * 当前缓存的所有图中的节点总数。
     *
     * @return 所有 {@link GeometryCache.GraphGeometry#nodes()} 大小之和
     */
    public static int getNodeCount() {
        int count = 0;
        for (GeometryCache.GraphGeometry geom : geometryCache.geometries()) {
            count += geom.nodes().size();
        }
        return count;
    }

    /**
     * 当前缓存的所有图中的边总数。
     *
     * @return 所有 {@link GeometryCache.GraphGeometry#edges()} 大小之和
     */
    public static int getEdgeCount() {
        int count = 0;
        for (GeometryCache.GraphGeometry geom : geometryCache.geometries()) {
            count += geom.edges().size();
        }
        return count;
    }

    /**
     * 当前缓存的所有图中的边点总数。
     *
     * @return 所有 {@link GeometryCache.GraphGeometry#edgePoints()} 大小之和
     */
    public static int getEdgePointCount() {
        int count = 0;
        for (GeometryCache.GraphGeometry geom : geometryCache.geometries()) {
            count += geom.edgePoints().size();
        }
        return count;
    }

    /**
     * 将当前维度的 TrackGraph 数据完整转储到日志。
     *
     * <p>由 {@code /kp debug dump} 命令调用。输出每个图的 UUID、颜色、
     * 节点坐标列表、边类型与端点、边点数量。地图未打开时输出提示。
     */
    public static void dumpData() {
        KineticPlannerMod.LOGGER.info("[KP] === TrackGraph Dump ===");
        if (lastContext == null) {
            KineticPlannerMod.LOGGER.info("[KP] No map context (map not open)");
            return;
        }
        KineticPlannerMod.LOGGER.info("[KP] Dimension: {}", lastContext.dimension().location());
        for (GeometryCache.GraphGeometry geom : geometryCache.geometries()) {
            KineticPlannerMod.LOGGER.info("[KP] Graph {} | color={}",
                geom.graphId(), String.format("%08X", geom.graphColor()));
            KineticPlannerMod.LOGGER.info("[KP]   Nodes: {}", geom.nodes().size());
            for (Vec3 node : geom.nodes()) {
                KineticPlannerMod.LOGGER.info("[KP]     ({}, {}, {})",
                    node.x, node.y, node.z);
            }
            KineticPlannerMod.LOGGER.info("[KP]   Edges: {}", geom.edges().size());
            for (EdgeGeometry edge : geom.edges()) {
                KineticPlannerMod.LOGGER.info("[KP]     {} | {} -> {}",
                    edge.type(), edge.p1(), edge.p2());
            }
            KineticPlannerMod.LOGGER.info("[KP]   EdgePoints: {}", geom.edgePoints().size());
        }
        KineticPlannerMod.LOGGER.info("[KP] === End Dump ===");
    }

    /**
     * 各图层对象计数字符串（供 {@code /kp debug layer-count} 命令输出）。
     *
     * @return 格式化字符串，如 {@code "[KP] Layers | Tracks: 12 | Nodes: 15 | EdgePoints: 5"}
     */
    public static String getLayerCounts() {
        int tracks = 0, nodes = 0, edgePoints = 0;
        for (GeometryCache.GraphGeometry geom : geometryCache.geometries()) {
            tracks += geom.edges().size();
            nodes += geom.nodes().size();
            edgePoints += geom.edgePoints().size();
        }
        return String.format("[KP] Layers | Tracks: %d | Nodes: %d | EdgePoints: %d",
            tracks, nodes, edgePoints);
    }

    /**
     * 叠加层锚点诊断字符串（供 {@code /kp debug overlay-anchors} 命令输出）。
     *
     * <p>输出相机坐标、缩放比例、屏幕中心、屏幕尺寸、DPR 和当前维度，
     * 用于诊断叠加层对齐问题。
     *
     * @return 诊断信息字符串，地图未打开时返回提示
     */
    public static String getOverlayAnchors() {
        if (lastContext == null || lastTransform == null) {
            return "[KP] No map context (map not open)";
        }
        return String.format(
            "[KP] Anchors | CamX: %.1f | CamZ: %.1f | BPP: %.4f | CenterX: %d | CenterY: %d | ScreenW: %d | ScreenH: %d | DPR: %.2f | Dim: %s",
            lastTransform.cam().cameraBlockX(),
            lastTransform.cam().cameraBlockZ(),
            lastTransform.cam().blocksPerPixel(),
            lastTransform.cam().screenCenterX(),
            lastTransform.cam().screenCenterY(),
            lastContext.screenWidth(),
            lastContext.screenHeight(),
            lastContext.dpr(),
            lastContext.dimension().location());
    }

    private static int applyAlpha(int color, float alpha) {
        int a = (int) (alpha * 255) & 0xFF;
        return (a << 24) | (color & 0x00FFFFFF);
    }

    /**
     * 渲染标签（MC Font.draw，屏幕坐标）。
     */
    private static void renderLabels(GuiGraphics guiGraphics) {
        if (lastContext == null || lastTransform == null) return;
        // 仅在足够缩放时绘制标签
        if (lastTransform.cam().blocksPerPixel() > 1.0) return;

        var font = Minecraft.getInstance().font;
        for (GeometryCache.GraphGeometry geom : geometryCache.geometries()) {
            for (Vec3 node : geom.nodes()) {
                var screen = lastTransform.worldToScreen(node.x, node.z);
                guiGraphics.drawString(font, "N",
                    (int) screen.x + 4, (int) screen.y - 4, 0xFFFFFFFF);
            }
        }
    }
}

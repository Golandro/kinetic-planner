package net.jsmua.kinetic_planner.cadengine;

/**
 * 主题数据（简化核心子集）。
 *
 * <p>定义矢量渲染的视觉参数（线宽/透明度/图层开关/缩放策略）。
 * 颜色不在主题中定义，由 instrument 层委托 Create（TrackGraph.color 等）。
 *
 * <p>Phase 0b 简化：去掉 LineCap/LineJoin/dashPattern/BezierHandleStyle。
 *
 * @param name       主题名称
 * @param track      轨道线样式
 * @param node       节点样式
 * @param edgePoint  边点样式
 * @param layers     图层可见性
 * @param global     全局样式（缩放/线宽模式）
 */
public record Theme(
    String name,
    GeometryStyle track,
    GeometryStyle node,
    GeometryStyle edgePoint,
    LayerVisibility layers,
    GlobalStyle global
) {
    /**
     * 几何样式（线宽/虚线/透明度）。
     *
     * @param width  线宽（屏幕像素或世界单位，取决于 GlobalStyle.constantScreenLineWidth）
     * @param dashed 是否虚线（Phase 0b 恒 false，预留）
     * @param alpha  透明度 0.0-1.0
     */
    public record GeometryStyle(float width, boolean dashed, float alpha) {}

    /**
     * 图层可见性。
     *
     * @param tracks     轨道线层
     * @param nodes      节点层
     * @param edgePoints 边点层
     */
    public record LayerVisibility(boolean tracks, boolean nodes, boolean edgePoints) {}

    /**
     * 全局样式。
     *
     * @param minZoomBlocksPerPixel    最小缩放（blocksPerPixel），低于此值不渲染
     * @param maxZoomBlocksPerPixel    最大缩放，高于此值不渲染
     * @param constantScreenLineWidth  true=固定屏幕像素线宽，false=世界单位线宽随缩放
     * @param fixedScreenLineWidthPx   固定屏幕像素线宽（constantScreenLineWidth=true 时使用）
     */
    public record GlobalStyle(
        float minZoomBlocksPerPixel,
        float maxZoomBlocksPerPixel,
        boolean constantScreenLineWidth,
        float fixedScreenLineWidthPx
    ) {}

    /**
     * 创建默认主题。
     */
    public static Theme defaultValue() {
        return new Theme(
            "default",
            new GeometryStyle(2.0f, false, 1.0f),
            new GeometryStyle(4.0f, false, 1.0f),
            new GeometryStyle(3.0f, false, 1.0f),
            new LayerVisibility(true, true, true),
            new GlobalStyle(0.05f, 5.0f, true, 2.0f)
        );
    }
}

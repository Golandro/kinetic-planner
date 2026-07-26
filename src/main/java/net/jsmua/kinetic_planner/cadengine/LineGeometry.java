package net.jsmua.kinetic_planner.cadengine;

/**
 * 纯数学工具：将线段展开为三角形带顶点。
 *
 * <p>给定线段 (x1,y1)->(x2,y2) 和宽度 widthPx，计算 4 个顶点坐标
 * 构成三角形带（2 个三角形）。法线方向为线段方向的垂直方向。
 *
 * <p>顶点顺序：v1(x1+n), v2(x1-n), v3(x2+n), v4(x2-n)，
 * 三角形带索引：0-1-2, 1-3-2。
 *
 * <p>此类在 common sourceSet 中，纯 JVM 可测，不依赖任何 GL 类。
 */
public final class LineGeometry {

    private LineGeometry() {}

    /**
     * 将线段展开为三角形带顶点。
     *
     * @param x1       起点 x
     * @param y1       起点 y
     * @param x2       终点 x
     * @param y2       终点 y
     * @param widthPx  线宽（像素）
     * @return 8 个 float：[v1x, v1y, v2x, v2y, v3x, v3y, v4x, v4y]
     */
    public static float[] expandLineToTriangleStrip(
            float x1, float y1, float x2, float y2, float widthPx) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float len = (float) Math.sqrt(dx * dx + dy * dy);

        float nx, ny;
        if (len < 1e-6f) {
            // 零长度线段：退化四边形，法线默认向上
            nx = 0;
            ny = widthPx / 2;
        } else {
            // 法线 = (-dy, dx) / len * (width/2)
            float halfWidth = widthPx / 2;
            nx = -dy / len * halfWidth;
            ny = dx / len * halfWidth;
        }

        return new float[] {
            x1 + nx, y1 + ny,  // v1: 起点 + 法线
            x1 - nx, y1 - ny,  // v2: 起点 - 法线
            x2 + nx, y2 + ny,  // v3: 终点 + 法线
            x2 - nx, y2 - ny   // v4: 终点 - 法线
        };
    }

    /**
     * 将线段按 dash/gap 模式分段，用于虚线渲染。
     *
     * <p>仅生成完整长度的 dash 段；末尾不足一个 dashLen 的部分会被丢弃
     * （保持虚线视觉一致性，避免出现短截线）。
     *
     * @param x1      起点 x
     * @param y1      起点 y
     * @param x2      终点 x
     * @param y2      终点 y
     * @param dashLen 每段实线长度（像素）
     * @param gapLen  每段间隔长度（像素）
     * @return 分段数组，每段 [x1, y1, x2, y2]；无线段时返回空数组
     */
    public static float[][] buildDashedSegments(
            float x1, float y1, float x2, float y2,
            float dashLen, float gapLen) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float totalLen = (float) Math.sqrt(dx * dx + dy * dy);
        if (totalLen < 1e-6f || dashLen <= 0) {
            return new float[0][];
        }

        float unitX = dx / totalLen;
        float unitY = dy / totalLen;
        float cycleLen = dashLen + gapLen;

        java.util.List<float[]> segments = new java.util.ArrayList<>();
        float pos = 0;
        while (pos + dashLen <= totalLen) {
            float dashEnd = pos + dashLen;
            segments.add(new float[]{
                x1 + unitX * pos, y1 + unitY * pos,
                x1 + unitX * dashEnd, y1 + unitY * dashEnd
            });
            pos += cycleLen;
        }
        return segments.toArray(new float[0][]);
    }
}

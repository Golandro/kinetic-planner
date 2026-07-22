package net.jsmua.kinetic_planner.cadengine;

/**
 * 纯数学工具：三次贝塞尔曲线均匀采样。
 *
 * <p>给定 4 个控制点 P0-P3 和采样段数，按 t∈[0,1] 均匀采样
 * 生成折线点序列。
 *
 * <p>三次贝塞尔公式：B(t) = (1-t)³P0 + 3(1-t)²tP1 + 3(1-t)t²P2 + t³P3
 *
 * <p>此类在 common sourceSet 中，纯 JVM 可测。
 */
public final class BezierTessellator {

    private BezierTessellator() {}

    /**
     * 对三次贝塞尔曲线进行均匀采样。
     *
     * @param p0x 第一个控制点 x（曲线起点）
     * @param p0y 第一个控制点 y
     * @param p1x 第二个控制点 x
     * @param p1y 第二个控制点 y
     * @param p2x 第三个控制点 x
     * @param p2y 第三个控制点 y
     * @param p3x 第四个控制点 x（曲线终点）
     * @param p3y 第四个控制点 y
     * @param segments 采样段数（返回 segments+1 个点）
     * @return 顶点数组：[x0, y0, x1, y1, ..., xn, yn]，长度 = (segments+1)*2
     */
    public static float[] tessellate(
            float p0x, float p0y, float p1x, float p1y,
            float p2x, float p2y, float p3x, float p3y,
            int segments) {
        float[] points = new float[(segments + 1) * 2];
        for (int i = 0; i <= segments; i++) {
            float t = (float) i / segments;
            float u = 1 - t;
            float tt = t * t;
            float uu = u * u;
            float ttt = tt * t;
            float uuu = uu * u;

            float x = uuu * p0x + 3 * uu * t * p1x + 3 * u * tt * p2x + ttt * p3x;
            float y = uuu * p0y + 3 * uu * t * p1y + 3 * u * tt * p2y + ttt * p3y;

            points[i * 2] = x;
            points[i * 2 + 1] = y;
        }
        return points;
    }
}

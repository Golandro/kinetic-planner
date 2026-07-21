package net.jsmua.kinetic_planner.projection;

/**
 * 世界坐标系中的轴对齐矩形（XZ 平面），用于描述可见区域。
 *
 * <p>由 {@link WorldScreenTransform#visibleWorldRect()} 计算得出，
 * 用于判断轨道节点/边是否在当前视口可见，从而跳过不可见图元的渲染。
 *
 * <p>坐标含义：X = 世界东西方向，Z = 世界南北方向（与 Minecraft 惯例一致）。
 *
 * @param minX 矩形西边界（最小 X）
 * @param minZ 矩形北边界（最小 Z）
 * @param maxX 矩形东边界（最大 X）
 * @param maxZ 矩形南边界（最大 Z）
 */
public record WorldRect(double minX, double minZ, double maxX, double maxZ) {

    /**
     * 判断给定的世界坐标是否在矩形内（含边界）。
     *
     * @param x 世界 X 坐标
     * @param z 世界 Z 坐标
     * @return {@code true} 如果 {@code minX <= x <= maxX && minZ <= z <= maxZ}
     */
    public boolean contains(double x, double z) {
        return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
    }

    /**
     * 判断给定的世界坐标是否在矩形外扩 {@code margin} 后的范围内（含边界）。
     *
     * <p>用于视口剔除时保留少量边距，避免线条端点刚好在视口边缘时被错误剔除。
     *
     * @param x      世界 X 坐标
     * @param z      世界 Z 坐标
     * @param margin 外扩余量（方块数）
     * @return {@code true} 如果坐标在 {@code [minX-margin, maxX+margin] × [minZ-margin, maxZ+margin]} 内
     */
    public boolean containsWithMargin(double x, double z, double margin) {
        return x >= minX - margin && x <= maxX + margin && z >= minZ - margin && z <= maxZ + margin;
    }
}

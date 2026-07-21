package net.jsmua.kinetic_planner.projection;

/**
 * 不可变的二维双精度向量，用于世界坐标投影计算。
 *
 * <p>Minecraft 内置的 {@link org.joml.Vector2f} 使用 {@code float} 精度，
 * 但 Create 的轨道坐标可能很大（世界边界 ±30,000,000），float 在此尺度下
 * 精度不足（约 7 位有效数字）。本类使用 {@code double} 保证投影逆变换的精度。
 *
 * <p>主要消费者：
 * <ul>
 *   <li>{@link WorldScreenTransform#screenToWorld(double, double)} - 返回值</li>
 *   <li>{@link WorldScreenTransform#visibleWorldRect()} - 内部计算</li>
 * </ul>
 *
 * @param x X 分量（世界 X 轴或屏幕 X 轴，取决于上下文）
 * @param y Y 分量（世界 Z 轴或屏幕 Y 轴，取决于上下文）
 */
public record Vec2d(double x, double y) {

    /**
     * 向量逐分量加法。
     *
     * @param other 加数
     * @return {@code (x + other.x, y + other.y)}
     */
    public Vec2d add(Vec2d other) {
        return new Vec2d(x + other.x, y + other.y);
    }

    /**
     * 向量逐分量减法。
     *
     * @param other 减数
     * @return {@code (x - other.x, y - other.y)}
     */
    public Vec2d subtract(Vec2d other) {
        return new Vec2d(x - other.x, y - other.y);
    }

    /**
     * 向量标量缩放。
     *
     * @param factor 缩放因子
     * @return {@code (x * factor, y * factor)}
     */
    public Vec2d scale(double factor) {
        return new Vec2d(x * factor, y * factor);
    }

    /**
     * 计算到另一个向量的欧几里得距离。
     *
     * @param other 目标向量
     * @return {@code sqrt((x-other.x)^2 + (y-other.y)^2)}
     */
    public double distanceTo(Vec2d other) {
        double dx = x - other.x;
        double dy = y - other.y;
        return Math.sqrt(dx * dx + dy * dy);
    }
}

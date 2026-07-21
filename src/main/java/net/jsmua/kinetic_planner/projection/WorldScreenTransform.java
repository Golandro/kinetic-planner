package net.jsmua.kinetic_planner.projection;

import org.joml.Vector2f;

/**
 * 世界坐标 ↔ 屏幕坐标双向投影变换器（纯函数，无渲染依赖）。
 *
 * <p>封装了从 Create/Xaero 的地图相机参数到屏幕像素坐标的数学变换。
 * 所有方法均为无副作用的纯函数，线程安全。
 *
 * <h2>变换公式</h2>
 * <pre>
 * 正向（世界 → 屏幕）：
 *   screenX = (worldX - cameraX) / blocksPerPixel + screenCenterX
 *   screenY = (worldZ - cameraZ) / blocksPerPixel + screenCenterY
 *
 * 逆向（屏幕 → 世界）：
 *   worldX = (screenX - screenCenterX) * blocksPerPixel + cameraX
 *   worldZ = (screenY - screenCenterY) * blocksPerPixel + cameraZ
 * </pre>
 *
 * <p>注意：正向变换返回 {@link Vector2f}（float 精度，用于渲染），
 * 逆向变换返回 {@link Vec2d}（double 精度，用于坐标计算）。
 * 由于 float→double→float 往返存在精度损失，逆变换的容差应设为 1e-3 级别。
 *
 * <h2>世界边界</h2>
 * <p>Minecraft 世界边界为 ±30,000,000 方块。{@link #visibleWorldRect()} 会对计算结果
 * 进行钳制，防止极端缩放级别下出现超出世界边界的无意义坐标。
 *
 * <h2>消费者</h2>
 * <ul>
 *   <li>{@link net.jsmua.kinetic_planner.instrument.NativeLineOverlay} - 使用 {@code worldToScreen} 绘制轨道线</li>
 *   <li>测试 {@code WorldScreenTransformTest} - 验证正逆变换的一致性</li>
 * </ul>
 */
public final class WorldScreenTransform {

    /** Minecraft 世界边界（±30,000,000 方块），用于钳制可见矩形。 */
    private static final double WORLD_BOUNDARY = 3.0e7;

    /** 当前帧的相机参数，构造后不可变。 */
    private final CameraParams cam;

    /**
     * 构造投影变换器。
     *
     * @param cam 相机参数，包含相机位置、缩放和屏幕尺寸
     */
    public WorldScreenTransform(CameraParams cam) {
        this.cam = cam;
    }

    /**
     * 返回构造时传入的相机参数。
     *
     * @return 相机参数
     */
    public CameraParams cam() {
        return cam;
    }

    /**
     * 将世界坐标投影到屏幕坐标。
     *
     * <p>使用 float 精度以匹配 MC 渲染管线的 {@code VertexConsumer} API。
     * 对于需要 double 精度的坐标计算，应使用 {@link #screenToWorld}。
     *
     * @param worldX 世界 X 坐标（东西方向）
     * @param worldZ 世界 Z 坐标（南北方向）
     * @return 屏幕像素坐标 {@code (screenX, screenY)}
     */
    public Vector2f worldToScreen(double worldX, double worldZ) {
        float screenX = (float) ((worldX - cam.cameraBlockX()) / cam.blocksPerPixel() + cam.screenCenterX());
        float screenY = (float) ((worldZ - cam.cameraBlockZ()) / cam.blocksPerPixel() + cam.screenCenterY());
        return new Vector2f(screenX, screenY);
    }

    /**
     * 将屏幕坐标逆投影回世界坐标。
     *
     * <p>使用 double 精度，适合坐标比较和视口剔除。
     *
     * @param screenX 屏幕 X 像素
     * @param screenY 屏幕 Y 像素
     * @return 世界坐标 {@code (worldX, worldZ)}
     */
    public Vec2d screenToWorld(double screenX, double screenY) {
        double worldX = (screenX - cam.screenCenterX()) * cam.blocksPerPixel() + cam.cameraBlockX();
        double worldZ = (screenY - cam.screenCenterY()) * cam.blocksPerPixel() + cam.cameraBlockZ();
        return new Vec2d(worldX, worldZ);
    }

    /**
     * 将屏幕像素距离转换为世界方块距离。
     *
     * @param pixelDist 像素距离
     * @return 方块距离 {@code pixelDist * blocksPerPixel}
     */
    public double screenToWorldDistance(double pixelDist) {
        return pixelDist * cam.blocksPerPixel();
    }

    /**
     * 计算当前相机参数下的可见世界矩形（XZ 平面）。
     *
     * <p>结果经过世界边界钳制，确保不返回超出 ±30,000,000 的坐标。
     * 用于视口剔除：只有在此矩形内的轨道节点/边才需要渲染。
     *
     * @return 可见世界矩形
     */
    public WorldRect visibleWorldRect() {
        double halfWidthBlocks = (cam.screenWidth() / 2.0) * cam.blocksPerPixel();
        double halfHeightBlocks = (cam.screenHeight() / 2.0) * cam.blocksPerPixel();
        double minX = clamp(cam.cameraBlockX() - halfWidthBlocks);
        double maxX = clamp(cam.cameraBlockX() + halfWidthBlocks);
        double minZ = clamp(cam.cameraBlockZ() - halfHeightBlocks);
        double maxZ = clamp(cam.cameraBlockZ() + halfHeightBlocks);
        return new WorldRect(minX, minZ, maxX, maxZ);
    }

    /**
     * 将值钳制到世界边界范围内。
     *
     * @param v 待钳制的值
     * @return {@code max(-WORLD_BOUNDARY, min(WORLD_BOUNDARY, v))}
     */
    private static double clamp(double v) {
        return Math.max(-WORLD_BOUNDARY, Math.min(WORLD_BOUNDARY, v));
    }
}

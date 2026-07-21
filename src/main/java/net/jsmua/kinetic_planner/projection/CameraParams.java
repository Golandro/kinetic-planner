package net.jsmua.kinetic_planner.projection;

/**
 * 地图相机参数，描述当前地图视口的中心位置、缩放比例和屏幕尺寸。
 *
 * <p>此类是纯数据 record，不包含任何渲染逻辑。由 {@link net.jsmua.kinetic_planner.mapadapter.MapOverlayContext}
 * 在地图打开时捕获，传递给 {@link WorldScreenTransform} 构造投影变换。
 *
 * <h2>坐标系约定</h2>
 * <ul>
 *   <li>{@code cameraBlockX} / {@code cameraBlockZ} - 相机中心在世界坐标系中的方块坐标（X 轴 = 东/西，Z 轴 = 南/北）</li>
 *   <li>{@code blocksPerPixel} - 每像素代表的方块数，值越大表示缩放越远（看到的范围越广）</li>
 *   <li>{@code screenWidth} / {@code screenHeight} - GUI 缩放后的屏幕宽高（像素）</li>
 * </ul>
 *
 * <p>屏幕中心计算使用整数除法（向下取整），与 Minecraft 的 {@code GuiGraphics} 像素对齐一致。
 *
 * @param cameraBlockX  相机中心的世界 X 坐标（方块）
 * @param cameraBlockZ  相机中心的世界 Z 坐标（方块）
 * @param blocksPerPixel 每像素代表的方块数（缩放因子）
 * @param screenWidth   屏幕宽度（GUI 缩放后像素）
 * @param screenHeight  屏幕高度（GUI 缩放后像素）
 */
public record CameraParams(
    double cameraBlockX,
    double cameraBlockZ,
    double blocksPerPixel,
    int screenWidth,
    int screenHeight
) {
    /**
     * 屏幕中心 X 坐标（像素），使用整数除法向下取整。
     *
     * @return {@code screenWidth / 2}
     */
    public int screenCenterX() { return screenWidth / 2; }

    /**
     * 屏幕中心 Y 坐标（像素），使用整数除法向下取整。
     *
     * @return {@code screenHeight / 2}
     */
    public int screenCenterY() { return screenHeight / 2; }
}

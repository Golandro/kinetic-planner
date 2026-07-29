package net.jsmua.kinetic_planner.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import xaero.map.gui.GuiMap;

/**
 * Mixin accessor 接口，用于读取 Xaero {@code GuiMap} 的 private 字段。
 *
 * <p>通过 {@code @Accessor} 注解生成字段访问方法，方法名使用 {@code kp$} 前缀
 * 避免与 Create 的 {@code XaeroFullscreenMapMixin} 等其他模组的 accessor 冲突。
 *
 * <p>{@code remap = false} 因为 Xaero 不在 Mojang 的 obfuscation 映射中，
 * 字段名 {@code cameraX}/{@code cameraZ}/{@code scale} 在生产环境中不经过重映射。
 *
 * <p>Mixin 配置在 {@code kinetic_planner.mixins.json} 的 {@code client} 数组中声明，
 * {@code defaultRequire = 0} 确保当 Xaero 未安装时 Mixin 注入失败不会导致游戏崩溃。
 */
@Mixin(value = GuiMap.class, remap = false)
public interface XaeroMapAccessor {

    /**
     * 读取 {@code GuiMap.cameraX} 字段 - 地图相机的世界 X 坐标。
     *
     * @return 相机 X 坐标（方块）
     */
    @Accessor("cameraX")
    double kp$cameraX();

    /**
     * 写入 {@code GuiMap.cameraX} 字段 - 编辑模式自定义地图导航使用。
     *
     * @param cameraX 相机 X 坐标（方块）
     */
    @Accessor("cameraX")
    void kp$setCameraX(double cameraX);

    /**
     * 读取 {@code GuiMap.cameraZ} 字段 - 地图相机的世界 Z 坐标。
     *
     * @return 相机 Z 坐标（方块）
     */
    @Accessor("cameraZ")
    double kp$cameraZ();

    /**
     * 写入 {@code GuiMap.cameraZ} 字段 - 编辑模式自定义地图导航使用。
     *
     * @param cameraZ 相机 Z 坐标（方块）
     */
    @Accessor("cameraZ")
    void kp$setCameraZ(double cameraZ);

    /**
     * 读取 {@code GuiMap.scale} 字段 - 地图缩放值。
     *
     * <p>值越大表示缩放越近（blocksPerPixel 越小）。
     *
     * @return 地图缩放值
     */
    @Accessor("scale")
    double kp$scale();

    /**
     * 写入 {@code GuiMap.scale} 字段 - 编辑模式自定义滚轮缩放使用。
     *
     * @param scale 地图缩放值
     */
    @Accessor("scale")
    void kp$setScale(double scale);
}

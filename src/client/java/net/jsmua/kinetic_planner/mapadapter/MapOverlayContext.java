package net.jsmua.kinetic_planner.mapadapter;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * 地图叠加渲染上下文，由 {@link MapOverlayProvider#captureContext} 在地图打开时捕获。
 *
 * <p>包含渲染叠加层所需的全部信息：当前维度、相机位置、缩放比例、屏幕尺寸、
 * 鼠标位置、partial tick 和设备像素比。
 *
 * <p>由 {@link MapOverlayDispatcher} 暂存，供 {@link net.jsmua.kinetic_planner.instrument.NativeLineOverlay}
 * 在渲染回调中读取，构造 {@link net.jsmua.kinetic_planner.projection.WorldScreenTransform}。
 *
 * @param dimension     当前显示的维度
 * @param cameraBlockX  相机中心的世界 X 坐标（方块）
 * @param cameraBlockZ  相机中心的世界 Z 坐标（方块）
 * @param blocksPerPixel 每像素代表的方块数（缩放因子，越大越远）
 * @param screenWidth   GUI 缩放后的屏幕宽度（像素）
 * @param screenHeight  GUI 缩放后的屏幕高度（像素）
 * @param mouseX        鼠标 X 位置（像素）
 * @param mouseY        鼠标 Y 位置（像素）
 * @param partialTicks  渲染插值 partial tick（Phase 0a 用 0f 占位）
 * @param dpr           设备像素比（screenWidth / guiScaledWidth），用于 HiDPI 适配
 */
public record MapOverlayContext(
    ResourceKey<Level> dimension,
    double cameraBlockX, double cameraBlockZ,
    double blocksPerPixel,
    int screenWidth, int screenHeight,
    int mouseX, int mouseY,
    float partialTicks,
    float dpr
) {}

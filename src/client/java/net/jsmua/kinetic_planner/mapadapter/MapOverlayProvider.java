package net.jsmua.kinetic_planner.mapadapter;

import net.minecraft.client.gui.screens.Screen;

import javax.annotation.Nullable;

    /**
 * 地图叠加适配器接口，抽象不同全屏地图模组（Xaero's World Map / JourneyMap）的差异化 API。
 *
 * <p>每个地图模组提供一个实现，由 {@link MapOverlayDispatcher} 在每 tick 轮询。
 * 当某个 provider 的 {@link #isMapOpen} 返回 {@code true} 时，dispatcher 调用
 * {@link #captureContext} 获取相机参数并停止继续轮询其他 provider。
 *
 * <h2>扩展指南</h2>
 * <p>新增地图模组支持时：
 * <ol>
 *   <li>实现本接口</li>
 *   <li>在 {@link MapOverlayDispatcher} 的 static 块中注册</li>
 *   <li>如有 Mixin 需求，在 {@code kinetic_planner.mixins.json} 的 {@code client} 数组中添加</li>
 * </ol>
 */
public interface MapOverlayProvider {

    /**
     * 判断当前屏幕是否为该地图模组的全屏地图界面。
     *
     * @param screen 当前 Minecraft 屏幕
     * @return {@code true} 如果是该地图模组的全屏地图
     */
    boolean isMapOpen(Screen screen);

    /**
     * 从地图界面中捕获相机参数和渲染上下文。
     *
     * <p>仅在 {@link #isMapOpen} 返回 {@code true} 时由 dispatcher 调用。
     * 实现应通过 Mixin accessor 读取地图内部的相机位置和缩放值。
     *
     * @param screen 当前地图屏幕（已确认是该 provider 的地图）
     * @return 地图叠加上下文，捕获失败时返回 null
     */
    @Nullable
    MapOverlayContext captureContext(Screen screen);

    /**
     * 返回该 provider 对应的地图模组 ID，用于熔断日志和依赖检查。
     *
     * @return mod ID（如 {@code "xaeroworldmap"}、{@code "journeymap"}）
     */
    String modId();
}

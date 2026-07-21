package net.jsmua.kinetic_planner;

import net.jsmua.kinetic_planner.instrument.NativeLineOverlay;
import net.jsmua.kinetic_planner.mapadapter.MapOverlayDispatcher;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Kinetic Planner 客户端入口类（client sourceSet）。
 *
 * <p>通过 {@code @Mod(dist = Dist.CLIENT)} 确保此类仅在逻辑客户端加载，
 * 专用服务端不会实例化此类，从而避免加载 {@code net.minecraft.client.*} 类。
 *
 * <p>职责：
 * <ul>
 *   <li>在客户端 setup 阶段输出日志，确认客户端入口已加载</li>
 *   <li>每 tick 调用 {@link MapOverlayDispatcher#tick()} 检测地图是否打开并捕获上下文</li>
 *   <li>每 tick 调用 {@link NativeLineOverlay#onClientTick()} 刷新几何缓存</li>
 * </ul>
 *
 * <p>实际渲染由 {@code XaeroMapRenderHook} Mixin 在 {@code GuiMap.render} 返回时触发，
 * 不在此类中直接调用渲染方法。
 */
@Mod(value = KineticPlannerMod.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = KineticPlannerMod.MODID, value = Dist.CLIENT)
public class KineticPlannerClient {

    /**
     * NeoForge 客户端 mod 构造函数。
     *
     * <p>Phase 0a 阶段构造函数为空，后续可在此注册配置屏幕扩展点等。
     *
     * @param container mod 容器
     */
    public KineticPlannerClient(ModContainer container) {
    }

    /**
     * 客户端 setup 事件回调，在资源加载完成后触发。
     *
     * <p>仅输出日志确认客户端入口已加载，Phase 0a 不做额外初始化。
     *
     * @param event FML 客户端 setup 事件
     */
    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        KineticPlannerMod.LOGGER.info("Kinetic Planner client setup");
    }

    /**
     * 客户端 tick 后置事件回调，每游戏 tick 执行一次。
     *
     * <p>执行两个操作：
     * <ol>
     *   <li>{@link MapOverlayDispatcher#tick()} — 检测当前屏幕是否为已注册的地图界面，
     *       若是则捕获相机参数并存入 {@code MapOverlayContext}</li>
     *   <li>{@link NativeLineOverlay#onClientTick()} — 根据最新上下文重建几何缓存
     *       （仅在数据版本或维度变化时重建）</li>
     * </ol>
     *
     * @param event 客户端 tick 后置事件
     */
    @SubscribeEvent
    static void onClientTickPost(ClientTickEvent.Post event) {
        MapOverlayDispatcher.tick();
        NativeLineOverlay.onClientTick();
    }
}

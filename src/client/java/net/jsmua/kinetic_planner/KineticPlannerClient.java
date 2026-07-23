package net.jsmua.kinetic_planner;

import net.jsmua.kinetic_planner.config.KPCommands;
import net.jsmua.kinetic_planner.config.KPConfig;
import net.jsmua.kinetic_planner.config.KPClothConfigScreen;
import net.jsmua.kinetic_planner.instrument.WorldTreeReadOverlay;
import net.jsmua.kinetic_planner.mapadapter.MapOverlayDispatcher;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

/**
 * Kinetic Planner 客户端入口类（client sourceSet）。
 *
 * <p>通过 {@code @Mod(dist = Dist.CLIENT)} 确保此类仅在逻辑客户端加载。
 *
 * <p>职责：
 * <ul>
 *   <li>注册客户端配置（KPConfig TOML）与配置屏幕（Cloth Config GUI）</li>
 *   <li>注册 /kp 客户端命令体系（{@link RegisterClientCommandsEvent}）</li>
 *   <li>每 tick 调用 {@link MapOverlayDispatcher#tick()} 检测地图是否打开</li>
 *   <li>每 tick 调用 {@link WorldTreeReadOverlay#onClientTick()} 刷新几何缓存</li>
 * </ul>
 */
@Mod(value = KineticPlannerMod.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = KineticPlannerMod.MODID, value = Dist.CLIENT)
public class KineticPlannerClient {

    public KineticPlannerClient(ModContainer container) {
        container.registerConfig(net.neoforged.fml.config.ModConfig.Type.CLIENT, KPConfig.SPEC);
        // TODO: Config screen registration API needs NeoForge 1.21.1 verification at runtime
        // container.registerConfigScreen(...) - exact API to be verified
    }

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        KineticPlannerMod.LOGGER.info("Kinetic Planner client setup");
    }

    @SubscribeEvent
    static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        KPCommands.register(event.getDispatcher());
    }

    @SubscribeEvent
    static void onClientTickPost(ClientTickEvent.Post event) {
        MapOverlayDispatcher.tick();
        WorldTreeReadOverlay.onClientTick();
    }
}

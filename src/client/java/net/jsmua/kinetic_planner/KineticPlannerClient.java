package net.jsmua.kinetic_planner;

import net.jsmua.kinetic_planner.command.KPClientCommands;
import net.jsmua.kinetic_planner.command.KPCommandTree;
import net.jsmua.kinetic_planner.config.IKPConfig;
import net.jsmua.kinetic_planner.config.KPConfig;
import net.jsmua.kinetic_planner.gui.config.KPClothConfigScreen;
import net.jsmua.kinetic_planner.gui.editor.EditToolState;
import net.jsmua.kinetic_planner.gui.editor.ribbon.KpRibbonRegistration;
import net.jsmua.kinetic_planner.gui.ribbon.registry.RibbonRegistry;
import net.jsmua.kinetic_planner.data.ProviderConfigRegistry;
import net.jsmua.kinetic_planner.instrument.WorldTreeReadOverlay;
import net.jsmua.kinetic_planner.mapadapter.MapOverlayDispatcher;
import net.jsmua.kinetic_planner.mapadapter.MapProviderRegistry;
import net.jsmua.kinetic_planner.mapadapter.XaeroMapProviderFactory;
import net.jsmua.kinetic_planner.mapadapter.JourneyMapMapProviderFactory;
import net.jsmua.kinetic_planner.compat.create.KPIntegration;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
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

    /**
     * 全局 IKPConfig 引用, 供 KpMapEditor 等运行时组件获取配置 (非 getInstance() 反模式,
     * 与 EditToolState.getInstance() 同性质: 运行时入口点)。
     *
     * <p>volatile: 构造函数赋值后只读, 但跨线程可见性需要 volatile。
     */
    public static volatile IKPConfig CONFIG;

    public KineticPlannerClient(ModContainer container) {
        container.registerConfig(net.neoforged.fml.config.ModConfig.Type.CLIENT, KPConfig.SPEC);
        KineticPlannerClient.CONFIG = KPConfig.getInstance();
        // TODO: Config screen registration API needs NeoForge 1.21.1 verification at runtime
        // container.registerConfigScreen(...) - exact API to be verified
    }

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        KineticPlannerMod.LOGGER.info("Kinetic Planner client setup");

        // 注册内置地图 provider 工厂 / Register built-in map provider factories
        MapProviderRegistry.register(new XaeroMapProviderFactory());
        MapProviderRegistry.register(new JourneyMapMapProviderFactory());

        // 冻结工厂注册表——不允许后续注册 / Freeze factory registry — no more registrations allowed
        MapProviderRegistry.freeze();

        // 注册 KP Ribbon tab/group/component, 然后冻结 Ribbon 注册表
        // Register KP Ribbon tab/group/component, then freeze Ribbon registry
        KpRibbonRegistration.register(EditToolState.getInstance());
        RibbonRegistry.freeze();

        // 从已注册工厂初始化 provider 实例（仅针对已安装的模组）
        // Initialize provider instances from registered factories (only for installed mods)
        MapOverlayDispatcher.initProviders();

        // 为所有工厂（包括未安装的模组）注册默认配置，
        // 以便 ProviderConfigControl 可以列出/配置它们
        // Register default config for ALL factories (including uninstalled mods)
        // so that ProviderConfigControl can list/configure them
        for (var factory : MapProviderRegistry.all()) {
            ProviderConfigRegistry.register(factory.modId(), factory.defaultConfig());
        }

        // 一次性打通 Create 列车地图管线：此后叠加层仅由 KP "Show Create Track Map" 开关把守
        event.enqueueWork(() -> {
            if (ModList.get().isLoaded("create")) {
                KPIntegration.forceCreateOverlayPipeline();
            }
        });
    }

    @SubscribeEvent
    static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        KPCommandTree.register(event.getDispatcher(), new KPClientCommands());
    }

    @SubscribeEvent
    static void onClientTickPost(ClientTickEvent.Post event) {
        MapOverlayDispatcher.tick();
        WorldTreeReadOverlay.onClientTick();
    }
}

package net.jsmua.kinetic_planner;

import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;

/**
 * Kinetic Planner 模组主入口类（common 侧）。
 *
 * <p>本类注册在 NeoForge 的 mod 加载管线上，在逻辑服务端和逻辑客户端均可加载。
 * 所有 common 代码（纯数学、数据接口、几何描述符）位于 {@code main} sourceSet，
 * 不引用任何 {@code net.minecraft.client.*} 或 {@code com.mojang.blaze3d.*} 类。
 *
 * <p>客户端专属代码（渲染、Mixin、地图适配）由 {@link KineticPlannerClient} 承载，
 * 位于 {@code client} sourceSet，通过 {@code @Mod(dist=CLIENT)} 双保险确保专用服务端不加载。
 *
 * <h2>架构概览</h2>
 * <ul>
 *   <li>{@code data/} — 只读数据访问接口（{@link net.jsmua.kinetic_planner.data.IRailwayDataAccess}）与几何描述符</li>
 *   <li>{@code projection/} — 世界坐标 ↔ 屏幕坐标纯函数变换</li>
 *   <li>{@code instrument/} — MC 原生线叠加渲染器（client sourceSet）</li>
 *   <li>{@code mapadapter/} — Xaero/JourneyMap 地图适配层（client sourceSet）</li>
 *   <li>{@code mixin/} — Mixin 注入点（client sourceSet）</li>
 * </ul>
 *
 * <h2>依赖关系</h2>
 * <pre>
 *   Create 6.0.10  (implementation, 可访问内部类)
 *   Xaero's World Map + XaeroLib  (compileOnly, 运行时由用户装)
 *   Cloth Config 15.0.140  (api)
 *   NeoForge 21.1.235 / Minecraft 1.21.1 / Java 21
 * </pre>
 */
@Mod(KineticPlannerMod.MODID)
public class KineticPlannerMod {

    /** 模组唯一标识符，需与 neoforge.mods.toml 中的 modId 一致。 */
    public static final String MODID = "kinetic_planner";

    /** 全局 SLF4J 日志器，所有包通过此字段输出日志。 */
    public static final Logger LOGGER = LogUtils.getLogger();

    /**
     * NeoForge mod 构造函数，在 mod 加载阶段调用。
     *
     * @param modEventBus  mod 事件总线，用于注册延迟注册器等 common 事件
     * @param modContainer mod 容器，可用于注册配置屏幕扩展点等
     */
    public KineticPlannerMod(IEventBus modEventBus, ModContainer modContainer) {
        LOGGER.info("Kinetic Planner loading (common)");
    }
}

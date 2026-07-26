package net.jsmua.kinetic_planner.mapadapter;

import journeymap.api.v2.client.IClientAPI;
import journeymap.api.v2.client.IClientPlugin;
import journeymap.api.v2.common.JourneyMapPlugin;
import net.jsmua.kinetic_planner.KineticPlannerMod;

import javax.annotation.Nullable;

/**
 * JourneyMap 客户端插件入口。
 *
 * <p>通过 {@code @JourneyMapPlugin} 注解由 JM 自动发现并加载。
 * JM 不安装时此类不会被加载，因此可以安全引用 JM API 类型。
 *
 * <p>在 {@link #initialize} 中接收 {@link IClientAPI} 实例并保存在 static 字段，
 * 供 {@link JourneyMapOverlayProvider} 通过 {@link #getApi()} 获取。
 *
 * <p>Phase B 扩展：在 initialize 中订阅 FullscreenEventRegistry 事件，
 * 实现 JM 全屏地图上的齿轮按钮和配置面板。
 */
@JourneyMapPlugin(apiVersion = "2.0.0-SNAPSHOT")
public class KineticPlannerJMPlugin implements IClientPlugin {

    @Nullable
    private static IClientAPI api;

    @Override
    public String getModId() {
        return KineticPlannerMod.MODID;
    }

    @Override
    public void initialize(IClientAPI jmClientApi) {
        api = jmClientApi;
        KineticPlannerMod.LOGGER.info("[KP] JourneyMap API initialized");
    }

    /**
     * 返回 JM 客户端 API 实例。
     *
     * <p>仅在 JM 安装且插件已初始化时非 null。
     * 调用方应先检查 {@code ModList.get().isLoaded("journeymap")}。
     *
     * @return IClientAPI 实例，或 null 如果 JM 未安装/未初始化
     */
    @Nullable
    public static IClientAPI getApi() {
        return api;
    }
}

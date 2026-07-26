package net.jsmua.kinetic_planner.compat.create;

import com.simibubi.create.infrastructure.config.AllConfigs;
import net.jsmua.kinetic_planner.KineticPlannerMod;
import net.jsmua.kinetic_planner.config.OverlayControl;

/**
 * 接管 Create 列车地图（Track Map）显示激活逻辑的集成层。
 *
 * <p>KP 安装后，通过 {@link net.jsmua.kinetic_planner.mixin.CreateTrainMapMixin} 与
 * {@link net.jsmua.kinetic_planner.mixin.CreateTrainMapOverlayMixin} 分治 Create
 * 在 Xaero / JourneyMap 上的逻辑点（两侧适配层均汇聚于 TrainMapManager 静态方法，
 * 一套 Mixin 全覆盖）：
 * <ul>
 *   <li><b>按钮</b> ({@code renderToggleWidget}) — 空体覆写，永不渲染；</li>
 *   <li><b>悬停</b> ({@code isToggleWidgetHovered}) — 恒 {@code false}，
 *       "列车网络叠加层" Toast 与点击处理一并消灭（单点封杀）；</li>
 *   <li><b>叠加层</b> ({@code renderAndPick}) — 受 "Show Create Track Map" 开关控制。</li>
 * </ul>
 *
 * <p>此外，Create 渲染/同步管线受其自身配置 {@code showTrainMapOverlay} 把守。
 * KP 在客户端初始化时通过 {@link #forceCreateOverlayPipeline()} <b>一次性</b>将其置为
 * {@code true}，使 "Show Create Track Map" 成为唯一闸门。
 */
public final class KPIntegration {

    private KPIntegration() {
    }

    /**
     * 一次性打通 Create 列车地图渲染/同步管线。
     *
     * <p>将 Create 客户端配置 {@code showTrainMapOverlay} 强制置为 {@code true}：
     * Xaero/Journey 的 {@code tick}/{@code onRender} 不再受其把守，
     * 叠加层是否渲染完全由 KP 的 "Show Create Track Map" 开关（{@code renderAndPick}
     * 覆写）决定。应在客户端初始化（{@code FMLClientSetupEvent#enqueueWork}）调用一次，
     * 且仅在 Create 已加载时调用。
     *
     * <p>副作用：地图打开期间 Create 的列车数据同步（{@code TrainMapSyncClient}）保持
     * 活跃，即使 KP 开关当前为关——换来开关切换时数据即时可用，代价与 Create 原生
     * 开启叠加层时相同。
     */
    public static void forceCreateOverlayPipeline() {
        try {
            AllConfigs.client().showTrainMapOverlay.set(true);
        } catch (Throwable t) {
            KineticPlannerMod.LOGGER.warn("Failed to force Create train map overlay pipeline on", t);
        }
    }

    /**
     * 是否应阻止 Create 的列车地图叠加层渲染。
     *
     * <p>判定策略：<b>"Show Create Track Map" 开关关闭时阻止</b>。
     * <ul>
     *   <li>{@link OverlayControl#isShowCreateTrackMap()} 为 {@code false}（默认）→
     *       阻止 Create 叠加层渲染，KP 完全替代；</li>
     *   <li>用户在 KP 菜单中勾选 "Show Create Track Map" → 返回 {@code false} →
     *       Create 列车地图叠加层按原逻辑正常渲染（KP 与 Create 共存显示）。</li>
     * </ul>
     */
    public static boolean shouldBlockCreateOverlay() {
        return !OverlayControl.isShowCreateTrackMap();
    }
}

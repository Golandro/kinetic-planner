package net.jsmua.kinetic_planner.mapadapter;

import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import journeymap.api.v2.client.IClientAPI;
import journeymap.api.v2.client.IClientPlugin;
import journeymap.api.v2.client.event.FullscreenDisplayEvent;
import journeymap.api.v2.client.event.FullscreenMapEvent;
import journeymap.api.v2.client.event.FullscreenRenderEvent;
import journeymap.api.v2.client.fullscreen.IThemeButton;
import journeymap.api.v2.client.fullscreen.ThemeButtonDisplay;
import journeymap.api.v2.common.JourneyMapPlugin;
import journeymap.api.v2.common.event.FullscreenEventRegistry;
import net.jsmua.kinetic_planner.KineticPlannerMod;
import net.jsmua.kinetic_planner.config.KpClientState;
import net.jsmua.kinetic_planner.config.KpConfigUIFactory;
import net.jsmua.kinetic_planner.config.KpGearButton;
import net.jsmua.kinetic_planner.config.KpUIEventForwarder;
import net.jsmua.kinetic_planner.config.OverlayControl;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

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
 * <p>Phase B 扩展（spec §D）：在 initialize 中订阅 {@link FullscreenEventRegistry} 事件，
 * 实现 JM 全屏地图上的齿轮按钮和配置面板。事件通过 {@link KpUIEventForwarder}
 * 转发到 LDLib2 {@link ModularUI}{@code .ModularUIWidget}，与 Xaero 侧共用同一 UI 树。
 *
 * <h2>订阅的事件</h2>
 * <ul>
 *   <li>{@link FullscreenEventRegistry#ADDON_BUTTON_DISPLAY_EVENT} - 添加 "KP" 按钮到 JM 工具栏</li>
 *   <li>{@link FullscreenEventRegistry#FULLSCREEN_RENDER_EVENT} - 渲染齿轮按钮 + 配置面板</li>
 *   <li>{@link FullscreenEventRegistry#FULLSCREEN_MAP_CLICK_EVENT} - 鼠标点击路由</li>
 * </ul>
 */
@JourneyMapPlugin(apiVersion = "2.0.0-SNAPSHOT")
public class KineticPlannerJMPlugin implements IClientPlugin {

    @Nullable
    private static IClientAPI api;

    @Nullable
    private static KpGearButton jmGearButton;

    @Nullable
    private static KpUIEventForwarder jmForwarder;

    @Override
    public String getModId() {
        return KineticPlannerMod.MODID;
    }

    @Override
    public void initialize(IClientAPI jmClientApi) {
        api = jmClientApi;
        KineticPlannerMod.LOGGER.info("[KP] JourneyMap API initialized");

        // 订阅 JM 全屏地图事件
        FullscreenEventRegistry.ADDON_BUTTON_DISPLAY_EVENT.subscribe(
            KineticPlannerMod.MODID, this::onAddonButtonDisplay);
        FullscreenEventRegistry.FULLSCREEN_RENDER_EVENT.subscribe(
            KineticPlannerMod.MODID, this::onFullscreenRender);
        FullscreenEventRegistry.FULLSCREEN_MAP_CLICK_EVENT.subscribe(
            KineticPlannerMod.MODID, this::onFullscreenMapClick);
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

    /**
     * JM 工具栏按钮显示事件 -- 添加 "KP" 按钮到 JM 右侧面板。
     *
     * <p>点击切换配置面板可见性（spec §5.4）。
     */
    private void onAddonButtonDisplay(FullscreenDisplayEvent.AddonButtonDisplayEvent event) {
        ThemeButtonDisplay display = event.getThemeButtonDisplay();
        // icon ResourceLocation 指向不存在的纹理，JM 会显示无图标的按钮
        ResourceLocation icon = ResourceLocation.fromNamespaceAndPath(
            KineticPlannerMod.MODID, "textures/gui/gear.png");
        display.addThemeButton("KP", "KP", icon, button -> {
            KpClientState.toggleConfigPanel();
        });
    }

    /**
     * JM 全屏地图渲染事件 -- 在地图渲染后、按钮前渲染齿轮按钮 + 配置面板。
     *
     * <p>spec §8.2：齿轮按钮始终渲染；配置面板仅在 {@link KpClientState#isConfigPanelVisible()} 时渲染。
     */
    private void onFullscreenRender(FullscreenRenderEvent event) {
        if (!OverlayControl.isEnabled()) return;
        ensureUiComponents();
        if (jmGearButton == null || jmForwarder == null) return;

        GuiGraphics gg = event.getGraphics();
        int mouseX = event.getMouseX();
        int mouseY = event.getMouseY();
        float partialTicks = event.getPartialTicks();

        // 齿轮按钮始终渲染
        jmGearButton.render(gg, mouseX, mouseY);
        // 配置面板仅在可见时渲染
        if (KpClientState.isConfigPanelVisible()) {
            jmForwarder.render(gg, mouseX, mouseY, partialTicks);
        }
    }

    /**
     * JM 全屏地图鼠标点击事件 -- 路由到配置面板和齿轮按钮。
     *
     * <p>仅在 PRE 阶段处理（{@link FullscreenMapEvent.ClickEvent#isCancellable()}
     * 返回 {@code true} 当且仅当 {@code stage == PRE}）。
     */
    private void onFullscreenMapClick(FullscreenMapEvent.ClickEvent event) {
        if (!OverlayControl.isEnabled()) return;
        if (event.getStage() != FullscreenMapEvent.Stage.PRE) return;
        ensureUiComponents();
        if (jmGearButton == null || jmForwarder == null) return;

        double mouseX = event.getMouseX();
        double mouseY = event.getMouseY();
        int button = event.getButton();

        // 先检查齿轮按钮
        if (jmGearButton.mouseClicked(mouseX, mouseY, button)) {
            event.cancel();
            return;
        }
        // 再检查配置面板（仅在可见时）
        if (KpClientState.isConfigPanelVisible()
            && jmForwarder.mouseClicked(mouseX, mouseY, button)) {
            event.cancel();
        }
    }

    /**
     * 懒加载 UI 组件 - 与 Xaero Mixin 共用 {@link KpConfigUIFactory} 构建的 UI 树。
     */
    private static void ensureUiComponents() {
        var window = Minecraft.getInstance().getWindow();
        int screenW = window.getGuiScaledWidth();
        int screenH = window.getGuiScaledHeight();

        if (jmForwarder == null) {
            var modularUI = KpConfigUIFactory.create();
            modularUI.init(screenW, screenH);
            jmForwarder = new KpUIEventForwarder(modularUI);
        }
        jmForwarder.checkResize(screenW, screenH);

        if (jmGearButton == null) {
            // 齿轮按钮位于右上角 (screenW - 20, 4)，16x16
            jmGearButton = new KpGearButton(screenW - 20, 4,
                () -> KpClientState.toggleConfigPanel());
        }
    }
}

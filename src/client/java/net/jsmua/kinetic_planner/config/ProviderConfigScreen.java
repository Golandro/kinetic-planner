package net.jsmua.kinetic_planner.config;

import net.jsmua.kinetic_planner.data.ProviderConfig;
import net.jsmua.kinetic_planner.mapadapter.MapOverlayDispatcher;
import net.jsmua.kinetic_planner.mapadapter.MapOverlayProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * 嵌入式配置面板渲染器（非 {@code Screen} 子类）。
 *
 * <p>由 Xaero Mixin 或 JM 事件在地图渲染后调用 {@link #renderPanel}。
 * 面板覆盖在地图之上，半透明背景 + provider Tab + 参数控件。
 *
 * <p>控件交互通过 {@link #handleMouseClick} 路由，不依赖 MC Widget 体系。
 * 修改参数实时通过 {@link ProviderConfigControl} / {@link OverlayControl} 应用。
 */
public class ProviderConfigScreen {

    private static final int PANEL_WIDTH = 200;
    private static final int PANEL_HEIGHT = 160;
    private static final int BG_COLOR = 0xC0202020;
    private static final int TAB_HEIGHT = 16;
    private static final int TAB_WIDTH = 80;

    private final MapGearButtonWidget gearButton;
    private boolean visible = false;
    private String selectedProviderModId;

    // 控件区域定义（相对于面板左上角）
    private int panelX, panelY;

    public ProviderConfigScreen(MapGearButtonWidget gearButton) {
        this.gearButton = gearButton;
    }

    public boolean isVisible() {
        return visible;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
        if (visible && selectedProviderModId == null) {
            // 默认选中第一个 provider
            List<MapOverlayProvider> providers = MapOverlayDispatcher.registeredProviders();
            if (!providers.isEmpty()) {
                selectedProviderModId = providers.get(0).modId();
            }
        }
    }

    public void toggle() {
        setVisible(!visible);
    }

    /**
     * 渲染配置面板。仅在 {@link #visible} 时绘制。
     */
    public void renderPanel(GuiGraphics gg, int mouseX, int mouseY, float partialTicks) {
        if (!visible) return;

        int screenWidth = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int screenHeight = Minecraft.getInstance().getWindow().getGuiScaledHeight();

        // 面板定位：右上角，齿轮按钮下方
        panelX = screenWidth - PANEL_WIDTH - 4;
        panelY = 24;

        // 半透明背景
        gg.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + PANEL_HEIGHT, BG_COLOR);

        int cursorY = panelY + 4;

        // 标题
        gg.drawString(Minecraft.getInstance().font,
            Component.literal("Kinetic Planner Config"),
            panelX + 6, cursorY, 0xFFFFFFFF);
        cursorY += 14;

        // Provider Tab 行
        List<MapOverlayProvider> providers = MapOverlayDispatcher.registeredProviders();
        int tabX = panelX + 4;
        for (MapOverlayProvider p : providers) {
            boolean selected = p.modId().equals(selectedProviderModId);
            int tabBg = selected ? 0xFF4040C0 : 0x80404040;
            gg.fill(tabX, cursorY, tabX + TAB_WIDTH, cursorY + TAB_HEIGHT, tabBg);
            String label = p.modId();
            if (MapOverlayDispatcher.isCircuitBroken(p.modId())) {
                label += " [FUSED]";
            }
            gg.drawString(Minecraft.getInstance().font,
                Component.literal(label),
                tabX + 3, cursorY + 4, 0xFFFFFFFF);
            tabX += TAB_WIDTH + 2;
        }
        cursorY += TAB_HEIGHT + 4;

        // 选中 provider 的参数
        if (selectedProviderModId != null) {
            ProviderConfig config = KPConfig.getProviderConfig(selectedProviderModId);
            if (config != null) {
                // enabled toggle
                String enabledText = "[ " + (config.enabled() ? "X" : " ") + " ] Enabled";
                gg.drawString(Minecraft.getInstance().font,
                    Component.literal(enabledText),
                    panelX + 6, cursorY, 0xFFFFFFFF);
                cursorY += 12;

                // priority
                gg.drawString(Minecraft.getInstance().font,
                    Component.literal("Priority: " + config.priority()),
                    panelX + 6, cursorY, 0xFFCCCCCC);
                cursorY += 12;

                // lineWidthScale
                gg.drawString(Minecraft.getInstance().font,
                    Component.literal(String.format("Line Width: %.2f", config.lineWidthScale())),
                    panelX + 6, cursorY, 0xFFCCCCCC);
                cursorY += 12;

                // alphaScale
                gg.drawString(Minecraft.getInstance().font,
                    Component.literal(String.format("Alpha: %.2f", config.alphaScale())),
                    panelX + 6, cursorY, 0xFFCCCCCC);
                cursorY += 12;

                // dashed
                String dashedText = "[ " + (config.dashed() ? "X" : " ") + " ] Dashed";
                gg.drawString(Minecraft.getInstance().font,
                    Component.literal(dashedText),
                    panelX + 6, cursorY, 0xFFFFFFFF);
                cursorY += 12;

                // hideCreateTrackMap
                boolean hideCreate = OverlayControl.isHideCreateTrackMap();
                String hideCreateText = "[ " + (hideCreate ? "X" : " ") + " ] Hide Create Track Map";
                gg.drawString(Minecraft.getInstance().font,
                    Component.literal(hideCreateText),
                    panelX + 6, cursorY, 0xFFFFFFFF);
                cursorY += 12;

                // 提示
                gg.drawString(Minecraft.getInstance().font,
                    Component.literal("Use /kp provider set for changes"),
                    panelX + 6, cursorY, 0xFF808080);
            }
        }

        // Close 提示
        gg.drawString(Minecraft.getInstance().font,
            Component.literal("Click gear to close"),
            panelX + 6, panelY + PANEL_HEIGHT - 12, 0xFF808080);
    }

    /**
     * 处理鼠标点击事件。
     *
     * <p>仅在面板可见时处理。点击面板内时消费事件，不传递给地图。
     *
     * @return true 如果事件被消费
     */
    public boolean handleMouseClick(double mouseX, double mouseY, int button) {
        if (!visible || button != 0) return false;

        // 检查是否点击在面板内
        if (mouseX >= panelX && mouseX < panelX + PANEL_WIDTH
            && mouseY >= panelY && mouseY < panelY + PANEL_HEIGHT) {

            // Provider Tab 点击
            int tabY = panelY + 18; // 标题下方
            if (mouseY >= tabY && mouseY < tabY + TAB_HEIGHT) {
                List<MapOverlayProvider> providers = MapOverlayDispatcher.registeredProviders();
                int tabX = panelX + 4;
                for (MapOverlayProvider p : providers) {
                    if (mouseX >= tabX && mouseX < tabX + TAB_WIDTH) {
                        selectedProviderModId = p.modId();
                        return true;
                    }
                    tabX += TAB_WIDTH + 2;
                }
            }

            // enabled toggle 点击
            ProviderConfig config = selectedProviderModId != null
                ? KPConfig.getProviderConfig(selectedProviderModId) : null;
            if (config != null) {
                int toggleY = tabY + TAB_HEIGHT + 4;
                // enabled
                if (mouseY >= toggleY && mouseY < toggleY + 12) {
                    ProviderConfigControl.enable(selectedProviderModId);
                    if (config.enabled()) ProviderConfigControl.disable(selectedProviderModId);
                    return true;
                }
                toggleY += 36; // 跳过 priority + lineWidth + alpha
                // dashed
                if (mouseY >= toggleY && mouseY < toggleY + 12) {
                    ProviderConfigControl.setParam(selectedProviderModId, "dashed",
                        String.valueOf(!config.dashed()));
                    return true;
                }
                toggleY += 12;
                // hideCreateTrackMap
                if (mouseY >= toggleY && mouseY < toggleY + 12) {
                    OverlayControl.setHideCreateTrackMap(!OverlayControl.isHideCreateTrackMap());
                    return true;
                }
            }

            // 面板内其他区域：消费但不做操作
            return true;
        }

        return false;
    }
}

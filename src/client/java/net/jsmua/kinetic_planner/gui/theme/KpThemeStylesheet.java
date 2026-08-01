package net.jsmua.kinetic_planner.gui.theme;

import com.lowdragmc.lowdraglib2.gui.ui.style.Stylesheet;
import com.lowdragmc.lowdraglib2.gui.ui.style.StylesheetManager;
import net.minecraft.resources.ResourceLocation;

/**
 * 程序化生成需要 token 插值的 LSS 样式表，并注册到 StylesheetManager。
 *
 * <p>分工：
 * <ul>
 *   <li>本类：所有需要 {@code #%08X} 色值插值的规则</li>
 *   <li>资源包 {@code assets/kinetic_planner/lss/*.lss}：无需插值的纯结构/状态规则</li>
 * </ul>
 *
 * <p>调用 {@link #register()} 在客户端 setup 阶段注册为 builtin stylesheet。
 */
public final class KpThemeStylesheet {

    private static final ResourceLocation THEME_RL =
        ResourceLocation.fromNamespaceAndPath("kinetic_planner", "theme_colors");

    private KpThemeStylesheet() {}

    /**
     * 生成含 token 插值的主题 LSS 样式表（颜色规则）。
     *
     * <p>颜色值通过 {@code String.formatted} 以 {@code #%08X} 插值 KpTheme 常量。
     * 结构/状态规则由资源包 {@code assets/kinetic_planner/lss/*.lss} 提供。
     */
    public static Stylesheet create() {
        String lss = """
            // ===== Ribbon 颜色规则 =====

            .kp-ribbon-bar {
                background: rect(#%08X);
            }
            .kp-ribbon-tab {
                base-background: rect(#%08X);
                hover-background: rect(#%08X);
                pressed-background: rect(#%08X);
                color: #%08X;
            }
            .kp-ribbon-tab.__selected__ {
                base-background: rect(#%08X);
                color: #%08X;
            }
            .kp-ribbon-tab.__hovered__ {
                color: #%08X;
            }
            .kp-ribbon-tool {
                base-background: rect(#%08X);
                hover-background: rect(#%08X);
                pressed-background: rect(#%08X);
                color: #%08X;
            }
            .kp-ribbon-tool.__on__ {
                base-background: rect(#%08X);
                color: #%08X;
            }
            .kp-ribbon-tool-label {
                color: #%08X;
            }
            .kp-ribbon-group-label {
                color: #%08X;
            }
            .kp-ribbon-qat-empty {
                color: #%08X;
            }
            .kp-ribbon-separator {
                background: rect(#%08X);
            }

            // ===== ToolPanel 颜色规则 =====

            .kp-tool-panel {
                background: rect(#%08X);
            }
            .kp-tool-button {
                base-background: rect(#%08X);
                hover-background: rect(#%08X);
                pressed-background: rect(#%08X);
                color: #%08X;
            }

            // ===== 配置面板颜色规则 =====

            .kp-panel {
                background: sdf(#%08X, 4, 1, #%08X);
            }
            .kp-panel label {
                color: #%08X;
            }
            .kp-title-row {
                background: sdf(#%08X, 2, 1, #%08X);
            }
            .kp-config-row:hover {
                background: rect(#%08X);
            }
            .kp-panel button {
                base-background: rect(#%08X);
                hover-background: rect(#%08X);
                pressed-background: rect(#%08X);
                color: #%08X;
            }
            .kp-panel button:disabled {
                color: #%08X;
            }
            .kp-panel .__toggle_button__ {
                base-background: rect(#%08X);
                hover-background: rect(#%08X);
                pressed-background: rect(#%08X);
            }
            .kp-panel tab {
                base-background: rect(#%08X);
                hover-background: rect(#%08X);
                pressed-background: rect(#%08X);
                color: #%08X;
            }
            .kp-panel tab.__selected__ {
                pressed-background: rect(#%08X);
                color: #%08X;
            }
            .kp-tab-fused {
                color: #%08X;
            }
            .kp-panel tab.__selected__.kp-tab-fused {
                color: #%08X;
            }
            .kp-text-secondary {
                color: #%08X;
            }
            .kp-title {
                color: #%08X;
            }
            """.formatted(
                // ribbon bar
                KpTheme.PANEL_BG,
                // ribbon tab
                KpTheme.TRANSPARENT, KpTheme.TAB_HOVER_BG, KpTheme.ACCENT, KpTheme.TEXT_SECONDARY,
                // ribbon tab selected
                KpTheme.ACCENT, KpTheme.TEXT_PRIMARY,
                // ribbon tab hovered
                KpTheme.TEXT_PRIMARY,
                // ribbon tool
                KpTheme.BUTTON_BG, KpTheme.BUTTON_HOVER, KpTheme.ACCENT_DIM, KpTheme.TEXT_PRIMARY,
                // ribbon tool on
                KpTheme.ACCENT, KpTheme.TEXT_PRIMARY,
                // ribbon tool label
                KpTheme.TEXT_PRIMARY,
                // ribbon group label
                KpTheme.TEXT_SECONDARY,
                // ribbon qat empty
                KpTheme.SEPARATOR,
                // ribbon separator
                KpTheme.SEPARATOR,
                // tool panel
                KpTheme.PANEL_BG,
                // tool button
                KpTheme.BUTTON_BG, KpTheme.BUTTON_HOVER, KpTheme.ACCENT_DIM, KpTheme.TEXT_PRIMARY,
                // config panel
                KpTheme.PANEL_BG_TRANS, KpTheme.PANEL_BORDER,
                // config panel label
                KpTheme.TEXT_PRIMARY,
                // config title row
                KpTheme.PANEL_BG_TRANS, KpTheme.PANEL_HIGHLIGHT,
                // config row hover
                KpTheme.ROW_HOVER,
                // config panel button
                KpTheme.BUTTON_BG, KpTheme.BUTTON_HOVER, KpTheme.BUTTON_PRESSED, KpTheme.TEXT_PRIMARY,
                // config panel button disabled
                KpTheme.TEXT_SECONDARY,
                // config toggle button
                KpTheme.BUTTON_BG, KpTheme.BUTTON_HOVER, KpTheme.BUTTON_PRESSED,
                // config tab
                KpTheme.TRANSPARENT, KpTheme.ROW_HOVER, KpTheme.ACCENT, KpTheme.TEXT_SECONDARY,
                // config tab selected
                KpTheme.ACCENT, KpTheme.TEXT_PRIMARY,
                // tab fused
                KpTheme.DANGER,
                // tab selected fused
                KpTheme.DANGER,
                // text secondary
                KpTheme.TEXT_SECONDARY,
                // title
                KpTheme.TEXT_PRIMARY
            );
        return Stylesheet.parse(lss);
    }

    /**
     * 注册为 builtin stylesheet。
     *
     * <p>在客户端 setup 阶段调用一次。StylesheetManager 在资源包 reload 时
     * 会保留此注册，并通知 StyleEngine 重新匹配。
     */
    public static void register() {
        StylesheetManager.INSTANCE.registerBuiltinStylesheet(THEME_RL, create());
    }
}

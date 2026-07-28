package net.jsmua.kinetic_planner.gui.config;

import com.lowdragmc.lowdraglib2.gui.ui.style.Stylesheet;

/**
 * KP 主题 LSS 样式表定义。
 *
 * <p>spec §3.2 + §9.5：定义 KP 紫色 accent 主题，集中管理配色与控件样式。
 *
 * <p><b>LSS 能力修正</b>（见 {@code 2026-07-27-ldlib2-api-verification.md} §3）：
 * <ul>
 *   <li>LSS <b>不支持</b> CSS 变量（{@code var(--x)}），改用 Java 常量 + LSS 字符串插值</li>
 *   <li>LSS <b>支持</b> {@code :hover} 伪类（框架自动管理 {@code __hovered__} 类）</li>
 *   <li>边框用 {@code background: sdf(#color, radius, borderWidth, #borderColor)} 而非 {@code border-bottom}</li>
 *   <li>纯色背景用 {@code background: rect(#color)} 而非 {@code background-color}</li>
 *   <li>Button / Toggle / Tab 使用 {@code base-background}、{@code hover-background}、{@code pressed-background}</li>
 * </ul>
 *
 * <p>颜色 token（spec §3.1）作为 Java 常量暴露，供 {@link KpConfigUIFactory} 内联样式使用。
 */
public final class KpStylesheet {

    // ===== Design Tokens（spec §3.1） =====

    /** 面板底色：95% 不透明深灰 */
    public static final int PANEL_BG = 0xF228282C;
    /** 面板边框：黑色 */
    public static final int PANEL_BORDER = 0xFF000000;
    /** 面板顶部高光：40% 白 */
    public static final int PANEL_HIGHLIGHT = 0x40FFFFFF;
    /** KP 紫 accent：选中 Tab、勾选态、控件 hover */
    public static final int KP_ACCENT = 0xFF7C57D4;
    /** Create 米白：标题、label */
    public static final int TEXT_PRIMARY = 0xFFF3EFE0;
    /** 次级色：提示、单位 */
    public static final int TEXT_SECONDARY = 0xFF8B8B93;
    /** Create 橙：[FUSED] 熔断标记 */
    public static final int KP_DANGER = 0xFFFFAD60;
    /** 行 hover 高亮：8% 白 */
    public static final int ROW_HOVER = 0x14FFFFFF;

    /** 按钮默认背景：50% 不透明深灰 */
    public static final int BUTTON_BG = 0x803D3D42;
    /** 按钮 hover 背景：稍亮 */
    public static final int BUTTON_HOVER_BG = 0xB04A4A52;
    /** 按钮按下背景：accent 半透明 */
    public static final int BUTTON_PRESSED_BG = 0x807C57D4;
    /** 面板内透明白 */
    public static final int TEXTURE_NONE = 0x00000000;

    private KpStylesheet() {}

    /**
     * 构建 KP 主题 LSS 样式表。
     *
     * <p>用 {@code String#formatted} 插入 Java 颜色常量（LSS 不支持 CSS 变量）。
     *
     * @return 已解析的 {@link Stylesheet}，可传给 {@code UI.of(root, stylesheet)}
     */
    public static Stylesheet create() {
        String lss = """
            // KP 主题样式表（spec §3.2）
            // 颜色值通过 Java 常量插值，LSS 不支持 CSS 变量

            // 面板根容器：圆角 + 1px 黑边
            .kp-panel {
                background: sdf(#%08X, 4, 1, #%08X);
                padding-all: 8;
                gap-all: 4;
            }

            // 面板内所有 label 默认主色
            .kp-panel label {
                color: #%08X;
            }

            // 标题栏：1px 全边框 + 高光色
            .kp-title-row {
                padding-bottom: 4;
                margin-bottom: 2;
                background: sdf(#%08X, 2, 1, #%08X);
            }

            // 配置行：hover 时 8%% 白底高亮
            .kp-config-row {
                padding-horizontal: 4;
                padding-vertical: 2;
            }
            .kp-config-row:hover {
                background: rect(#%08X);
            }

            // 步进器容器：保证右侧对齐
            .kp-stepper {
                align-items: center;
                justify-content: flex-end;
            }

            // ===== 按钮通用样式 =====
            .kp-panel button {
                base-background: rect(#%08X);
                hover-background: rect(#%08X);
                pressed-background: rect(#%08X);
                color: #%08X;
                padding-horizontal: 0;
                padding-vertical: 0;
            }
            .kp-panel button:disabled {
                color: #%08X;
            }

            // ===== Toggle 内部按钮 =====
            .kp-panel .__toggle_button__ {
                base-background: rect(#%08X);
                hover-background: rect(#%08X);
                pressed-background: rect(#%08X);
            }

            // ===== Tab 样式 =====
            .kp-panel tab {
                base-background: rect(#%08X);
                hover-background: rect(#%08X);
                pressed-background: rect(#%08X);
                color: #%08X;
            }
            // 选中态（LDLib2 内部类名 __selected__，选中时按下背景生效）
            .kp-panel tab.__selected__ {
                pressed-background: rect(#%08X);
                color: #%08X;
            }

            // 熔断 Tab：danger 色（含选中态）
            .kp-tab-fused {
                color: #%08X;
            }
            .kp-panel tab.__selected__.kp-tab-fused {
                color: #%08X;
            }

            // 次级文本
            .kp-text-secondary {
                color: #%08X;
            }

            // 标题文本
            .kp-title {
                color: #%08X;
            }
            """.formatted(
                PANEL_BG, PANEL_BORDER,
                TEXT_PRIMARY,
                PANEL_BG, PANEL_HIGHLIGHT,
                ROW_HOVER,
                // button
                BUTTON_BG, BUTTON_HOVER_BG, BUTTON_PRESSED_BG, TEXT_PRIMARY, TEXT_SECONDARY,
                // toggle button
                BUTTON_BG, BUTTON_HOVER_BG, BUTTON_PRESSED_BG,
                // tab
                TEXTURE_NONE, ROW_HOVER, KP_ACCENT, TEXT_SECONDARY,
                KP_ACCENT, TEXT_PRIMARY,
                // fused / secondary / title
                KP_DANGER, KP_DANGER,
                TEXT_SECONDARY,
                TEXT_PRIMARY
            );
        return Stylesheet.parse(lss);
    }
}

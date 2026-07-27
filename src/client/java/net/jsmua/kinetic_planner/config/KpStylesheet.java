package net.jsmua.kinetic_planner.config;

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
    /** KP 紫 accent：选中 Tab 下划线、勾选态、控件 hover */
    public static final int KP_ACCENT = 0xFF7C57D4;
    /** Create 米白：标题、label */
    public static final int TEXT_PRIMARY = 0xFFF3EFE0;
    /** 次级色：提示、单位 */
    public static final int TEXT_SECONDARY = 0xFF8B8B93;
    /** Create 橙：[FUSED] 熔断标记 */
    public static final int KP_DANGER = 0xFFFFAD60;
    /** 行 hover 高亮：8% 白 */
    public static final int ROW_HOVER = 0x14FFFFFF;

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

            // 面板根容器：圆角 + 1px 黑边 + SDF 阴影
            .kp-panel {
                background: sdf(#%08X, 4, 1, #%08X);
                padding-all: 8;
                gap-all: 4;
            }

            // 标题栏：底部 1px 高光分隔线
            .kp-title-row {
                padding-bottom: 4;
                border-bottom: 0;  // LSS 不支持 border-bottom，用 background 模拟
                background: sdf(#%08X, 0, 0, #%08X);
            }

            // 配置行：hover 时 8%% 白底高亮（:hover 由框架自动管理）
            .kp-config-row {
                padding-horizontal: 4;
                padding-vertical: 2;
            }
            .kp-config-row:hover {
                background: rect(#%08X);
            }

            // 熔断 Tab：danger 色
            .kp-tab-fused {
                color: #%08X;
            }

            // 选中 Tab：底部 2px KP 紫（通过 ColorBorderTexture inset 实现，运行时设置）
            .kp-tab-selected {
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
                PANEL_BG, PANEL_HIGHLIGHT,
                ROW_HOVER,
                KP_DANGER,
                KP_ACCENT,
                TEXT_SECONDARY,
                TEXT_PRIMARY
            );
        return Stylesheet.parse(lss);
    }
}

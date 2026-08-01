package net.jsmua.kinetic_planner.gui.theme;

/**
 * KP 设计 token 常量。所有 LSS 颜色/间距的唯一来源。
 *
 * <p>供 {@link KpThemeStylesheet} 通过 {@code String.formatted} 插值进 LSS 字符串。
 * 禁止在组件 Java 类或 LSS 文件中硬编码色值。
 *
 * <p>放在 client sourceSet：颜色/间距是 GUI 渲染关注点。
 */
public final class KpTheme {
    private KpTheme() {}

    // ===== Background =====
    public static final int PANEL_BG          = 0xFF2C2C34;
    public static final int PANEL_BG_TRANS    = 0xF228282C;
    public static final int TOOLTIP_BG        = 0xE61E1F22;

    // ===== Border / Highlight =====
    public static final int PANEL_BORDER      = 0xFF000000;
    public static final int PANEL_HIGHLIGHT   = 0x40FFFFFF;
    public static final int SEPARATOR         = 0xFF4B5563;

    // ===== Accent =====
    public static final int ACCENT            = 0xFF7C57D4;
    public static final int ACCENT_DIM        = 0x807C57D4;
    public static final int DANGER            = 0xFFFFAD60;

    // ===== Text =====
    public static final int TEXT_PRIMARY      = 0xFFF3EFE0;
    public static final int TEXT_SECONDARY    = 0xFF9CA3AF;
    public static final int TEXT_MUTED        = 0xFF8B8B93;

    // ===== Button =====
    public static final int BUTTON_BG         = 0x803D3D42;
    public static final int BUTTON_HOVER      = 0xB04A4A52;
    public static final int BUTTON_PRESSED    = 0x807C57D4;

    // ===== Row hover =====
    public static final int ROW_HOVER         = 0x14FFFFFF;

    // ===== Transparent =====
    public static final int TRANSPARENT       = 0x00000000;

    // ===== Tab hover =====
    public static final int TAB_HOVER_BG      = 0x14555560;

    // ===== Icon button (self-drawn POJO) =====
    public static final int ICON_BUTTON_BG    = 0x80000000;
    public static final int ICON_BUTTON_HOVER = 0xB0404040;
    public static final int ICON_COLOR        = 0xFFFFFFFF;

    // ===== Spacing =====
    public static final int GAP_SM     = 2;
    public static final int PADDING_SM = 2;
    public static final int PADDING_MD = 4;
    public static final int PADDING_LG = 8;
}

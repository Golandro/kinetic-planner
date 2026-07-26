package net.jsmua.kinetic_planner.config;

import net.jsmua.kinetic_planner.data.ProviderConfig;
import net.jsmua.kinetic_planner.data.ProviderConfigRegistry;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Kinetic Planner 客户端配置（TOML）。
 *
 * <p>配置段：overlay / theme / layers / label / debug / provider.&lt;modId&gt;
 */
public class KPConfig {

    public static final ModConfigSpec SPEC;

    // [overlay]
    public static final ModConfigSpec.BooleanValue OVERLAY_ENABLED;
    public static final ModConfigSpec.BooleanValue SHOW_CREATE_TRACK_MAP;

    // [theme]
    public static final ModConfigSpec.ConfigValue<String> THEME_ACTIVE;
    public static final ModConfigSpec.BooleanValue THEME_CONSTANT_SCREEN_LINE_WIDTH;
    public static final ModConfigSpec.DoubleValue THEME_FIXED_SCREEN_LINE_WIDTH_PX;
    public static final ModConfigSpec.DoubleValue THEME_MIN_ZOOM;
    public static final ModConfigSpec.DoubleValue THEME_MAX_ZOOM;

    // [layers]
    public static final ModConfigSpec.BooleanValue LAYERS_TRACKS;
    public static final ModConfigSpec.BooleanValue LAYERS_NODES;
    public static final ModConfigSpec.BooleanValue LAYERS_EDGE_POINTS;

    // [label]
    public static final ModConfigSpec.BooleanValue LABEL_SHOW_NODE_LABELS;
    public static final ModConfigSpec.BooleanValue LABEL_SHOW_STATION_NAMES;
    public static final ModConfigSpec.DoubleValue LABEL_MIN_ZOOM;

    // [debug]
    public static final ModConfigSpec.BooleanValue DEBUG_SHOW_FPS;
    public static final ModConfigSpec.BooleanValue DEBUG_SHOW_GEOMETRY_COUNT;
    public static final ModConfigSpec.BooleanValue DEBUG_DISABLE_GL_STATE_GUARD;

    // [provider.xaeroworldmap]
    public static final ModConfigSpec.BooleanValue PROVIDER_XAERO_ENABLED;
    public static final ModConfigSpec.IntValue PROVIDER_XAERO_PRIORITY;
    public static final ModConfigSpec.DoubleValue PROVIDER_XAERO_LINE_WIDTH_SCALE;
    public static final ModConfigSpec.DoubleValue PROVIDER_XAERO_ALPHA_SCALE;
    public static final ModConfigSpec.BooleanValue PROVIDER_XAERO_DASHED;

    // [provider.journeymap]
    public static final ModConfigSpec.BooleanValue PROVIDER_JM_ENABLED;
    public static final ModConfigSpec.IntValue PROVIDER_JM_PRIORITY;
    public static final ModConfigSpec.DoubleValue PROVIDER_JM_LINE_WIDTH_SCALE;
    public static final ModConfigSpec.DoubleValue PROVIDER_JM_ALPHA_SCALE;
    public static final ModConfigSpec.BooleanValue PROVIDER_JM_DASHED;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.push("overlay");
        OVERLAY_ENABLED = builder.define("enabled", true);
        SHOW_CREATE_TRACK_MAP = builder.define("showCreateTrackMap", false);
        builder.pop();

        builder.push("theme");
        THEME_ACTIVE = builder.define("activeTheme", "default");
        THEME_CONSTANT_SCREEN_LINE_WIDTH = builder.define("constantScreenLineWidth", true);
        THEME_FIXED_SCREEN_LINE_WIDTH_PX = builder.defineInRange("fixedScreenLineWidthPx", 2.0, 0.1, 20.0);
        THEME_MIN_ZOOM = builder.defineInRange("minZoomBlocksPerPixel", 0.05, 0.001, 100.0);
        THEME_MAX_ZOOM = builder.defineInRange("maxZoomBlocksPerPixel", 5.0, 0.001, 1000.0);
        builder.pop();

        builder.push("layers");
        LAYERS_TRACKS = builder.define("tracks", true);
        LAYERS_NODES = builder.define("nodes", true);
        LAYERS_EDGE_POINTS = builder.define("edgePoints", true);
        builder.pop();

        builder.push("label");
        LABEL_SHOW_NODE_LABELS = builder.define("showNodeLabels", false);
        LABEL_SHOW_STATION_NAMES = builder.define("showStationNames", true);
        LABEL_MIN_ZOOM = builder.defineInRange("labelMinZoomBlocksPerPixel", 1.0, 0.001, 100.0);
        builder.pop();

        // [provider.xaeroworldmap]
        builder.push("provider").push("xaeroworldmap");
        PROVIDER_XAERO_ENABLED = builder.define("enabled", true);
        PROVIDER_XAERO_PRIORITY = builder.defineInRange("priority", 0, 0, 100);
        PROVIDER_XAERO_LINE_WIDTH_SCALE = builder.defineInRange("lineWidthScale", 1.0, 0.1, 10.0);
        PROVIDER_XAERO_ALPHA_SCALE = builder.defineInRange("alphaScale", 1.0, 0.0, 1.0);
        PROVIDER_XAERO_DASHED = builder.define("dashed", false);
        builder.pop().pop();

        // [provider.journeymap]
        builder.push("provider").push("journeymap");
        PROVIDER_JM_ENABLED = builder.define("enabled", true);
        PROVIDER_JM_PRIORITY = builder.defineInRange("priority", 1, 0, 100);
        PROVIDER_JM_LINE_WIDTH_SCALE = builder.defineInRange("lineWidthScale", 1.0, 0.1, 10.0);
        PROVIDER_JM_ALPHA_SCALE = builder.defineInRange("alphaScale", 1.0, 0.0, 1.0);
        PROVIDER_JM_DASHED = builder.define("dashed", false);
        builder.pop().pop();

        builder.push("debug");
        DEBUG_SHOW_FPS = builder.define("showFps", false);
        DEBUG_SHOW_GEOMETRY_COUNT = builder.define("showGeometryCount", false);
        DEBUG_DISABLE_GL_STATE_GUARD = builder.define("disableGlStateGuard", false);
        builder.pop();

        SPEC = builder.build();
    }

    /**
     * 从配置值构建 Theme record。
     */
    public static net.jsmua.kinetic_planner.cadengine.Theme toTheme() {
        return new net.jsmua.kinetic_planner.cadengine.Theme(
            THEME_ACTIVE.get(),
            // 轨道宽度使用世界单位默认值；恒定屏幕像素线宽由 GlobalStyle.fixedScreenLineWidthPx 承载
            net.jsmua.kinetic_planner.cadengine.Theme.defaultValue().track(),
            new net.jsmua.kinetic_planner.cadengine.Theme.GeometryStyle(4.0f, false, 1.0f),
            new net.jsmua.kinetic_planner.cadengine.Theme.GeometryStyle(3.0f, false, 1.0f),
            new net.jsmua.kinetic_planner.cadengine.Theme.LayerVisibility(
                LAYERS_TRACKS.get(), LAYERS_NODES.get(), LAYERS_EDGE_POINTS.get()),
            new net.jsmua.kinetic_planner.cadengine.Theme.GlobalStyle(
                THEME_MIN_ZOOM.get().floatValue(),
                THEME_MAX_ZOOM.get().floatValue(),
                THEME_CONSTANT_SCREEN_LINE_WIDTH.get(),
                THEME_FIXED_SCREEN_LINE_WIDTH_PX.get().floatValue())
        );
    }

    /**
     * 查询指定 provider 的配置（合并 TOML 值与默认值）。
     *
     * <p>已知 modId（xaeroworldmap / journeymap）读 TOML 段；
     * 未知 modId 读 {@link ProviderConfigRegistry} 默认值。
     *
     * @param modId provider mod ID
     * @return 配置；未知 modId 且未注册返回 null
     */
    public static ProviderConfig getProviderConfig(String modId) {
        ProviderConfig defaultConfig = ProviderConfigRegistry.getDefault(modId);
        String displayName = defaultConfig != null ? defaultConfig.displayName() : modId;

        return switch (modId) {
            case "xaeroworldmap" -> new ProviderConfig(
                modId, displayName,
                PROVIDER_XAERO_ENABLED.get(),
                PROVIDER_XAERO_PRIORITY.get(),
                PROVIDER_XAERO_LINE_WIDTH_SCALE.get().floatValue(),
                PROVIDER_XAERO_ALPHA_SCALE.get().floatValue(),
                PROVIDER_XAERO_DASHED.get());
            case "journeymap" -> new ProviderConfig(
                modId, displayName,
                PROVIDER_JM_ENABLED.get(),
                PROVIDER_JM_PRIORITY.get(),
                PROVIDER_JM_LINE_WIDTH_SCALE.get().floatValue(),
                PROVIDER_JM_ALPHA_SCALE.get().floatValue(),
                PROVIDER_JM_DASHED.get());
            default -> defaultConfig; // 未知 modId 返回注册表默认值（可能为 null）
        };
    }

    /**
     * 设置 provider 的 enabled 状态。
     *
     * @param modId   provider mod ID
     * @param enabled 是否启用
     * @return true 如果设置成功（modId 已知）
     */
    public static boolean setProviderEnabled(String modId, boolean enabled) {
        return switch (modId) {
            case "xaeroworldmap" -> { PROVIDER_XAERO_ENABLED.set(enabled); yield true; }
            case "journeymap" -> { PROVIDER_JM_ENABLED.set(enabled); yield true; }
            default -> false;
        };
    }

    /**
     * 设置 provider 的视觉参数。
     *
     * @param modId  provider mod ID
     * @param param  参数名（lineWidthScale / alphaScale / dashed / priority）
     * @param value  字符串形式的新值
     * @return true 如果设置成功（modId 已知 + 参数名合法 + 值合法）
     */
    public static boolean setProviderParam(String modId, String param, String value) {
        try {
            return switch (modId) {
                case "xaeroworldmap" -> setXaeroParam(param, value);
                case "journeymap" -> setJmParam(param, value);
                default -> false;
            };
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static boolean setXaeroParam(String param, String value) {
        return switch (param) {
            case "lineWidthScale" -> { PROVIDER_XAERO_LINE_WIDTH_SCALE.set(Double.parseDouble(value)); yield true; }
            case "alphaScale" -> { PROVIDER_XAERO_ALPHA_SCALE.set(Double.parseDouble(value)); yield true; }
            case "dashed" -> {
                if (!isStrictBool(value)) yield false;
                PROVIDER_XAERO_DASHED.set(Boolean.parseBoolean(value)); yield true;
            }
            case "priority" -> { PROVIDER_XAERO_PRIORITY.set(Integer.parseInt(value)); yield true; }
            default -> false;
        };
    }

    private static boolean setJmParam(String param, String value) {
        return switch (param) {
            case "lineWidthScale" -> { PROVIDER_JM_LINE_WIDTH_SCALE.set(Double.parseDouble(value)); yield true; }
            case "alphaScale" -> { PROVIDER_JM_ALPHA_SCALE.set(Double.parseDouble(value)); yield true; }
            case "dashed" -> {
                if (!isStrictBool(value)) yield false;
                PROVIDER_JM_DASHED.set(Boolean.parseBoolean(value)); yield true;
            }
            case "priority" -> { PROVIDER_JM_PRIORITY.set(Integer.parseInt(value)); yield true; }
            default -> false;
        };
    }

    /**
     * 严格布尔解析：仅接受 {@code "true"}/{@code "false"}（忽略大小写）。
     *
     * <p>用于 {@code dashed} 等布尔参数，避免 {@link Boolean#parseBoolean} 将任意非法字符串静默当作 false。
     *
     * @param value 待解析字符串
     * @return true 如果值为严格布尔字面量
     */
    private static boolean isStrictBool(String value) {
        return "true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value);
    }
}

package net.jsmua.kinetic_planner.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Kinetic Planner 客户端配置（TOML）。
 *
 * <p>5 个配置段：overlay / theme / layers / label / debug
 */
public class KPConfig {

    public static final ModConfigSpec SPEC;

    // [overlay]
    public static final ModConfigSpec.BooleanValue OVERLAY_ENABLED;

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

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.push("overlay");
        OVERLAY_ENABLED = builder.define("enabled", true);
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
            new net.jsmua.kinetic_planner.cadengine.Theme.GeometryStyle(
                THEME_FIXED_SCREEN_LINE_WIDTH_PX.get().floatValue(), false, 1.0f),
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
}

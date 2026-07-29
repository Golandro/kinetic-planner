package net.jsmua.kinetic_planner.config;

import net.jsmua.kinetic_planner.cadengine.Theme;
import net.jsmua.kinetic_planner.data.ProviderConfig;
import net.jsmua.kinetic_planner.data.ProviderConfigBinding;
import net.jsmua.kinetic_planner.data.ProviderConfigRegistry;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Kinetic Planner 客户端配置（TOML）。
 *
 * <p>配置段：overlay / theme / layers / label / debug / provider.&lt;modId&gt;
 *
 * <p>实现 {@link IKPConfig} 中介接口，所有外部访问通过 {@link #getInstance()} 获取实例，
 * 不直接接触 {@link ModConfigSpec} 字段。{@link #SPEC} 保留 public static 供 NeoForge 注册。
 */
public final class KPConfig implements IKPConfig {

    private static final KPConfig INSTANCE = new KPConfig();

    /** 按 modId 索引的 per-provider 配置 binding。替代 switch 语句。 / Per-provider config bindings, keyed by modId. Replaces switch statements. */
    private static final java.util.Map<String, ProviderConfigBinding> BINDINGS = new java.util.LinkedHashMap<>();

    public static final ModConfigSpec SPEC;

    // [overlay]
    private static final ModConfigSpec.BooleanValue OVERLAY_ENABLED;
    private static final ModConfigSpec.BooleanValue SHOW_CREATE_TRACK_MAP;

    // [theme]
    private static final ModConfigSpec.ConfigValue<String> THEME_ACTIVE;
    private static final ModConfigSpec.BooleanValue THEME_CONSTANT_SCREEN_LINE_WIDTH;
    private static final ModConfigSpec.DoubleValue THEME_FIXED_SCREEN_LINE_WIDTH_PX;
    private static final ModConfigSpec.DoubleValue THEME_MIN_ZOOM;
    private static final ModConfigSpec.DoubleValue THEME_MAX_ZOOM;

    // [layers]
    private static final ModConfigSpec.BooleanValue LAYERS_TRACKS;
    private static final ModConfigSpec.BooleanValue LAYERS_NODES;
    private static final ModConfigSpec.BooleanValue LAYERS_EDGE_POINTS;

    // [label]
    private static final ModConfigSpec.BooleanValue LABEL_SHOW_NODE_LABELS;
    private static final ModConfigSpec.BooleanValue LABEL_SHOW_STATION_NAMES;
    private static final ModConfigSpec.DoubleValue LABEL_MIN_ZOOM;

    // [debug]
    private static final ModConfigSpec.BooleanValue DEBUG_SHOW_FPS;
    private static final ModConfigSpec.BooleanValue DEBUG_SHOW_GEOMETRY_COUNT;
    private static final ModConfigSpec.BooleanValue DEBUG_DISABLE_GL_STATE_GUARD;

    // [provider.xaeroworldmap]
    private static final ModConfigSpec.BooleanValue PROVIDER_XAERO_ENABLED;
    private static final ModConfigSpec.IntValue PROVIDER_XAERO_PRIORITY;
    private static final ModConfigSpec.DoubleValue PROVIDER_XAERO_LINE_WIDTH_SCALE;
    private static final ModConfigSpec.DoubleValue PROVIDER_XAERO_ALPHA_SCALE;
    private static final ModConfigSpec.BooleanValue PROVIDER_XAERO_DASHED;

    // [provider.journeymap]
    private static final ModConfigSpec.BooleanValue PROVIDER_JM_ENABLED;
    private static final ModConfigSpec.IntValue PROVIDER_JM_PRIORITY;
    private static final ModConfigSpec.DoubleValue PROVIDER_JM_LINE_WIDTH_SCALE;
    private static final ModConfigSpec.DoubleValue PROVIDER_JM_ALPHA_SCALE;
    private static final ModConfigSpec.BooleanValue PROVIDER_JM_DASHED;

    private KPConfig() {}

    /**
     * 返回 {@link IKPConfig} 单例实例，供外部消费者访问配置。
     */
    public static IKPConfig getInstance() {
        return INSTANCE;
    }

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

        // 注册内置 provider 的配置 binding / Register config bindings for built-in providers
        BINDINGS.put("xaeroworldmap", new ModConfigSpecConfigBinding(
            "xaeroworldmap", "Xaero's World Map",
            PROVIDER_XAERO_ENABLED,
            PROVIDER_XAERO_PRIORITY,
            PROVIDER_XAERO_LINE_WIDTH_SCALE,
            PROVIDER_XAERO_ALPHA_SCALE,
            PROVIDER_XAERO_DASHED));
        BINDINGS.put("journeymap", new ModConfigSpecConfigBinding(
            "journeymap", "JourneyMap",
            PROVIDER_JM_ENABLED,
            PROVIDER_JM_PRIORITY,
            PROVIDER_JM_LINE_WIDTH_SCALE,
            PROVIDER_JM_ALPHA_SCALE,
            PROVIDER_JM_DASHED));

        SPEC = builder.build();
    }

    // ===== IKPConfig: [overlay] =====

    @Override
    public boolean isOverlayEnabled() {
        return OVERLAY_ENABLED.get();
    }

    @Override
    public void setOverlayEnabled(boolean enabled) {
        OVERLAY_ENABLED.set(enabled);
    }

    @Override
    public boolean isShowCreateTrackMap() {
        return SHOW_CREATE_TRACK_MAP.get();
    }

    @Override
    public void setShowCreateTrackMap(boolean show) {
        SHOW_CREATE_TRACK_MAP.set(show);
    }

    // ===== IKPConfig: [theme] =====

    @Override
    public String getActiveThemeName() {
        return THEME_ACTIVE.get();
    }

    @Override
    public void setActiveThemeName(String name) {
        THEME_ACTIVE.set(name);
    }

    @Override
    public boolean isConstantScreenLineWidth() {
        return THEME_CONSTANT_SCREEN_LINE_WIDTH.get();
    }

    @Override
    public void setConstantScreenLineWidth(boolean value) {
        THEME_CONSTANT_SCREEN_LINE_WIDTH.set(value);
    }

    @Override
    public double getFixedScreenLineWidthPx() {
        return THEME_FIXED_SCREEN_LINE_WIDTH_PX.get();
    }

    @Override
    public void setFixedScreenLineWidthPx(double value) {
        THEME_FIXED_SCREEN_LINE_WIDTH_PX.set(value);
    }

    // ===== IKPConfig: [layers] =====

    @Override
    public boolean isLayerTracksVisible() {
        return LAYERS_TRACKS.get();
    }

    @Override
    public void setLayerTracksVisible(boolean value) {
        LAYERS_TRACKS.set(value);
    }

    @Override
    public boolean isLayerNodesVisible() {
        return LAYERS_NODES.get();
    }

    @Override
    public void setLayerNodesVisible(boolean value) {
        LAYERS_NODES.set(value);
    }

    @Override
    public boolean isLayerEdgePointsVisible() {
        return LAYERS_EDGE_POINTS.get();
    }

    @Override
    public void setLayerEdgePointsVisible(boolean value) {
        LAYERS_EDGE_POINTS.set(value);
    }

    // ===== IKPConfig: [label] =====

    @Override
    public boolean isShowNodeLabels() {
        return LABEL_SHOW_NODE_LABELS.get();
    }

    @Override
    public void setShowNodeLabels(boolean value) {
        LABEL_SHOW_NODE_LABELS.set(value);
    }

    @Override
    public boolean isShowStationNames() {
        return LABEL_SHOW_STATION_NAMES.get();
    }

    @Override
    public void setShowStationNames(boolean value) {
        LABEL_SHOW_STATION_NAMES.set(value);
    }

    // ===== IKPConfig: [provider] =====

    @Override
    public ProviderConfig getProviderConfig(String modId) {
        ProviderConfigBinding binding = BINDINGS.get(modId);
        if (binding != null) {
            return binding.read();
        }
        // 没有 TOML 条目的 provider 回退到 registry 默认值
        // Fallback to registry default for providers without TOML entries
        return ProviderConfigRegistry.getDefault(modId);
    }

    @Override
    public boolean setProviderEnabled(String modId, boolean enabled) {
        ProviderConfigBinding binding = BINDINGS.get(modId);
        if (binding != null) {
            return binding.writeEnabled(enabled);
        }
        return false;
    }

    @Override
    public boolean setProviderParam(String modId, String param, String value) {
        ProviderConfigBinding binding = BINDINGS.get(modId);
        if (binding != null) {
            return binding.writeParam(param, value);
        }
        return false;
    }

    // ===== IKPConfig: [theme conversion] =====

    @Override
    public Theme toTheme() {
        return new Theme(
            THEME_ACTIVE.get(),
            Theme.defaultValue().track(),
            new Theme.GeometryStyle(4.0f, false, 1.0f),
            new Theme.GeometryStyle(3.0f, false, 1.0f),
            new Theme.LayerVisibility(
                LAYERS_TRACKS.get(), LAYERS_NODES.get(), LAYERS_EDGE_POINTS.get()),
            new Theme.GlobalStyle(
                THEME_MIN_ZOOM.get().floatValue(),
                THEME_MAX_ZOOM.get().floatValue(),
                THEME_CONSTANT_SCREEN_LINE_WIDTH.get(),
                THEME_FIXED_SCREEN_LINE_WIDTH_PX.get().floatValue())
        );
    }

    // ===== private helpers =====
}

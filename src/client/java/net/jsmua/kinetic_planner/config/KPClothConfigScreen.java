package net.jsmua.kinetic_planner.config;

import net.jsmua.kinetic_planner.KineticPlannerMod;
import net.jsmua.kinetic_planner.instrument.WorldTreeReadOverlay;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Cloth Config 配置屏幕构建器。
 */
public final class KPClothConfigScreen {

    private KPClothConfigScreen() {}

    public static Screen build(Screen parent) {
        ConfigBuilder builder = ConfigBuilder.create()
            .setParentScreen(parent)
            .setTitle(Component.literal("Kinetic Planner Config"));

        ConfigEntryBuilder entryBuilder = builder.entryBuilder();

        // [overlay]
        ConfigCategory overlay = builder.getOrCreateCategory(Component.literal("Overlay"));
        overlay.addEntry(entryBuilder.startBooleanToggle(
                Component.literal("Enabled"), KPConfig.OVERLAY_ENABLED.get())
            .setDefaultValue(true)
            .setSaveConsumer(KPConfig.OVERLAY_ENABLED::set)
            .build());

        // [layers]
        ConfigCategory layers = builder.getOrCreateCategory(Component.literal("Layers"));
        layers.addEntry(entryBuilder.startBooleanToggle(
                Component.literal("Tracks"), KPConfig.LAYERS_TRACKS.get())
            .setDefaultValue(true)
            .setSaveConsumer(KPConfig.LAYERS_TRACKS::set)
            .build());
        layers.addEntry(entryBuilder.startBooleanToggle(
                Component.literal("Nodes"), KPConfig.LAYERS_NODES.get())
            .setDefaultValue(true)
            .setSaveConsumer(KPConfig.LAYERS_NODES::set)
            .build());
        layers.addEntry(entryBuilder.startBooleanToggle(
                Component.literal("Edge Points"), KPConfig.LAYERS_EDGE_POINTS.get())
            .setDefaultValue(true)
            .setSaveConsumer(KPConfig.LAYERS_EDGE_POINTS::set)
            .build());

        // [theme]
        ConfigCategory theme = builder.getOrCreateCategory(Component.literal("Theme"));
        theme.addEntry(entryBuilder.startStrField(
                Component.literal("Active Theme"), KPConfig.THEME_ACTIVE.get())
            .setDefaultValue("default")
            .setSaveConsumer(KPConfig.THEME_ACTIVE::set)
            .build());
        theme.addEntry(entryBuilder.startBooleanToggle(
                Component.literal("Constant Screen Line Width"),
                KPConfig.THEME_CONSTANT_SCREEN_LINE_WIDTH.get())
            .setDefaultValue(true)
            .setSaveConsumer(KPConfig.THEME_CONSTANT_SCREEN_LINE_WIDTH::set)
            .build());
        theme.addEntry(entryBuilder.startDoubleField(
                Component.literal("Fixed Line Width (px)"),
                KPConfig.THEME_FIXED_SCREEN_LINE_WIDTH_PX.get())
            .setDefaultValue(2.0)
            .setMin(0.1).setMax(20.0)
            .setSaveConsumer(KPConfig.THEME_FIXED_SCREEN_LINE_WIDTH_PX::set)
            .build());

        // [label]
        ConfigCategory label = builder.getOrCreateCategory(Component.literal("Labels"));
        label.addEntry(entryBuilder.startBooleanToggle(
                Component.literal("Show Node Labels"), KPConfig.LABEL_SHOW_NODE_LABELS.get())
            .setDefaultValue(false)
            .setSaveConsumer(KPConfig.LABEL_SHOW_NODE_LABELS::set)
            .build());
        label.addEntry(entryBuilder.startBooleanToggle(
                Component.literal("Show Station Names"), KPConfig.LABEL_SHOW_STATION_NAMES.get())
            .setDefaultValue(true)
            .setSaveConsumer(KPConfig.LABEL_SHOW_STATION_NAMES::set)
            .build());

        builder.setSavingRunnable(() -> {
            KineticPlannerMod.LOGGER.info("KP config saved, reloading theme");
            WorldTreeReadOverlay.setTheme(KPConfig.toTheme());
        });

        return builder.build();
    }
}

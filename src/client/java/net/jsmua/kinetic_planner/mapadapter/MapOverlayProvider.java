package net.jsmua.kinetic_planner.mapadapter;

import net.minecraft.client.gui.screens.Screen;

import javax.annotation.Nullable;

public interface MapOverlayProvider {
    boolean isMapOpen(Screen screen);
    @Nullable MapOverlayContext captureContext(Screen screen);
    String modId();
}

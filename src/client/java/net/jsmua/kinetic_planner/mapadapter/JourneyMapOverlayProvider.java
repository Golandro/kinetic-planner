package net.jsmua.kinetic_planner.mapadapter;

import net.minecraft.client.gui.screens.Screen;
import javax.annotation.Nullable;

public class JourneyMapOverlayProvider implements MapOverlayProvider {
    @Override
    public boolean isMapOpen(Screen screen) { return false; }

    @Override
    @Nullable
    public MapOverlayContext captureContext(Screen screen) { return null; }

    @Override
    public String modId() { return "journeymap"; }
}

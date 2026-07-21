package net.jsmua.kinetic_planner.mapadapter;

import net.jsmua.kinetic_planner.KineticPlannerMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import java.util.*;

public class MapOverlayDispatcher {
    private static final List<MapOverlayProvider> PROVIDERS = new ArrayList<>();
    private static final Set<String> FAILED = new HashSet<>();
    private static MapOverlayContext currentContext;

    static {
        PROVIDERS.add(new XaeroMapOverlayProvider());
        PROVIDERS.add(new JourneyMapOverlayProvider());
    }

    public static void tick() {
        Screen screen = Minecraft.getInstance().screen;
        if (screen == null) {
            currentContext = null;
            return;
        }
        for (MapOverlayProvider p : PROVIDERS) {
            if (FAILED.contains(p.modId())) continue;
            try {
                if (p.isMapOpen(screen)) {
                    currentContext = p.captureContext(screen);
                    return;
                }
            } catch (Throwable t) {
                KineticPlannerMod.LOGGER.error("MapOverlayProvider {} failed, circuit-breaking", p.modId(), t);
                FAILED.add(p.modId());
            }
        }
        currentContext = null;
    }

    public static Optional<MapOverlayContext> currentContext() {
        return Optional.ofNullable(currentContext);
    }
}

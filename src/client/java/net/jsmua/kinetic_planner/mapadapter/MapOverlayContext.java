package net.jsmua.kinetic_planner.mapadapter;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public record MapOverlayContext(
    ResourceKey<Level> dimension,
    double cameraBlockX, double cameraBlockZ,
    double blocksPerPixel,
    int screenWidth, int screenHeight,
    int mouseX, int mouseY,
    float partialTicks,
    float dpr
) {}

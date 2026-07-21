package net.jsmua.kinetic_planner.mapadapter;

import net.jsmua.kinetic_planner.KineticPlannerMod;
import net.jsmua.kinetic_planner.mixin.XaeroMapAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import xaero.map.gui.GuiMap;

import javax.annotation.Nullable;

public class XaeroMapOverlayProvider implements MapOverlayProvider {
    @Override
    public boolean isMapOpen(Screen screen) {
        return screen instanceof GuiMap;
    }

    @Override
    @Nullable
    public MapOverlayContext captureContext(Screen screen) {
        try {
            GuiMap map = (GuiMap) screen;
            XaeroMapAccessor acc = (XaeroMapAccessor) map;
            double cameraX = acc.kp$cameraX();
            double cameraZ = acc.kp$cameraZ();
            double mapScale = acc.kp$scale();
            Minecraft mc = Minecraft.getInstance();
            int screenWidth = mc.getWindow().getScreenWidth();
            int guiScaledWidth = mc.getWindow().getGuiScaledWidth();
            float dpr = (float) screenWidth / guiScaledWidth;
            double guiScale = (double) screenWidth / mc.getWindow().getGuiScaledWidth();
            double interfaceScale = (double) mc.getWindow().getWidth() / screenWidth;
            double blocksPerPixel = guiScale * interfaceScale / mapScale;

            ResourceKey<Level> dim = Level.OVERWORLD; // TODO: 从 Xaero mapProcessor 获取当前维度
            return new MapOverlayContext(
                dim, cameraX, cameraZ, blocksPerPixel,
                mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight(),
                (int) mc.mouseHandler.xpos(), (int) mc.mouseHandler.ypos(),
                0f, dpr
            );
        } catch (Throwable t) {
            KineticPlannerMod.LOGGER.error("XaeroMapOverlayProvider.captureContext failed", t);
            return null;
        }
    }

    @Override
    public String modId() { return "xaeroworldmap"; }
}

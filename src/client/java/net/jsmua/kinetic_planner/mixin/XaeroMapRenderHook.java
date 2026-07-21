package net.jsmua.kinetic_planner.mixin;

import net.jsmua.kinetic_planner.KineticPlannerMod;
import net.jsmua.kinetic_planner.instrument.NativeLineOverlay;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xaero.map.gui.GuiMap;

@Mixin(value = GuiMap.class, remap = false)
public class XaeroMapRenderHook {
    @Inject(method = "render", at = @At("RETURN"))
    private void kp$onMapRender(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        try {
            NativeLineOverlay.onMapRender((GuiMap) (Object) this, guiGraphics, mouseX, mouseY, partialTicks);
        } catch (Throwable t) {
            KineticPlannerMod.LOGGER.error("NativeLineOverlay render failed", t);
        }
    }
}

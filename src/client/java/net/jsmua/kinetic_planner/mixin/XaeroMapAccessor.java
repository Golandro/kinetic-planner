package net.jsmua.kinetic_planner.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import xaero.map.gui.GuiMap;

@Mixin(value = GuiMap.class, remap = false)
public interface XaeroMapAccessor {
    @Accessor("cameraX")
    double kp$cameraX();
    @Accessor("cameraZ")
    double kp$cameraZ();
    @Accessor("scale")
    double kp$scale();
}

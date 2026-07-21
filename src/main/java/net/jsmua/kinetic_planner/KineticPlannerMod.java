package net.jsmua.kinetic_planner;

import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;

@Mod(KineticPlannerMod.MODID)
public class KineticPlannerMod {
    public static final String MODID = "kinetic_planner";
    public static final Logger LOGGER = LogUtils.getLogger();

    public KineticPlannerMod(IEventBus modEventBus, ModContainer modContainer) {
        LOGGER.info("Kinetic Planner loading (common)");
    }
}

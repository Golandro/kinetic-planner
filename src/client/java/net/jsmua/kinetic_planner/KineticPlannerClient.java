package net.jsmua.kinetic_planner;

import net.jsmua.kinetic_planner.instrument.NativeLineOverlay;
import net.jsmua.kinetic_planner.mapadapter.MapOverlayDispatcher;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@Mod(value = KineticPlannerMod.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = KineticPlannerMod.MODID, value = Dist.CLIENT)
public class KineticPlannerClient {
    public KineticPlannerClient(ModContainer container) {
    }

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        KineticPlannerMod.LOGGER.info("Kinetic Planner client setup");
    }

    @SubscribeEvent
    static void onClientTickPost(ClientTickEvent.Post event) {
        MapOverlayDispatcher.tick();
        NativeLineOverlay.onClientTick();
    }
}

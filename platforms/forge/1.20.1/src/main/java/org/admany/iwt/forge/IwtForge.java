package org.admany.iwt.forge;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.fml.common.Mod;
import org.admany.iwt.core.IwtAreaTemplates;
import org.admany.quantified.api.QuantifiedAPI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(IwtForge.MOD_ID)
public final class IwtForge {
    public static final String MOD_ID = "iwt";
    private static final Logger LOGGER = LoggerFactory.getLogger(IwtForge.class);
    public IwtForge() {
        MinecraftForge.EVENT_BUS.addListener(this::serverStarted);
    }

    private void serverStarted(ServerStartedEvent event) {
        QuantifiedAPI.<Void>compute(MOD_ID, "iwt-area-template-precompute")
            .key("iw-2.0.5-area-templates").background().threadSafe().cpuOnly().allowMainThreadRerouting(false)
            .parallelUnits(1).dataSizeBytes(IwtAreaTemplates.precomputeIw205Bytes())
            .work(() -> { IwtAreaTemplates.precomputeIw205(); return null; }).submit()
            .exceptionally(error -> { LOGGER.error("Failed to precompute IW area templates", error); return null; });
    }

}

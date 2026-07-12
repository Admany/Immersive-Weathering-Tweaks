package org.admany.iwt.fabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import org.admany.iwt.core.IwtAreaTemplates;
import org.admany.quantified.api.QuantifiedAPI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class IwtFabric implements ModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger(IwtFabric.class);
    @Override
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(server ->
            QuantifiedAPI.<Void>compute("iwt", "iwt-area-template-precompute")
                .key("iw-2.0.5-area-templates").background().threadSafe().cpuOnly().allowMainThreadRerouting(false)
                .parallelUnits(1).dataSizeBytes(IwtAreaTemplates.precomputeIw205Bytes())
                .work(() -> { IwtAreaTemplates.precomputeIw205(); return null; }).submit()
                .exceptionally(error -> { LOGGER.error("Failed to precompute IW area templates", error); return null; }));
    }
}

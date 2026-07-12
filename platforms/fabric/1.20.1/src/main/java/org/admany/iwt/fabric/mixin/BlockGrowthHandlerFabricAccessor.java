package org.admany.iwt.mixin.fabric;

import com.ordana.immersive_weathering.data.block_growths.TickSource;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.Map;

@Pseudo
@Mixin(targets = "com.ordana.immersive_weathering.data.block_growths.BlockGrowthHandler", remap = false)
public interface BlockGrowthHandlerFabricAccessor {
    @Accessor("GROWTH_FOR_BLOCK")
    static Map<?, ?> iwt$growthForBlock() {
        throw new AssertionError("Mixin accessor was not transformed");
    }

    @Accessor("UNIVERSAL_GROWTHS")
    static Map<?, ?> iwt$universalGrowths() {
        throw new AssertionError("Mixin accessor was not transformed");
    }

    @Invoker("tickBlock")
    static void iwt$tickBlock(TickSource source, BlockState state, ServerLevel level, BlockPos pos) {
        throw new AssertionError("Mixin invoker was not transformed");
    }
}

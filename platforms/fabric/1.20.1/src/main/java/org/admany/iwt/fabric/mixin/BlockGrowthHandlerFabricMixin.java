package org.admany.iwt.mixin.fabric;

import com.ordana.immersive_weathering.data.block_growths.TickSource;
import com.ordana.immersive_weathering.data.block_growths.IConditionalGrowingBlock;
import com.ordana.immersive_weathering.data.block_growths.growths.IBlockGrowth;
import com.ordana.immersive_weathering.configs.CommonConfigs;
import com.google.common.base.Suppliers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

@Pseudo
@Mixin(targets = "com.ordana.immersive_weathering.data.block_growths.BlockGrowthHandler", remap = false)
public abstract class BlockGrowthHandlerFabricMixin {
    @Inject(method = "tickBlock", at = @At("HEAD"), cancellable = true, remap = false)
    @SuppressWarnings("unchecked")
    private static void iwt$evaluateGrowthOnce(TickSource source, BlockState state, ServerLevel level, BlockPos pos, CallbackInfo ci) {
        if (!CommonConfigs.BLOCK_GROWTHS.get()) {
            ci.cancel();
            return;
        }
        if (state.getBlock() instanceof IConditionalGrowingBlock conditional && !conditional.canGrow(state)) {
            ci.cancel();
            return;
        }
        iwt$runGrowths(source, state, level, pos);
        ci.cancel();
    }

    @SuppressWarnings("unchecked")
    private static void iwt$runGrowths(TickSource source, BlockState state, ServerLevel level, BlockPos pos) {
        Set<IBlockGrowth> universal = (Set<IBlockGrowth>) ((Map<?, ?>) BlockGrowthHandlerFabricAccessor.iwt$universalGrowths()).get(source);
        Map<Block, Set<IBlockGrowth>> byBlock = (Map<Block, Set<IBlockGrowth>>) ((Map<?, ?>) BlockGrowthHandlerFabricAccessor.iwt$growthForBlock()).get(source);
        Set<IBlockGrowth> blockGrowths = byBlock == null ? null : byBlock.get(state.getBlock());
        if ((universal == null || universal.isEmpty()) && (blockGrowths == null || blockGrowths.isEmpty())) {
            return;
        }
        Supplier<Holder<Biome>> biome = Suppliers.memoize(() -> level.getBiome(pos));
        if (universal != null) {
            for (IBlockGrowth growth : universal) growth.tryGrowing(pos, state, level, biome);
        }
        if (blockGrowths != null) {
            for (IBlockGrowth growth : blockGrowths) growth.tryGrowing(pos, state, level, biome);
        }
    }

    @Inject(method = "performSkyAccessTick", at = @At("HEAD"), cancellable = true, remap = false)
    private static void iwt$skipImpossibleSkySamples(ServerLevel level, LevelChunk levelChunk, int randomTickSpeed, CallbackInfo ci) {
        ChunkPos chunkPos = levelChunk.getPos();
        float chance = randomTickSpeed / 48.0F;
        int minX = chunkPos.getMinBlockX();
        int minZ = chunkPos.getMinBlockZ();
        boolean raining = level.isRaining();
        do {
            if (chance > level.getRandom().nextFloat()) {
                BlockPos firstAir = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING,
                    level.getBlockRandomPos(minX, 0, minZ, 15));
                BlockPos target = firstAir.below();
                BlockState state = level.getBlockState(target);
                Block block = state.getBlock();

                if (!raining) {
                    if (iwt$hasGrowth(TickSource.CLEAR_SKY, block)) {
                        iwt$runGrowths(TickSource.CLEAR_SKY, state, level, target.immutable());
                    }
                } else if (iwt$hasGrowth(TickSource.RAIN, block) || iwt$hasGrowth(TickSource.SNOW, block)) {
                    Biome.Precipitation precipitation = level.getBiome(target).value().getPrecipitationAt(target);
                    TickSource source = precipitation == Biome.Precipitation.SNOW ? TickSource.SNOW : TickSource.RAIN;
                    if (iwt$hasGrowth(source, block)) {
                        iwt$runGrowths(source, state, level, target.immutable());
                    }
                }
            }
            chance--;
        } while (chance > 0.0F);
        ci.cancel();
    }

    @SuppressWarnings("unchecked")
    private static boolean iwt$hasGrowth(TickSource source, Block block) {
        Set<?> universal = ((Map<Object, Set<?>>) (Map<?, ?>) BlockGrowthHandlerFabricAccessor.iwt$universalGrowths()).get(source);
        if (universal != null && !universal.isEmpty()) return true;
        Map<Object, Set<?>> byBlock = ((Map<Object, Map<Object, Set<?>>>) (Map<?, ?>) BlockGrowthHandlerFabricAccessor.iwt$growthForBlock()).get(source);
        if (byBlock == null) return false;
        Set<?> growths = byBlock.get(block);
        return growths != null && !growths.isEmpty();
    }
}

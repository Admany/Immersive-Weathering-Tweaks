package org.admany.iwt.mixin.forge;

import com.ordana.immersive_weathering.data.block_growths.growths.ConfigurableBlockGrowth;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderSet;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.RuleTest;
import org.admany.iwt.core.IwtAreaTemplates;
import org.admany.iwt.forge.AreaCheckForgeScratch;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;
import java.util.Random;

@Pseudo
@Mixin(targets = "com.ordana.immersive_weathering.data.block_growths.area_condition.AreaCheck", remap = false)
public abstract class AreaCheckForgeMixin {
    @Shadow private int rX;
    @Shadow private int rY;
    @Shadow private int rZ;
    @Shadow private int requiredAmount;
    @Shadow private Optional<Integer> yOffset;
    @Shadow private Optional<RuleTest> mustHavePredicate;
    @Shadow private Optional<RuleTest> mustNotHavePredicate;
    @Shadow private Optional<HolderSet<Block>> extraIncluded;
    private static final ThreadLocal<AreaCheckForgeScratch> IWT_SCRATCH = ThreadLocal.withInitial(AreaCheckForgeScratch::new);

    @Inject(method = "test", at = @At("HEAD"), cancellable = true, remap = false)
    private void iwt$testWithoutAllocations(BlockPos originalPos, Level level, ConfigurableBlockGrowth config,
                                            CallbackInfoReturnable<Boolean> cir) {
        BlockPos pos = yOffset.map(originalPos::above).orElse(originalPos);
        AreaCheckForgeScratch scratch = IWT_SCRATCH.get();
        int size = iwt$fillOffsets(scratch, rX, rY, rZ);
        scratch.random.setSeed(Mth.getSeed(pos));
        for (int i = size; i > 1; i--) {
            int swap = scratch.random.nextInt(i);
            long value = scratch.offsets[i - 1]; scratch.offsets[i - 1] = scratch.offsets[swap]; scratch.offsets[swap] = value;
        }
        int count = 0;
        boolean hasRequirement = mustHavePredicate.isEmpty();
        RandomSource ruleRandom = RandomSource.create(Mth.getSeed(pos));
        for (int i = 0; i < size; i++) {
            long offset = scratch.offsets[i];
            scratch.cursor.set(pos.getX() + unpackX(offset), pos.getY() + unpackY(offset), pos.getZ() + unpackZ(offset));
            BlockState state = level.getBlockState(scratch.cursor);
            if (config.getPossibleBlocks().contains(state.getBlock()) || (extraIncluded.isPresent() && state.is(extraIncluded.get()))) count++;
            if (!hasRequirement && mustHavePredicate.get().test(state, ruleRandom)) {
                hasRequirement = true;
                if (requiredAmount == -1) { cir.setReturnValue(true); return; }
            } else if (mustNotHavePredicate.isPresent() && mustNotHavePredicate.get().test(state, ruleRandom)) {
                cir.setReturnValue(false); return;
            }
            if (count >= requiredAmount) { cir.setReturnValue(false); return; }
        }
        cir.setReturnValue(hasRequirement);
    }

    private static int iwt$fillOffsets(AreaCheckForgeScratch scratch, int rx, int ry, int rz) {
        IwtAreaTemplates.Template template = IwtAreaTemplates.get(rx, ry, rz);
        scratch.ensure(template.size());
        template.copyTo(scratch.offsets);
        return template.size();
    }
    private static int unpackX(long value) { return (int) (value >>> 42) - 1_048_576; }
    private static int unpackY(long value) { return (int) ((value >>> 21) & 0x1F_FFFFL) - 1_048_576; }
    private static int unpackZ(long value) { return (int) (value & 0x1F_FFFFL) - 1_048_576; }
}

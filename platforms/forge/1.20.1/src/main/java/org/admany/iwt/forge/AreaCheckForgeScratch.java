package org.admany.iwt.forge;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;

import java.util.Random;

public final class AreaCheckForgeScratch {
    public long[] offsets = new long[0];
    public final Random random = new Random();
    /** Reused with the exact position seed used by Immersive Weathering's AreaCheck. */
    public final RandomSource ruleRandom = RandomSource.create();
    public final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

    public void ensure(int length) {
        if (offsets.length < length) offsets = new long[length];
    }
}

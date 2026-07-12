package org.admany.iwt.forge;

import net.minecraft.core.BlockPos;

import java.util.Random;

public final class AreaCheckForgeScratch {
    public long[] offsets = new long[0];
    public final Random random = new Random();
    public final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

    public void ensure(int length) {
        if (offsets.length < length) offsets = new long[length];
    }
}

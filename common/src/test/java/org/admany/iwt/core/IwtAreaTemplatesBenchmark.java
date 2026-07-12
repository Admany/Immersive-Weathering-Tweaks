package org.admany.iwt.core;

import com.sun.management.ThreadMXBean;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public final class IwtAreaTemplatesBenchmark {
    private static final int RX = 4;
    private static final int RY = 3;
    private static final int RZ = 4;
    private static final int ITERATIONS = 10_000;
    private static final int LOOKUP_ITERATIONS = 1_000_000;

    private IwtAreaTemplatesBenchmark() { }

    public static void main(String[] args) {
        ThreadMXBean bean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        if (!bean.isThreadAllocatedMemorySupported()) throw new IllegalStateException("Thread allocation accounting is unavailable");
        bean.setThreadAllocatedMemoryEnabled(true);
        for (int i = 0; i < 2_000; i++) {
            nativeEquivalent(i);
            optimized(i);
        }
        IwtAreaTemplates.clearForTests();
        long lazyStart = System.nanoTime();
        long lazyAllocatedStart = bean.getThreadAllocatedBytes(Thread.currentThread().getId());
        long lazyChecksum = optimized(0);
        long lazyAllocated = bean.getThreadAllocatedBytes(Thread.currentThread().getId()) - lazyAllocatedStart;
        long lazyNanos = System.nanoTime() - lazyStart;

        Result nativeResult = measure(bean, true);
        Result optimizedResult = measure(bean, false);
        Result scratchResult = measureScratch(bean);
        Result lookupResult = measureLookup(bean);
        long retainedBytes = (long) IwtAreaTemplates.get(RX, RY, RZ).size() * Long.BYTES;
        System.out.printf("shape=%dx%dx%d positions=%d retainedBytes=%d lazyNs=%d lazyAllocated=%d lazyChecksum=%d%n",
            RX, RY, RZ, IwtAreaTemplates.get(RX, RY, RZ).size(), retainedBytes, lazyNanos, lazyAllocated, lazyChecksum);
        System.out.printf("native ns=%d allocated=%d checksum=%d%n", nativeResult.nanos, nativeResult.allocatedBytes, nativeResult.checksum);
        System.out.printf("optimized ns=%d allocated=%d checksum=%d%n", optimizedResult.nanos, optimizedResult.allocatedBytes, optimizedResult.checksum);
        System.out.printf("optimizedScratch ns=%d allocated=%d checksum=%d%n", scratchResult.nanos, scratchResult.allocatedBytes, scratchResult.checksum);
        System.out.printf("warmLookup ns=%d allocated=%d checksum=%d%n", lookupResult.nanos, lookupResult.allocatedBytes, lookupResult.checksum);
    }

    private static Result measure(ThreadMXBean bean, boolean nativePath) {
        long allocatedStart = bean.getThreadAllocatedBytes(Thread.currentThread().getId());
        long start = System.nanoTime();
        long checksum = 0;
        for (int i = 0; i < ITERATIONS; i++) checksum += nativePath ? nativeEquivalent(i) : optimized(i);
        return new Result(System.nanoTime() - start, bean.getThreadAllocatedBytes(Thread.currentThread().getId()) - allocatedStart, checksum);
    }

    private static Result measureScratch(ThreadMXBean bean) {
        long[] scratch = new long[IwtAreaTemplates.get(RX, RY, RZ).size()];
        long allocatedStart = bean.getThreadAllocatedBytes(Thread.currentThread().getId());
        long start = System.nanoTime();
        long checksum = 0;
        for (int i = 0; i < ITERATIONS; i++) checksum += optimizedScratch(i, scratch);
        return new Result(System.nanoTime() - start, bean.getThreadAllocatedBytes(Thread.currentThread().getId()) - allocatedStart, checksum);
    }

    private static Result measureLookup(ThreadMXBean bean) {
        long allocatedStart = bean.getThreadAllocatedBytes(Thread.currentThread().getId());
        long start = System.nanoTime();
        long checksum = 0;
        for (int i = 0; i < LOOKUP_ITERATIONS; i++) checksum += IwtAreaTemplates.get(RX, RY, RZ).size();
        return new Result(System.nanoTime() - start, bean.getThreadAllocatedBytes(Thread.currentThread().getId()) - allocatedStart, checksum);
    }

    private static long nativeEquivalent(int seed) {
        List<Offset> offsets = new ArrayList<>((2 * RX + 1) * (2 * RY + 1) * (2 * RZ + 1));
        for (int depth = 0, total = RX + RY + RZ; depth <= total; depth++) {
            int maxX = Math.min(RX, depth);
            for (int x = -maxX; x <= maxX; x++) {
                int maxY = Math.min(RY, depth - Math.abs(x));
                for (int y = -maxY; y <= maxY; y++) {
                    int z = depth - Math.abs(x) - Math.abs(y);
                    if (z > RZ) continue;
                    offsets.add(new Offset(x, y, z));
                    if (z != 0) offsets.add(new Offset(x, y, -z));
                }
            }
        }
        Collections.shuffle(offsets, new Random(seed));
        long checksum = 1;
        for (Offset offset : offsets) checksum = checksum * 31 + offset.x * 31L + offset.y * 17L + offset.z;
        return checksum;
    }

    private static long optimized(int seed) {
        IwtAreaTemplates.Template template = IwtAreaTemplates.get(RX, RY, RZ);
        long[] offsets = new long[template.size()];
        return optimizedScratch(seed, offsets);
    }

    private static long optimizedScratch(int seed, long[] offsets) {
        IwtAreaTemplates.Template template = IwtAreaTemplates.get(RX, RY, RZ);
        template.copyTo(offsets);
        Random random = new Random(seed);
        for (int i = offsets.length; i > 1; i--) {
            int swap = random.nextInt(i);
            long value = offsets[i - 1]; offsets[i - 1] = offsets[swap]; offsets[swap] = value;
        }
        long checksum = 1;
        for (long offset : offsets) checksum = checksum * 31 + IwtAreaTemplates.unpackX(offset) * 31L + IwtAreaTemplates.unpackY(offset) * 17L + IwtAreaTemplates.unpackZ(offset);
        return checksum;
    }

    private record Offset(int x, int y, int z) { }
    private record Result(long nanos, long allocatedBytes, long checksum) { }
}

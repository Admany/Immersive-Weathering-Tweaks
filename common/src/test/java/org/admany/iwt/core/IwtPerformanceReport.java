package org.admany.iwt.core;

import com.sun.management.ThreadMXBean;

import java.io.IOException;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public final class IwtPerformanceReport {
    private static final int[][] SHAPES = {{1,1,1},{2,2,2},{2,3,2},{2,4,2},{3,1,3},{3,2,3},{3,3,3},{4,3,4}};
    private static final int WARMUP = 2_000;
    private static final int ITERATIONS = 10_000;
    private static final int SAMPLES = 9;

    private IwtPerformanceReport() { }

    public static void main(String[] args) throws IOException {
        ThreadMXBean allocations = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        allocations.setThreadAllocatedMemoryEnabled(true);
        StringBuilder out = new StringBuilder("# IWT microbenchmark\n\n");
        out.append("Warmup: ").append(WARMUP).append(" iterations; samples: ").append(SAMPLES).append("; measured iterations/sample: ").append(ITERATIONS).append(".\n\n");
        out.append("| Shape | Positions | Native ns/op median | Fresh ns/op median | Scratch ns/op median | Native B/op | Fresh B/op | Scratch B/op | Scratch speedup | Scratch allocation reduction | Checksum |\n");
        out.append("|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|\n");
        for (int[] shape : SHAPES) {
            verify(shape[0], shape[1], shape[2]);
            for (int i = 0; i < WARMUP; i++) {
                nativePath(shape[0], shape[1], shape[2], i);
                freshPath(shape[0], shape[1], shape[2], i);
                scratchPath(shape[0], shape[1], shape[2], i, new Scratch());
            }
            Result nativeResult = measure(allocations, shape, Mode.NATIVE);
            Result freshResult = measure(allocations, shape, Mode.FRESH);
            Result scratchResult = measure(allocations, shape, Mode.SCRATCH);
            if (nativeResult.checksum != freshResult.checksum || nativeResult.checksum != scratchResult.checksum) throw new IllegalStateException("Checksum mismatch");
            double speedup = nativeResult.medianNsPerOp / scratchResult.medianNsPerOp;
            double allocationReduction = 100.0 * (1.0 - scratchResult.medianBytesPerOp / nativeResult.medianBytesPerOp);
            out.append(String.format("| (%d,%d,%d) | %d | %.1f | %.1f | %.1f | %.2f | %.2f | %.2f | %.2fx | %.2f%% | %d |%n", shape[0], shape[1], shape[2], IwtAreaTemplates.get(shape[0], shape[1], shape[2]).size(), nativeResult.medianNsPerOp, freshResult.medianNsPerOp, scratchResult.medianNsPerOp, nativeResult.medianBytesPerOp, freshResult.medianBytesPerOp, scratchResult.medianBytesPerOp, speedup, allocationReduction, nativeResult.checksum));
            out.append(String.format("\n- (%d,%d,%d) scratch: p95 %.1f ns/op, p99 %.1f ns/op, %.0f ops/s; native p95 %.1f ns/op.\n", shape[0], shape[1], shape[2], scratchResult.p95NsPerOp, scratchResult.p99NsPerOp, 1_000_000_000.0 / scratchResult.medianNsPerOp, nativeResult.p95NsPerOp));
        }
        IwtAreaTemplates.clearForTests();
        long beforeBytes = allocations.getThreadAllocatedBytes(Thread.currentThread().getId());
        long coldStart = System.nanoTime();
        scratchPath(4, 3, 4, 0, new Scratch());
        long coldNs = System.nanoTime() - coldStart;
        long coldBytes = allocations.getThreadAllocatedBytes(Thread.currentThread().getId()) - beforeBytes;
        IwtAreaTemplates.Template warm = IwtAreaTemplates.get(4, 3, 4);
        beforeBytes = allocations.getThreadAllocatedBytes(Thread.currentThread().getId());
        long warmStart = System.nanoTime();
        long lookupChecksum = 0;
        for (int i = 0; i < 1_000_000; i++) lookupChecksum += IwtAreaTemplates.get(4, 3, 4).size();
        long warmNs = System.nanoTime() - warmStart;
        long warmBytes = allocations.getThreadAllocatedBytes(Thread.currentThread().getId()) - beforeBytes;
        MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
        out.append(String.format("%nCold largest-shape first use: %d ns, %d B. Warm lookup: %.2f ns/op, %.6f B/op, checksum=%d. Retained raw payload after prewarm: %d B. Largest scratch: %d B.%n", coldNs, coldBytes, warmNs / 1_000_000.0, warmBytes / 1_000_000.0, lookupChecksum, IwtAreaTemplates.precomputeIw205Bytes(), warm.size() * Long.BYTES));
        out.append("Heap used at report completion: ").append(memory.getHeapMemoryUsage().getUsed()).append(" B. GC count/time: ").append(gcCount()).append("/").append(gcTime()).append(" ms.\n");
        Path report = Path.of("build", "reports", "iwt-performance.md");
        Files.createDirectories(report.getParent());
        Files.writeString(report, out.toString());
        System.out.print(out);
    }

    private static Result measure(ThreadMXBean allocations, int[] shape, Mode mode) {
        double[] nanos = new double[SAMPLES];
        double[] bytes = new double[SAMPLES];
        long checksum = 0;
        for (int sample = 0; sample < SAMPLES; sample++) {
            Scratch scratch = new Scratch();
            long startBytes = allocations.getThreadAllocatedBytes(Thread.currentThread().getId());
            long start = System.nanoTime();
            long local = 0;
            for (int i = 0; i < ITERATIONS; i++) local += switch (mode) {
                case NATIVE -> nativePath(shape[0], shape[1], shape[2], i);
                case FRESH -> freshPath(shape[0], shape[1], shape[2], i);
                case SCRATCH -> scratchPath(shape[0], shape[1], shape[2], i, scratch);
            };
            nanos[sample] = (System.nanoTime() - start) / (double) ITERATIONS;
            bytes[sample] = (allocations.getThreadAllocatedBytes(Thread.currentThread().getId()) - startBytes) / (double) ITERATIONS;
            checksum = local;
        }
        Arrays.sort(nanos);
        Arrays.sort(bytes);
        return new Result(nanos[4], nanos[7], nanos[8], bytes[4], checksum);
    }

    private static long nativePath(int rx, int ry, int rz, int seed) {
        List<Offset> values = reference(rx, ry, rz);
        Collections.shuffle(values, new Random(seed));
        return checksumOffsets(values);
    }

    private static long freshPath(int rx, int ry, int rz, int seed) {
        IwtAreaTemplates.Template template = IwtAreaTemplates.get(rx, ry, rz);
        long[] values = new long[template.size()];
        return packedPath(template, values, seed);
    }

    private static long scratchPath(int rx, int ry, int rz, int seed, Scratch scratch) {
        IwtAreaTemplates.Template template = IwtAreaTemplates.get(rx, ry, rz);
        if (scratch.values.length < template.size()) scratch.values = new long[template.size()];
        return packedPath(template, scratch.values, seed);
    }

    private static long packedPath(IwtAreaTemplates.Template template, long[] values, int seed) {
        template.copyTo(values);
        Random random = new Random(seed);
        for (int i = template.size(); i > 1; i--) {
            int swap = random.nextInt(i);
            long value = values[i - 1]; values[i - 1] = values[swap]; values[swap] = value;
        }
        long checksum = 1;
        for (int i = 0; i < template.size(); i++) checksum = checksum * 31 + IwtAreaTemplates.unpackX(values[i]) * 31L + IwtAreaTemplates.unpackY(values[i]) * 17L + IwtAreaTemplates.unpackZ(values[i]);
        return checksum;
    }

    private static void verify(int rx, int ry, int rz) {
        IwtAreaTemplates.Template template = IwtAreaTemplates.get(rx, ry, rz);
        List<Offset> reference = reference(rx, ry, rz);
        if (reference.size() != template.size()) throw new IllegalStateException("Position count mismatch");
        for (int i = 0; i < template.size(); i++) {
            Offset expected = reference.get(i);
            long actual = template.valueAt(i);
            if (expected.x != IwtAreaTemplates.unpackX(actual) || expected.y != IwtAreaTemplates.unpackY(actual) || expected.z != IwtAreaTemplates.unpackZ(actual)) throw new IllegalStateException("Order mismatch");
        }
        List<Offset> shuffled = new ArrayList<>(reference);
        Collections.shuffle(shuffled, new Random(12345));
        long[] packed = new long[template.size()];
        packedPath(template, packed, 12345);
        for (int i = 0; i < packed.length; i++) {
            Offset expected = shuffled.get(i);
            if (expected.x != IwtAreaTemplates.unpackX(packed[i]) || expected.y != IwtAreaTemplates.unpackY(packed[i]) || expected.z != IwtAreaTemplates.unpackZ(packed[i])) throw new IllegalStateException("Shuffle mismatch");
        }
    }

    private static List<Offset> reference(int rx, int ry, int rz) {
        List<Offset> values = new ArrayList<>((2 * rx + 1) * (2 * ry + 1) * (2 * rz + 1));
        for (int depth = 0, total = rx + ry + rz; depth <= total; depth++) for (int x = -Math.min(rx, depth); x <= Math.min(rx, depth); x++) for (int y = -Math.min(ry, depth - Math.abs(x)); y <= Math.min(ry, depth - Math.abs(x)); y++) {
            int z = depth - Math.abs(x) - Math.abs(y);
            if (z <= rz) { values.add(new Offset(x, y, z)); if (z != 0) values.add(new Offset(x, y, -z)); }
        }
        return values;
    }

    private static long checksumOffsets(List<Offset> values) {
        long checksum = 1;
        for (Offset value : values) checksum = checksum * 31 + value.x * 31L + value.y * 17L + value.z;
        return checksum;
    }

    private static long gcCount() { return ManagementFactory.getGarbageCollectorMXBeans().stream().mapToLong(GarbageCollectorMXBean::getCollectionCount).filter(value -> value >= 0).sum(); }
    private static long gcTime() { return ManagementFactory.getGarbageCollectorMXBeans().stream().mapToLong(GarbageCollectorMXBean::getCollectionTime).filter(value -> value >= 0).sum(); }
    private enum Mode { NATIVE, FRESH, SCRATCH }
    private static final class Scratch { private long[] values = new long[0]; }
    private record Offset(int x, int y, int z) { }
    private record Result(double medianNsPerOp, double p95NsPerOp, double p99NsPerOp, double medianBytesPerOp, long checksum) { }
}

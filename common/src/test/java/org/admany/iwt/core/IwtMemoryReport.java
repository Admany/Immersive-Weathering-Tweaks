package org.admany.iwt.core;

import org.openjdk.jol.info.GraphLayout;

public final class IwtMemoryReport {
    private IwtMemoryReport() { }

    public static void main(String[] args) {
        IwtAreaTemplates.clearForTests();
        long emptyCache = GraphLayout.parseInstance(IwtAreaTemplates.cacheForTests()).totalSize();
        IwtAreaTemplates.precomputeIw205();
        GraphLayout cache = GraphLayout.parseInstance(IwtAreaTemplates.cacheForTests());
        IwtAreaTemplates.Template largest = IwtAreaTemplates.get(4, 3, 4);
        long[] scratch = new long[largest.size()];
        GraphLayout scratchLayout = GraphLayout.parseInstance(scratch);
        System.out.printf("empty-cache=%d B%n", emptyCache);
        System.out.printf("prewarmed-cache=%d B%n", cache.totalSize());
        System.out.printf("prewarmed-cache-over-empty=%d B%n", cache.totalSize() - emptyCache);
        System.out.printf("raw-template-payload=%d B%n", IwtAreaTemplates.precomputeIw205Bytes());
        System.out.printf("largest-scratch=%d B%n", scratchLayout.totalSize());
        System.out.printf("largest-scratch-raw=%d B%n", (long) largest.size() * Long.BYTES);
        System.out.println(cache.toFootprint());
    }
}

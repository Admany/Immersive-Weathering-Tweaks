package org.admany.iwt.core;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IwtAreaTemplatesTest {
    private static final int[][] IW_205_SHAPES = {
        {1, 1, 1}, {2, 2, 2}, {2, 3, 2}, {2, 4, 2}, {3, 1, 3}, {3, 2, 3}, {3, 3, 3}, {4, 3, 4}
    };

    @AfterEach
    void clearCache() {
        IwtAreaTemplates.clearForTests();
    }

    @Test
    void iw205ShapesAreCompleteUniqueSymmetricAndDistanceOrdered() {
        for (int[] shape : IW_205_SHAPES) verifyShape(shape[0], shape[1], shape[2]);
    }

    @Test
    void generatedTemplatesMatchAllSmallRectangularShapes() {
        for (int x = 0; x <= 4; x++) {
            for (int y = 0; y <= 4; y++) {
                for (int z = 0; z <= 4; z++) verifyShape(x, y, z);
            }
        }
    }

    @Test
    void packingRoundTripsAtBoundaries() {
        int[] values = {-IwtAreaTemplates.PACK_BIAS, -1, 0, 1, IwtAreaTemplates.PACK_BIAS - 1};
        for (int x : values) for (int y : values) for (int z : values) {
            long packed = IwtAreaTemplates.pack(x, y, z);
            assertEquals(x, IwtAreaTemplates.unpackX(packed));
            assertEquals(y, IwtAreaTemplates.unpackY(packed));
            assertEquals(z, IwtAreaTemplates.unpackZ(packed));
        }
        assertThrows(IllegalArgumentException.class, () -> IwtAreaTemplates.pack(IwtAreaTemplates.PACK_BIAS, 0, 0));
    }

    @Test
    void invalidDimensionsAndOversizedTemplatesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> IwtAreaTemplates.get(-1, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> IwtAreaTemplates.get(100, 100, 100));
        assertThrows(IllegalArgumentException.class, () -> IwtAreaTemplates.get(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE));
    }

    @Test
    void cachePublishesOneImmutableTemplateToConcurrentReaders() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            Callable<IwtAreaTemplates.Template> task = () -> IwtAreaTemplates.get(4, 3, 4);
            Set<IwtAreaTemplates.Template> templates = new HashSet<>();
            for (Future<IwtAreaTemplates.Template> future : executor.invokeAll(java.util.Collections.nCopies(64, task))) templates.add(future.get());
            assertEquals(1, templates.size());
            assertEquals(1, IwtAreaTemplates.cacheSize());
            IwtAreaTemplates.Template template = templates.iterator().next();
            long[] first = new long[template.size()];
            long[] second = new long[template.size()];
            template.copyTo(first);
            first[0] = Long.MIN_VALUE;
            template.copyTo(second);
            assertNotSame(first, second);
            assertTrue(second[0] != Long.MIN_VALUE);
            assertSame(template, IwtAreaTemplates.get(4, 3, 4));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void cacheRetainsAtMostItsConfiguredBound() {
        for (int x = 0; x < IwtAreaTemplates.MAX_CACHED_TEMPLATES + 8; x++) {
            assertTrue(IwtAreaTemplates.get(x, 0, 0).size() > 0);
        }
        assertEquals(IwtAreaTemplates.MAX_CACHED_TEMPLATES, IwtAreaTemplates.cacheSize());
    }

    @Test
    void iw205PrecomputeMetadataMatchesRetainedPayload() {
        assertEquals(14_832L, IwtAreaTemplates.precomputeIw205Bytes());
    }

    private static void verifyShape(int radiusX, int radiusY, int radiusZ) {
        IwtAreaTemplates.Template template = IwtAreaTemplates.get(radiusX, radiusY, radiusZ);
        int expectedSize = (2 * radiusX + 1) * (2 * radiusY + 1) * (2 * radiusZ + 1);
        assertEquals(expectedSize, template.size());
        Set<String> coordinates = new HashSet<>();
        int previousDistance = -1;
        int centers = 0;
        for (int index = 0; index < template.size(); index++) {
            long value = template.valueAt(index);
            int x = IwtAreaTemplates.unpackX(value);
            int y = IwtAreaTemplates.unpackY(value);
            int z = IwtAreaTemplates.unpackZ(value);
            assertTrue(Math.abs(x) <= radiusX && Math.abs(y) <= radiusY && Math.abs(z) <= radiusZ);
            int distance = Math.abs(x) + Math.abs(y) + Math.abs(z);
            assertTrue(distance >= previousDistance);
            previousDistance = distance;
            if (x == 0 && y == 0 && z == 0) centers++;
            coordinates.add(x + ":" + y + ":" + z);
        }
        assertEquals(expectedSize, coordinates.size());
        assertEquals(1, centers);
        for (String coordinate : coordinates) {
            String[] split = coordinate.split(":");
            int x = Integer.parseInt(split[0]);
            int y = Integer.parseInt(split[1]);
            int z = Integer.parseInt(split[2]);
            assertTrue(coordinates.contains((-x) + ":" + (-y) + ":" + (-z)));
        }
    }
}

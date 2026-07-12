package org.admany.iwt.core;

import java.util.concurrent.ConcurrentHashMap;

public final class IwtAreaTemplates {
    static final int PACK_BIAS = 1_048_576;
    static final int MAX_TEMPLATE_VALUES = 1_000_000;
    static final int MAX_CACHED_TEMPLATES = 64;
    private static final ConcurrentHashMap<Key, Template> CACHE = new ConcurrentHashMap<>();
    private static final Key[] IW_205_SHAPES = {
        new Key(1, 1, 1), new Key(2, 2, 2), new Key(2, 3, 2), new Key(2, 4, 2),
        new Key(3, 1, 3), new Key(3, 2, 3), new Key(3, 3, 3), new Key(4, 3, 4)
    };

    private IwtAreaTemplates() { }

    public static void precomputeIw205() {
        for (Key key : IW_205_SHAPES) get(key.x, key.y, key.z);
    }

    public static long precomputeIw205Bytes() {
        long values = 0;
        for (Key key : IW_205_SHAPES) values += valueCount(key.x, key.y, key.z);
        return values * Long.BYTES;
    }

    public static Template get(int x, int y, int z) {
        int size = valueCount(x, y, z);
        Key key = knownKey(x, y, z);
        if (key == null) key = new Key(x, y, z);
        Template template = CACHE.get(key);
        if (template != null) return template;
        synchronized (CACHE) {
            template = CACHE.get(key);
            if (template != null) return template;
            template = build(key, size);
            if (CACHE.size() < MAX_CACHED_TEMPLATES) CACHE.put(key, template);
            return template;
        }
    }

    static int cacheSize() {
        return CACHE.size();
    }

    static void clearForTests() {
        CACHE.clear();
    }

    static Object cacheForTests() {
        return CACHE;
    }

    private static Key knownKey(int x, int y, int z) {
        for (Key key : IW_205_SHAPES) {
            if (key.x == x && key.y == y && key.z == z) return key;
        }
        return null;
    }

    private static Template build(Key key, int size) {
        long[] values = new long[size];
        int cursor = 0;
        for (int depth = 0, total = key.x + key.y + key.z; depth <= total; depth++) {
            int maxX = Math.min(key.x, depth);
            for (int x = -maxX; x <= maxX; x++) {
                int maxY = Math.min(key.y, depth - Math.abs(x));
                for (int y = -maxY; y <= maxY; y++) {
                    int z = depth - Math.abs(x) - Math.abs(y);
                    if (z > key.z) continue;
                    values[cursor++] = pack(x, y, z);
                    if (z != 0) values[cursor++] = pack(x, y, -z);
                }
            }
        }
        if (cursor != values.length) throw new IllegalStateException("Incomplete area template");
        return new Template(values);
    }

    private static int valueCount(int x, int y, int z) {
        if (x < 0 || y < 0 || z < 0) throw new IllegalArgumentException("Area template dimensions must be non-negative");
        long count;
        try {
            count = Math.multiplyExact(Math.multiplyExact(2L * x + 1L, 2L * y + 1L), 2L * z + 1L);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("Area template dimensions overflow", exception);
        }
        if (count > MAX_TEMPLATE_VALUES) throw new IllegalArgumentException("Area template exceeds " + MAX_TEMPLATE_VALUES + " values");
        return Math.toIntExact(count);
    }

    static long pack(int x, int y, int z) {
        if (x < -PACK_BIAS || x >= PACK_BIAS || y < -PACK_BIAS || y >= PACK_BIAS || z < -PACK_BIAS || z >= PACK_BIAS) {
            throw new IllegalArgumentException("Packed area offset is outside the 21-bit range");
        }
        return ((long) (x + PACK_BIAS) << 42) | ((long) (y + PACK_BIAS) << 21) | (z + PACK_BIAS);
    }

    static int unpackX(long value) { return (int) (value >>> 42) - PACK_BIAS; }
    static int unpackY(long value) { return (int) ((value >>> 21) & 0x1F_FFFFL) - PACK_BIAS; }
    static int unpackZ(long value) { return (int) (value & 0x1F_FFFFL) - PACK_BIAS; }

    public static final class Template {
        private final long[] values;

        private Template(long[] values) {
            this.values = values;
        }

        public int size() {
            return values.length;
        }

        public void copyTo(long[] destination) {
            if (destination.length < values.length) throw new IllegalArgumentException("Destination is too small");
            System.arraycopy(values, 0, destination, 0, values.length);
        }

        long valueAt(int index) {
            return values[index];
        }
    }

    private record Key(int x, int y, int z) { }
}

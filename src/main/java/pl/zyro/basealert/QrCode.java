package pl.zyro.basealert;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Mały generator kodów QR: tryb bajtowy, korekcja błędów L, wersje 1-10. */
public final class QrCode {
    private static final int[] ECC_PER_BLOCK = {-1, 7, 10, 15, 20, 26, 18, 20, 24, 30, 18};
    private static final int[] NUM_BLOCKS = {-1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 4};

    public final int size;
    private final int version;
    private final boolean[][] modules;
    private final boolean[][] isFunction;

    public static QrCode encode(String text) {
        byte[] data = text.getBytes(StandardCharsets.UTF_8);
        int ver = -1;
        for (int v = 1; v <= 10; v++) {
            int used = 4 + (v <= 9 ? 8 : 16) + data.length * 8;
            if (used <= dataCodewords(v) * 8) { ver = v; break; }
        }
        if (ver < 0) throw new IllegalArgumentException("Tekst za długi do kodu QR");

        int capBits = dataCodewords(ver) * 8;
        boolean[] bits = new boolean[capBits];
        int[] len = {0};
        append(bits, len, 0x4, 4);
        append(bits, len, data.length, ver <= 9 ? 8 : 16);
        for (byte b : data) append(bits, len, b & 0xFF, 8);
        append(bits, len, 0, Math.min(4, capBits - len[0]));
        append(bits, len, 0, (8 - len[0] % 8) % 8);
        for (int pad = 0xEC; len[0] < capBits; pad ^= 0xEC ^ 0x11) append(bits, len, pad, 8);

        byte[] codewords = new byte[capBits / 8];
        for (int i = 0; i < capBits; i++) {
            if (bits[i]) codewords[i >>> 3] |= (byte) (1 << (7 - (i & 7)));
        }
        return new QrCode(ver, codewords);
    }

    public boolean get(int x, int y) {
        return x >= 0 && x < size && y >= 0 && y < size && modules[y][x];
    }

    private static void append(boolean[] bits, int[] len, int value, int count) {
        for (int i = count - 1; i >= 0; i--) {
            bits[len[0]++] = ((value >>> i) & 1) != 0;
        }
    }

    private static int rawModules(int ver) {
        int result = (16 * ver + 128) * ver + 64;
        if (ver >= 2) {
            int numAlign = ver / 7 + 2;
            result -= (25 * numAlign - 10) * numAlign - 55;
            if (ver >= 7) result -= 36;
        }
        return result;
    }

    private static int dataCodewords(int ver) {
        return rawModules(ver) / 8 - ECC_PER_BLOCK[ver] * NUM_BLOCKS[ver];
    }

    private QrCode(int ver, byte[] data) {
        this.version = ver;
        this.size = ver * 4 + 17;
        this.modules = new boolean[size][size];
        this.isFunction = new boolean[size][size];

        drawFunctionPatterns();
        byte[] all = addEccAndInterleave(data);
        drawCodewords(all);

        int best = 0;
        int minPenalty = Integer.MAX_VALUE;
        for (int m = 0; m < 8; m++) {
            applyMask(m);
            drawFormatBits(m);
            int p = penalty();
            if (p < minPenalty) { minPenalty = p; best = m; }
            applyMask(m); // XOR cofa maskę
        }
        applyMask(best);
        drawFormatBits(best);
    }

    private void setFunction(int x, int y, boolean dark) {
        modules[y][x] = dark;
        isFunction[y][x] = true;
    }

    private static boolean bit(int x, int i) {
        return ((x >>> i) & 1) != 0;
    }

    private void drawFunctionPatterns() {
        for (int i = 0; i < size; i++) {
            setFunction(6, i, i % 2 == 0);
            setFunction(i, 6, i % 2 == 0);
        }
        drawFinder(3, 3);
        drawFinder(size - 4, 3);
        drawFinder(3, size - 4);

        int[] pos = alignmentPositions();
        int n = pos.length;
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if (!((i == 0 && j == 0) || (i == 0 && j == n - 1) || (i == n - 1 && j == 0))) {
                    drawAlignment(pos[i], pos[j]);
                }
            }
        }
        drawFormatBits(0);
        drawVersion();
    }

    private int[] alignmentPositions() {
        if (version == 1) return new int[0];
        int numAlign = version / 7 + 2;
        int step = (version * 4 + numAlign * 2 + 1) / (numAlign * 2 - 2) * 2;
        int[] result = new int[numAlign];
        result[0] = 6;
        for (int i = numAlign - 1, pos = version * 4 + 10; i >= 1; i--, pos -= step) result[i] = pos;
        return result;
    }

    private void drawFinder(int cx, int cy) {
        for (int dy = -4; dy <= 4; dy++) {
            for (int dx = -4; dx <= 4; dx++) {
                int dist = Math.max(Math.abs(dx), Math.abs(dy));
                int xx = cx + dx, yy = cy + dy;
                if (xx >= 0 && xx < size && yy >= 0 && yy < size) {
                    setFunction(xx, yy, dist != 2 && dist != 4);
                }
            }
        }
    }

    private void drawAlignment(int cx, int cy) {
        for (int dy = -2; dy <= 2; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                setFunction(cx + dx, cy + dy, Math.max(Math.abs(dx), Math.abs(dy)) != 1);
            }
        }
    }

    private void drawFormatBits(int mask) {
        int data = (1 << 3) | mask; // poziom L = 01
        int rem = data;
        for (int i = 0; i < 10; i++) rem = (rem << 1) ^ ((rem >>> 9) * 0x537);
        int bits = ((data << 10) | rem) ^ 0x5412;

        for (int i = 0; i <= 5; i++) setFunction(8, i, bit(bits, i));
        setFunction(8, 7, bit(bits, 6));
        setFunction(8, 8, bit(bits, 7));
        setFunction(7, 8, bit(bits, 8));
        for (int i = 9; i < 15; i++) setFunction(14 - i, 8, bit(bits, i));

        for (int i = 0; i < 8; i++) setFunction(size - 1 - i, 8, bit(bits, i));
        for (int i = 8; i < 15; i++) setFunction(8, size - 15 + i, bit(bits, i));
        setFunction(8, size - 8, true);
    }

    private void drawVersion() {
        if (version < 7) return;
        int rem = version;
        for (int i = 0; i < 12; i++) rem = (rem << 1) ^ ((rem >>> 11) * 0x1F25);
        int bits = (version << 12) | rem;
        for (int i = 0; i < 18; i++) {
            boolean b = bit(bits, i);
            int a = size - 11 + i % 3;
            int c = i / 3;
            setFunction(a, c, b);
            setFunction(c, a, b);
        }
    }

    private byte[] addEccAndInterleave(byte[] data) {
        int numBlocks = NUM_BLOCKS[version];
        int blockEccLen = ECC_PER_BLOCK[version];
        int rawCodewords = rawModules(version) / 8;
        int numShortBlocks = numBlocks - rawCodewords % numBlocks;
        int shortBlockLen = rawCodewords / numBlocks;

        byte[][] blocks = new byte[numBlocks][];
        byte[] div = rsDivisor(blockEccLen);
        for (int i = 0, k = 0; i < numBlocks; i++) {
            byte[] dat = Arrays.copyOfRange(data, k, k + shortBlockLen - blockEccLen + (i < numShortBlocks ? 0 : 1));
            k += dat.length;
            byte[] ecc = rsRemainder(dat, div);
            byte[] block = Arrays.copyOf(dat, shortBlockLen + 1);
            System.arraycopy(ecc, 0, block, block.length - blockEccLen, ecc.length);
            blocks[i] = block;
        }

        byte[] result = new byte[rawCodewords];
        for (int i = 0, k = 0; i < blocks[0].length; i++) {
            for (int j = 0; j < blocks.length; j++) {
                if (i != shortBlockLen - blockEccLen || j >= numShortBlocks) {
                    result[k++] = blocks[j][i];
                }
            }
        }
        return result;
    }

    private void drawCodewords(byte[] data) {
        int i = 0;
        for (int right = size - 1; right >= 1; right -= 2) {
            if (right == 6) right = 5;
            for (int vert = 0; vert < size; vert++) {
                for (int j = 0; j < 2; j++) {
                    int x = right - j;
                    boolean upward = ((right + 1) & 2) == 0;
                    int y = upward ? size - 1 - vert : vert;
                    if (!isFunction[y][x] && i < data.length * 8) {
                        modules[y][x] = bit(data[i >>> 3], 7 - (i & 7));
                        i++;
                    }
                }
            }
        }
    }

    private void applyMask(int mask) {
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                boolean invert;
                switch (mask) {
                    case 0 -> invert = (x + y) % 2 == 0;
                    case 1 -> invert = y % 2 == 0;
                    case 2 -> invert = x % 3 == 0;
                    case 3 -> invert = (x + y) % 3 == 0;
                    case 4 -> invert = (x / 3 + y / 2) % 2 == 0;
                    case 5 -> invert = x * y % 2 + x * y % 3 == 0;
                    case 6 -> invert = (x * y % 2 + x * y % 3) % 2 == 0;
                    default -> invert = ((x + y) % 2 + x * y % 3) % 2 == 0;
                }
                if (!isFunction[y][x] && invert) modules[y][x] = !modules[y][x];
            }
        }
    }

    /** Uproszczona ocena maski (reguły: ciągi, bloki 2x2, balans). */
    private int penalty() {
        int result = 0;
        for (int y = 0; y < size; y++) {
            int run = 1;
            for (int x = 1; x < size; x++) {
                if (modules[y][x] == modules[y][x - 1]) {
                    run++;
                    if (run == 5) result += 3; else if (run > 5) result++;
                } else run = 1;
            }
        }
        for (int x = 0; x < size; x++) {
            int run = 1;
            for (int y = 1; y < size; y++) {
                if (modules[y][x] == modules[y - 1][x]) {
                    run++;
                    if (run == 5) result += 3; else if (run > 5) result++;
                } else run = 1;
            }
        }
        for (int y = 0; y < size - 1; y++) {
            for (int x = 0; x < size - 1; x++) {
                boolean c = modules[y][x];
                if (c == modules[y][x + 1] && c == modules[y + 1][x] && c == modules[y + 1][x + 1]) result += 3;
            }
        }
        int dark = 0;
        for (boolean[] row : modules) for (boolean m : row) if (m) dark++;
        int total = size * size;
        int k = (Math.abs(dark * 20 - total * 10) + total - 1) / total - 1;
        result += Math.max(k, 0) * 10;
        return result;
    }

    private static byte[] rsDivisor(int degree) {
        byte[] result = new byte[degree];
        result[degree - 1] = 1;
        int root = 1;
        for (int i = 0; i < degree; i++) {
            for (int j = 0; j < degree; j++) {
                result[j] = (byte) gfMul(result[j] & 0xFF, root);
                if (j + 1 < degree) result[j] ^= result[j + 1];
            }
            root = gfMul(root, 0x02);
        }
        return result;
    }

    private static byte[] rsRemainder(byte[] data, byte[] divisor) {
        byte[] result = new byte[divisor.length];
        for (byte b : data) {
            int factor = (b ^ result[0]) & 0xFF;
            System.arraycopy(result, 1, result, 0, result.length - 1);
            result[result.length - 1] = 0;
            for (int i = 0; i < result.length; i++) {
                result[i] ^= (byte) gfMul(divisor[i] & 0xFF, factor);
            }
        }
        return result;
    }

    private static int gfMul(int x, int y) {
        int z = 0;
        for (int i = 7; i >= 0; i--) {
            z = (z << 1) ^ ((z >>> 7) * 0x11D);
            z ^= ((y >>> i) & 1) * x;
        }
        return z;
    }
}

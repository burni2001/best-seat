package tv.bestseat.launcher;

import java.nio.charset.StandardCharsets;

/**
 * Kleiner QR-Code-Erzeuger (Byte-Modus, Fehlerkorrektur M, Version 1–10, Maske 0) für die
 * Adresse der Bild-Webseite (W126). Ohne Bibliothek, nach ISO/IEC 18004.
 */
final class QrCode {

    private static final int[] ECC_PER_BLOCK_M = {0, 10, 16, 26, 18, 24, 16, 18, 22, 22, 26};
    private static final int[] BLOCKS_M = {0, 1, 1, 1, 2, 2, 4, 4, 4, 5, 5};

    private final int size;
    private final boolean[][] modules;
    private final boolean[][] function;

    private QrCode(int version) {
        size = version * 4 + 17;
        modules = new boolean[size][size];
        function = new boolean[size][size];
    }

    /** Module [y][x]; true = dunkel. Ohne Ruhezone. */
    static boolean[][] encode(String text) {
        byte[] data = text.getBytes(StandardCharsets.UTF_8);
        int version = 1;
        while (true) {
            if (version > 10) throw new IllegalArgumentException("Text too long for a QR code");
            int need = 4 + (version < 10 ? 8 : 16) + data.length * 8;
            if (need <= dataCodewords(version) * 8) break;
            version++;
        }
        int capacityBits = dataCodewords(version) * 8;
        BitBuffer bb = new BitBuffer(capacityBits);
        bb.append(4, 4);
        bb.append(data.length, version < 10 ? 8 : 16);
        for (byte b : data) bb.append(b & 0xFF, 8);
        bb.append(0, Math.min(4, capacityBits - bb.length));
        bb.append(0, (8 - bb.length % 8) % 8);
        for (int pad = 0xEC; bb.length < capacityBits; pad ^= 0xEC ^ 0x11) bb.append(pad, 8);
        byte[] codewords = new byte[capacityBits / 8];
        for (int i = 0; i < bb.length; i++) {
            if (bb.bits[i]) codewords[i >>> 3] |= (byte) (1 << (7 - (i & 7)));
        }

        QrCode qr = new QrCode(version);
        qr.drawFunctionPatterns(version);
        qr.drawCodewords(addEccAndInterleave(codewords, version));
        qr.applyMask0();
        qr.drawFormatBits();
        return qr.modules;
    }

    // ---------------------------------------------------------------- Muster

    private void set(int x, int y, boolean dark) {
        modules[y][x] = dark;
        function[y][x] = true;
    }

    private void drawFunctionPatterns(int version) {
        for (int i = 0; i < size; i++) {
            set(6, i, i % 2 == 0);
            set(i, 6, i % 2 == 0);
        }
        drawFinder(3, 3);
        drawFinder(size - 4, 3);
        drawFinder(3, size - 4);
        int[] pos = alignmentPositions(version);
        int n = pos.length;
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if ((i == 0 && j == 0) || (i == 0 && j == n - 1) || (i == n - 1 && j == 0)) continue;
                for (int dy = -2; dy <= 2; dy++) {
                    for (int dx = -2; dx <= 2; dx++) {
                        set(pos[i] + dx, pos[j] + dy, Math.max(Math.abs(dx), Math.abs(dy)) != 1);
                    }
                }
            }
        }
        drawFormatBits(); // Platz reservieren, später richtig gesetzt
        if (version >= 7) {
            int rem = version;
            for (int i = 0; i < 12; i++) rem = (rem << 1) ^ ((rem >>> 11) * 0x1F25);
            int bits = version << 12 | rem;
            for (int i = 0; i < 18; i++) {
                boolean bit = ((bits >>> i) & 1) != 0;
                int a = size - 11 + i % 3;
                int b = i / 3;
                set(a, b, bit);
                set(b, a, bit);
            }
        }
    }

    private void drawFinder(int x, int y) {
        for (int dy = -4; dy <= 4; dy++) {
            for (int dx = -4; dx <= 4; dx++) {
                int dist = Math.max(Math.abs(dx), Math.abs(dy));
                int xx = x + dx;
                int yy = y + dy;
                if (xx >= 0 && xx < size && yy >= 0 && yy < size) set(xx, yy, dist != 2 && dist != 4);
            }
        }
    }

    /** Formatbits für Fehlerkorrektur M (00) und Maske 0. */
    private void drawFormatBits() {
        int data = 0; // M = 00, Maske 000
        int rem = data;
        for (int i = 0; i < 10; i++) rem = (rem << 1) ^ ((rem >>> 9) * 0x537);
        int bits = (data << 10 | rem) ^ 0x5412;
        for (int i = 0; i <= 5; i++) set(8, i, bit(bits, i));
        set(8, 7, bit(bits, 6));
        set(8, 8, bit(bits, 7));
        set(7, 8, bit(bits, 8));
        for (int i = 9; i < 15; i++) set(14 - i, 8, bit(bits, i));
        for (int i = 0; i < 8; i++) set(size - 1 - i, 8, bit(bits, i));
        for (int i = 8; i < 15; i++) set(8, size - 15 + i, bit(bits, i));
        set(8, size - 8, true);
    }

    private static boolean bit(int x, int i) {
        return ((x >>> i) & 1) != 0;
    }

    private static int[] alignmentPositions(int version) {
        if (version == 1) return new int[0];
        int num = version / 7 + 2;
        int step = (version * 4 + num * 2 + 1) / (num * 2 - 2) * 2;
        int[] result = new int[num];
        result[0] = 6;
        for (int i = num - 1, pos = version * 4 + 10; i >= 1; i--, pos -= step) result[i] = pos;
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
                    if (!function[y][x] && i < data.length * 8) {
                        modules[y][x] = ((data[i >>> 3] >>> (7 - (i & 7))) & 1) != 0;
                        i++;
                    }
                }
            }
        }
    }

    private void applyMask0() {
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                if (!function[y][x] && (x + y) % 2 == 0) modules[y][x] = !modules[y][x];
            }
        }
    }

    // ---------------------------------------------------------------- Fehlerkorrektur

    private static int rawDataModules(int version) {
        int result = (16 * version + 128) * version + 64;
        if (version >= 2) {
            int num = version / 7 + 2;
            result -= (25 * num - 10) * num - 55;
            if (version >= 7) result -= 36;
        }
        return result;
    }

    private static int dataCodewords(int version) {
        return rawDataModules(version) / 8 - ECC_PER_BLOCK_M[version] * BLOCKS_M[version];
    }

    private static byte[] addEccAndInterleave(byte[] data, int version) {
        int numBlocks = BLOCKS_M[version];
        int eccLen = ECC_PER_BLOCK_M[version];
        int raw = rawDataModules(version) / 8;
        int numShort = numBlocks - raw % numBlocks;
        int shortLen = raw / numBlocks;
        byte[][] blocks = new byte[numBlocks][];
        byte[] divisor = rsDivisor(eccLen);
        for (int i = 0, k = 0; i < numBlocks; i++) {
            int datLen = shortLen - eccLen + (i < numShort ? 0 : 1);
            byte[] dat = new byte[datLen];
            System.arraycopy(data, k, dat, 0, datLen);
            k += datLen;
            byte[] ecc = rsRemainder(dat, divisor);
            byte[] block = new byte[shortLen + 1];
            // Kurze Blöcke bekommen an der Stelle datLen einen Platzhalter, der nicht übertragen wird.
            int p = 0;
            for (byte b : dat) block[p++] = b;
            if (i < numShort) p++;
            for (byte b : ecc) block[p++] = b;
            blocks[i] = block;
        }
        byte[] result = new byte[raw];
        int r = 0;
        for (int i = 0; i < shortLen + 1; i++) {
            for (int j = 0; j < numBlocks; j++) {
                if (i != shortLen - eccLen || j >= numShort) result[r++] = blocks[j][i];
            }
        }
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
            for (int i = 0; i < result.length; i++) result[i] ^= (byte) gfMul(divisor[i] & 0xFF, factor);
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

    private static final class BitBuffer {
        final boolean[] bits;
        int length;

        BitBuffer(int capacity) {
            bits = new boolean[capacity];
        }

        void append(int value, int count) {
            for (int i = count - 1; i >= 0; i--) bits[length++] = ((value >>> i) & 1) != 0;
        }
    }
}

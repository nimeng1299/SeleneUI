package neuvillette.selene.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ColorTest {
    @Test
    void rgbaAndArgbPreserveByteAlpha() {
        for (int alpha : new int[] {0, 128, 255}) {
            int expected = alpha << 24 | 0x123456;
            assertEquals(expected, Color.fromRGBA(0x12, 0x34, 0x56, alpha).toARGB());
            assertEquals(expected, Color.fromARGB(alpha, 0x12, 0x34, 0x56).toARGB());
        }
        assertEquals(0xFF123456, Color.fromRGB(0x12, 0x34, 0x56).toARGB());
    }

    @Test
    void hslPercentageAndCssAlphaConvertToByteAlpha() {
        assertEquals(0xFFFF0000, Color.fromHSL(0, 100, 50).toARGB());
        assertEquals(0x80FF0000, Color.fromHSLA(0, 100, 50, 50).toARGB());
        assertEquals(0x80FF0000, Color.fromHSLACss(0, 100, 50, 0.5).toARGB());
        assertEquals(0, Color.fromHSLA(0, 100, 50, -10).Alpha());
        assertEquals(255, Color.fromHSLA(0, 100, 50, 110).Alpha());
    }
}

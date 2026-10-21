package neuvillette.selene.api;

public class Color {
    public int r;
    public int g;
    public int b;
    // 0 - 100
    public int a;

    private Color(int r, int g, int b, int a) {
        this.r = r;
        this.g = g;
        this.b = b;
        this.a = a;
    }

    public int Red() {
        return r;
    }

    public int Green() {
        return g;
    }

    public int Blue() {
        return b;
    }

    public int Alpha() {
        return a;
    }

    public static Color fromRGBA(int r, int g, int b, int a) {
        return new Color(r, g, b, a);
    }

    public static Color fromRGB(int r, int g, int b) {
        return new Color(r, g, b, 100);
    }

    public static Color fromARGB(int a, int r, int g, int b) {
        return new Color(r, g, b, a);
    }

    /**
     * 从 HSLA 创建颜色。
     *
     * @param h 色相 0 - 360（可越界，会自动取模）
     * @param s 饱和度 0 - 100
     * @param l 亮度   0 - 100
     * @param a 透明度 0 - 100
     */
    public static Color fromHSLA(double h, double s, double l, double a) {
        int[] rgb = HslHelper.hslToRgb(h, s, l);
        int alpha = (int) Math.round(HslHelper.clamp(a, 0, 100));
        return new Color(rgb[0], rgb[1], rgb[2], alpha);
    }

    public static Color fromHSL(double h, double s, double l) {
        return fromHSLA(h, s, l, 100);
    }

    /** 支持 CSS 风格 alpha（0 - 1）的 HSLA */
    public static Color fromHSLACss(double h, double s, double l, double a) {
        return fromHSLA(h, s, l, a * 100);
    }

    private static final class HslHelper {

        private HslHelper() {}

        static double clamp(double v, double min, double max) {
            return Math.clamp(v, min, max);
        }

        static int toByte(double v) {
            return (int) Math.round(clamp(v, 0, 255));
        }

        /** HSL(h:0-360, s:0-100, l:0-100) -> [r, g, b] */
        static int[] hslToRgb(double h, double s, double l) {
            h = h % 360;
            if (h < 0) h += 360;

            s = clamp(s, 0, 100) / 100.0;
            l = clamp(l, 0, 100) / 100.0;

            double c = (1 - Math.abs(2 * l - 1)) * s;           // chroma
            double x = c * (1 - Math.abs((h / 60.0) % 2 - 1));  // 次分量
            double m = l - c / 2;                               // 偏移

            double r1, g1, b1;
            if (h < 60)       { r1 = c; g1 = x; b1 = 0; }
            else if (h < 120) { r1 = x; g1 = c; b1 = 0; }
            else if (h < 180) { r1 = 0; g1 = c; b1 = x; }
            else if (h < 240) { r1 = 0; g1 = x; b1 = c; }
            else if (h < 300) { r1 = x; g1 = 0; b1 = c; }
            else              { r1 = c; g1 = 0; b1 = x; }

            return new int[] {
                    toByte((r1 + m) * 255),
                    toByte((g1 + m) * 255),
                    toByte((b1 + m) * 255)
            };
        }
    }
}
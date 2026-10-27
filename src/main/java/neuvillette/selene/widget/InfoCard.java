package neuvillette.selene.widget;

import java.util.Objects;
import javax.annotation.Nullable;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import neuvillette.selene.api.Color;
import neuvillette.selene.api.Paint;
import neuvillette.selene.api.Rect;

/**
 * 上方图片、下方单行信息的圆角卡片。text 是信息栏文字。
 * 默认使用带细微颗粒的金棕色渐变，也可叠加自定义背景纹理；图片保持比例居中显示。
 * 图片、背景和信息栏共用同一个抗锯齿圆角遮罩，不会露出方形图片边缘。
 * 信息栏右上角沿四分之一圆弧向上延伸，默认跟随 Radius，可通过 FooterRadius 单独设置。
 * 文字仍在底部矩形区域居中。
 * 子控件相对上方图片区域定位，padding 不影响卡片外观。
 */
public class InfoCard extends BaseWidget {
    private static final Color DEFAULT_TEXT_COLOR = Color.fromRGB(58, 100, 145);

    /** 内置背景渐变；GOLD 为默认金棕色，PURPLE 为蓝紫到浅紫的渐变。 */
    public enum BackgroundPreset {
        GOLD(0xB17B44, 0xCB8E3D),
        PURPLE(0x606A96, 0x9A79B7);

        private final int topRgb;
        private final int bottomRgb;

        BackgroundPreset(int topRgb, int bottomRgb) {
            this.topRgb = topRgb;
            this.bottomRgb = bottomRgb;
        }

        private Color topColor() {
            return Color.fromRGB(topRgb >>> 16 & 255, topRgb >>> 8 & 255, topRgb & 255);
        }

        private Color bottomColor() {
            return Color.fromRGB(bottomRgb >>> 16 & 255, bottomRgb >>> 8 & 255, bottomRgb & 255);
        }
    }

    @Nullable private ResourceLocation image;
    @Nullable private ResourceLocation backgroundTexture;
    private Color gradientTop = BackgroundPreset.GOLD.topColor();
    private Color gradientBottom = BackgroundPreset.GOLD.bottomColor();
    private Color footerColor = Color.fromRGB(236, 230, 217);
    private Text text;
    private int radius = 8;
    private int footerHeight = 30;
    private float footerRadius = -1f;
    private float grain = 0.006f;
    private float imageScale = 1f;

    public InfoCard(Text text) {
        this(0, 0, 0, 0, text);
    }

    public InfoCard(@Nullable BaseWidget parent, Text text) {
        this(parent, 0, 0, 0, 0, text);
    }

    public InfoCard(int x, int y, int width, int height, Text text) {
        super(x, y, width, height, Objects.requireNonNull(text, "text").getMessage());
        this.text = text;
    }

    public InfoCard(@Nullable BaseWidget parent, int x, int y, int width, int height, Text text) {
        this(x, y, width, height, text);
        setRelativePosition(parent, x, y);
    }

    @Override
    public Component getMessage() {
        return text == null ? super.getMessage() : text.getMessage();
    }

    @Override
    public void setMessage(Component message) {
        text.setMessage(message);
    }

    public Text getText() {
        return text;
    }

    public void setText(Text text) {
        this.text = Objects.requireNonNull(text, "text");
    }

    public InfoCard Text(Text text) {
        setText(text);
        return this;
    }

    /** 传入带透明背景的图片；null 清除图片。 */
    public InfoCard Image(@Nullable ResourceLocation texture) {
        image = texture;
        return this;
    }

    /** 背景纹理铺满上方区域，透明部分保留背景色；null 清除纹理，露出当前背景色。 */
    public InfoCard BackgroundTexture(@Nullable ResourceLocation texture) {
        backgroundTexture = texture;
        return this;
    }

    /** 设置上方区域的背景色，保留 Grain 设置；需要两端渐变时使用 Gradient。 */
    public InfoCard BackgroundColor(Color color) {
        return Gradient(color, color);
    }

    /** 选择内置背景渐变，保留图片、背景纹理、信息栏及颗粒设置。 */
    public InfoCard Background(BackgroundPreset preset) {
        Objects.requireNonNull(preset, "preset");
        return Gradient(preset.topColor(), preset.bottomColor());
    }

    /** 自定义左上、右下的背景渐变端点，支持透明颜色。 */
    public InfoCard Gradient(Color top, Color bottom) {
        Objects.requireNonNull(top, "top");
        Objects.requireNonNull(bottom, "bottom");
        gradientTop = top;
        gradientBottom = bottom;
        return this;
    }

    /** 颗粒亮度幅度，范围 0–1；0 关闭颗粒。颗粒固定在卡片局部坐标中，不随时间闪动。 */
    public InfoCard Grain(float amount) {
        if (!Float.isFinite(amount) || amount < 0 || amount > 1) {
            throw new IllegalArgumentException("Grain must be finite and within [0, 1]");
        }
        grain = amount;
        return this;
    }

    /** 1 为等比例完整显示；大于 1 时放大，并裁去超出上方区域的部分。 */
    public InfoCard ImageScale(float scale) {
        requirePositiveScale(scale);
        imageScale = scale;
        return this;
    }

    public InfoCard Radius(int radius) {
        if (radius < 0) throw new IllegalArgumentException("Radius must be non-negative");
        this.radius = radius;
        return this;
    }

    public InfoCard FooterHeight(int height) {
        if (height < 0) throw new IllegalArgumentException("Footer height must be non-negative");
        footerHeight = height;
        invalidateLayout();
        return this;
    }

    /** 单独设置白条右上角圆弧半径，单位为 GUI 像素；0 取消圆弧，不影响卡片外框圆角。 */
    public InfoCard FooterRadius(float radius) {
        if (!Float.isFinite(radius) || radius < 0) {
            throw new IllegalArgumentException("Footer radius must be finite and non-negative");
        }
        footerRadius = radius;
        return this;
    }

    public InfoCard FooterColor(Color color) {
        footerColor = Objects.requireNonNull(color, "color");
        return this;
    }

    public InfoCard TextColor(Color color) {
        text.TextColor(color);
        return this;
    }

    public InfoCard TextScale(float scale) {
        text.TextScale(scale);
        return this;
    }

    public InfoCard Padding(int padding) {
        setPadding(padding);
        return this;
    }

    public InfoCard Padding(int vertical, int horizontal) {
        setPadding(vertical, horizontal);
        return this;
    }

    public InfoCard Padding(int top, int right, int bottom, int left) {
        setPadding(top, right, bottom, left);
        return this;
    }

    /** 实际信息栏高度，最多占满整张卡片；缩小后再放大仍恢复配置的高度。 */
    public int getFooterHeight() {
        return Math.min(footerHeight, Math.max(0, getHeight()));
    }

    public int getImageHeight() {
        return Math.max(0, getHeight()) - getFooterHeight();
    }

    /** 实际圆弧半径，限制在卡片宽度和图片区域高度内；未设置时跟随外框圆角。 */
    public float getFooterRadius() {
        if (getFooterHeight() == 0) return 0;
        float requested = footerRadius < 0
                ? Math.min(radius, Math.min(Math.max(0, getWidth()), Math.max(0, getHeight())) / 2f)
                : footerRadius;
        return Math.clamp(requested, 0f, Math.min(Math.max(0, getWidth()), getImageHeight()));
    }

    @Override
    public int getContentHeight() {
        return (int) Math.max(0L, (long) getImageHeight() - getPaddingTop() - getPaddingBottom());
    }

    @Override
    protected void renderSelf(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int width = getWidth();
        int height = getHeight();
        float opacity = Math.clamp(alpha, 0f, 1f);
        if (width <= 0 || height <= 0 || !(opacity > 0)) return;

        Rect bounds = Rect.from_xywh(getX(), getY(), width, height);
        graphics.flush();
        renderCard(graphics, bounds, Math.min(radius, Math.min(width, height) / 2f), getFooterHeight(),
                new Paint.InfoCardColors(gradientTop, gradientBottom, footerColor), opacity);
        renderLabel(graphics, bounds, opacity);
    }

    protected void renderCard(GuiGraphics graphics, Rect bounds, float cornerRadius, int footer,
                              Paint.InfoCardColors colors, float opacity) {
        Paint.drawInfoCard(graphics.pose().last().pose(), bounds, cornerRadius, footer, getFooterRadius(),
                colors, grain, image, imageScale, backgroundTexture, opacity);
    }

    private void renderLabel(GuiGraphics graphics, Rect bounds, float opacity) {
        int footer = getFooterHeight();
        int margin = Math.max(4, Math.min(radius, Math.min(bounds.width(), bounds.height()) / 2));
        int available = Math.max(0, bounds.width() - 2 * margin);
        Text.SingleLine line = text.singleLine(available, footer, false, true);
        if (line == null) return;
        // 小卡片也留在圆角内部；单行文本过长时只显示可容纳的第一行。
        int top = boundedCoordinate((long) bounds.y() + getImageHeight());
        int left = boundedCoordinate((long) bounds.x() + margin);
        text.renderLine(graphics, line, left, top, available, footer, opacity, DEFAULT_TEXT_COLOR,
                Text.HorizontalAlignment.CENTER, Text.VerticalAlignment.CENTER, false);
    }

    private static void requirePositiveScale(float scale) {
        if (!Float.isFinite(scale) || scale <= 0) {
            throw new IllegalArgumentException("Scale must be finite and positive");
        }
    }

    private static int boundedCoordinate(long coordinate) {
        return (int) Math.clamp(coordinate, Integer.MIN_VALUE, Integer.MAX_VALUE);
    }
}

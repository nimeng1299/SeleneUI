package neuvillette.selene.widget;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import neuvillette.selene.api.Color;
import neuvillette.selene.api.Paint;
import neuvillette.selene.api.Rect;
import neuvillette.selene.layout.Constraint;

/**
 * 标题、属性图片区、说明区组成的详情卡。HeaderHeight 和 BodyHeight 指定前两段高度，底部占用剩余空间。
 * 三段均为独立 Layout，支持多个 Text、嵌套 Layout、padding、间距及约束，内容裁剪在各自区域内。
 * 图片等比例居中放在中段右侧；左侧文字区域自动避开图片，中段使用带颗粒的渐变背景。
 * 所有子控件由父树挂载和分发输入，无需向 Screen 单独注册。
 */
public class DetailCard extends BaseWidget {
    private static final Color TRANSPARENT = Color.fromRGBA(0, 0, 0, 0);
    private final Text title;
    private final Section header;
    private final Section body;
    private final Section footer;
    private int headerHeight = 32;
    private int bodyHeight = 120;
    private int borderWidth = 1;
    private int imageWidth = -1;
    private float imageScale = 1f;
    private float grain = 0.006f;
    @Nullable private ResourceLocation image;
    @Nullable private ResourceLocation backgroundTexture;
    private Color headerColor = Color.fromRGB(183, 103, 45);
    private Color headerBorderColor = Color.fromRGB(145, 88, 43);
    private Color gradientTop = Color.fromRGB(128, 95, 76);
    private Color gradientBottom = Color.fromRGB(213, 161, 80);
    private Color footerColor = Color.fromRGB(237, 229, 216);
    private Color borderColor = Color.fromRGB(159, 157, 149);

    public DetailCard(Text title) {
        this(0, 0, 0, 0, title);
    }

    public DetailCard(Component title) {
        this(new Text(title));
    }

    public DetailCard(@Nullable BaseWidget parent, Text title) {
        this(parent, 0, 0, 0, 0, title);
    }

    public DetailCard(@Nullable BaseWidget parent, Component title) {
        this(parent, new Text(title));
    }

    public DetailCard(int x, int y, int width, int height, Component title) {
        this(x, y, width, height, new Text(title));
    }

    public DetailCard(int x, int y, int width, int height, Text title) {
        super(x, y, width, height, Objects.requireNonNull(title, "title").getMessage());
        this.title = title;
        header = new Section(this, Color.fromRGB(255, 248, 235));
        body = new Section(this, Color.fromRGB(237, 229, 216));
        footer = new Section(this, Color.fromRGB(40, 71, 95));
        header.Padding(8, 12);
        body.Padding(8, 12);
        footer.Padding(10, 16).Spacing(4);
        addChild(header);
        addChild(body);
        addChild(footer);
        header.addChild(title, Constraint.fill(1));
    }

    public DetailCard(@Nullable BaseWidget parent, int x, int y, int width, int height, Text title) {
        this(x, y, width, height, title);
        setRelativePosition(parent, x, y);
    }

    public DetailCard(@Nullable BaseWidget parent, int x, int y, int width, int height, Component title) {
        this(parent, x, y, width, height, new Text(title));
    }

    @Override
    public Component getMessage() {
        return title == null ? super.getMessage() : title.getMessage();
    }

    @Override
    public void setMessage(Component message) {
        if (title == null) super.setMessage(Objects.requireNonNull(message, "message"));
        else title.setMessage(message);
    }

    public Text getText() { return title; }
    public Section getHeader() { layout(); return header; }
    public Section getBody() { layout(); return body; }
    public Section getFooter() { layout(); return footer; }

    public DetailCard Header(Consumer<? super Section> configure) {
        Objects.requireNonNull(configure, "configure").accept(getHeader());
        return this;
    }

    public DetailCard Body(Consumer<? super Section> configure) {
        Objects.requireNonNull(configure, "configure").accept(getBody());
        return this;
    }

    public DetailCard Footer(Consumer<? super Section> configure) {
        Objects.requireNonNull(configure, "configure").accept(getFooter());
        return this;
    }

    public DetailCard HeaderHeight(int height) {
        requireNonNegative(height);
        headerHeight = height;
        invalidateLayout();
        return this;
    }

    public DetailCard BodyHeight(int height) {
        requireNonNegative(height);
        bodyHeight = height;
        invalidateLayout();
        return this;
    }

    public DetailCard Image(@Nullable ResourceLocation texture) {
        image = texture;
        invalidateLayout();
        return this;
    }

    /** 图片列宽度；默认取中段高度与卡片可用宽度的一半中较小的值，0 隐藏图片列。 */
    public DetailCard ImageWidth(int width) {
        requireNonNegative(width);
        imageWidth = width;
        invalidateLayout();
        return this;
    }

    /**
     * 图片相对于右侧图片区的缩放比例：1 为等比例完整显示，0.8 为缩小到 80%，1.5 为放大到 150%。
     * 放大后保持居中，超出图片区的部分裁剪；不改变图片列宽和文字布局，可在运行时修改。
     */
    public DetailCard ImageScale(float scale) {
        setImageScale(scale);
        return this;
    }

    public float getImageScale() {
        return imageScale;
    }

    public void setImageScale(float scale) {
        if (!Float.isFinite(scale) || scale <= 0) {
            throw new IllegalArgumentException("Image scale must be finite and positive");
        }
        imageScale = scale;
    }

    public DetailCard BackgroundTexture(@Nullable ResourceLocation texture) {
        backgroundTexture = texture;
        return this;
    }

    public DetailCard BackgroundColor(Color color) { return Gradient(color, color); }

    public DetailCard Gradient(Color top, Color bottom) {
        Objects.requireNonNull(top, "top");
        Objects.requireNonNull(bottom, "bottom");
        gradientTop = top;
        gradientBottom = bottom;
        return this;
    }

    public DetailCard Grain(float amount) {
        if (!Float.isFinite(amount) || amount < 0 || amount > 1) throw new IllegalArgumentException("Grain must be within [0, 1]");
        grain = amount;
        return this;
    }

    public DetailCard HeaderColor(Color color) { headerColor = Objects.requireNonNull(color, "color"); return this; }
    public DetailCard HeaderBorderColor(Color color) { headerBorderColor = Objects.requireNonNull(color, "color"); return this; }
    public DetailCard FooterColor(Color color) { footerColor = Objects.requireNonNull(color, "color"); return this; }
    public DetailCard BorderColor(Color color) { borderColor = Objects.requireNonNull(color, "color"); return this; }

    public DetailCard BorderWidth(int width) {
        requireNonNegative(width);
        borderWidth = width;
        invalidateLayout();
        return this;
    }

    public DetailCard Padding(int padding) { setPadding(padding); return this; }
    public DetailCard Padding(int vertical, int horizontal) { setPadding(vertical, horizontal); return this; }
    public DetailCard Padding(int top, int right, int bottom, int left) { setPadding(top, right, bottom, left); return this; }

    private int borderInset() {
        return Math.min(borderWidth, Math.min(Math.max(0, getWidth()), Math.max(0, getHeight())) / 2);
    }

    @Override public int getContentX() { return boundedCoordinate((long) super.getContentX() + borderInset()); }
    @Override public int getContentY() { return boundedCoordinate((long) super.getContentY() + borderInset()); }
    @Override public int getContentWidth() { return Math.max(0, super.getContentWidth() - 2 * borderInset()); }
    @Override public int getContentHeight() { return Math.max(0, super.getContentHeight() - 2 * borderInset()); }

    public int getHeaderHeight() { return Math.min(headerHeight, getContentHeight()); }
    public int getBodyHeight() { return Math.min(bodyHeight, getContentHeight() - getHeaderHeight()); }
    public int getFooterHeight() { return getContentHeight() - getHeaderHeight() - getBodyHeight(); }

    public int getImageWidth() {
        if (image == null) return 0;
        int available = getContentWidth();
        return Math.min(available, imageWidth < 0 ? Math.min(getBodyHeight(), available / 2) : imageWidth);
    }

    @Override
    protected void layoutChildren() {
        if (header == null || body == null || footer == null) return;
        int width = getContentWidth();
        int top = getHeaderHeight();
        int middle = getBodyHeight();
        place(header, 0, width, top);
        place(body, top, width - getImageWidth(), middle);
        place(footer, top + middle, width, getFooterHeight());
    }

    private void place(Section section, int y, int width, int height) {
        if (section.getRelativeX() != 0 || section.getRelativeY() != y) section.setRelativePosition(0, y);
        if (section.getWidth() != width || section.getHeight() != height) section.setSize(width, height);
    }

    @Override
    protected void renderSelf(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        float opacity = Math.clamp(alpha, 0f, 1f);
        if (getWidth() <= 0 || getHeight() <= 0 || !(opacity > 0)) return;
        graphics.flush();
        int x = getContentX();
        int y = getContentY();
        int width = getContentWidth();
        int top = getHeaderHeight();
        int middle = getBodyHeight();
        if (width > 0) {
            if (top > 0) {
                renderFlatArea(graphics, Rect.from_xywh(x, y, width, top), withOpacity(headerColor, opacity), TRANSPARENT, 0);
                if (width > 6 && top > 6) renderFlatArea(graphics, Rect.from_xywh(x + 3, y + 3, width - 6, top - 6),
                        TRANSPARENT, withOpacity(headerBorderColor, opacity), 1);
            }
            if (middle > 0) {
                Rect bounds = Rect.from_xywh(x, boundedCoordinate((long) y + top), width, middle);
                renderImageArea(graphics, bounds, new Paint.InfoCardColors(gradientTop, gradientBottom, TRANSPARENT),
                        grain, null, 1f, backgroundTexture, opacity);
                int pictureWidth = getImageWidth();
                if (pictureWidth > 0) {
                    renderImageArea(graphics, Rect.from_xywh(boundedCoordinate((long) x + width - pictureWidth), bounds.y(), pictureWidth, middle),
                            new Paint.InfoCardColors(TRANSPARENT, TRANSPARENT, TRANSPARENT), 0,
                            image, imageScale, null, opacity);
                }
            }
            int bottom = getFooterHeight();
            if (bottom > 0) renderFlatArea(graphics, Rect.from_xywh(x, boundedCoordinate((long) y + top + middle), width, bottom),
                    withOpacity(footerColor, opacity), TRANSPARENT, 0);
        }
        renderFlatArea(graphics, Rect.from_xywh(getX(), getY(), getWidth(), getHeight()),
                TRANSPARENT, withOpacity(borderColor, opacity), borderInset());
    }

    protected void renderFlatArea(GuiGraphics graphics, Rect bounds, Color fill, Color border, int width) {
        Paint.drawBorderedRectangle(graphics.pose().last().pose(), bounds, fill, border, width);
    }

    protected void renderImageArea(GuiGraphics graphics, Rect bounds, Paint.InfoCardColors colors, float grain,
                                    @Nullable ResourceLocation image, float scale,
                                    @Nullable ResourceLocation background, float opacity) {
        graphics.enableScissor(bounds.x(), bounds.y(), boundedCoordinate((long) bounds.x() + bounds.width()),
                boundedCoordinate((long) bounds.y() + bounds.height()));
        try {
            Paint.drawInfoCard(graphics.pose().last().pose(), bounds, 0, 0, 0, colors, grain, image, scale, background, opacity);
        } finally {
            graphics.disableScissor();
        }
    }

    /** 每段都是可独立配置的布局容器；默认颜色仅作用于直属 Text，其显式样式优先。 */
    public static class Section extends Layout {
        private final DetailCard owner;
        private Color textColor;

        private Section(DetailCard owner, Color color) {
            this.owner = owner;
            textColor = color;
        }

        public Section TextColor(Color color) { textColor = Objects.requireNonNull(color, "color"); return this; }

        @Override
        protected ScreenRectangle getChildrenClip() {
            return new ScreenRectangle(getX(), getY(), Math.max(0, getWidth()), Math.max(0, getHeight()));
        }

        @Override
        protected void renderChildren(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            float opacity = Math.clamp(alpha, 0f, 1f) * Math.clamp(owner.alpha, 0f, 1f);
            if (getWidth() <= 0 || getHeight() <= 0 || !(opacity > 0)) return;
            ScreenRectangle clip = getChildrenClip();
            graphics.enableScissor(clip.left(), clip.top(), boundedCoordinate((long) clip.left() + clip.width()),
                    boundedCoordinate((long) clip.top() + clip.height()));
            try {
                for (BaseWidget child : List.copyOf(children())) {
                    if (child.getParent() != this) continue;
                    if (child instanceof Text text) {
                        text.renderIn(graphics, text.getX(), text.getY(), text.getWidth(), text.getHeight(), opacity, textColor);
                    } else {
                        child.render(graphics, mouseX, mouseY, partialTick);
                    }
                }
            } finally {
                graphics.disableScissor();
            }
        }
    }

    private static Color withOpacity(Color color, float opacity) {
        return Color.fromRGBA(color.Red(), color.Green(), color.Blue(), Math.round(Math.clamp(color.Alpha(), 0, 255) * opacity));
    }

    private static void requireNonNegative(int size) {
        if (size < 0) throw new IllegalArgumentException("Size must be non-negative");
    }

    private static int boundedCoordinate(long coordinate) {
        return (int) Math.clamp(coordinate, Integer.MIN_VALUE, Integer.MAX_VALUE);
    }
}

package neuvillette.selene.widget;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ComponentPath;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.FocusNavigationEvent;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import neuvillette.selene.api.Color;
import neuvillette.selene.state.Subscription;

/**
 * 使用游戏字体的纯文本控件，支持 Component 样式、显式换行和自动换行。
 * 默认白色、原始字号、左上对齐、自动换行且无阴影；文字裁剪到 padding 内的内容区。
 * 外框尺寸由调用方或布局容器管理，换行不会自动修改控件高度。
 * 不接收鼠标命中或键盘焦点，可用作按钮上的标签，或直接加入 Layout、ScrollList。
 * 作为其他控件的文字时，未显式设置的颜色与对齐沿用宿主控件的默认值。
 */
public class Text extends BaseWidget {
    private static final Color DEFAULT_TEXT_COLOR = Color.fromRGB(255, 255, 255);

    public enum HorizontalAlignment {
        LEFT, CENTER, RIGHT
    }

    public enum VerticalAlignment {
        TOP, CENTER, BOTTOM
    }

    private final List<Runnable> changeListeners = new ArrayList<>();
    @Nullable private Color textColor;
    private float textScale = 1f;
    private boolean wrap = true;
    private boolean shadow;
    private int lineSpacing;
    @Nullable private HorizontalAlignment alignment;
    @Nullable private VerticalAlignment verticalAlignment;

    public Text(String text) {
        this(Component.literal(Objects.requireNonNull(text, "text")));
    }

    public Text(Component message) {
        this(null, message);
    }

    /** 将原生文本转换为可配置颜色、字号、对齐和阴影的 Text，保留 Component 样式。 */
    public static Text FromComponent(Component message) {
        return new Text(message);
    }

    public Text(@Nullable BaseWidget parent, Component message) {
        this(parent, 0, 0, 0, 0, message);
    }

    public Text(int x, int y, int width, int height, Component message) {
        super(x, y, width, height, Objects.requireNonNull(message, "message"));
    }

    public Text(int x, int y, int width, int height, String text) {
        this(x, y, width, height, Component.literal(Objects.requireNonNull(text, "text")));
    }

    public Text(@Nullable BaseWidget parent, int x, int y, int width, int height, Component message) {
        this(x, y, width, height, message);
        setRelativePosition(parent, x, y);
    }

    @Override
    public void setMessage(Component message) {
        super.setMessage(Objects.requireNonNull(message, "message"));
        invalidateLayout();
    }

    @Override
    protected void invalidateLayout() {
        super.invalidateLayout();
        if (changeListeners != null) {
            for (Runnable listener : List.copyOf(changeListeners)) listener.run();
        }
    }

    Subscription onChange(Runnable listener) {
        changeListeners.add(listener);
        return () -> changeListeners.remove(listener);
    }

    public float getTextScale() {
        return textScale;
    }

    Color textColorOr(Color fallback) {
        return textColor == null ? fallback : textColor;
    }

    int singleLineHeight() {
        return boundedCoordinate((long) Math.ceil(Minecraft.getInstance().font.lineHeight * (double) textScale
                + getPaddingTop() + getPaddingBottom()));
    }

    /** 默认文字颜色；Component 内显式指定的颜色仍由游戏字体保留。 */
    public Text TextColor(Color color) {
        textColor = Objects.requireNonNull(color, "color");
        return this;
    }

    /** 相对于游戏字体的缩放倍数，必须为有限正数；内容过高时裁剪，不自动缩小字号。 */
    public Text TextScale(float scale) {
        if (!Float.isFinite(scale) || scale <= 0) {
            throw new IllegalArgumentException("Text scale must be finite and positive");
        }
        textScale = scale;
        invalidateLayout();
        return this;
    }

    /** 是否按内容区宽度自动换行；关闭后仍保留显式换行，过宽部分会被裁剪。 */
    public Text Wrap(boolean wrap) {
        this.wrap = wrap;
        return this;
    }

    public Text Shadow(boolean shadow) {
        this.shadow = shadow;
        return this;
    }

    /** 行与行之间额外增加的 GUI 像素，不随字号缩放。 */
    public Text LineSpacing(int spacing) {
        if (spacing < 0) {
            throw new IllegalArgumentException("Line spacing must be nonnegative");
        }
        lineSpacing = spacing;
        return this;
    }

    /** 每行分别对齐；行宽超出内容区时从左侧开始裁剪。 */
    public Text Align(HorizontalAlignment alignment) {
        this.alignment = Objects.requireNonNull(alignment, "alignment");
        return this;
    }

    /** 整段文字在内容区内对齐；段落过高时从顶部开始裁剪。 */
    public Text VerticalAlign(VerticalAlignment alignment) {
        verticalAlignment = Objects.requireNonNull(alignment, "alignment");
        return this;
    }

    public Text Padding(int padding) {
        setPadding(padding);
        return this;
    }

    /** 参数顺序：上下、左右。 */
    public Text Padding(int vertical, int horizontal) {
        setPadding(vertical, horizontal);
        return this;
    }

    /** 参数顺序：上、右、下、左。 */
    public Text Padding(int top, int right, int bottom, int left) {
        setPadding(top, right, bottom, left);
        return this;
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return false;
    }

    @Override
    protected boolean clicked(double mouseX, double mouseY) {
        return false;
    }

    @Nullable
    @Override
    public ComponentPath nextFocusPath(FocusNavigationEvent event) {
        return null;
    }

    @Override
    public NarrationPriority narrationPriority() {
        return NarrationPriority.NONE;
    }

    @Override
    protected void renderSelf(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderIn(graphics, getX(), getY(), getWidth(), getHeight(), 1f);
    }

    /** 在指定区域绘制，保留 Text 的样式、padding 和透明度，不改变位置、尺寸或父子关系。 */
    public void renderIn(GuiGraphics graphics, int x, int y, int width, int height, float opacity) {
        renderIn(graphics, x, y, width, height, opacity, DEFAULT_TEXT_COLOR);
    }

    /** 未显式配置文字颜色时使用宿主控件的默认颜色。 */
    public void renderIn(GuiGraphics graphics, int x, int y, int width, int height, float opacity, Color fallbackColor) {
        if (!visible) return;
        int contentWidth = (int) Math.max(0L, (long) width - getPaddingLeft() - getPaddingRight());
        int contentHeight = (int) Math.max(0L, (long) height - getPaddingTop() - getPaddingBottom());
        Color color = textColorOr(fallbackColor);
        int textAlpha = Math.round(Math.clamp(color.Alpha(), 0, 255)
                * Math.clamp(alpha, 0f, 1f) * Math.clamp(opacity, 0f, 1f));
        // 游戏字体会把极低的 alpha 当作未指定透明度，提前跳过以避免透明文字闪成不透明。
        if (contentWidth <= 0 || contentHeight <= 0 || textAlpha < 4) {
            return;
        }
        int maxLineWidth = wrap ? (int) Math.floor(contentWidth / (double) textScale) : Integer.MAX_VALUE;
        if (maxLineWidth <= 0) {
            return;
        }
        var font = Minecraft.getInstance().font;
        List<FormattedCharSequence> lines = font.split(getMessage(), maxLineWidth);
        if (lines.isEmpty()) {
            return;
        }
        double lineHeight = font.lineHeight * (double) textScale;
        double lineAdvance = lineHeight + lineSpacing;
        double textHeight = lines.size() * lineHeight + (lines.size() - 1L) * lineSpacing;
        double verticalSpace = Math.max(0, contentHeight - textHeight);
        double topOffset = switch (verticalAlignment == null ? VerticalAlignment.TOP : verticalAlignment) {
            case TOP -> 0;
            case CENTER -> verticalSpace / 2;
            case BOTTOM -> verticalSpace;
        };
        int left = boundedCoordinate((long) x + getPaddingLeft());
        int top = boundedCoordinate((long) y + getPaddingTop());
        int packedColor = textAlpha << 24 | color.toARGB() & 0xFFFFFF;

        graphics.enableScissor(left, top, boundedCoordinate((long) left + contentWidth),
                boundedCoordinate((long) top + contentHeight));
        try {
            for (int i = 0; i < lines.size(); i++) {
                double lineTop = topOffset + i * lineAdvance;
                if (lineTop >= contentHeight) {
                    break;
                }
                FormattedCharSequence line = lines.get(i);
                double horizontalSpace = Math.max(0, contentWidth - font.width(line) * (double) textScale);
                double leftOffset = switch (alignment == null ? HorizontalAlignment.LEFT : alignment) {
                    case LEFT -> 0;
                    case CENTER -> horizontalSpace / 2;
                    case RIGHT -> horizontalSpace;
                };
                graphics.pose().pushPose();
                try {
                    graphics.pose().translate(left + leftOffset, top + lineTop, 0);
                    graphics.pose().scale(textScale, textScale, 1f);
                    graphics.drawString(font, line, 0, 0, packedColor, shadow);
                } finally {
                    graphics.pose().popPose();
                }
            }
        } finally {
            graphics.disableScissor();
        }
    }

    /** 单行控件共用的测量与绘制；按钮截短，卡片保留可容纳的第一行。 */
    @Nullable
    SingleLine singleLine(float width, int height, boolean fitHeight, boolean firstWrappedLine) {
        double availableWidth = Math.max(0, (double) width - getPaddingLeft() - getPaddingRight());
        long availableHeight = Math.max(0L, (long) height - getPaddingTop() - getPaddingBottom());
        if (!visible || availableWidth <= 0 || availableHeight <= 0 || getMessage().getString().isEmpty()) return null;
        var font = Minecraft.getInstance().font;
        float scale = fitHeight ? Math.min(textScale, (float) availableHeight / font.lineHeight) : textScale;
        int maxWidth = (int) Math.floor(availableWidth / scale);
        if (maxWidth <= 0) return null;
        FormattedCharSequence sequence;
        if (firstWrappedLine) {
            List<FormattedCharSequence> lines = font.split(getMessage(), maxWidth);
            if (lines.isEmpty()) return null;
            sequence = lines.getFirst();
        } else {
            sequence = Language.getInstance().getVisualOrder(font.substrByWidth(getMessage(), maxWidth));
        }
        return new SingleLine(sequence, scale, font.width(sequence) * (double) scale, font.lineHeight * (double) scale);
    }

    double lineX(SingleLine line, double x, double width, HorizontalAlignment fallback) {
        double space = Math.max(0, width - getPaddingLeft() - getPaddingRight() - line.width());
        return x + getPaddingLeft() + switch (alignment == null ? fallback : alignment) {
            case LEFT -> 0;
            case CENTER -> space / 2;
            case RIGHT -> space;
        };
    }

    void renderLine(GuiGraphics graphics, SingleLine line, double x, int y, double width, int height,
                    float opacity, Color fallbackColor, HorizontalAlignment fallbackAlignment,
                    VerticalAlignment fallbackVerticalAlignment, boolean forceColor) {
        Color color = forceColor ? fallbackColor : textColorOr(fallbackColor);
        int textAlpha = Math.round(Math.clamp(color.Alpha(), 0, 255)
                * Math.clamp(alpha, 0f, 1f) * Math.clamp(opacity, 0f, 1f));
        if (!visible || textAlpha < 4) return;
        double space = Math.max(0, (double) height - getPaddingTop() - getPaddingBottom() - line.height());
        VerticalAlignment effectiveAlignment = verticalAlignment == null ? fallbackVerticalAlignment : verticalAlignment;
        double top = y + (double) getPaddingTop() + switch (effectiveAlignment) {
            case TOP -> 0;
            case CENTER -> space / 2;
            case BOTTOM -> space;
        };
        FormattedCharSequence sequence = line.sequence();
        if (forceColor) {
            FormattedCharSequence original = sequence;
            sequence = sink -> original.accept((index, style, codePoint) ->
                    sink.accept(index, style.withColor(color.toARGB() & 0xFFFFFF), codePoint));
        }
        graphics.enableScissor(boundedCoordinate((long) Math.ceil(x + getPaddingLeft())),
                boundedCoordinate((long) y + getPaddingTop()),
                boundedCoordinate((long) Math.floor(x + width - getPaddingRight())),
                boundedCoordinate((long) y + height - getPaddingBottom()));
        try {
            graphics.pose().pushPose();
            try {
                graphics.pose().translate(lineX(line, x, width, fallbackAlignment), top, 0);
                graphics.pose().scale(line.scale(), line.scale(), 1f);
                graphics.drawString(Minecraft.getInstance().font, sequence, 0, 0,
                        textAlpha << 24 | color.toARGB() & 0xFFFFFF, shadow);
            } finally {
                graphics.pose().popPose();
            }
        } finally {
            graphics.disableScissor();
        }
    }

    record SingleLine(FormattedCharSequence sequence, float scale, double width, double height) {}

    private static int boundedCoordinate(long coordinate) {
        return (int) Math.clamp(coordinate, Integer.MIN_VALUE, Integer.MAX_VALUE);
    }
}

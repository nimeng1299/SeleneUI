package neuvillette.selene.widget;

import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ComponentPath;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.FocusNavigationEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import neuvillette.selene.api.Color;

/**
 * 使用游戏字体的纯文本控件，支持 Component 样式、显式换行和自动换行。
 * 默认白色、原始字号、左上对齐、自动换行且无阴影；文字裁剪到 padding 内的内容区。
 * 外框尺寸由调用方或布局容器管理，换行不会自动修改控件高度。
 * 不接收鼠标命中或键盘焦点，可用作按钮上的标签，或直接加入 Layout、ScrollList。
 */
public class Text extends BaseWidget {
    public enum HorizontalAlignment {
        LEFT, CENTER, RIGHT
    }

    public enum VerticalAlignment {
        TOP, CENTER, BOTTOM
    }

    private Color textColor = Color.fromRGB(255, 255, 255);
    private float textScale = 1f;
    private boolean wrap = true;
    private boolean shadow;
    private int lineSpacing;
    private HorizontalAlignment alignment = HorizontalAlignment.LEFT;
    private VerticalAlignment verticalAlignment = VerticalAlignment.TOP;

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
        int contentWidth = getContentWidth();
        int contentHeight = getContentHeight();
        int textAlpha = Math.round(Math.clamp(textColor.Alpha(), 0, 255) * Math.clamp(alpha, 0f, 1f));
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
        double topOffset = switch (verticalAlignment) {
            case TOP -> 0;
            case CENTER -> verticalSpace / 2;
            case BOTTOM -> verticalSpace;
        };
        int left = getContentX();
        int top = getContentY();
        int packedColor = textAlpha << 24 | textColor.toARGB() & 0xFFFFFF;

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
                double leftOffset = switch (alignment) {
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

    private static int boundedCoordinate(long coordinate) {
        return (int) Math.clamp(coordinate, Integer.MIN_VALUE, Integer.MAX_VALUE);
    }
}

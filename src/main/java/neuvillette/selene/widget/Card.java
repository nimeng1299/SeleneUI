package neuvillette.selene.widget;

import java.util.Objects;
import javax.annotation.Nullable;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import neuvillette.selene.api.Color;
import neuvillette.selene.api.Paint;
import neuvillette.selene.api.Rect;
import neuvillette.selene.state.Subscription;

/**
 * 在 Block 左上边框中嵌入单行标题的卡片，沿用 Block 的背景、边框及圆角设置。
 * 标题位于控件外框内，过长时截短；边框在标题两侧留出间隙，透明背景也不会露出穿字的边框。
 * 默认 padding 为 8，内容原点另向下预留一行标题高度；空标题恢复普通 Block 的内容区域。
 * 与 Block 一样保留子控件的相对位置和尺寸，需要自动排列时可在内部加入 Layout 或 ScrollList。
 */
public class Card extends Block {
    private static final int TITLE_INSET = 12;
    private static final int TITLE_GAP = 4;

    private static final Color DEFAULT_TITLE_COLOR = Color.fromRGB(220, 220, 220);
    private Text title;
    private Subscription titleChanges;

    public Card(Text title) {
        this(0, 0, 0, 0, title);
    }

    public Card(String title) {
        this(new Text(title));
    }

    public Card(@Nullable BaseWidget parent, Text title) {
        this(parent, 0, 0, 0, 0, title);
    }

    public Card(int x, int y, int width, int height, Text title) {
        super(x, y, width, height, Objects.requireNonNull(title, "title").getMessage());
        setText(title);
        setPadding(8);
    }

    public Card(int x, int y, int width, int height, String title) {
        this(x, y, width, height, new Text(title));
    }

    public Card(@Nullable BaseWidget parent, int x, int y, int width, int height, Text title) {
        this(x, y, width, height, title);
        setRelativePosition(parent, x, y);
    }

    @Override
    public void setMessage(Component title) {
        this.title.setMessage(title);
    }

    @Override
    public Component getMessage() {
        return title == null ? super.getMessage() : title.getMessage();
    }

    public Text getText() {
        return title;
    }

    public void setText(Text title) {
        Objects.requireNonNull(title, "title");
        if (titleChanges != null) titleChanges.unsubscribe();
        this.title = title;
        titleChanges = title.onChange(this::invalidateLayout);
        invalidateLayout();
    }

    public Card Title(Text title) {
        setText(title);
        return this;
    }

    public Card Title(String title) {
        setMessage(Component.literal(Objects.requireNonNull(title, "title")));
        return this;
    }

    public Card TitleColor(Color color) {
        title.TextColor(color);
        return this;
    }

    public Card TitleScale(float scale) {
        title.TextScale(scale);
        return this;
    }

    /** 预留的标题行高，单位为 GUI 像素；标题为空时为 0，不包含卡片自身的 padding。 */
    public int getTitleHeight() {
        return getMessage().getString().isEmpty() ? 0 : title.singleLineHeight();
    }

    @Override
    public int getContentY() {
        return boundedCoordinate((long) super.getContentY() + getTitleHeight());
    }

    @Override
    public int getContentHeight() {
        return Math.max(0, super.getContentHeight() - getTitleHeight());
    }

    @Override
    public Card Padding(int padding) {
        super.Padding(padding);
        return this;
    }

    /** 参数顺序：上下、左右。 */
    @Override
    public Card Padding(int vertical, int horizontal) {
        super.Padding(vertical, horizontal);
        return this;
    }

    /** 参数顺序：上、右、下、左。 */
    @Override
    public Card Padding(int top, int right, int bottom, int left) {
        super.Padding(top, right, bottom, left);
        return this;
    }

    @Override
    public Card Radius(int radius) {
        super.Radius(radius);
        return this;
    }

    @Override
    public Card BorderWidth(int size) {
        super.BorderWidth(size);
        return this;
    }

    @Override
    public Card BorderColor(Color color) {
        super.BorderColor(Objects.requireNonNull(color, "color"));
        return this;
    }

    @Override
    public Card FillColor(Color color) {
        super.FillColor(Objects.requireNonNull(color, "color"));
        return this;
    }

    @Override
    protected void renderSelf(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int width = getWidth();
        int height = getHeight();
        float opacity = Math.clamp(alpha, 0f, 1f);
        if (width <= 0 || height <= 0 || !(opacity > 0)) {
            return;
        }
        int x = getX();
        int y = getY();
        int right = boundedCoordinate((long) x + width);
        int bottom = boundedCoordinate((long) y + height);
        int titleHeight = getTitleHeight();
        int frameOffset = Math.min(height, titleHeight / 2);
        Rect frame = Rect.from_xywh(x, boundedCoordinate((long) y + frameOffset), width, height - frameOffset);
        float limit = Math.min(frame.width(), frame.height()) / 2f;
        float frameBorder = Math.clamp((float) borderSize, 0f, limit);
        int edge = (int) Math.ceil(Math.max(Math.clamp((float) radius, 0f, limit), frameBorder));
        int titleLeft = Math.max(TITLE_INSET, edge + TITLE_GAP);
        int availableWidth = (int) Math.max(0L, (long) width - edge - TITLE_GAP - titleLeft);
        Color fill = withOpacity(fillColor, opacity);
        Color border = withOpacity(borderColor, opacity);
        Text.SingleLine line = title.singleLine(availableWidth, titleHeight, false, true);
        int textWidth = 0;
        int titleX = boundedCoordinate((long) x + titleLeft);
        int textX = line == null ? titleX
                : boundedCoordinate((long) Math.floor(title.lineX(line, titleX, availableWidth, Text.HorizontalAlignment.LEFT)));
        if (line != null) textWidth = (int) Math.min(availableWidth, Math.ceil(line.width()));

        // Paint 立即提交绘制；先完成此前的 GUI 批次，整张卡片的绘制保持在自己的外框内。
        graphics.flush();
        graphics.enableScissor(x, y, right, bottom);
        try {
            int gapBottomOffset = Math.min(frameOffset + (int) Math.ceil(frameBorder) + 1,
                    height - (int) Math.ceil(frameBorder));
            if (textWidth > 0 && frameBorder > 0 && gapBottomOffset > frameOffset) {
                int gapLeft = boundedCoordinate((long) textX - TITLE_GAP);
                int gapRight = boundedCoordinate((long) textX + textWidth + TITLE_GAP);
                int gapBottom = boundedCoordinate((long) y + gapBottomOffset);
                // 四块区域互不重叠；标题缺口仅去掉顶边，不覆盖底边，也不叠加半透明颜色。
                renderFramePart(graphics, frame, fill, border, frameBorder, x, y, gapLeft, bottom);
                renderFramePart(graphics, frame, fill, border, frameBorder, gapRight, y, right, bottom);
                renderFramePart(graphics, frame, fill, fill, 0, gapLeft, y, gapRight, gapBottom);
                renderFramePart(graphics, frame, fill, border, frameBorder, gapLeft, gapBottom, gapRight, bottom);
            } else {
                renderCardFrame(graphics, frame, fill, border, frameBorder);
            }

            if (line != null && textWidth > 0) {
                graphics.enableScissor(titleX, y,
                        boundedCoordinate((long) titleX + availableWidth),
                        boundedCoordinate(Math.min((long) bottom, (long) y + titleHeight)));
                try {
                    title.renderLine(graphics, line, titleX, y, availableWidth, titleHeight, opacity,
                            DEFAULT_TITLE_COLOR, Text.HorizontalAlignment.LEFT, Text.VerticalAlignment.TOP, false);
                } finally {
                    graphics.disableScissor();
                }
            }
        } finally {
            graphics.disableScissor();
        }
    }

    private void renderFramePart(GuiGraphics graphics, Rect bounds, Color fill, Color border, float width,
                                 int left, int top, int right, int bottom) {
        if (left >= right || top >= bottom) {
            return;
        }
        graphics.enableScissor(left, top, right, bottom);
        try {
            renderCardFrame(graphics, bounds, fill, border, width);
        } finally {
            graphics.disableScissor();
        }
    }

    /** 绘制同一个完整圆角矩形，标题缺口由调用方的裁剪区域控制。 */
    protected void renderCardFrame(GuiGraphics graphics, Rect bounds, Color fill, Color border, float width) {
        Paint.drawBorderedRoundedRectangle(graphics.pose().last().pose(), bounds, radius, fill, border, width);
    }

    private static Color withOpacity(Color color, float opacity) {
        return Color.fromRGBA(color.Red(), color.Green(), color.Blue(),
                Math.round(Math.clamp(color.Alpha(), 0, 255) * opacity));
    }

    private static int boundedCoordinate(long coordinate) {
        return (int) Math.clamp(coordinate, Integer.MIN_VALUE, Integer.MAX_VALUE);
    }
}

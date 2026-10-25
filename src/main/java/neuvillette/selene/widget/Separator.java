package neuvillette.selene.widget;

import java.util.Objects;
import javax.annotation.Nullable;
import net.minecraft.client.gui.ComponentPath;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.FocusNavigationEvent;
import net.minecraft.network.chat.Component;
import neuvillette.selene.api.Color;
import neuvillette.selene.layout.Direction;

/**
 * 横向或纵向分隔线，默认横向、灰色（#808080）、粗细为 1 个 GUI 像素。
 * 线条填满内容区的长度，并在另一轴上居中；粗细不会超过可用内容尺寸。
 * 外框由调用方或布局容器管理，切换方向或粗细不会改变外框大小。
 * 横线放入纵向 Layout（或竖线放入横向 Layout）时，可用 Constraint.length(1) 限定占位。
 * 纯装饰控件，不拦截鼠标，也不参与键盘焦点和朗读。
 */
public class Separator extends BaseWidget {
    private Direction direction = Direction.HORIZONTAL;
    private Color lineColor = Color.fromRGB(128, 128, 128);
    private int thickness = 1;

    /** 默认外框为 1×1，适合由 Layout 分配长度，或直接加入 ScrollList。 */
    public Separator() {
        this(0, 0, 1, 1);
    }

    /** 保留默认的 1×1 外框；message 仅作为元数据，不绘制文字。 */
    public Separator(@Nullable BaseWidget parent, Component message) {
        this();
        setMessage(message);
        setRelativePosition(parent, 0, 0);
    }

    public Separator(int x, int y, int width, int height) {
        super(x, y, width, height, Component.empty());
    }

    public Separator(int x, int y, int width, int height, Direction direction) {
        this(x, y, width, height);
        setDirection(direction);
    }

    public Separator(@Nullable BaseWidget parent, int x, int y, int width, int height) {
        this(parent, x, y, width, height, Direction.HORIZONTAL);
    }

    public Separator(@Nullable BaseWidget parent, int x, int y, int width, int height, Direction direction) {
        this(x, y, width, height, direction);
        setRelativePosition(parent, x, y);
    }

    public Direction getDirection() {
        return direction;
    }

    public void setDirection(Direction direction) {
        this.direction = Objects.requireNonNull(direction, "direction");
    }

    public Separator Direction(Direction direction) {
        setDirection(direction);
        return this;
    }

    public Separator Horizontal() {
        return Direction(Direction.HORIZONTAL);
    }

    public Separator Vertical() {
        return Direction(Direction.VERTICAL);
    }

    public int getThickness() {
        return thickness;
    }

    /** 设置线条粗细，单位为 GUI 像素，必须大于 0；外框尺寸保持不变。 */
    public void setThickness(int thickness) {
        if (thickness <= 0) {
            throw new IllegalArgumentException("Separator thickness must be positive");
        }
        this.thickness = thickness;
    }

    public Separator Thickness(int thickness) {
        setThickness(thickness);
        return this;
    }

    public Separator LineColor(Color color) {
        lineColor = Objects.requireNonNull(color, "color");
        return this;
    }

    public Separator Padding(int padding) {
        setPadding(padding);
        return this;
    }

    /** 参数顺序：上下、左右。 */
    public Separator Padding(int vertical, int horizontal) {
        setPadding(vertical, horizontal);
        return this;
    }

    /** 参数顺序：上、右、下、左。 */
    public Separator Padding(int top, int right, int bottom, int left) {
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
        int width = getContentWidth();
        int height = getContentHeight();
        int opacity = Math.round(Math.clamp(lineColor.Alpha(), 0, 255) * Math.clamp(alpha, 0f, 1f));
        if (width <= 0 || height <= 0 || opacity == 0) {
            return;
        }

        long left = getContentX();
        long top = getContentY();
        if (direction == Direction.HORIZONTAL) {
            int lineHeight = Math.min(thickness, height);
            top += (height - lineHeight) / 2;
            height = lineHeight;
        } else {
            int lineWidth = Math.min(thickness, width);
            left += (width - lineWidth) / 2;
            width = lineWidth;
        }
        int color = (opacity << 24) | (lineColor.toARGB() & 0xFFFFFF);
        graphics.fill(boundedCoordinate(left), boundedCoordinate(top),
                boundedCoordinate(left + width), boundedCoordinate(top + height), color);
    }

    private static int boundedCoordinate(long coordinate) {
        return (int) Math.clamp(coordinate, Integer.MIN_VALUE, Integer.MAX_VALUE);
    }
}

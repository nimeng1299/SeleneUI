package neuvillette.selene.widget;

import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.client.gui.ComponentPath;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.FocusNavigationEvent;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.network.chat.Component;
import neuvillette.selene.api.Color;
import neuvillette.selene.api.Paint;
import neuvillette.selene.api.Rect;
import org.lwjgl.glfw.GLFW;

/**
 * 保留子控件自然高度的竖向滚动列表，隐藏项仍占据位置。
 * 子控件宽度填满视区，位置由列表管理；改变行高、尺寸、padding 或 spacing 后自动重排。
 * 溢出时在内容区域右侧预留 6 像素操作区及 4 像素间隙，绘制 2 像素宽的胶囊形滑块。
 * 滚动或拖动时显示滑块，闲置 800ms 后在 200ms 内淡出；显隐不改变视区宽度。
 * 子树绘制和输入均裁剪到视区。
 * 本控件不绘制背景，空白区域不消费点击；滚轮到达边界时可交给外层滚动容器。
 */
public class ScrollList extends BaseWidget {
    private static final int SCROLLBAR_WIDTH = 2;
    private static final int SCROLLBAR_HIT_WIDTH = 6;
    private static final int SCROLLBAR_GUTTER = 4;
    private static final int MIN_THUMB_HEIGHT = 16;
    private static final long SCROLLBAR_IDLE_MILLIS = 800;
    private static final long SCROLLBAR_FADE_MILLIS = 200;

    private int spacing;
    private double scrollStep = 20;
    private double scrollAmount;
    private long naturalScrollHeight;
    private int scrollHeight;
    private int maxScroll;
    private int viewportWidth;
    private int viewportHeight;
    private boolean dirty = true;
    private boolean applyingLayout;
    private boolean draggingScrollbar;
    private double thumbGrab;
    private boolean scrollbarShown;
    private long lastScrollbarActivity;

    public ScrollList() {
        this(0, 0, 0, 0);
    }

    public ScrollList(int x, int y, int width, int height) {
        this(x, y, width, height, Component.empty());
    }

    public ScrollList(int x, int y, int width, int height, Component message) {
        super(x, y, width, height, message);
    }

    public ScrollList(@Nullable BaseWidget parent, int x, int y, int width, int height, Component message) {
        super(parent, x, y, width, height, message);
    }

    public ScrollList Padding(int padding) {
        setPadding(padding);
        return this;
    }

    /** 参数顺序：上下、左右。 */
    public ScrollList Padding(int vertical, int horizontal) {
        setPadding(vertical, horizontal);
        return this;
    }

    /** 参数顺序：上、右、下、左。 */
    public ScrollList Padding(int top, int right, int bottom, int left) {
        setPadding(top, right, bottom, left);
        return this;
    }

    public int getSpacing() {
        return spacing;
    }

    public void setSpacing(int spacing) {
        if (spacing < 0) {
            throw new IllegalArgumentException("Spacing must be nonnegative");
        }
        this.spacing = spacing;
        invalidateLayout();
    }

    public ScrollList Spacing(int spacing) {
        setSpacing(spacing);
        return this;
    }

    public double getScrollStep() {
        return scrollStep;
    }

    public void setScrollStep(double scrollStep) {
        if (!Double.isFinite(scrollStep) || scrollStep <= 0) {
            throw new IllegalArgumentException("Scroll step must be finite and positive");
        }
        this.scrollStep = scrollStep;
    }

    public ScrollList ScrollStep(double scrollStep) {
        setScrollStep(scrollStep);
        return this;
    }

    public double getScrollAmount() {
        layout();
        return scrollAmount;
    }

    /** 保留小数滚动量，布局时四舍五入为像素；超出范围的有限值会被截断。 */
    public void setScrollAmount(double scrollAmount) {
        requireFinite(scrollAmount, "Scroll amount");
        layout();
        double clamped = Math.max(0, Math.min(maxScroll, scrollAmount));
        if (this.scrollAmount != clamped) {
            this.scrollAmount = clamped;
            showScrollbar();
            invalidateLayout();
        }
    }

    public void scrollBy(double amount) {
        requireFinite(amount, "Scroll delta");
        setScrollAmount(getScrollAmount() + amount);
    }

    /** 自然内容高度，超过整数范围时饱和到 Integer.MAX_VALUE。 */
    public int getScrollHeight() {
        layout();
        return scrollHeight;
    }

    /** 根据完整自然高度计算可滚动距离，结果独立饱和到 Integer.MAX_VALUE。 */
    public int getMaxScroll() {
        layout();
        return maxScroll;
    }

    public int getViewportWidth() {
        layout();
        return viewportWidth;
    }

    public int getViewportHeight() {
        layout();
        return viewportHeight;
    }

    /** 指定初始行高；之后仍可通过 child.setHeight 修改，不形成固定高度约束。 */
    public <T extends BaseWidget> T addChild(T child, int height) {
        Objects.requireNonNull(child, "child");
        if (height < 0) {
            throw new IllegalArgumentException("Row height must be nonnegative");
        }
        for (BaseWidget ancestor = this; ancestor != null; ancestor = ancestor.getParent()) {
            if (ancestor == child) {
                throw new IllegalArgumentException("A list cannot contain itself or its ancestors");
            }
        }
        // 先读取旧坐标；若旧父是 Layout，后读坐标会触发它重排并覆盖新行高。
        int relativeX = child.getRelativeX();
        int relativeY = child.getRelativeY();
        // 挂载中的加入操作会立即执行组件订阅，初始回调必须先看到正确行高。
        child.setHeight(height);
        child.setRelativePosition(this, relativeX, relativeY);
        return child;
    }

    /** 将直接子控件滚入视区；超高行已部分可见时保持位置，否则显示其顶部。 */
    public void ensureVisible(BaseWidget directChild) {
        Objects.requireNonNull(directChild, "directChild");
        if (directChild.getParent() != this) {
            throw new IllegalArgumentException("The row must be a direct child of this list");
        }
        layout();
        long top = 0;
        for (BaseWidget child : children()) {
            int height = Math.max(0, child.getHeight());
            if (child == directChild) {
                ensureRangeVisible(top, height);
                return;
            }
            top += (long) height + spacing;
        }
    }

    @Override
    protected void invalidateLayout() {
        if (!applyingLayout) {
            dirty = true;
        }
    }

    @Override
    protected void layoutChildren() {
        if (!dirty) {
            return;
        }
        List<BaseWidget> children = List.copyOf(children());
        int[] heights = new int[children.size()];
        long total = (long) Math.max(0, children.size() - 1) * spacing;
        for (int i = 0; i < children.size(); i++) {
            heights[i] = Math.max(0, children.get(i).getHeight());
            total += heights[i];
        }
        naturalScrollHeight = total;
        scrollHeight = (int) Math.min(Integer.MAX_VALUE, total);
        viewportHeight = getContentHeight();
        maxScroll = (int) Math.min(Integer.MAX_VALUE, Math.max(0L, total - viewportHeight));
        viewportWidth = Math.max(0, getContentWidth()
                - (maxScroll > 0 ? SCROLLBAR_HIT_WIDTH + SCROLLBAR_GUTTER : 0));
        scrollAmount = Math.max(0, Math.min(maxScroll, scrollAmount));
        long offset = Math.round(scrollAmount);

        applyingLayout = true;
        try {
            long top = 0;
            for (int i = 0; i < children.size(); i++) {
                BaseWidget child = children.get(i);
                child.setRelativePosition(0, boundedCoordinate(top - offset));
                child.setSize(viewportWidth, heights[i]);
                top += (long) heights[i] + spacing;
            }
            if (maxScroll == 0) {
                draggingScrollbar = false;
                scrollbarShown = false;
            }
            dirty = false;
        } finally {
            applyingLayout = false;
        }
    }

    @Override
    protected void renderSelf(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (!hasScrollbar()) {
            return;
        }
        int alpha = scrollbarAlpha();
        if (alpha == 0) {
            return;
        }
        int left = scrollbarX();
        int right = boundedCoordinate((long) getContentX() + getContentWidth());
        int thumbTop = thumbY();
        int color = !isActive() ? 0x666666 : draggingScrollbar ? 0xDDDDDD : 0xAAAAAA;
        renderScrollbarThumb(graphics,
                Rect.from_x1y1x2y2(left, thumbTop, right, boundedCoordinate((long) thumbTop + thumbHeight())),
                alpha << 24 | color);
    }

    /** 以半宽为圆角半径绘制抗锯齿胶囊；极矮视区的半径由 Paint 按短边限制。 */
    protected void renderScrollbarThumb(GuiGraphics graphics, Rect bounds, int color) {
        // Paint 立即提交绘制，先提交之前的 GUI 批次以保持图层顺序。
        graphics.flush();
        Paint.drawRoundedRectangle(graphics.pose().last().pose(), bounds, bounds.width() / 2f,
                Color.fromRGBA((color >>> 16) & 255, (color >>> 8) & 255, color & 255,
                        color >>> 24));
    }

    @Override
    protected void renderChildren(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int left = getContentX();
        int top = getContentY();
        graphics.enableScissor(left, top, boundedCoordinate((long) left + viewportWidth),
                boundedCoordinate((long) top + viewportHeight));
        try {
            super.renderChildren(graphics, mouseX, mouseY, partialTick);
        } finally {
            graphics.disableScissor();
        }
    }

    @Override
    protected boolean isPointInsideChildren(double mouseX, double mouseY) {
        layout();
        return contains(mouseX, mouseY, getContentX(), getContentY(), viewportWidth, viewportHeight);
    }

    @Override
    protected ScreenRectangle getChildrenClip() {
        return new ScreenRectangle(getContentX(), getContentY(), getViewportWidth(), getViewportHeight());
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        layout();
        return isActive() && isPointVisibleInTree(mouseX, mouseY) && insideContent(mouseX, mouseY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        layout();
        if (!isActive() || !isPointVisibleInTree(mouseX, mouseY) || !insideContent(mouseX, mouseY)) {
            return false;
        }
        if (super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
            return true;
        }
        if (!Double.isFinite(scrollY) || scrollY == 0) {
            return false;
        }
        double before = scrollAmount;
        double delta = -scrollY * scrollStep;
        scrollBy(Double.isFinite(delta) ? delta : Math.copySign(Double.MAX_VALUE, delta));
        return scrollAmount != before;
    }

    @Override
    protected boolean clicked(double mouseX, double mouseY) {
        layout();
        return isActive() && isPointVisibleInTree(mouseX, mouseY) && hasScrollbar()
                && contains(mouseX, mouseY, scrollbarHitX(), getContentY(),
                        Math.min(SCROLLBAR_HIT_WIDTH, getContentWidth()), viewportHeight);
    }

    @Override
    public void onClick(double mouseX, double mouseY, int button) {
        if (!clicked(mouseX, mouseY)) {
            return;
        }
        int top = thumbY();
        int height = thumbHeight();
        draggingScrollbar = true;
        showScrollbar();
        if (mouseY >= top && mouseY < (long) top + height) {
            thumbGrab = mouseY - top;
        } else {
            thumbGrab = height / 2.0;
            scrollFromPointer(mouseY);
        }
    }

    @Override
    protected void onDrag(double mouseX, double mouseY, double dragX, double dragY) {
        if (draggingScrollbar && isActive()) {
            scrollFromPointer(mouseY);
        }
    }

    @Override
    public void onRelease(double mouseX, double mouseY) {
        finishScrollbarDrag();
    }

    @Override
    public void setDragging(boolean dragging) {
        if (!dragging) {
            finishScrollbarDrag();
        }
        super.setDragging(dragging);
    }

    /** 焦点路径应用到最终叶控件后，按其真实屏幕矩形处理嵌套行内的导航。 */
    @Override
    protected void onDescendantFocused(BaseWidget descendant) {
        layout();
        long top = (long) descendant.getY() - getContentY() + Math.round(scrollAmount);
        ensureRangeVisible(top, Math.max(0, descendant.getHeight()));
    }

    @Nullable
    @Override
    public ComponentPath nextFocusPath(FocusNavigationEvent event) {
        return children().isEmpty() ? null : super.nextFocusPath(event);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (super.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (!isActive() || !isFocused()) {
            return false;
        }
        double before = getScrollAmount();
        switch (keyCode) {
            case GLFW.GLFW_KEY_PAGE_UP -> scrollBy(-viewportHeight);
            case GLFW.GLFW_KEY_PAGE_DOWN -> scrollBy(viewportHeight);
            case GLFW.GLFW_KEY_HOME -> setScrollAmount(0);
            case GLFW.GLFW_KEY_END -> setScrollAmount(maxScroll);
            default -> { return false; }
        }
        return scrollAmount != before;
    }

    private boolean insideContent(double mouseX, double mouseY) {
        return contains(mouseX, mouseY, getContentX(), getContentY(), getContentWidth(), getContentHeight());
    }

    private boolean hasScrollbar() {
        return maxScroll > 0 && viewportHeight > 0 && getContentWidth() > 0;
    }

    private int scrollbarX() {
        return boundedCoordinate((long) getContentX() + Math.max(0, getContentWidth() - SCROLLBAR_WIDTH));
    }

    private int scrollbarHitX() {
        return boundedCoordinate((long) getContentX() + Math.max(0, getContentWidth() - SCROLLBAR_HIT_WIDTH));
    }

    // 使用单调时钟，避免系统时间调整影响淡出；包内测试可替换时钟。
    long scrollbarTimeMillis() {
        return Util.getMillis();
    }

    private void showScrollbar() {
        scrollbarShown = true;
        lastScrollbarActivity = scrollbarTimeMillis();
    }

    private void finishScrollbarDrag() {
        if (draggingScrollbar) {
            draggingScrollbar = false;
            showScrollbar();
        }
    }

    private int scrollbarAlpha() {
        if (!scrollbarShown) {
            return 0;
        }
        if (draggingScrollbar) {
            return 255;
        }
        long idle = Math.max(0L, scrollbarTimeMillis() - lastScrollbarActivity);
        if (idle <= SCROLLBAR_IDLE_MILLIS) {
            return 255;
        }
        if (idle >= SCROLLBAR_IDLE_MILLIS + SCROLLBAR_FADE_MILLIS) {
            return 0;
        }
        return (int) ((SCROLLBAR_IDLE_MILLIS + SCROLLBAR_FADE_MILLIS - idle) * 255
                / SCROLLBAR_FADE_MILLIS);
    }

    private int thumbHeight() {
        if (!hasScrollbar()) {
            return 0;
        }
        int proportional = (int) ((long) viewportHeight * viewportHeight / naturalScrollHeight);
        return Math.min(viewportHeight, Math.max(MIN_THUMB_HEIGHT, proportional));
    }

    private int thumbY() {
        int travel = viewportHeight - thumbHeight();
        return boundedCoordinate((long) getContentY() + Math.round(scrollAmount / maxScroll * travel));
    }

    private void scrollFromPointer(double mouseY) {
        layout();
        int travel = viewportHeight - thumbHeight();
        if (!Double.isFinite(mouseY) || !hasScrollbar() || travel <= 0) {
            return;
        }
        double fraction = Math.max(0, Math.min(1, (mouseY - getContentY() - thumbGrab) / travel));
        setScrollAmount(fraction * maxScroll);
    }

    private void ensureRangeVisible(long top, int height) {
        if (height >= viewportHeight) {
            boolean intersects = viewportHeight > 0 && top < scrollAmount + viewportHeight
                    && top + height > scrollAmount;
            if (!intersects) {
                setScrollAmount(top);
            }
        } else if (top < scrollAmount) {
            setScrollAmount(top);
        } else if (top + height > scrollAmount + viewportHeight) {
            setScrollAmount(top + height - viewportHeight);
        }
    }

    private static boolean contains(double x, double y, int left, int top, int width, int height) {
        return x >= left && y >= top && x < (long) left + width && y < (long) top + height;
    }

    private static int boundedCoordinate(long coordinate) {
        return (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, coordinate));
    }

    private static void requireFinite(double value, String name) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
    }
}

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
 * 保留子控件自由位置及尺寸的竖向滚动视图，隐藏项仍占据内容范围。
 * 虚拟高度自动覆盖可视内容区、子树下边界和配置的最小高度。
 * 无需预设总高度，内容向下扩展；公开高度使用 int，超出范围时饱和到 Integer.MAX_VALUE。
 * 内容收缩时自动限制滚动量，确保视区不超出内容底部。
 * 普通容器的越界后代计入范围；带子树裁剪的内层容器只按自身外框测量。
 * 溢出时在内容区域右侧预留 6 像素操作区及 4 像素间隙，绘制 2 像素宽的胶囊形滑块。
 * 滚动或拖动时显示滑块，闲置 800ms 后在 200ms 内淡出；显隐不改变视区宽度。
 * 子树绘制和输入均裁剪到视区。
 * 本控件不绘制背景，空白区域不消费点击；滚轮到达边界时可交给外层滚动容器。
 */
public class ScrollView extends BaseWidget {
    private static final int SCROLLBAR_WIDTH = 2;
    private static final int SCROLLBAR_HIT_WIDTH = 6;
    private static final int SCROLLBAR_GUTTER = 4;
    private static final int MIN_THUMB_HEIGHT = 16;
    private static final long SCROLLBAR_IDLE_MILLIS = 800;
    private static final long SCROLLBAR_FADE_MILLIS = 200;

    private double scrollStep = 20;
    private double scrollAmount;
    private long virtualScrollHeight;
    private int minimumVirtualHeight;
    private int virtualHeight;
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

    public ScrollView() {
        this(0, 0, 0, 0);
    }

    public ScrollView(int x, int y, int width, int height) {
        this(x, y, width, height, Component.empty());
    }

    public ScrollView(int x, int y, int width, int height, Component message) {
        super(x, y, width, height, message);
    }

    public ScrollView(@Nullable BaseWidget parent, int x, int y, int width, int height, Component message) {
        super(parent, x, y, width, height, message);
    }

    public ScrollView Padding(int padding) {
        setPadding(padding);
        return this;
    }

    /** 参数顺序：上下、左右。 */
    public ScrollView Padding(int vertical, int horizontal) {
        setPadding(vertical, horizontal);
        return this;
    }

    /** 参数顺序：上、右、下、左。 */
    public ScrollView Padding(int top, int right, int bottom, int left) {
        setPadding(top, right, bottom, left);
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

    public ScrollView ScrollStep(double scrollStep) {
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

    /** 内容范围与配置下限的较大值，未设置下限的空视图为 0；超出整数范围时饱和。 */
    public int getScrollHeight() {
        layout();
        return scrollHeight;
    }

    /** 实际虚拟高度至少覆盖视区和内容，超过整数范围时饱和到 Integer.MAX_VALUE。 */
    public int getVirtualHeight() {
        layout();
        return virtualHeight;
    }

    /** 设置虚拟高度的下限，不缩小已存在的内容范围；0 表示仅按内容自动扩展。 */
    public void setVirtualHeight(int minimum) {
        if (minimum < 0) {
            throw new IllegalArgumentException("Minimum virtual height must be nonnegative");
        }
        minimumVirtualHeight = minimum;
        invalidateLayout();
    }

    public ScrollView VirtualHeight(int minimum) {
        setVirtualHeight(minimum);
        return this;
    }

    /** 根据未饱和的虚拟高度计算可滚动距离，结果独立饱和到 Integer.MAX_VALUE。 */
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

    /**
     * 将本视图的任意后代滚入本层视区；超高项部分可见时保持当前滚动。
     * 此方法只调整本层，嵌套滚动容器仍需先将该后代滚入其自身视区。
     * 常规焦点导航会从内到外通知各层滚动容器，自动逐层显露。
     */
    public void ensureVisible(BaseWidget descendant) {
        Objects.requireNonNull(descendant, "descendant");
        BaseWidget ancestor = descendant.getParent();
        while (ancestor != null && ancestor != this) {
            ancestor = ancestor.getParent();
        }
        if (ancestor == null) {
            throw new IllegalArgumentException("The widget must be a descendant of this view");
        }
        onDescendantFocused(descendant);
    }

    @Override
    protected void invalidateLayout() {
        if (!applyingLayout) {
            dirty = true;
            super.invalidateLayout();
        }
    }

    @Override
    protected void onDescendantLayoutChanged(BaseWidget descendant) {
        if (!applyingLayout) {
            dirty = true;
        }
        super.onDescendantLayoutChanged(descendant);
    }

    @Override
    protected void layoutChildren() {
        if (!dirty) {
            return;
        }
        applyingLayout = true;
        try {
            long total = Math.max(0L, measureContentHeight());
            scrollHeight = (int) Math.min(Integer.MAX_VALUE, Math.max(total, minimumVirtualHeight));
            viewportHeight = getContentHeight();
            virtualScrollHeight = Math.max(total, Math.max(viewportHeight, minimumVirtualHeight));
            virtualHeight = (int) Math.min(Integer.MAX_VALUE, virtualScrollHeight);
            maxScroll = (int) Math.min(Integer.MAX_VALUE, virtualScrollHeight - viewportHeight);
            viewportWidth = Math.max(0, getContentWidth()
                    - (maxScroll > 0 ? SCROLLBAR_HIT_WIDTH + SCROLLBAR_GUTTER : 0));
            scrollAmount = Math.max(0, Math.min(maxScroll, scrollAmount));
            layoutContent(viewportWidth, Math.round(scrollAmount));
            if (maxScroll == 0) {
                draggingScrollbar = false;
                scrollbarShown = false;
            }
            dirty = false;
        } finally {
            applyingLayout = false;
        }
    }

    /** 测量不含滚动偏移的自然内容高度，返回 long 以保留超大内容的尺寸。 */
    protected long measureContentHeight() {
        return measureDescendants(this, 0);
    }

    /** 子类可在此排布内容；自由定位视图默认保留全部子控件几何。 */
    protected void layoutContent(int viewportWidth, long scrollOffset) {
    }

    /** 通过子坐标原点平移内容，不改写自由定位控件的相对坐标。 */
    @Override
    protected int getChildOriginY() {
        layout();
        return boundedCoordinate((long) getContentY() - Math.round(scrollAmount));
    }

    private long measureDescendants(BaseWidget container, long contentTop) {
        long bottom = 0;
        for (BaseWidget child : List.copyOf(container.children())) {
            if (child.getParent() != container) {
                continue;
            }
            child.layout();
            long childTop = saturatedAdd(contentTop, child.getRelativeY());
            bottom = Math.max(bottom, saturatedAdd(childTop, Math.max(0, child.getHeight())));
            if (child.getChildrenClip() == null) {
                bottom = Math.max(bottom, measureDescendants(child,
                        saturatedAdd(childTop, child.getPaddingTop())));
            }
        }
        return bottom;
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
        int proportional = (int) ((long) viewportHeight * viewportHeight / virtualScrollHeight);
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

    protected final void ensureRangeVisible(long top, int height) {
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

    protected static int boundedCoordinate(long coordinate) {
        return (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, coordinate));
    }

    private static long saturatedAdd(long value, int addition) {
        if (addition > 0 && value > Long.MAX_VALUE - addition) {
            return Long.MAX_VALUE;
        }
        if (addition < 0 && value < Long.MIN_VALUE - addition) {
            return Long.MIN_VALUE;
        }
        return value + addition;
    }

    private static void requireFinite(double value, String name) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
    }
}

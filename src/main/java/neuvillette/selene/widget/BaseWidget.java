package neuvillette.selene.widget;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.client.gui.ComponentPath;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.navigation.FocusNavigationEvent;
import net.minecraft.network.chat.Component;
import neuvillette.selene.state.State;
import neuvillette.selene.state.Subscription;

/**
 * 支持相对父控件内容区域左上角定位的控件基类，坐标使用 GUI 缩放后的单位。
 * <p>
 * getX/getY 和 setX/setY/setPosition 始终使用屏幕坐标；相对偏移通过
 * getRelativeX/getRelativeY 和 setRelativePosition 访问。屏幕坐标按父链实时计算，
 * 因此父控件移动后，绘制、鼠标命中和焦点矩形会一起移动，无需手动刷新布局。
 * padding 默认为 0，占用控件内部空间，不改变外框大小；修改后子控件保留相对偏移，
 * 自动跟随内容区域移动。右侧和底部 padding 用于计算可用内容尺寸，不自动缩放或裁剪子控件。
 * <p>
 * 只将根控件注册到 Screen。绑定父控件会自动加入其子控件列表，由父控件递归绘制、
 * 分发输入和管理焦点。子控件按添加顺序绘制，后添加的优先接收鼠标事件。
 * 隐藏父控件会隐藏整个子树，禁用父控件会阻止整个子树的输入；默认不裁剪越界子控件。
 * <p>
 * 组件可通过 subscribe(state, listener) 声明状态绑定。挂载时订阅，卸载时暂停，
 * 重新挂载时恢复并同步最新值；SeleneScreen 自动管理根控件的挂载生命周期。
 */
public abstract class BaseWidget extends AbstractWidget implements ContainerEventHandler {
    private final List<BaseWidget> children = new ArrayList<>();
    private final List<BaseWidget> childrenView = Collections.unmodifiableList(children);
    private final WidgetSubscriptions subscriptions = new WidgetSubscriptions();
    @Nullable
    private BaseWidget parent;
    @Nullable
    private BaseWidget focusedChild;
    @Nullable
    private BaseWidget pressedWidget;
    private int pressedButton = -1;
    private int paddingTop;
    private int paddingRight;
    private int paddingBottom;
    private int paddingLeft;
    private boolean layingOut;

    protected BaseWidget(int x, int y, int width, int height, Component message) {
        super(x, y, width, height, message);
    }

    protected BaseWidget(@Nullable BaseWidget parent, int x, int y, int width, int height, Component message) {
        this(x, y, width, height, message);
        setRelativePosition(parent, x, y);
    }

    /**
     * 在组件内声明状态绑定。挂载时订阅并立即同步当前值，卸载时暂停，重新挂载时恢复。
     * 已挂载组件的新增绑定立即生效；返回句柄可永久取消这一条绑定。
     * 建议在组件构造器中完成所需字段初始化后调用。
     */
    protected final <T> Subscription subscribe(State<T> state, Consumer<? super T> observer) {
        return subscriptions.subscribe(state, observer);
    }

    /** 是否已启用本组件的状态绑定；与 visible、active 独立。 */
    public final boolean isMounted() {
        return subscriptions.isMounted();
    }

    /**
     * 挂载本组件及子树，重复调用不会重复订阅。SeleneScreen 自动调用；
     * 普通 Screen 应在 init 中对根控件调用。挂载失败时卸载本次子树，可再次尝试。
     */
    public final void mount() {
        try {
            subscriptions.mount();
            for (BaseWidget child : List.copyOf(children)) {
                if (!isMounted()) {
                    break;
                }
                if (child.parent == this) {
                    child.mount();
                }
            }
        } catch (RuntimeException | Error error) {
            unmount();
            throw error;
        }
    }

    /** 暂停整棵子树的组件订阅并清理交互状态，保留绑定定义以便重新挂载。 */
    public final void unmount() {
        subscriptions.unmount();
        clearInteraction();
        for (BaseWidget child : List.copyOf(children)) {
            if (child.parent == this) {
                child.unmount();
            }
        }
    }

    /** 按父级到子级的顺序更新本控件布局；几何查询、绘制和输入会自动调用。 */
    public final void layout() {
        if (layingOut) {
            return;
        }
        layingOut = true;
        try {
            ensureParentLayout();
            layoutChildren();
        } finally {
            layingOut = false;
        }
    }

    /** 普通控件保留手动坐标；布局容器覆盖此方法分配子控件的位置和尺寸。 */
    protected void layoutChildren() {
    }

    /** 尺寸、内边距或子控件发生变化时，仅标记布局失效，不立即访问正在构造的子控件。 */
    protected void invalidateLayout() {
    }

    protected void onChildRemoved(BaseWidget child) {
        invalidateLayout();
    }

    private void ensureParentLayout() {
        if (parent != null) {
            parent.layout();
        }
    }

    private void invalidateParentLayout() {
        if (parent != null) {
            parent.invalidateLayout();
        }
    }

    /** 设置四边相同的内边距，单位为 GUI 坐标，必须非负。 */
    public void setPadding(int padding) {
        setPadding(padding, padding, padding, padding);
    }

    /** 设置上下、左右内边距，参数顺序与 CSS 的两个值写法一致。 */
    public void setPadding(int vertical, int horizontal) {
        setPadding(vertical, horizontal, vertical, horizontal);
    }

    /** 按上、右、下、左的顺序设置内边距；任一值为负时拒绝整次修改。 */
    public void setPadding(int top, int right, int bottom, int left) {
        if (top < 0 || right < 0 || bottom < 0 || left < 0) {
            throw new IllegalArgumentException("Padding must be non-negative");
        }
        paddingTop = top;
        paddingRight = right;
        paddingBottom = bottom;
        paddingLeft = left;
        invalidateLayout();
    }

    public int getPaddingTop() {
        return paddingTop;
    }

    public int getPaddingRight() {
        return paddingRight;
    }

    public int getPaddingBottom() {
        return paddingBottom;
    }

    public int getPaddingLeft() {
        return paddingLeft;
    }

    /** 内容区域左边界的屏幕坐标，也是子控件的 X 坐标原点。 */
    public int getContentX() {
        return getX() + paddingLeft;
    }

    /** 内容区域上边界的屏幕坐标，也是子控件的 Y 坐标原点。 */
    public int getContentY() {
        return getY() + paddingTop;
    }

    /** 扣除左右内边距的可用宽度，内边距超过外框尺寸时返回 0。 */
    public int getContentWidth() {
        return (int) Math.max(0L, (long) getWidth() - paddingLeft - paddingRight);
    }

    /** 扣除上下内边距的可用高度，内边距超过外框尺寸时返回 0。 */
    public int getContentHeight() {
        return (int) Math.max(0L, (long) getHeight() - paddingTop - paddingBottom);
    }

    @Nullable
    public BaseWidget getParent() {
        return parent;
    }

    /** 按绘制顺序返回只读子控件列表，不能通过列表直接修改父子关系。 */
    @Override
    public List<BaseWidget> children() {
        return childrenView;
    }

    /** 加入子控件，将其原有相对偏移用于当前父控件。重复加入不会改变层次顺序。 */
    public <T extends BaseWidget> T addChild(T child) {
        Objects.requireNonNull(child, "child");
        child.setRelativePosition(this, child.getRelativeX(), child.getRelativeY());
        return child;
    }

    /** 移除直接子控件并保留其屏幕位置，同时暂停组件订阅并清除相关焦点和鼠标捕获。 */
    public boolean removeChild(BaseWidget child) {
        if (child.parent != this) {
            return false;
        }
        child.setParent(null);
        return true;
    }

    public void clearChildren() {
        for (BaseWidget child : List.copyOf(children)) {
            removeChild(child);
        }
    }

    /** 更换父控件并保持当前屏幕位置；传入 null 可解除绑定。 */
    public void setParent(@Nullable BaseWidget parent) {
        validateParent(parent);
        int x = getX();
        int y = getY();
        boolean changed = changeParent(parent);
        setPosition(x, y);
        if (changed) {
            synchronizeMount();
        }
    }

    /** 同时设置父控件及相对偏移；parent 为 null 时，偏移即屏幕坐标。 */
    public void setRelativePosition(@Nullable BaseWidget parent, int x, int y) {
        boolean changed = changeParent(parent);
        setRelativePosition(x, y);
        if (changed) {
            synchronizeMount();
        }
    }

    /** 设置相对于当前父控件内容区域左上角的偏移，允许负值；无父控件时使用屏幕坐标。 */
    public void setRelativePosition(int x, int y) {
        super.setX(x);
        super.setY(y);
        invalidateParentLayout();
    }

    public int getRelativeX() {
        ensureParentLayout();
        return super.getX();
    }

    public int getRelativeY() {
        ensureParentLayout();
        return super.getY();
    }

    @Override
    public int getX() {
        ensureParentLayout();
        return super.getX() + (parent == null ? 0 : parent.getContentX());
    }

    @Override
    public int getY() {
        ensureParentLayout();
        return super.getY() + (parent == null ? 0 : parent.getContentY());
    }

    /** 设置屏幕 X 坐标，同时更新相对于父控件的偏移。 */
    @Override
    public void setX(int x) {
        super.setX(x - (parent == null ? 0 : parent.getContentX()));
        invalidateParentLayout();
    }

    /** 设置屏幕 Y 坐标，同时更新相对于父控件的偏移。 */
    @Override
    public void setY(int y) {
        super.setY(y - (parent == null ? 0 : parent.getContentY()));
        invalidateParentLayout();
    }

    @Override
    public int getWidth() {
        ensureParentLayout();
        return super.getWidth();
    }

    @Override
    public int getHeight() {
        ensureParentLayout();
        return super.getHeight();
    }

    @Override
    public void setWidth(int width) {
        super.setWidth(width);
        invalidateLayout();
        invalidateParentLayout();
    }

    @Override
    public void setHeight(int height) {
        super.setHeight(height);
        invalidateLayout();
        invalidateParentLayout();
    }

    @Override
    public void setSize(int width, int height) {
        super.setSize(width, height);
        invalidateLayout();
        invalidateParentLayout();
    }

    /** 子类只绘制自身，子树的绘制统一由基类完成。坐标仍为屏幕坐标。 */
    protected abstract void renderSelf(GuiGraphics graphics, int mouseX, int mouseY, float partialTick);

    @Override
    protected final void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        layout();
        if (!isVisibleInTree()) {
            isHovered = false;
            return;
        }
        // 被上层兄弟或子控件覆盖时，不显示本控件的悬停效果和提示。
        isHovered = isHovered && isActive() && isTopmostAt(mouseX, mouseY)
                && getChildAt(mouseX, mouseY).isEmpty();
        graphics.pose().pushPose();
        try {
            renderSelf(graphics, mouseX, mouseY, partialTick);
        } finally {
            graphics.pose().popPose();
        }
        for (BaseWidget child : List.copyOf(children)) {
            if (child.parent == this) {
                child.render(graphics, mouseX, mouseY, partialTick);
            }
        }
    }

    @Override
    public boolean isActive() {
        return super.isActive() && (parent == null || parent.isActive());
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return isActive() && (super.isMouseOver(mouseX, mouseY) || getChildAt(mouseX, mouseY).isPresent());
    }

    @Override
    public Optional<GuiEventListener> getChildAt(double mouseX, double mouseY) {
        layout();
        if (isActive()) {
            for (BaseWidget child : List.copyOf(children).reversed()) {
                if (child.parent == this && child.isMouseOver(mouseX, mouseY)) {
                    return Optional.of(child);
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        layout();
        if (isActive()) {
            for (BaseWidget child : List.copyOf(children)) {
                if (child.parent == this && child.isActive()) {
                    child.mouseMoved(mouseX, mouseY);
                }
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        layout();
        if (!isActive()) {
            return false;
        }
        for (BaseWidget child : List.copyOf(children).reversed()) {
            if (child.parent == this && child.isMouseOver(mouseX, mouseY)
                    && child.mouseClicked(mouseX, mouseY, button)) {
                // 点击回调可能移除或换绑控件，不能再把焦点交给已离开的子控件。
                if (child.parent == this && child.isActive()) {
                    setFocused(child);
                    pressedWidget = child;
                    pressedButton = button;
                }
                return true;
            }
        }
        BaseWidget previousParent = parent;
        if (super.mouseClicked(mouseX, mouseY, button)) {
            if (parent != previousParent || !isActive()) {
                clearInteraction();
                return true;
            }
            setFocused((GuiEventListener) null);
            super.setFocused(true);
            pressedWidget = this;
            pressedButton = button;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (!validPress() || button != pressedButton) {
            return false;
        }
        return pressedWidget == this ? super.mouseDragged(mouseX, mouseY, button, dragX, dragY)
                : pressedWidget.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (!validPress() || button != pressedButton) {
            return false;
        }
        BaseWidget target = pressedWidget;
        clearPress();
        return target == this ? super.mouseReleased(mouseX, mouseY, button)
                : target.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean isDragging() {
        return validPress() && pressedButton == 0;
    }

    @Override
    public void setDragging(boolean dragging) {
        if (!dragging) {
            if (pressedWidget != null && pressedWidget != this) {
                pressedWidget.setDragging(false);
            }
            clearPress();
        } else if (isActive()) {
            pressedWidget = getFocused() == null ? this : getFocused();
            pressedButton = 0;
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return isActive() && ContainerEventHandler.super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return isActive() && isFocused() && ContainerEventHandler.super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        return isActive() && isFocused() && ContainerEventHandler.super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        return isActive() && isFocused() && ContainerEventHandler.super.charTyped(codePoint, modifiers);
    }

    @Nullable
    @Override
    public BaseWidget getFocused() {
        if (focusedChild != null && !focusedChild.isActive()) {
            setFocused((GuiEventListener) null);
        }
        return isFocused() ? focusedChild : null;
    }

    @Override
    public void setFocused(@Nullable GuiEventListener focused) {
        if (focused != null && (!(focused instanceof BaseWidget child) || child.parent != this)) {
            throw new IllegalArgumentException("Focus must belong to a direct child");
        }
        if (focusedChild != focused) {
            if (focusedChild != null) {
                focusedChild.clearInteraction();
            }
            focusedChild = (BaseWidget) focused;
        }
        super.setFocused(focused != null);
        if (focusedChild != null) {
            focusedChild.setFocused(true);
        }
    }

    @Override
    public void setFocused(boolean focused) {
        super.setFocused(focused);
        // Screen 再次点击同一个根控件时也会先 false 再 true。
        // 此处只传递焦点状态，不能丢掉刚建立的子焦点和鼠标捕获。
        if (focusedChild != null) {
            focusedChild.setFocused(focused);
        }
    }

    @Nullable
    @Override
    public ComponentPath getCurrentFocusPath() {
        if (!isActive() || !isFocused()) {
            return null;
        }
        BaseWidget focused = getFocused();
        return focused == null ? super.getCurrentFocusPath()
                : ComponentPath.path(this, focused.getCurrentFocusPath());
    }

    @Nullable
    @Override
    public ComponentPath nextFocusPath(FocusNavigationEvent event) {
        layout();
        if (!isActive()) {
            return null;
        }
        return children.isEmpty() ? super.nextFocusPath(event) : ContainerEventHandler.super.nextFocusPath(event);
    }

    @Override
    public NarrationPriority narrationPriority() {
        if (!isActive()) {
            return NarrationPriority.NONE;
        }
        BaseWidget child = narratedChild();
        return child == null ? super.narrationPriority() : child.narrationPriority();
    }

    @Override
    protected final void updateWidgetNarration(NarrationElementOutput output) {
        if (isActive()) {
            BaseWidget child = narratedChild();
            if (child == null) {
                updateSelfNarration(output);
            } else {
                child.updateNarration(output.nest());
            }
        }
    }

    protected void updateSelfNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, getMessage());
    }

    @Nullable
    private BaseWidget narratedChild() {
        BaseWidget focused = getFocused();
        if (focused != null) {
            return focused;
        }
        for (BaseWidget child : List.copyOf(children).reversed()) {
            if (child.isActive() && child.narrationPriority() != NarrationPriority.NONE) {
                return child;
            }
        }
        return null;
    }

    private boolean isVisibleInTree() {
        return visible && (parent == null || parent.isVisibleInTree());
    }

    private boolean isTopmostAt(double mouseX, double mouseY) {
        return parent == null || parent.getChildAt(mouseX, mouseY).orElse(null) == this
                && parent.isTopmostAt(mouseX, mouseY);
    }

    private boolean validPress() {
        if (!isActive() || !isFocused() || pressedWidget == null || !pressedWidget.isActive()
                || pressedWidget != this && (pressedWidget.parent != this || !pressedWidget.isFocused())) {
            setDragging(false);
            return false;
        }
        return true;
    }

    private void clearPress() {
        pressedWidget = null;
        pressedButton = -1;
    }

    private void clearInteraction() {
        setDragging(false);
        setFocused((GuiEventListener) null);
    }

    private void synchronizeMount() {
        if (parent != null && parent.isMounted()) {
            mount();
        } else {
            unmount();
        }
    }

    private boolean changeParent(@Nullable BaseWidget nextParent) {
        validateParent(nextParent);
        if (parent == nextParent) {
            return false;
        }
        if (parent != null) {
            if (parent.focusedChild == this) {
                parent.setFocused((GuiEventListener) null);
            }
            if (parent.pressedWidget == this) {
                parent.setDragging(false);
            }
            parent.children.remove(this);
            parent.onChildRemoved(this);
        }
        clearInteraction();
        parent = nextParent;
        if (parent != null) {
            parent.children.add(this);
            parent.invalidateLayout();
        }
        return true;
    }

    private void validateParent(@Nullable BaseWidget parent) {
        for (BaseWidget ancestor = parent; ancestor != null; ancestor = ancestor.parent) {
            if (ancestor == this) {
                throw new IllegalArgumentException("A widget cannot be positioned relative to itself or its descendants");
            }
        }
    }
}

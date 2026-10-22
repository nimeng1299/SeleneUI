package neuvillette.selene.widget;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.annotation.Nullable;
import net.minecraft.client.gui.ComponentPath;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.FocusNavigationEvent;
import net.minecraft.network.chat.Component;
import neuvillette.selene.layout.Constraint;
import neuvillette.selene.layout.Direction;
import neuvillette.selene.layout.Flex;
import neuvillette.selene.layout.LayoutSolver;

/**
 * <p>
 * 默认竖向排列、起点对齐，未指定约束的子控件使用 Fill(1)。主轴按约束分配，副轴填满
 * 内容区域；padding 为内边距，spacing 为相邻子控件的间距。隐藏的子控件仍保留布局位置。
 * <p>
 * 布局管理直接子控件的位置和尺寸，手动设置这些值会在下一次布局时被覆盖。
 * 查询子控件几何、绘制、输入时自动刷新，也可调用 layout() 显式刷新。
 * 本控件不绘制背景，空白区域不拦截鼠标事件；需要背景时可将它放入 Block。
 * <p>
 * 分配规则及空间不足时的确定性退让策略见 {@link LayoutSolver}。
 */
public class Layout extends BaseWidget {
    private static final Constraint DEFAULT_CONSTRAINT = Constraint.fill(1);
    private final Map<BaseWidget, Constraint> constraints = new IdentityHashMap<>();
    private Direction direction = Direction.VERTICAL;
    private Flex flex = Flex.START;
    private int spacing;
    private boolean dirty = true;
    private boolean applyingLayout;

    public Layout() {
        this(0, 0, 0, 0);
    }

    public Layout(int x, int y, int width, int height) {
        this(x, y, width, height, Component.empty());
    }

    public Layout(int x, int y, int width, int height, Component message) {
        super(x, y, width, height, message);
    }

    public Layout(@Nullable BaseWidget parent, int x, int y, int width, int height, Component message) {
        super(parent, x, y, width, height, message);
    }

    public Direction getDirection() {
        return direction;
    }

    public void setDirection(Direction direction) {
        this.direction = Objects.requireNonNull(direction, "direction");
        invalidateLayout();
    }

    public Layout Direction(Direction direction) {
        setDirection(direction);
        return this;
    }

    public Layout Horizontal() {
        setDirection(Direction.HORIZONTAL);
        return this;
    }

    public Layout Vertical() {
        setDirection(Direction.VERTICAL);
        return this;
    }

    public Flex getFlex() {
        return flex;
    }

    public void setFlex(Flex flex) {
        this.flex = Objects.requireNonNull(flex, "flex");
        invalidateLayout();
    }

    public Layout Flex(Flex flex) {
        setFlex(flex);
        return this;
    }

    public int getSpacing() {
        return spacing;
    }

    public void setSpacing(int spacing) {
        if (spacing < 0) {
            throw new IllegalArgumentException("Spacing must be non-negative");
        }
        this.spacing = spacing;
        invalidateLayout();
    }

    public Layout Spacing(int spacing) {
        setSpacing(spacing);
        return this;
    }

    public Layout Padding(int padding) {
        setPadding(padding);
        return this;
    }

    /** 参数顺序：上下、左右。 */
    public Layout Padding(int vertical, int horizontal) {
        setPadding(vertical, horizontal);
        return this;
    }

    /** 参数顺序：上、右、下、左。 */
    public Layout Padding(int top, int right, int bottom, int left) {
        setPadding(top, right, bottom, left);
        return this;
    }

    public <T extends BaseWidget> T addChild(T child, Constraint constraint) {
        Objects.requireNonNull(child, "child");
        Objects.requireNonNull(constraint, "constraint");
        // 已挂载的父控件会在加入时立即激活子控件订阅；先让初始回调看到完整约束。
        constraints.put(child, constraint);
        invalidateLayout();
        try {
            super.addChild(child);
        } finally {
            // 初始回调可能移除或换绑控件，加入失败也不能残留无主约束。
            if (child.getParent() != this) {
                constraints.remove(child);
            }
        }
        return child;
    }

    public Constraint getConstraint(BaseWidget child) {
        requireChild(child);
        return constraints.getOrDefault(child, DEFAULT_CONSTRAINT);
    }

    public void setConstraint(BaseWidget child, Constraint constraint) {
        requireChild(child);
        constraints.put(child, Objects.requireNonNull(constraint, "constraint"));
        invalidateLayout();
    }

    /** 按添加顺序批量配置现有子控件，数量必须一致；校验失败时不修改原约束。 */
    public void setConstraints(Constraint... constraints) {
        List<Constraint> validated = List.of(constraints);
        List<BaseWidget> children = children();
        if (validated.size() != children.size()) {
            throw new IllegalArgumentException("Each existing child must have exactly one constraint");
        }
        for (int i = 0; i < children.size(); i++) {
            this.constraints.put(children.get(i), validated.get(i));
        }
        invalidateLayout();
    }

    public Layout Constraints(Constraint... constraints) {
        setConstraints(constraints);
        return this;
    }

    private void requireChild(BaseWidget child) {
        Objects.requireNonNull(child, "child");
        if (child.getParent() != this) {
            throw new IllegalArgumentException("Constraint must belong to a direct child");
        }
    }

    @Override
    protected void invalidateLayout() {
        if (!applyingLayout) {
            dirty = true;
        }
    }

    @Override
    protected void onChildRemoved(BaseWidget child) {
        constraints.remove(child);
        invalidateLayout();
    }

    @Override
    protected void layoutChildren() {
        if (!dirty) {
            return;
        }
        List<BaseWidget> children = List.copyOf(children());
        List<Constraint> rules = children.stream().map(this::getConstraint).toList();
        int width = getContentWidth();
        int height = getContentHeight();
        boolean horizontal = direction == Direction.HORIZONTAL;
        List<LayoutSolver.Span> spans = LayoutSolver.split(horizontal ? width : height, rules, spacing, flex);
        applyingLayout = true;
        try {
            for (int i = 0; i < children.size(); i++) {
                BaseWidget child = children.get(i);
                LayoutSolver.Span span = spans.get(i);
                child.setRelativePosition(horizontal ? span.offset() : 0, horizontal ? 0 : span.offset());
                child.setSize(horizontal ? span.length() : width, horizontal ? height : span.length());
            }
            dirty = false;
        } finally {
            applyingLayout = false;
        }
    }

    @Override
    protected void renderSelf(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Layout 只分配区域，内容和背景由子控件绘制。
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return isActive() && getChildAt(mouseX, mouseY).isPresent();
    }

    @Override
    protected boolean clicked(double mouseX, double mouseY) {
        return false;
    }

    @Nullable
    @Override
    public ComponentPath nextFocusPath(FocusNavigationEvent event) {
        return children().isEmpty() ? null : super.nextFocusPath(event);
    }
}

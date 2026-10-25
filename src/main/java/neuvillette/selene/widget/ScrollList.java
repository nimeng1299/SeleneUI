package neuvillette.selene.widget;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;

/**
 * 在 {@link ScrollView} 上自动纵向排列子控件的滚动容器，隐藏项仍占据位置。
 * 子控件按添加顺序从上到下排列，保留自身高度，宽度填满视区；无需设置子控件的 x/y 或布局约束。
 * 增删子控件、改变行高、尺寸、padding 或 spacing 后自动重排，虚拟高度随内容总高度更新。
 * 滚轮、内容裁剪、滚动条、淡出及焦点导航与 {@link ScrollView} 共用。
 * 相对 Y 坐标保持列表原有语义：行的自然位置减去当前整数滚动量。
 *
 * <pre>{@code
 * ScrollList list = new ScrollList(20, 20, 240, 180)
 *         .Padding(8)
 *         .Spacing(6)
 *         .ScrollStep(24);
 * list.addChild(new TextButton(0, 0, 0, 32, Component.literal("First")));
 * list.addChild(new TextButton(0, 0, 0, 48, Component.literal("Second")));
 * // 也可以在添加时指定行高：list.addChild(widget, 40)。
 * }</pre>
 */
public class ScrollList extends ScrollView {
    private int spacing;

    public ScrollList() {
        this(0, 0, 0, 0);
    }

    public ScrollList(@Nullable BaseWidget parent, Component message) {
        this(parent, 0, 0, 0, 0, message);
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

    @Override
    public ScrollList Padding(int padding) {
        setPadding(padding);
        return this;
    }

    /** 参数顺序：上下、左右。 */
    @Override
    public ScrollList Padding(int vertical, int horizontal) {
        setPadding(vertical, horizontal);
        return this;
    }

    /** 参数顺序：上、右、下、左。 */
    @Override
    public ScrollList Padding(int top, int right, int bottom, int left) {
        setPadding(top, right, bottom, left);
        return this;
    }

    @Override
    public ScrollList ScrollStep(double scrollStep) {
        setScrollStep(scrollStep);
        return this;
    }

    @Override
    public ScrollList VirtualHeight(int minimum) {
        setVirtualHeight(minimum);
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

    /**
     * 指定初始行高；之后仍可通过 child.setHeight 修改，不形成固定高度约束。
     * 末尾可变参数仅用于消除工厂重载歧义，调用时必须省略。
     */
    public <T extends BaseWidget> T addChild(T child, int height, Void... overloadResolution) {
        requireNoExtraArguments(overloadResolution);
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

    /**
     * 将当前列表传入工厂，再为返回的子控件设置初始行高并加入列表。
     * 工厂应返回尚未绑定父控件的子控件，使其首次挂载回调可以看到正确的行高。
     */
    public <T extends BaseWidget> T addChild(Function<? super ScrollList, ? extends T> factory, int height) {
        Objects.requireNonNull(factory, "factory");
        if (height < 0) {
            throw new IllegalArgumentException("Row height must be nonnegative");
        }
        return addChild(factory.apply(this), height);
    }

    /** 将直接子控件滚入视区；超高行已部分可见时保持位置，否则显示其顶部。 */
    @Override
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

    /** 列表只按直接行的自然高度计量，行内的越界后代不改变行距。 */
    @Override
    protected long measureContentHeight() {
        List<BaseWidget> children = List.copyOf(children());
        long total = (long) Math.max(0, children.size() - 1) * spacing;
        for (BaseWidget child : children) {
            total += Math.max(0, child.getHeight());
        }
        return total;
    }

    @Override
    protected void layoutContent(int viewportWidth, long scrollOffset) {
        long top = 0;
        for (BaseWidget child : List.copyOf(children())) {
            int height = Math.max(0, child.getHeight());
            // 在 long 中减去偏移后才饱和，保留超大内容靠近视区时的精确位置。
            child.setRelativePosition(0, boundedCoordinate(top - scrollOffset));
            child.setSize(viewportWidth, height);
            top += (long) height + spacing;
        }
    }

    /** 行坐标已经减过滚动量，不能再应用 ScrollView 的原点偏移。 */
    @Override
    protected int getChildOriginY() {
        return getContentY();
    }
}

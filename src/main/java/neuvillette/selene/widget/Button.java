package neuvillette.selene.widget;

import java.util.Objects;
import javax.annotation.Nullable;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import neuvillette.selene.api.Color;
import neuvillette.selene.api.Paint;
import neuvillette.selene.api.Rect;
import neuvillette.selene.state.Subscription;

/**
 * 浅米色胶囊文字按钮，使用游戏字体；点击和键盘操作由 BaseButton 处理。
 * 可选图片图标自动裁成圆形，与左端半圆同心；文字在图标右侧的剩余内容区域居中。
 * 文字为空或仅包含空白时，宽度跟随高度形成圆形，图标居中；恢复文字后使用最近设置的宽度。
 * 禁用时背景透明，1 像素边框及文字使用配置的填充色；重新启用后恢复原样式。
 */
public class Button extends BaseButton {
    private static final Runnable NO_ACTION = () -> {};
    private static final Color DEFAULT_TEXT_COLOR = Color.fromRGB(72, 85, 103);

    private Runnable action;
    private Text text;
    private Subscription textChanges;
    private int textWidth;
    private Color fillColor = Color.fromRGB(237, 229, 216);
    @Nullable
    private ResourceLocation icon;
    private float iconScale = 0.6f;
    private int iconGap = 4;

    public Button(@Nullable BaseWidget parent, Text text) {
        this(parent, 0, 0, 0, 0, text);
    }

    public Button(int x, int y, int width, int height, Text text) {
        this(x, y, width, height, text, NO_ACTION);
    }

    public Button(int x, int y, int width, int height, Text text, Runnable action) {
        super(x, y, width, height, Objects.requireNonNull(text, "text").getMessage());
        this.action = Objects.requireNonNull(action, "action");
        this.textWidth = width;
        setText(text);
        setPadding(4, 12);
    }

    public Button(@Nullable BaseWidget parent, int x, int y, int width, int height, Text text) {
        this(parent, x, y, width, height, text, NO_ACTION);
    }

    public Button(@Nullable BaseWidget parent, int x, int y, int width, int height,
                  Text text, Runnable action) {
        this(x, y, width, height, text, action);
        setRelativePosition(parent, x, y);
    }

    private boolean isCircular() {
        return getMessage().getString().isBlank();
    }

    @Override
    public void setMessage(Component message) {
        text.setMessage(message);
    }

    @Override
    public Component getMessage() {
        return text == null ? super.getMessage() : text.getMessage();
    }

    public Text getText() {
        return text;
    }

    public void setText(Text text) {
        Objects.requireNonNull(text, "text");
        if (textChanges != null) textChanges.unsubscribe();
        this.text = text;
        textChanges = text.onChange(this::updateTextWidth);
        updateTextWidth();
    }

    public Button Text(Text text) {
        setText(text);
        return this;
    }

    private void updateTextWidth() {
        super.setWidth(isCircular() ? Math.max(0, height) : textWidth);
    }

    /** 圆形模式记录请求的宽度，实际宽度仍跟随高度；恢复文字后再应用此宽度。 */
    @Override
    public void setWidth(int width) {
        textWidth = width;
        super.setWidth(isCircular() ? Math.max(0, height) : width);
    }

    @Override
    public void setHeight(int height) {
        super.setHeight(height);
        if (isCircular()) {
            super.setWidth(Math.max(0, height));
        }
    }

    @Override
    public void setSize(int width, int height) {
        textWidth = width;
        super.setSize(isCircular() ? Math.max(0, height) : width, height);
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return super.isMouseOver(mouseX, mouseY) && (!isCircular() || isInsideCircle(mouseX, mouseY));
    }

    @Override
    protected boolean clicked(double mouseX, double mouseY) {
        return super.clicked(mouseX, mouseY) && (!isCircular() || isInsideCircle(mouseX, mouseY));
    }

    private boolean isInsideCircle(double mouseX, double mouseY) {
        double radius = getHeight() / 2.0;
        double dx = mouseX - (getX() + radius);
        double dy = mouseY - (getY() + radius);
        return radius > 0 && dx * dx + dy * dy <= radius * radius;
    }

    public Button OnPress(Runnable action) {
        this.action = Objects.requireNonNull(action, "action");
        return this;
    }

    /** 当前控件自身的禁用属性；祖先控件的可用状态仍由 isActive() 合并。 */
    public boolean isDisabled() {
        return !active;
    }

    /** 复用原生 active 状态，禁用时同时结束按压并清理自身及子控件焦点。 */
    public void setDisabled(boolean disabled) {
        active = !disabled;
        if (disabled) {
            setDragging(false);
            setFocused((GuiEventListener) null);
            isHovered = false;
        }
    }

    public Button Disable(boolean disabled) {
        setDisabled(disabled);
        return this;
    }

    public Button FillColor(Color color) {
        fillColor = Objects.requireNonNull(color, "color");
        return this;
    }

    public Button TextColor(Color color) {
        text.TextColor(color);
        return this;
    }

    /** 使用整张纹理作为圆形图标，建议提供正方形图片；传入 null 移除图标。 */
    public Button Icon(@Nullable ResourceLocation texture) {
        icon = texture;
        return this;
    }

    /** 图标直径占按钮短边的比例，默认 0.6，范围为 (0, 1]；改变大小不改变圆心。 */
    public Button IconScale(float scale) {
        if (!Float.isFinite(scale) || scale <= 0 || scale > 1) {
            throw new IllegalArgumentException("Icon scale must be finite and in (0, 1]");
        }
        iconScale = scale;
        return this;
    }

    /** 图标右边缘与文字可用区域之间的最小间距，单位为 GUI 坐标。 */
    public Button IconGap(int gap) {
        if (gap < 0) {
            throw new IllegalArgumentException("Icon gap must be nonnegative");
        }
        iconGap = gap;
        return this;
    }

    /** 相对游戏字体的缩放倍数；高度不足时自动缩小到内容区域内。 */
    public Button TextScale(float scale) {
        text.TextScale(scale);
        return this;
    }

    public Button Padding(int padding) {
        setPadding(padding);
        return this;
    }

    public Button Padding(int vertical, int horizontal) {
        setPadding(vertical, horizontal);
        return this;
    }

    public Button Padding(int top, int right, int bottom, int left) {
        setPadding(top, right, bottom, left);
        return this;
    }

    @Override
    public void onPress() {
        action.run();
    }

    @Override
    protected void renderSelf(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0) {
            return;
        }
        boolean circular = isCircular();
        if (circular) {
            // 原版悬停检测使用矩形，去掉圆形外框四角的悬停效果。
            isHovered = isHovered && isInsideCircle(mouseX, mouseY);
        }
        boolean enabled = isActive();
        if (!enabled) {
            // 也覆盖调用方直接修改 active 或父控件禁用的情况，防止重新启用后恢复旧按压。
            setDragging(false);
        }
        boolean pressed = enabled && isDragging() && getFocused() == null;
        float opacity = Math.clamp(alpha, 0f, 1f);
        float brightness = pressed ? 0.9f : enabled && isHovered() ? 1.04f : 1f;
        Color background = enabled ? tinted(fillColor, brightness, opacity) : Color.fromRGBA(0, 0, 0, 0);
        Color foreground = tinted(enabled ? text.textColorOr(DEFAULT_TEXT_COLOR) : fillColor, 1f, opacity);

        // Paint 立即提交网格，先提交前面的 GUI 绘制以保持图层顺序。
        graphics.flush();
        float outerRadius = Math.min(width, height) / 2f;
        renderBackground(graphics, Rect.from_xywh(getX(), getY(), width, height), outerRadius,
                background, foreground, enabled ? 0 : 1);

        float contentLeft = getContentX();
        float contentRight = contentLeft + getContentWidth();
        if (icon != null) {
            float iconRadius = outerRadius * iconScale;
            float centerX = getX() + outerRadius;
            float centerY = getY() + height / 2f;
            renderIcon(graphics, icon, centerX, centerY, iconRadius,
                    tinted(Color.fromRGB(255, 255, 255), brightness, opacity * (enabled ? 1f : 0.5f)));
            // 以外框确定圆心，padding 只约束文字，避免非对称 padding 使图标偏心。
            contentLeft = Math.max(contentLeft, centerX + iconRadius + iconGap);
        }
        if (circular) {
            return;
        }
        float contentWidth = Math.max(0, contentRight - contentLeft);
        int contentHeight = getContentHeight();
        Text.SingleLine line = text.singleLine(contentWidth, contentHeight, true, false);
        if (line != null) {
            text.renderLine(graphics, line, contentLeft, getContentY(), contentWidth, contentHeight,
                    opacity, enabled ? DEFAULT_TEXT_COLOR : fillColor,
                    Text.HorizontalAlignment.CENTER, Text.VerticalAlignment.CENTER, !enabled);
        }
    }

    protected void renderBackground(GuiGraphics graphics, Rect bounds, float radius, Color color) {
        Paint.drawRoundedRectangle(graphics.pose().last().pose(), bounds, radius, color);
    }

    protected void renderBackground(GuiGraphics graphics, Rect bounds, float radius,
                                    Color fill, Color border, float borderWidth) {
        if (borderWidth <= 0) {
            renderBackground(graphics, bounds, radius, fill);
        } else {
            Paint.drawBorderedRoundedRectangle(graphics.pose().last().pose(), bounds, radius,
                    fill, border, borderWidth);
        }
    }

    protected void renderIcon(GuiGraphics graphics, ResourceLocation texture,
                              float centerX, float centerY, float radius, Color tint) {
        Paint.drawCircularTexture(graphics.pose().last().pose(), centerX, centerY, radius, texture, tint);
    }

    private static Color tinted(Color color, float brightness, float opacity) {
        return Color.fromRGBA(Math.round(color.Red() * brightness), Math.round(color.Green() * brightness),
                Math.round(color.Blue() * brightness), Math.round(Math.clamp(color.Alpha(), 0, 255) * opacity));
    }
}

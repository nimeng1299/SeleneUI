package neuvillette.selene.widget;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.navigation.CommonInputs;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import neuvillette.selene.SeleneUI;
import neuvillette.selene.api.Color;
import neuvillette.selene.api.Paint;
import neuvillette.selene.api.Rect;

/**
 * 纯图标圆形按钮，高度决定直径，message 仅用于朗读，不绘制文字。
 * 图片横向排列两个等大的正方形帧：左侧为正常图标，右侧为按下图标。
 * 鼠标或确认键按住时显示右帧，松开恢复左帧；动作沿用 BaseButton 的按下触发语义。
 * 禁用时使用与按下相同的图片和背景，但不会触发操作；重新启用后恢复正常样式。
 */
public class IconButton extends BaseButton {
    /** variant 目录中内置的左右两帧图标。 */
    public enum Variant {
        PLUS("plus"),
        MINUS("minus");

        private final ResourceLocation texture;

        Variant(String name) {
            texture = ResourceLocation.fromNamespaceAndPath(SeleneUI.MODID, "textures/icon/variant/" + name + ".png");
        }

        public ResourceLocation texture() {
            return texture;
        }
    }

    private Runnable action = () -> {};
    private final Set<Integer> pressedKeys = new HashSet<>();
    @Nullable
    private ResourceLocation icon;
    private float iconScale = 1f;
    private Color fillColor = Color.fromRGB(237, 229, 216);
    private Color pressedFillColor = Color.fromRGB(110, 118, 129);

    public IconButton(@Nullable BaseWidget parent, Component message) {
        this(parent, 0, 0, 0, 0, message);
    }

    public IconButton(int x, int y, int size, Component message) {
        this(x, y, size, size, message);
    }

    public IconButton(int x, int y, int width, int height, Component message) {
        super(x, y, Math.max(0, height), Math.max(0, height), Objects.requireNonNull(message, "message"));
    }

    public IconButton(@Nullable BaseWidget parent, int x, int y, int width, int height, Component message) {
        this(x, y, width, height, message);
        setRelativePosition(parent, x, y);
    }

    /** 接收左右两帧的完整纹理，例如 256×128 的 plus.png；传入 null 清除图标。 */
    public IconButton Icon(@Nullable ResourceLocation texture) {
        icon = texture;
        return this;
    }

    /** 使用内置图标；末尾参数必须省略，用于避免 Icon(null) 的重载歧义。 */
    public IconButton Icon(@Nullable Variant variant, Void... overloadResolution) {
        requireNoExtraArguments(overloadResolution);
        return Icon(variant == null ? null : variant.texture());
    }

    /** 单帧图片相对按钮直径的缩放比例，默认 1；图片自身的透明留白会保留。 */
    public IconButton IconScale(float scale) {
        if (!Float.isFinite(scale) || scale <= 0 || scale > 1) {
            throw new IllegalArgumentException("Icon scale must be finite and in (0, 1]");
        }
        iconScale = scale;
        return this;
    }

    public IconButton FillColor(Color color) {
        fillColor = Objects.requireNonNull(color, "color");
        return this;
    }

    public IconButton PressedFillColor(Color color) {
        pressedFillColor = Objects.requireNonNull(color, "color");
        return this;
    }

    public IconButton OnPress(Runnable action) {
        this.action = Objects.requireNonNull(action, "action");
        return this;
    }

    /** 当前控件自身的禁用属性；祖先控件的可用状态仍由 isActive() 合并。 */
    public boolean isDisabled() {
        return !active;
    }

    public void setDisabled(boolean disabled) {
        active = !disabled;
        if (disabled) {
            setDragging(false);
            setFocused((GuiEventListener) null);
            isHovered = false;
        }
    }

    public IconButton Disable(boolean disabled) {
        setDisabled(disabled);
        return this;
    }

    @Override
    public void onPress() {
        action.run();
    }

    @Override
    public void setWidth(int width) {
        super.setWidth(Math.max(0, height));
    }

    @Override
    public void setHeight(int height) {
        setSize(height, height);
    }

    @Override
    public void setSize(int width, int height) {
        int diameter = Math.max(0, height);
        super.setSize(diameter, diameter);
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return super.isMouseOver(mouseX, mouseY) && isInsideCircle(mouseX, mouseY);
    }

    @Override
    protected boolean clicked(double mouseX, double mouseY) {
        return super.clicked(mouseX, mouseY) && isInsideCircle(mouseX, mouseY);
    }

    private boolean isInsideCircle(double mouseX, double mouseY) {
        double radius = getHeight() / 2.0;
        double dx = mouseX - (getX() + radius);
        double dy = mouseY - (getY() + radius);
        return radius > 0 && dx * dx + dy * dy <= radius * radius;
    }

    /** 只有按钮自身正在按住时才返回 true，单纯获得焦点不会停留在按下样式。 */
    public boolean isPressed() {
        if (!isActive() || !isFocused()) {
            pressedKeys.clear();
            setDragging(false);
            return false;
        }
        if (getFocused() != null) {
            pressedKeys.clear();
            return false;
        }
        return !pressedKeys.isEmpty() || isDragging();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        boolean ownKey = CommonInputs.selected(keyCode) && isActive() && isFocused() && getFocused() == null;
        boolean handled = super.keyPressed(keyCode, scanCode, modifiers);
        // 回调可能移除控件或改变焦点，不能在回调后重新激活已失效的按压。
        if (ownKey && handled && isActive() && isFocused() && getFocused() == null) {
            pressedKeys.add(keyCode);
        }
        return handled;
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        boolean released = pressedKeys.remove(keyCode);
        return super.keyReleased(keyCode, scanCode, modifiers) || released;
    }

    @Override
    public void setFocused(boolean focused) {
        if (!focused) {
            pressedKeys.clear();
        }
        super.setFocused(focused);
    }

    @Override
    public void setFocused(@Nullable GuiEventListener focused) {
        pressedKeys.clear();
        super.setFocused(focused);
    }

    @Override
    protected void renderSelf(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int diameter = getHeight();
        if (diameter <= 0) {
            return;
        }
        isHovered = isHovered && isInsideCircle(mouseX, mouseY);
        // 先查询真实按压以清理禁用时的捕获，再决定外观，避免重新启用后恢复旧按压。
        boolean pressed = isPressed();
        boolean showPressed = pressed || !isActive();
        float opacity = Math.clamp(alpha, 0f, 1f);
        float radius = diameter / 2f;
        Color background = withOpacity(showPressed ? pressedFillColor : fillColor, opacity);
        graphics.flush();
        renderBackground(graphics, Rect.from_xywh(getX(), getY(), diameter, diameter), radius, background);
        if (icon != null) {
            renderIconFrame(graphics, icon, showPressed ? 1 : 0, getX() + radius, getY() + radius,
                    radius * iconScale, Color.fromRGBA(255, 255, 255, Math.round(255 * opacity)));
        }
    }

    protected void renderBackground(GuiGraphics graphics, Rect bounds, float radius, Color color) {
        Paint.drawRoundedRectangle(graphics.pose().last().pose(), bounds, radius, color);
    }

    protected void renderIconFrame(GuiGraphics graphics, ResourceLocation texture, int frame,
                                   float centerX, float centerY, float radius, Color tint) {
        Paint.drawCircularTexture(graphics.pose().last().pose(), centerX, centerY, radius, texture, tint,
                frame * 0.5f, 0, (frame + 1) * 0.5f, 1);
    }

    private static Color withOpacity(Color color, float opacity) {
        return Color.fromRGBA(color.Red(), color.Green(), color.Blue(),
                Math.round(Math.clamp(color.Alpha(), 0, 255) * opacity));
    }
}

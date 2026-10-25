package neuvillette.selene.widget;

import java.util.Objects;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import neuvillette.selene.api.Color;
import neuvillette.selene.api.Paint;
import neuvillette.selene.api.Rect;

/** 浅米色胶囊文字按钮，使用游戏字体；点击和键盘操作由 BaseButton 处理。 */
public class TextButton extends BaseButton {
    private static final Runnable NO_ACTION = () -> {};

    private Runnable action;
    private Color fillColor = Color.fromRGB(237, 229, 216);
    private Color textColor = Color.fromRGB(72, 85, 103);
    private float textScale = 1f;

    public TextButton(@Nullable BaseWidget parent, Component message) {
        this(parent, 0, 0, 0, 0, message);
    }

    public TextButton(int x, int y, int width, int height, Component message) {
        this(x, y, width, height, message, NO_ACTION);
    }

    public TextButton(int x, int y, int width, int height, Component message, Runnable action) {
        super(x, y, width, height, message);
        this.action = Objects.requireNonNull(action, "action");
        setPadding(4, 12);
    }

    public TextButton(@Nullable BaseWidget parent, int x, int y, int width, int height, Component message) {
        this(parent, x, y, width, height, message, NO_ACTION);
    }

    public TextButton(@Nullable BaseWidget parent, int x, int y, int width, int height,
                      Component message, Runnable action) {
        this(x, y, width, height, message, action);
        setRelativePosition(parent, x, y);
    }

    public TextButton OnPress(Runnable action) {
        this.action = Objects.requireNonNull(action, "action");
        return this;
    }

    public TextButton FillColor(Color color) {
        fillColor = Objects.requireNonNull(color, "color");
        return this;
    }

    public TextButton TextColor(Color color) {
        textColor = Objects.requireNonNull(color, "color");
        return this;
    }

    /** 相对游戏字体的缩放倍数；高度不足时自动缩小到内容区域内。 */
    public TextButton TextScale(float scale) {
        if (!Float.isFinite(scale) || scale <= 0) {
            throw new IllegalArgumentException("Text scale must be finite and positive");
        }
        textScale = scale;
        return this;
    }

    public TextButton Padding(int padding) {
        setPadding(padding);
        return this;
    }

    public TextButton Padding(int vertical, int horizontal) {
        setPadding(vertical, horizontal);
        return this;
    }

    public TextButton Padding(int top, int right, int bottom, int left) {
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
        boolean enabled = isActive();
        boolean pressed = enabled && isDragging() && getFocused() == null;
        float opacity = Math.clamp(alpha, 0f, 1f) * (enabled ? 1f : 0.5f);
        float brightness = pressed ? 0.9f : isHovered() ? 1.04f : 1f;
        Color background = tinted(fillColor, brightness, opacity);
        Color foreground = tinted(textColor, 1f, opacity);

        // Paint 立即提交网格，先提交前面的 GUI 绘制以保持图层顺序。
        graphics.flush();
        Paint.drawRoundedRectangle(graphics.pose().last().pose(),
                Rect.from_xywh(getX(), getY(), width, height), Math.min(width, height) / 2f,
                background);

        int contentWidth = getContentWidth();
        int contentHeight = getContentHeight();
        int textAlpha = foreground.Alpha();
        // Minecraft 会把极低的字色 alpha 当成未指定透明度，必须提前跳过。
        if (contentWidth <= 0 || contentHeight <= 0 || textAlpha < 4) {
            return;
        }
        var font = Minecraft.getInstance().font;
        float scale = Math.min(textScale, (float) contentHeight / font.lineHeight);
        int maxTextWidth = (int) Math.floor(contentWidth / (double) scale);
        if (maxTextWidth <= 0) {
            return;
        }
        var text = Language.getInstance().getVisualOrder(font.substrByWidth(getMessage(), maxTextWidth));
        float textX = getContentX() + (contentWidth - font.width(text) * scale) / 2f;
        float textY = getContentY() + (contentHeight - font.lineHeight * scale) / 2f;
        int packedColor = textAlpha << 24 | foreground.Red() << 16 | foreground.Green() << 8 | foreground.Blue();

        graphics.enableScissor(getContentX(), getContentY(),
                getContentX() + contentWidth, getContentY() + contentHeight);
        try {
            graphics.pose().pushPose();
            try {
                graphics.pose().translate(textX, textY, 0);
                graphics.pose().scale(scale, scale, 1f);
                graphics.drawString(font, text, 0, 0, packedColor, false);
            } finally {
                graphics.pose().popPose();
            }
        } finally {
            graphics.disableScissor();
        }
    }

    private static Color tinted(Color color, float brightness, float opacity) {
        return Color.fromRGBA(Math.round(color.Red() * brightness), Math.round(color.Green() * brightness),
                Math.round(color.Blue() * brightness), Math.round(Math.clamp(color.Alpha(), 0, 255) * opacity));
    }
}

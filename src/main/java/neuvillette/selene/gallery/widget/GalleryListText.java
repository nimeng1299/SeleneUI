package neuvillette.selene.gallery.widget;

import java.util.Objects;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import neuvillette.selene.api.Color;
import neuvillette.selene.widget.BaseWidget;
import neuvillette.selene.widget.Block;
import neuvillette.selene.widget.Text;

public class GalleryListText extends Block {
    public boolean is_click_active = false;
    private Text text;


    public GalleryListText(String text) {
        this(null, new Text(text).VerticalAlign(Text.VerticalAlignment.CENTER));
    }

    public GalleryListText(Text text) {
        this(null, text);
    }

    public GalleryListText(@Nullable BaseWidget parent, Text text) {
        super(0, 0, 0, 0, Objects.requireNonNull(text, "text").getMessage());
        this.text = text;
        Radius(2).BorderWidth(0);
        setRelativePosition(parent, 0, 0);
    }

    public Text getText() {
        return text;
    }

    public void setText(Text text) {
        this.text = Objects.requireNonNull(text, "text");
    }

    @Override
    public Component getMessage() {
        return text == null ? super.getMessage() : text.getMessage();
    }

    @Override
    public void setMessage(Component message) {
        text.setMessage(message);
    }

    public GalleryListText SetClick(boolean value) {
        this.is_click_active = value;
        return this;
    }

    @Override
    protected void renderSelf(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderSelf(graphics, mouseX, mouseY, partialTick);

        var font = Minecraft.getInstance().font;
        int textY = getY() + (getHeight() - font.lineHeight) / 2;
        int markerWidth = font.width("I");
        Color textColor = Color.fromRGB(0, 0, 0);

        if (is_click_active) {
            textColor = Color.fromRGB(62, 175, 124);
            graphics.fill(getX(), textY, getX() + markerWidth, textY + font.lineHeight, textColor.toARGB());
        }

        int inset = Math.max(8, markerWidth + 2);
        text.renderIn(graphics, getContentX() + inset, getContentY(),
                Math.max(0, getContentWidth() - inset), getContentHeight(), alpha, textColor);
    }
}

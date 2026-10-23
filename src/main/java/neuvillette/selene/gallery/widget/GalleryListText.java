package neuvillette.selene.gallery.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import neuvillette.selene.api.Color;
import neuvillette.selene.widget.Block;

public class GalleryListText extends Block {
    public boolean is_click_active = false;


    public GalleryListText(String text) {
        super(0, 0, 0, 0, Component.literal(text));
        Radius(2).BorderWidth(0);
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

        graphics.drawString(font, getMessage(),
                getContentX() + Math.max(8, markerWidth + 2), textY, textColor.toARGB(), false);
    }
}

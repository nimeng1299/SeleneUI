package neuvillette.selene.widget;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import neuvillette.selene.api.Color;
import neuvillette.selene.api.Paint;
import neuvillette.selene.api.Rect;

public class Block extends BaseWidget {

    public Block(int x, int y, int width, int height, Component message) {
        super(x, y, width, height, message);
    }

    public Block(BaseWidget parent, int x, int y, int width, int height, Component message) {
        super(parent, x, y, width, height, message);
    }

    int radius = 0;
    int borderSize = 3;
    Color borderColor = Color.fromRGB(255, 0, 0);
    Color fillColor = Color.fromRGB(0, 255, 255);

    public Block Padding(int padding) {
        setPadding(padding);
        return this;
    }

    /** 参数顺序：上下、左右。 */
    public Block Padding(int vertical, int horizontal) {
        setPadding(vertical, horizontal);
        return this;
    }

    /** 参数顺序：上、右、下、左。 */
    public Block Padding(int top, int right, int bottom, int left) {
        setPadding(top, right, bottom, left);
        return this;
    }

    public Block Radius(int radius) {
        this.radius = radius;
        return this;
    }

    public Block BorderWidth(int size) {
        this.borderSize = size;
        return this;
    }

    public Block BorderColor(Color color) {
        this.borderColor = color;
        return this;
    }

    public Block FillColor(Color color) {
        this.fillColor = color;
        return this;
    }

    @Override
    protected void renderSelf(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        Paint.drawBorderedRoundedRectangle(
                guiGraphics.pose().last().pose(),
                Rect.from_xywh(getX(), getY(), getWidth(), getHeight()),
                radius, fillColor, borderColor, borderSize
        );
    }

}

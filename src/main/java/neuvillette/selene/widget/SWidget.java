package neuvillette.selene.widget;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import neuvillette.selene.api.Color;
import neuvillette.selene.api.Paint;
import neuvillette.selene.api.Rect;

public class SWidget extends AbstractWidget {

    public SWidget(int x, int y, int width, int height, Component message) {
        super(x, y, width, height, message);
    }

    int radius = 0;
    int borderSize = 3;
    Color borderColor = Color.fromRGB(255, 0, 0);
    Color fillColor = Color.fromRGB(0, 255, 255);

    public SWidget Radius(int radius) {
        this.radius = radius;
        return this;
    }

    public SWidget BorderWidth(int size) {
        this.borderSize = size;
        return this;
    }

    public SWidget BorderColor(Color color) {
        this.borderColor = color;
        return this;
    }

    public SWidget FillColor(Color color) {
        this.fillColor = color;
        return this;
    }

    @Override
    public void renderWidget(GuiGraphics guiGraphics, int i, int i1, float v) {
        Rect rect = Rect.from_xywh(200, 100, 50, 40);
        Color fill = Color.fromRGB(40, 160, 255);
        Color border = Color.fromRGB(255, 255, 255);

// 圆角半径 10
        Paint.drawRoundedRectangle(Rect.from_xywh(200, 200, 50, 40), 5, fill);

// 圆角半径 10，边框宽度 2
        Paint.drawBorderedRoundedRectangle(Rect.from_xywh(100, 200, 50, 40), 5, fill, border, 2);

        // 圆角半径 10，边框宽度 2
        Paint.drawBorderedRectangle(rect, fill, border, 2);
        Paint.drawBorderedRoundedRectangle(rect, 5, fill, border, 2);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {

    }


}

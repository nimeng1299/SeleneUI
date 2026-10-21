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
        Color color = Color.fromRGB(40, 160, 255);

// 圆心 (100, 100)，半径 30
        Paint.drawCircle(200, 200, 30, color);

// 右上方的四分之一圆
        Paint.drawSector(100, 100, 30, 0, 90, color);

// 跨过 0°，绘制顶部的 90° 扇形
        Paint.drawSector(200, 100, 30, 315, 45, color);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {

    }


}

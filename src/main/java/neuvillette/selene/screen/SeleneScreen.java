package neuvillette.selene.screen;

import java.util.List;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import neuvillette.selene.api.Color;
import neuvillette.selene.widget.BaseWidget;
import neuvillette.selene.widget.Block;

public class SeleneScreen extends Screen {
    private BaseWidget widget;

    public SeleneScreen(Component title, BaseWidget widget) {
        super(title);
        this.widget = widget;
    }

    @Override
    protected void init() {
        super.init();
        this.addRenderableWidget(widget); // 子控件由父控件绘制和分发事件
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        super.mouseMoved(mouseX, mouseY);
        for (var child : List.copyOf(children())) {
            child.mouseMoved(mouseX, mouseY);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false; // 打开界面时游戏是否暂停，默认 true
    }
}

package neuvillette.selene.screen;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import neuvillette.selene.widget.SWidget;

public class SeleneScreen extends Screen {
    public SeleneScreen(Component title) {
        super(title);
    }

    @Override
    protected void init() {
        super.init();
        this.addRenderableWidget(new SWidget(50, 50, 150, 150, Component.literal("Swidget")));
    }

    @Override
    public boolean isPauseScreen() {
        return false; // 打开界面时游戏是否暂停，默认 true
    }
}

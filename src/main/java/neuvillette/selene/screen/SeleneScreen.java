package neuvillette.selene.screen;

import java.util.List;
import java.util.Objects;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import neuvillette.selene.widget.BaseWidget;

public class SeleneScreen extends Screen {
    protected final BaseWidget root;
    protected final boolean fitToScreen;

    public SeleneScreen(Component title, BaseWidget root) {
        this(title, root, false);
    }

    /** fitToScreen 为 true 时，在打开或窗口尺寸变化时让根控件填满 GUI 区域。 */
    public SeleneScreen(Component title, BaseWidget root, boolean fitToScreen) {
        super(title);
        this.root = Objects.requireNonNull(root, "widget");
        this.fitToScreen = fitToScreen;
    }

    @Override
    protected void init() {
        super.init();
        if (fitToScreen) {
            root.setRectangle(width, height, 0, 0);
        }
        root.mount();
        root.layout();
        this.addRenderableWidget(root); // 子控件由父控件绘制和分发事件
    }

    @Override
    public void removed() {
        root.unmount();
        super.removed();
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

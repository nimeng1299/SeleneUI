package neuvillette.selene.screen;

import net.minecraft.network.chat.Component;
import neuvillette.selene.layout.Constraint;
import neuvillette.selene.widget.Layout;
import neuvillette.selene.widget.ScrollView;
import neuvillette.selene.widget.Button;

/** /selene scroll：自由放置的子控件自动扩展虚拟高度，只有根控件注册到 Screen。 */
public class ScrollViewDemoScreen extends SeleneScreen {
    public ScrollViewDemoScreen() {
        this(new Layout().Padding(16).Spacing(8));
    }

    private ScrollViewDemoScreen(Layout root) {
        super(Component.literal("Scroll view demo"), root, true);
        Button top = root.addChild(new Button(0, 0, 0, 28,
                Component.literal("Scroll view - click to return to top")), Constraint.length(28));
        ScrollView view = root.addChild(new ScrollView().Padding(8).ScrollStep(24), Constraint.fill(1));
        top.OnPress(() -> view.setScrollAmount(0));

        // x/y 都是虚拟内容坐标，不需要预先设置总高度。
        for (int i = 0; i < 12; i++) {
            int index = i + 1;
            Button button = view.addChild(new Button(12 + (i % 2) * 48, 24 + i * 80,
                    140, 32, Component.literal("Item " + index)));
            button.OnPress(() -> button.setMessage(Component.literal("Selected " + index)));
        }
    }
}

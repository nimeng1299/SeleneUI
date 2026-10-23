package neuvillette.selene.screen;

import net.minecraft.network.chat.Component;
import neuvillette.selene.layout.Constraint;
import neuvillette.selene.widget.Layout;
import neuvillette.selene.widget.Separator;
import neuvillette.selene.widget.Text;

/** /selene separator：使用默认颜色和粗细展示横向、纵向分隔栏。 */
public class SeparatorDemoScreen extends SeleneScreen {
    public SeparatorDemoScreen() {
        this(new Layout().Padding(16).Spacing(12));
    }

    private SeparatorDemoScreen(Layout root) {
        super(Component.literal("Separators / 分隔栏"), root, true);
        root.addChild(new Text(0, 0, 0, 20, "分隔栏 / Separators"), Constraint.length(20));
        root.addChild(new Separator(), Constraint.length(1));

        Layout columns = root.addChild(new Layout().Horizontal().Spacing(16).Padding(8, 0),
                Constraint.fill(1));
        columns.addChild(new Text(0, 0, 0, 0, "左侧内容 / Left"), Constraint.fill(1));
        columns.addChild(new Separator().Vertical(), Constraint.length(1));
        columns.addChild(new Text(0, 0, 0, 0, "右侧内容 / Right"), Constraint.fill(1));
    }
}

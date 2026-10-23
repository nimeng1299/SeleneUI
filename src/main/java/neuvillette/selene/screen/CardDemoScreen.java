package neuvillette.selene.screen;

import net.minecraft.network.chat.Component;
import neuvillette.selene.api.Color;
import neuvillette.selene.layout.Constraint;
import neuvillette.selene.widget.BaseWidget;
import neuvillette.selene.widget.Card;
import neuvillette.selene.widget.Layout;
import neuvillette.selene.widget.ScrollList;
import neuvillette.selene.widget.Separator;
import neuvillette.selene.widget.Text;
import neuvillette.selene.widget.TextButton;

/** /selene card：边框标题、透明背景与动态标题。 */
public class CardDemoScreen extends SeleneScreen {
    private int updates;

    public CardDemoScreen() {
        this(new Layout().Padding(16).Spacing(8));
    }

    private CardDemoScreen(Layout root) {
        super(Component.literal("Cards / 卡片"), root, true);
        root.addChild(new Text(0, 0, 0, 20, "卡片 / Cards"), Constraint.length(20));
        ScrollList content = root.addChild(new ScrollList().Padding(8).Spacing(16), Constraint.fill(1));

        content.addChild(self -> {
            Card card = card("实色卡片 / Solid background");
            Layout body = card.addChild(parent -> new Layout().Spacing(8));
            body.addChild(new Text(0, 0, 0, 0, "标题嵌在左上边框，内容区域自动避开标题。"),
                    Constraint.fill(1));
            body.addChild(new Separator(), Constraint.length(1));
            body.addChild(new TextButton(0, 0, 0, 28, Component.literal("更新标题 / Update title"))
                    .OnPress(() -> card.Title("标题已更新 " + ++updates + " 次 / Updated")),
                    Constraint.length(28));
            return card;
        }, 144);

        content.addChild(self -> {
            Card card = card("透明卡片 / Transparent background")
                    .FillColor(Color.fromRGBA(0, 0, 0, 0));
            Layout body = card.addChild(parent -> new Layout());
            body.addChild(new Text(0, 0, 0, 0,
                    "背景保持透明，标题两侧的边框仍然可见。\nThe game remains visible behind this card."),
                    Constraint.fill(1));
            return card;
        }, 100);

        content.addChild(self -> {
            Card card = card("一个很长的卡片标题：超过边框可用宽度时截断 / "
                    + "A deliberately long card title that stays inside its border even in a narrow window");
            Layout body = card.addChild(parent -> new Layout());
            body.addChild(new Text(0, 0, 0, 0,
                    "调整窗口宽度，观察长标题的裁剪。\nResize the window to see the title stay within its border."),
                    Constraint.fill(1));
            return card;
        }, 100);
    }

    private static Card card(String title) {
        return new Card(title) {
            @Override
            protected void layoutChildren() {
                super.layoutChildren();
                // Card 保持自由定位；示例主动让内层 Layout 填满标题下方的内容区域。
                int width = getContentWidth();
                int height = getContentHeight();
                for (BaseWidget child : children()) {
                    if (child.getRelativeX() != 0 || child.getRelativeY() != 0) {
                        child.setRelativePosition(0, 0);
                    }
                    if (child.getWidth() != width || child.getHeight() != height) {
                        child.setSize(width, height);
                    }
                }
            }
        }.Padding(12);
    }
}

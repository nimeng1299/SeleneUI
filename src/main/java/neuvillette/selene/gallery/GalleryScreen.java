package neuvillette.selene.gallery;

import net.minecraft.network.chat.Component;
import neuvillette.selene.api.Color;
import neuvillette.selene.gallery.widget.GalleryListText;
import neuvillette.selene.layout.Constraint;
import neuvillette.selene.screen.SeleneScreen;
import neuvillette.selene.state.State;
import neuvillette.selene.widget.Block;
import neuvillette.selene.widget.Layout;
import neuvillette.selene.widget.ScrollList;

public class GalleryScreen extends SeleneScreen {
    private final Layout layout = new Layout().Padding(8, 8, 8, 0).Spacing(8).Horizontal();

    State<Integer> pageSelected = new State<>(0);


    public GalleryScreen() {
        super(Component.literal("gallery"), new Block(0, 0, 0, 0, Component.literal("gallery")), true);
        getRoot()
                .BorderWidth(0)
                .BorderColor(Color.fromRGBA(0, 0, 0, 0))
                .FillColor(Color.fromRGBA(255, 255, 255, 255))
                .addChild(layout);
        DrawNavigation(layout);
        layout.addChild(panel("right", 40, 100, 180), Constraint.fill(1));
    }

    @Override
    protected void init() {
        super.init();
        // Block 不分配子控件尺寸，打开和窗口缩放时显式撑满内容区域。
        layout.setSize(getRoot().getContentWidth(), getRoot().getContentHeight());
        layout.layout();
    }

    public Block getRoot(){
        return (Block)root;
    }

    private void DrawNavigation(Layout root){
        // 导航和裁剪区域延伸到屏幕左缘，原来的左侧留白移到列表项内部。
        ScrollList list = root.addChild(new ScrollList().Padding(8, 8, 8, 0).Spacing(1).ScrollStep(24),
                Constraint.length(90));

        for (int i = 0; i < 40; i++) {
            int index = i;
            list.addChild(new GalleryListText("Item " + (index + 1)) {
                {
                    Padding(0, 0, 0, 16 + 8);
                    FillColor(Color.fromRGBA(0, 0, 0, 0));
                    BorderColor(Color.fromRGBA(0, 0, 0, 0));
                    subscribe(pageSelected, value -> SetClick(value == index));
                }

                @Override
                public void onClick(double mouseX, double mouseY, int button) {
                    pageSelected.set(index);
                }
            }, 15);
        }
    }


    private static Block panel(String title, int red, int green, int blue) {
        return new Block(0, 0, 0, 0, Component.literal(title))
                .Radius(5)
                .BorderWidth(1)
                .BorderColor(Color.fromRGB(180, 200, 220))
                .FillColor(Color.fromRGB(red, green, blue));
    }
}

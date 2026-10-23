package neuvillette.selene.gallery;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import neuvillette.selene.api.Color;
import neuvillette.selene.gallery.widget.GalleryListText;
import neuvillette.selene.layout.Constraint;
import neuvillette.selene.screen.SeleneScreen;
import neuvillette.selene.state.State;
import neuvillette.selene.widget.BaseWidget;
import neuvillette.selene.widget.Block;
import neuvillette.selene.widget.Layout;
import neuvillette.selene.widget.ScrollList;

public class GalleryScreen extends SeleneScreen {
    private final Layout layout = new Layout().Padding(8, 8, 8, 0).Spacing(8).Horizontal();

    final State<Integer> pageSelected = new State<>(0);


    public GalleryScreen() {
        super(Component.literal("gallery"), new Block(0, 0, 0, 0, Component.literal("gallery")), true);
        getRoot()
                .BorderWidth(0)
                .BorderColor(Color.fromRGBA(0, 0, 0, 0))
                .FillColor(Color.fromRGBA(220, 220, 220, 100))
                .addChild(layout);
        DrawNavigation(layout);
        layout.addChild(new Layout() {
            {
                // 与左侧高亮共用状态，挂载时也会立即显示初始选中的页面。
                subscribe(pageSelected, index -> {
                    clearChildren();
                    addChild(createPage(index), Constraint.fill(1));
                });
            }
        }, Constraint.fill(1));
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

    /** 在这里将各选项映射到实际页面；页面可以是任意 BaseWidget（如 Block 或 Layout）。 */
    private static BaseWidget createPage(int index) {
        return switch (index) {
            case 0 -> panel("Page 1", 40, 100, 180);
            case 1 -> panel("Page 2", 45, 120, 85);
            case 2 -> panel("Page 3", 110, 80, 150);
            default -> panel("Page " + (index + 1), 40, 100, 180);
        };
    }

    private static ScrollList panel(String title, int red, int green, int blue) {
        return new ScrollList(0, 0, 0, 0, Component.literal(title)) {
            @Override
            protected void renderSelf(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
                super.renderSelf(graphics, mouseX, mouseY, partialTick);
                graphics.enableScissor(getX(), getY(), getX() + getWidth(), getY() + getHeight());
                try {
                    graphics.drawString(Minecraft.getInstance().font, getMessage(),
                            getX() + 12, getY() + 12, 0xFFFFFFFF, false);
                } finally {
                    graphics.disableScissor();
                }
            }
        };
    }
}

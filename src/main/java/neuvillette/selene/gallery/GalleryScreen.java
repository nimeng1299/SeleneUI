package neuvillette.selene.gallery;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import neuvillette.selene.SeleneUI;
import neuvillette.selene.api.Color;
import neuvillette.selene.gallery.widget.GalleryListText;
import neuvillette.selene.layout.Constraint;
import neuvillette.selene.screen.SeleneScreen;
import neuvillette.selene.state.State;
import neuvillette.selene.widget.*;

import java.util.ArrayList;
import java.util.List;

public class GalleryScreen extends SeleneScreen {
    private final Layout layout;

    private final List<String> titles = new ArrayList<>(List.of("Button", "Card"));

    final State<Integer> pageSelected = new State<>(0);


    public GalleryScreen() {
        super(Component.literal("gallery"), new Block(0, 0, 0, 0, Component.literal("gallery")), true);
        layout = getRoot()
                .BorderWidth(0)
                .BorderColor(Color.fromRGBA(0, 0, 0, 0))
                .FillColor(Color.fromRGBA(220, 220, 220, 100))
                .addChild(parent -> new Layout(parent).Padding(8, 8, 8, 0).Spacing(8).Horizontal());
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

        for (int i = 0; i < titles.size(); i++) {
            int index = i;
            list.addChild(new GalleryListText(titles.get(i)) {
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

    /** 在这里将各选项映射到实际页面*/
    private static BaseWidget createPage(int index) {
        return switch (index) {
            case 0 -> buttonPage();
            case 1 -> cardPage();
            case 2 -> panel("Page 3", 110, 80, 150);
            default -> panel("Page " + (index + 1), 40, 100, 180);
        };
    }

    private static ScrollList buttonPage(){
        ScrollList view = page_head(Component.literal("button_page"), Component.literal("按钮"));

        Card textCard = view.addChild(card(Component.literal("文字按钮")), 38);
        textCard.addChild(parent -> new Button(parent, 0, 0, 80, parent.getContentHeight(), Component.literal("文字按钮")));

        Card disabledCard = view.addChild(card(Component.literal("禁用按钮")), 38);
        disabledCard.addChild(parent -> new Button(parent, 0, 0, 80, parent.getContentHeight(),
                Component.literal("不可点击")).Disable(true));

        Card iconCard = view.addChild(card(Component.literal("带图标文字按钮")), 38);
        Layout iconLayout = iconCard.addChild(parent -> new Layout().Padding(0).Spacing(3).Horizontal());
        iconLayout.addChild(parent -> new Button(parent, 0, 0, 80, parent.getContentHeight(),
                Component.literal("同意"))
                .Icon(ResourceLocation.fromNamespaceAndPath(SeleneUI.MODID, "textures/icon/agree.png")), Constraint.length(80));
        iconLayout.addChild(parent -> new Button(parent, 0, 0, 80, parent.getContentHeight(),
                Component.literal("取消"))
                .Icon(ResourceLocation.fromNamespaceAndPath(SeleneUI.MODID, "textures/icon/close.png")), Constraint.length(80));

        Card roundCard = view.addChild(card(Component.literal("无文字图标按钮")), 38);
        Layout roundLayout = roundCard.addChild(new Layout().Horizontal().Spacing(6));
        roundLayout.addChild(parent -> new Button(parent, 0, 0, 80, parent.getContentHeight(), Component.empty())
                .Icon(ResourceLocation.fromNamespaceAndPath(SeleneUI.MODID, "textures/icon/agree.png")),
                parent -> Constraint.length(parent.getContentHeight() + 1));
        roundLayout.addChild(parent -> new Button(parent, 0, 0, 80, parent.getContentHeight(), Component.empty())
                        .Icon(ResourceLocation.fromNamespaceAndPath(SeleneUI.MODID, "textures/icon/close.png")),
                parent -> Constraint.length(parent.getContentHeight() + 1));

        Card variantCard = view.addChild(card(Component.literal("点按切换图标")), 38);
        Layout variantLayout = variantCard.addChild(new Layout().Horizontal().Spacing(6));
        variantLayout.addChild(new IconButton(null, Component.literal("增加"))
                .Icon(IconButton.Variant.PLUS),
                parent -> Constraint.length(parent.getContentHeight() + 1));
        variantLayout.addChild(new IconButton(null, Component.literal("减少"))
                .Icon(IconButton.Variant.MINUS),
                parent -> Constraint.length(parent.getContentHeight() + 1));

        Card disabledIconCard = view.addChild(card(Component.literal("禁用图标按钮")), 38);
        disabledIconCard.addChild(parent -> new IconButton(parent, 0, 0, 0, parent.getContentHeight(),
                Component.literal("不可增加")).Icon(IconButton.Variant.PLUS).Disable(true));
        return view;
    }

    private static ScrollList cardPage(){
        ScrollList view = page_head(Component.literal("button_page"), Component.literal("按钮"));

        return view;
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

    private static ScrollList page_head(Component page_id, Component title){
        var view = new ScrollList(0, 0, 0, 0, page_id).Spacing(4);

        view.addChild(new Text(0, 0, 0, 18, title))
                .TextScale(1.5f).Align(Text.HorizontalAlignment.LEFT);
        view.addChild(new Separator().Horizontal());

        return view;

    }

    private static Card card(Component component) {
        return new Card(component) {
            @Override
            protected void layoutChildren() {
                super.layoutChildren();
                // Gallery 中的内层 Layout 填满卡片内容区，并随卡片尺寸变化重新布局。
                int width = getContentWidth();
                int height = getContentHeight();
                for (BaseWidget child : children()) {
                    if (child instanceof Layout) {
                        if (child.getRelativeX() != 0 || child.getRelativeY() != 0) {
                            child.setRelativePosition(0, 0);
                        }
                        if (child.getWidth() != width || child.getHeight() != height) {
                            child.setSize(width, height);
                        }
                    }
                }
            }
        }
                .FillColor(Color.fromRGBA(0, 0, 0, 0))
                .BorderColor(Color.fromRGB(114, 114, 114))
                .BorderWidth(1)
                .Radius(3)
                .Padding(5, 10);
    }
}

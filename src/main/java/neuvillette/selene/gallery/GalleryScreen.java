package neuvillette.selene.gallery;

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
        ScrollList view = page_head(Component.literal("button_page"), new Text("按钮"));

        Card textCard = view.addChild(card(new Text("文字按钮")), 38);
        textCard.addChild(parent -> new Button(parent, 0, 0, 80, parent.getContentHeight(), new Text("文字按钮")));

        Card disabledCard = view.addChild(card(new Text("禁用按钮")), 38);
        disabledCard.addChild(parent -> new Button(parent, 0, 0, 80, parent.getContentHeight(),
                new Text("不可点击")).Disable(true));

        Card iconCard = view.addChild(card(new Text("带图标文字按钮")), 38);
        Layout iconLayout = iconCard.addChild(parent -> new Layout().Padding(0).Spacing(3).Horizontal());
        iconLayout.addChild(parent -> new Button(parent, 0, 0, 80, parent.getContentHeight(),
                new Text("同意"))
                .Icon(ResourceLocation.fromNamespaceAndPath(SeleneUI.MODID, "textures/icon/agree.png")), Constraint.length(80));
        iconLayout.addChild(parent -> new Button(parent, 0, 0, 80, parent.getContentHeight(),
                new Text("取消"))
                .Icon(ResourceLocation.fromNamespaceAndPath(SeleneUI.MODID, "textures/icon/close.png")), Constraint.length(80));

        Card roundCard = view.addChild(card(new Text("无文字图标按钮")), 38);
        Layout roundLayout = roundCard.addChild(new Layout().Horizontal().Spacing(6));
        roundLayout.addChild(parent -> new Button(parent, 0, 0, 80, parent.getContentHeight(), new Text(""))
                .Icon(ResourceLocation.fromNamespaceAndPath(SeleneUI.MODID, "textures/icon/agree.png")),
                parent -> Constraint.length(parent.getContentHeight() + 1));
        roundLayout.addChild(parent -> new Button(parent, 0, 0, 80, parent.getContentHeight(), new Text(""))
                        .Icon(ResourceLocation.fromNamespaceAndPath(SeleneUI.MODID, "textures/icon/close.png")),
                parent -> Constraint.length(parent.getContentHeight() + 1));

        Card variantCard = view.addChild(card(new Text("点按切换图标")), 38);
        Layout variantLayout = variantCard.addChild(new Layout().Horizontal().Spacing(6));
        variantLayout.addChild(new IconButton(null, Component.literal("增加"))
                .Icon(IconButton.Variant.PLUS),
                parent -> Constraint.length(parent.getContentHeight() + 1));
        variantLayout.addChild(new IconButton(null, Component.literal("减少"))
                .Icon(IconButton.Variant.MINUS),
                parent -> Constraint.length(parent.getContentHeight() + 1));

        Card disabledIconCard = view.addChild(card(new Text("禁用图标按钮")), 38);
        disabledIconCard.addChild(parent -> new IconButton(parent, 0, 0, 0, parent.getContentHeight(),
                Component.literal("不可增加")).Icon(IconButton.Variant.PLUS).Disable(true));
        return view;
    }

    private static ScrollList cardPage(){
        ScrollList view = page_head(Component.literal("card_page"), new Text("卡片"));

        view.addChild(card(new Text("实色卡片 / Card")).FillColor(Color.fromRGB(114, 114, 114)).BorderColor(Color.fromRGB(0, 0, 0)), 50)
                .addChild(parent -> new Text(parent, Component.literal("标题嵌在左上边框，内容区域自动避开标题。")));

        view.addChild(card(new Text("透明卡片")), 50)
                .addChild(parent -> new Text(parent, Component.literal("背景保持透明，标题两侧的边框仍然可见。\nThe game remains visible behind this card.")));

        view.addChild(card(new Text("一个很长的卡片标题：超过边框可用宽度时截断 / "
                        + "A deliberately long card title that stays inside its border even in a narrow window")), 50)
                .addChild(parent -> new Text(parent, Component.literal("调整窗口宽度，观察长标题的裁剪。\nResize the window to see the title stay within its border.")));

        Card infoCard = view.addChild(card(new Text("信息卡 / InfoCard")), 100);
        Layout infoLayout = infoCard.addChild(new Layout().Horizontal().Spacing(6));
        infoLayout.addChild(parent -> new InfoCard(parent, 0, 0, 60, parent.getContentHeight(), new Text("下界合金剑"))
                .Background(InfoCard.BackgroundPreset.GOLD)
                .Image(ResourceLocation.withDefaultNamespace("textures/item/netherite_sword.png"))
                .ImageScale(0.85f).Radius(4).FooterRadius(12).FooterHeight(20), Constraint.length(60));
        infoLayout.addChild(parent -> new InfoCard(parent, 0, 0, 60, parent.getContentHeight(), new Text("钻石剑"))
                .Background(InfoCard.BackgroundPreset.PURPLE)
                .Image(ResourceLocation.withDefaultNamespace("textures/item/diamond_sword.png"))
                .ImageScale(0.85f).Radius(4).FooterRadius(12).FooterHeight(20), Constraint.length(60));

        Card detailCard = view.addChild(card(new Text("详情卡 / DetailCard")), 220);
        Layout detailLayout = detailCard.addChild(new Layout().Horizontal());
        DetailCard detail = detailLayout.addChild(parent -> new DetailCard(parent, 0, 0, 170, parent.getContentHeight(), new Text("假的天空之翼").TextScale(1.15f))
                .HeaderHeight(24).BodyHeight(70)
                .Image(ResourceLocation.withDefaultNamespace("textures/item/bow.png"))
                .ImageScale(0.75f), Constraint.length(170));
        detail.getHeader().Padding(6, 10);
        detail.getBody().Padding(6, 8).Spacing(0);
        detail.getBody().addChild(new Text("弓").TextScale(0.8f), Constraint.length(12));
        detail.getBody().addChild(new Text("暴击率").TextScale(0.8f).TextColor(Color.fromRGB(204, 194, 173)), Constraint.length(10));
        detail.getBody().addChild(new Text("99.9%").TextScale(0.8f), Constraint.length(12));
        detail.getBody().addChild(new Text("基础攻击力").TextScale(0.8f).TextColor(Color.fromRGB(204, 194, 173)), Constraint.length(10));
        detail.getBody().addChild(new Text("924").TextScale(1.2f), Constraint.length(20));
        detail.getFooter().addChild(new Text("· 攻击力+99\n· 暴击率+99.9%\n· 元素精通+60\n· 暴击伤害+150.2%")
                .TextScale(0.8f).LineSpacing(3), Constraint.length(48));
        detail.getFooter().addChild(new Text("象征风龙身为风之神眷属的大弓。释弦时的鸣声是风之神的偏爱，蕴含苍空与长风的力量")
                .TextColor(Color.fromRGB(123, 129, 134)).TextScale(0.8f).LineSpacing(3), Constraint.fill(1));
        return view;
    }

    private static ScrollList panel(String title, int red, int green, int blue) {
        Text label = new Text(title).Padding(12);
        return new ScrollList(0, 0, 0, 0, Component.literal(title)) {
            @Override
            protected void renderSelf(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
                super.renderSelf(graphics, mouseX, mouseY, partialTick);
                label.renderIn(graphics, getX(), getY(), getWidth(), getHeight(), alpha);
            }
        };
    }

    private static ScrollList page_head(Component page_id, Text title){
        var view = new ScrollList(0, 0, 0, 0, page_id).Spacing(4);

        title.setHeight(18);
        view.addChild(title)
                .TextScale(1.5f).Align(Text.HorizontalAlignment.LEFT);
        view.addChild(new Separator().Horizontal());

        return view;

    }

    private static Card card(Text component) {
        return new Card(component) {
            @Override
            protected void layoutChildren() {
                super.layoutChildren();
                // Gallery 中的 Layout 和正文 Text 填满卡片内容区，并随卡片尺寸变化重新布局。
                int width = getContentWidth();
                int height = getContentHeight();
                for (BaseWidget child : children()) {
                    if (child instanceof Layout || child instanceof Text) {
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

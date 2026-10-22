package neuvillette.selene.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import neuvillette.selene.api.Color;
import neuvillette.selene.layout.Constraint;
import neuvillette.selene.state.State;
import neuvillette.selene.widget.Block;
import neuvillette.selene.widget.Layout;
import neuvillette.selene.widget.ScrollList;

/** /selene list：不同高度的可点击列表项，支持滚轮和拖动滚动条。 */
public class ListDemoScreen extends SeleneScreen {
    public ListDemoScreen() {
        this(new Layout().Padding(16).Spacing(8));
    }

    private ListDemoScreen(Layout root) {
        super(Component.literal("Scroll list demo"), root, true);
        State<Integer> selected = new State<>(-1);
        TextPanel header = root.addChild(new TextPanel("Scroll or drag the bar; click a row") {
            {
                subscribe(selected, value -> setMessage(Component.literal(value < 0
                        ? "Scroll or drag the bar; click a row" : "Selected item: " + (value + 1))));
            }
        }, Constraint.length(28));
        header.FillColor(Color.fromRGB(35, 45, 60));

        ScrollList list = root.addChild(new ScrollList().Padding(8).Spacing(6).ScrollStep(24), Constraint.fill(1));
        for (int i = 0; i < 40; i++) {
            int index = i;
            list.addChild(new TextPanel("Item " + (index + 1)) {
                {
                    subscribe(selected, value -> FillColor(value == index
                            ? Color.fromRGB(40, 120, 180) : Color.fromRGB(45, 60, 80)));
                }

                @Override
                public void onClick(double mouseX, double mouseY, int button) {
                    selected.set(index);
                }
            }, i % 5 == 0 ? 40 : 28);
        }
    }

    private static class TextPanel extends Block {
        TextPanel(String text) {
            super(0, 0, 0, 0, Component.literal(text));
            Radius(4).BorderWidth(1).BorderColor(Color.fromRGB(110, 140, 170));
        }

        @Override
        protected void renderSelf(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            super.renderSelf(graphics, mouseX, mouseY, partialTick);
            graphics.drawString(Minecraft.getInstance().font, getMessage(),
                    getX() + 8, getY() + (getHeight() - 9) / 2, 0xFFFFFFFF);
        }
    }
}

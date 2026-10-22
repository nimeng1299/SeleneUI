package neuvillette.selene.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import neuvillette.selene.api.Color;
import neuvillette.selene.state.State;
import neuvillette.selene.widget.Block;
import neuvillette.selene.widget.Layout;

/** 跨组件数据同步 */
public class StateDemoScreen extends SeleneScreen {
    private final State<Integer> count = new State<>(0);

    public StateDemoScreen() {
        this(new Layout().Horizontal().Padding(16).Spacing(8));
    }

    private StateDemoScreen(Layout root) {
        super(Component.literal("Shared state demo"), root, true);
        root.addChild(new CounterPanel(count, "Left", Color.fromRGB(40, 100, 180), false));
        root.addChild(new CounterPanel(count, "Right", Color.fromRGB(35, 130, 130), true));
    }

    private static final class CounterPanel extends Block {
        private final State<Integer> count;

        private final boolean is_add;

        CounterPanel(State<Integer> count, String labelName, Color color, boolean is_add) {
            super(0, 0, 0, 0, Component.empty());
            this.count = count;
            this.is_add = is_add;
            Radius(5).BorderWidth(1).BorderColor(Color.fromRGB(180, 200, 220)).FillColor(color);
            subscribe(count, value -> setMessage(Component.literal(labelName + ": " + value)));
        }

        @Override
        public void onClick(double mouseX, double mouseY, int button) {
            count.update(value -> {
                if (is_add) {
                    value += 1;
                }else  {
                    value -= 1;
                }
                return value;
            });
        }

        @Override
        protected void renderSelf(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            super.renderSelf(graphics, mouseX, mouseY, partialTick);
            var font = Minecraft.getInstance().font;
            int centerX = getX() + getWidth() / 2;
            int centerY = getY() + getHeight() / 2;
            graphics.drawCenteredString(font, getMessage(), centerX, centerY - 10, 0xFFFFFFFF);
            graphics.drawCenteredString(font, "Click either panel: ±1", centerX, centerY + 3, 0xFFFFFFFF);
        }
    }
}

package neuvillette.selene.screen;

import net.minecraft.network.chat.Component;
import neuvillette.selene.api.Color;
import neuvillette.selene.layout.Constraint;
import neuvillette.selene.widget.Layout;
import neuvillette.selene.widget.ScrollList;
import neuvillette.selene.widget.Text;
import neuvillette.selene.widget.Button;

/** /selene text：颜色、对齐、换行与动态文本，仅根布局注册到 Screen。 */
public class TextDemoScreen extends SeleneScreen {
    private int updates;

    public TextDemoScreen() {
        this(new Layout().Padding(16).Spacing(8));
    }

    private TextDemoScreen(Layout root) {
        super(Component.literal("Text demo / 文本示例"), root, true);
        root.addChild(new Text(0, 0, 0, 32, "Text / 文本控件")
                .TextScale(1.4f)
                .Align(Text.HorizontalAlignment.CENTER)
                .VerticalAlign(Text.VerticalAlignment.CENTER), Constraint.length(32));

        ScrollList content = root.addChild(new ScrollList().Padding(8).Spacing(8).ScrollStep(24),
                Constraint.fill(1));
        content.addChild(new Text(0, 0, 0, 42,
                "滚动查看不同文本样式。Text 保持固定区域，支持中文与 English；底部按钮可更新文本。")
                .Padding(4, 8).LineSpacing(3), 42);

        content.addChild(new Text(0, 0, 0, 36, "左对齐 / Left — 青色文字")
                .TextColor(Color.fromRGB(128, 213, 237))
                .Align(Text.HorizontalAlignment.LEFT)
                .VerticalAlign(Text.VerticalAlignment.CENTER).Padding(4, 8), 36);
        content.addChild(new Text(0, 0, 0, 36, "居中对齐 / Center — 暖色文字")
                .TextColor(Color.fromRGB(241, 207, 147))
                .Align(Text.HorizontalAlignment.CENTER)
                .VerticalAlign(Text.VerticalAlignment.CENTER).Padding(4, 8), 36);
        content.addChild(new Text(0, 0, 0, 36, "右对齐 / Right — 绿色文字")
                .TextColor(Color.fromRGB(153, 218, 179))
                .Align(Text.HorizontalAlignment.RIGHT)
                .VerticalAlign(Text.VerticalAlignment.CENTER).Padding(4, 8), 36);

        content.addChild(new Text(0, 0, 0, 96,
                "自动换行 / Word wrapping\n"
                        + "文字会根据当前可用宽度换行。调整窗口大小，可以观察中文和 English text 的排布变化。"
                        + " Every row keeps its own height while the list handles scrolling.")
                .Wrap(true).LineSpacing(4).Padding(8), 96);
        content.addChild(new Text(0, 0, 0, 76,
                "手动换行 / Explicit line breaks\n第一行：你好，世界。\n第二行：Hello, world!")
                .LineSpacing(5).Padding(8), 76);
        content.addChild(new Text(0, 0, 0, 58, "底部对齐 / Bottom aligned")
                .Align(Text.HorizontalAlignment.CENTER)
                .VerticalAlign(Text.VerticalAlignment.BOTTOM).Padding(8), 58);
        content.addChild(new Text(0, 0, 0, 44, "缩放与阴影 / Scale + shadow")
                .TextScale(1.3f).Shadow(true).Wrap(false)
                .TextColor(Color.fromRGB(222, 202, 245))
                .VerticalAlign(Text.VerticalAlignment.CENTER).Padding(4, 8), 44);

        Text dynamic = content.addChild(new Text(0, 0, 0, 48, "点击底部按钮更新这段文字。")
                .TextColor(Color.fromRGB(153, 218, 179))
                .VerticalAlign(Text.VerticalAlignment.CENTER).Padding(8), 48);
        root.addChild(new Button(0, 0, 0, 30, Component.literal("更新文本 / Update text"))
                .OnPress(() -> {
                    updates++;
                    dynamic.setMessage(Component.literal("动态文本已更新 " + updates
                            + " 次 / Updated " + updates + " times."));
                    content.ensureVisible(dynamic);
                }), Constraint.length(30));
    }
}

package neuvillette.selene.widget;

import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ComponentPath;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.navigation.CommonInputs;
import net.minecraft.client.gui.navigation.FocusNavigationEvent;
import net.minecraft.network.chat.Component;

/**
 * 只提供按钮交互逻辑，具体外观由子类实现 renderSelf。
 * 鼠标按下或焦点位于按钮自身时按确认键都会调用 onPress，子控件优先处理输入。
 */
public abstract class BaseButton extends BaseWidget {
    protected BaseButton(@Nullable BaseWidget parent, Component message) {
        this(parent, 0, 0, 0, 0, message);
    }

    protected BaseButton(int x, int y, int width, int height, Component message) {
        super(x, y, width, height, message);
    }

    protected BaseButton(@Nullable BaseWidget parent, int x, int y, int width, int height, Component message) {
        super(parent, x, y, width, height, message);
    }

    /** 按钮的业务操作；鼠标和键盘入口负责检查可用状态，子类无需处理输入来源。 */
    public abstract void onPress();

    /** 仅有 Text 等不可聚焦子控件时，按钮自身仍可参与键盘导航。 */
    @Nullable
    @Override
    public ComponentPath nextFocusPath(FocusNavigationEvent event) {
        ComponentPath path = super.nextFocusPath(event);
        return path != null || !isActive() || isFocused() ? path : ComponentPath.leaf(this);
    }

    @Override
    public void onClick(double mouseX, double mouseY, int button) {
        // 命中检测和鼠标点击音效由 BaseWidget / AbstractWidget 统一处理。
        if (isActive() && isValidClickButton(button)) {
            onPress();
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (super.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        // 子控件拥有焦点时，即便它未消费确认键，也不能误触发父按钮。
        if (!isActive() || !isFocused() || getFocused() != null || !CommonInputs.selected(keyCode)) {
            return false;
        }
        playDownSound(Minecraft.getInstance().getSoundManager());
        onPress();
        return true;
    }

    @Override
    protected void updateSelfNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}

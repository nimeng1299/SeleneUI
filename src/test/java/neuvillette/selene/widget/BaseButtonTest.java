package neuvillette.selene.widget;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;
import sun.misc.Unsafe;

class BaseButtonTest {
    private static Field minecraftInstance;
    private static Object previousMinecraft;

    @BeforeAll
    static void installHeadlessMinecraft() throws Exception {
        Field access = Unsafe.class.getDeclaredField("theUnsafe");
        access.setAccessible(true);
        Unsafe unsafe = (Unsafe) access.get(null);
        minecraftInstance = Minecraft.class.getDeclaredField("instance");
        minecraftInstance.setAccessible(true);
        previousMinecraft = minecraftInstance.get(null);
        minecraftInstance.set(null, unsafe.allocateInstance(Minecraft.class));
    }

    @AfterAll
    static void restoreMinecraft() throws Exception {
        if (minecraftInstance != null) minecraftInstance.set(null, previousMinecraft);
    }

    @Test
    void screenDispatchesOneLeftClickAndSoundWithoutActivatingOnReleaseOrRightClick() {
        ContainerProbe root = new ContainerProbe(0, 0, 200, 100);
        ButtonProbe button = new ButtonProbe(root, 10, 10, 60, 30);
        Host screen = new Host(root);
        assertEquals(List.of(root), screen.children());

        assertTrue(screen.mouseClicked(15, 15, 0));
        assertEquals(1, button.presses);
        assertEquals(1, button.sounds);
        assertSame(button, root.getFocused());
        assertFalse(screen.mouseReleased(15, 15, 1));
        assertTrue(screen.mouseReleased(15, 15, 0));
        assertFalse(screen.mouseClicked(15, 15, 1));
        assertFalse(screen.mouseReleased(15, 15, 1));
        assertEquals(1, button.presses);
        assertEquals(1, button.sounds);
    }

    @Test
    void focusedButtonAcceptsAllThreeConfirmationKeysExactlyOncePerKeyPress() {
        ContainerProbe root = new ContainerProbe(0, 0, 200, 100);
        ButtonProbe button = new ButtonProbe(root, 10, 10, 60, 30);
        Host screen = new Host(root);
        assertTrue(screen.mouseClicked(15, 15, 0));
        screen.mouseReleased(15, 15, 0);
        button.resetCounts();

        int expected = 0;
        for (int key : new int[] {GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_SPACE, GLFW.GLFW_KEY_KP_ENTER}) {
            assertTrue(screen.keyPressed(key, 0, 0));
            assertEquals(++expected, button.presses);
            assertEquals(expected, button.sounds);
            assertFalse(screen.keyReleased(key, 0, 0));
            assertEquals(expected, button.presses);
            assertEquals(expected, button.sounds);
        }
        assertFalse(screen.keyPressed(GLFW.GLFW_KEY_A, 0, 0));
        assertEquals(3, button.presses);
        assertEquals(3, button.sounds);
    }

    @Test
    void missingFocusAndDisabledOrHiddenAncestorsPreventButtonActivation() {
        for (int blockedTarget = 0; blockedTarget < 4; blockedTarget++) {
            ContainerProbe root = new ContainerProbe(0, 0, 200, 100);
            ButtonProbe button = new ButtonProbe(root, 10, 10, 60, 30);
            Host screen = new Host(root);
            assertFalse(screen.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0));
            assertFalse(button.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0));
            assertEquals(0, button.presses);
            assertTrue(screen.mouseClicked(15, 15, 0));
            screen.mouseReleased(15, 15, 0);
            button.resetCounts();
            switch (blockedTarget) {
                case 0 -> root.active = false;
                case 1 -> root.visible = false;
                case 2 -> button.active = false;
                case 3 -> button.visible = false;
                default -> throw new AssertionError();
            }

            assertFalse(screen.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0));
            assertFalse(button.mouseClicked(15, 15, 0));
            if (blockedTarget < 2) {
                assertFalse(screen.mouseClicked(15, 15, 0));
            } else {
                screen.mouseClicked(15, 15, 0);
            }
            assertEquals(0, button.presses);
            assertEquals(0, button.sounds);
        }
    }

    @Test
    void parentButtonNeverActivatesWhileAChildOwnsFocusRegardlessOfKeyConsumption() {
        ButtonProbe parent = new ButtonProbe(0, 0, 200, 100);
        ButtonProbe child = new ButtonProbe(parent, 10, 10, 50, 30);
        ContainerProbe nonConsumingChild = parent.addChild(new ContainerProbe(80, 10, 30, 30));
        Host screen = new Host(parent);

        assertTrue(screen.mouseClicked(15, 15, 0));
        assertTrue(screen.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0));
        assertEquals(2, child.presses);
        assertEquals(2, child.sounds);
        assertEquals(0, parent.presses);
        assertEquals(0, parent.sounds);

        assertTrue(screen.mouseClicked(85, 15, 0));
        assertSame(nonConsumingChild, parent.getFocused());
        assertFalse(screen.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0));
        assertEquals(0, parent.presses);
        assertEquals(0, parent.sounds);

        assertTrue(screen.mouseClicked(150, 75, 0));
        assertNull(parent.getFocused());
        assertTrue(screen.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0));
        assertEquals(2, parent.presses);
        assertEquals(2, parent.sounds);
    }

    @Test
    void neoForgeClickCallbackChecksButtonAndAvailabilityWithoutPlayingAnotherSound() {
        ButtonProbe button = new ButtonProbe(0, 0, 30, 30);
        button.onClick(5, 5, 1);
        assertEquals(0, button.presses);
        button.onClick(5, 5, 0);
        assertEquals(1, button.presses);
        assertEquals(0, button.sounds, "The inherited mouseClicked entry point owns the mouse sound");

        button.active = false;
        button.onClick(5, 5, 0);
        button.active = true;
        button.visible = false;
        button.onClick(5, 5, 0);
        assertEquals(1, button.presses);
        assertEquals(0, button.sounds);
    }

    @Test
    void pressCallbackCanRemoveItsButtonWithoutRestoringFocusOrDragCapture() {
        ContainerProbe root = new ContainerProbe(0, 0, 200, 100);
        ButtonProbe button = new ButtonProbe(root, 10, 10, 60, 30);
        button.action = () -> root.removeChild(button);
        Host screen = new Host(root);

        assertTrue(screen.mouseClicked(15, 15, 0));

        assertEquals(1, button.presses);
        assertEquals(1, button.sounds);
        assertNull(button.getParent());
        assertFalse(button.isFocused());
        assertNull(root.getFocused());
        assertFalse(root.isDragging());
        assertFalse(screen.mouseDragged(300, 300, 0, 10, 10));
        assertFalse(screen.mouseReleased(300, 300, 0));
        assertFalse(screen.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0));
        assertEquals(1, button.presses);
    }

    private static final class Host extends Screen {
        Host(BaseWidget root) {
            super(Component.literal("Button test"));
            addRenderableWidget(root);
        }
    }

    private static final class ContainerProbe extends BaseWidget {
        ContainerProbe(int x, int y, int width, int height) {
            super(x, y, width, height, Component.empty());
        }

        @Override protected void renderSelf(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {}
        @Override public void playDownSound(SoundManager manager) {}
    }

    private static final class ButtonProbe extends BaseButton {
        int presses;
        int sounds;
        Runnable action;

        ButtonProbe(int x, int y, int width, int height) {
            super(x, y, width, height, Component.literal("Button"));
        }

        ButtonProbe(BaseWidget parent, int x, int y, int width, int height) {
            super(parent, x, y, width, height, Component.literal("Button"));
        }

        void resetCounts() {
            presses = 0;
            sounds = 0;
        }

        @Override
        public void onPress() {
            presses++;
            if (action != null) action.run();
        }

        @Override protected void renderSelf(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {}
        @Override public void playDownSound(SoundManager manager) { sounds++; }
    }
}

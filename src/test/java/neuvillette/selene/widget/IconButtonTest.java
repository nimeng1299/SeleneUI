package neuvillette.selene.widget;

import static org.junit.jupiter.api.Assertions.*;

import com.mojang.blaze3d.vertex.PoseStack;
import java.lang.reflect.Field;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.CommonInputs;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import neuvillette.selene.api.Color;
import neuvillette.selene.api.Rect;
import neuvillette.selene.layout.Constraint;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;
import sun.misc.Unsafe;

class IconButtonTest {
    private static final ResourceLocation PLUS = ResourceLocation.fromNamespaceAndPath("selene", "textures/icon/variant/plus.png");
    private static final ResourceLocation MINUS = ResourceLocation.fromNamespaceAndPath("selene", "textures/icon/variant/minus.png");
    private static Unsafe unsafe;
    private static Field minecraftInstance;
    private static Object previousMinecraft;

    @BeforeAll
    static void installHeadlessMinecraftWithoutFont() throws Exception {
        Field access = Unsafe.class.getDeclaredField("theUnsafe");
        access.setAccessible(true);
        unsafe = (Unsafe) access.get(null);
        minecraftInstance = Minecraft.class.getDeclaredField("instance");
        minecraftInstance.setAccessible(true);
        previousMinecraft = minecraftInstance.get(null);
        // No Font is installed: an icon-only button must not need text metrics.
        minecraftInstance.set(null, unsafe.allocateInstance(Minecraft.class));
    }

    @AfterAll
    static void restoreMinecraft() throws Exception {
        if (minecraftInstance != null) minecraftInstance.set(null, previousMinecraft);
    }

    @Test
    void mousePressSelectsRightFrameAndOutsideReleaseRestoresLeftWithoutAnotherAction() throws Exception {
        Probe button = new Probe(null, 10, 20, 140, 40);
        Color normal = Color.fromRGB(237, 229, 216);
        Color pressed = Color.fromRGB(112, 124, 138);
        button.Icon(PLUS).FillColor(normal).PressedFillColor(pressed);
        int[] actions = {0};
        button.OnPress(() -> actions[0]++);
        RecordingGraphics graphics = graphics();

        button.render(graphics, -1, -1, 0);
        assertEquals(0, button.iconDraw.frame());
        assertEquals(normal.toARGB(), button.background.toARGB());
        assertFalse(button.isPressed());
        assertTrue(button.mouseClicked(30, 40, 0));
        button.render(graphics, 30, 40, 0);
        assertEquals(1, actions[0]);
        assertEquals(1, button.iconDraw.frame());
        assertEquals(pressed.toARGB(), button.background.toARGB());
        assertTrue(button.isPressed());

        assertFalse(button.mouseReleased(200, 200, 1));
        assertTrue(button.isPressed());
        assertTrue(button.mouseReleased(200, 200, 0));
        button.render(graphics, -1, -1, 0);
        assertEquals(0, button.iconDraw.frame());
        assertEquals(normal.toARGB(), button.background.toARGB());
        assertTrue(button.isFocused(), "Keeping keyboard focus must not keep the pressed image");
        assertFalse(button.mouseClicked(30, 40, 1));
        assertEquals(1, actions[0]);
        assertEquals(1, button.sounds);
    }

    @Test
    void heightDefinesRoundBoundsAndCenteredIconRegardlessOfMessageOrLayoutWidth() throws Exception {
        Block parent = new Block(10, 20, 300, 200, Component.empty()).Padding(2, 3, 4, 5);
        Probe button = new Probe(parent, 4, 6, 140, 51);
        button.Icon(PLUS).IconScale(0.8f);
        RecordingGraphics graphics = graphics();
        button.render(graphics, -1, -1, 0);
        assertEquals(51, button.getWidth());
        assertEquals(51, button.backgroundBounds.width());
        assertEquals(51, button.backgroundBounds.height());
        assertEquals(25.5f, button.backgroundRadius);
        assertEquals(44.5f, button.iconDraw.x());
        assertEquals(53.5f, button.iconDraw.y());
        assertEquals(20.4f, button.iconDraw.radius(), 0.0001f);
        assertFalse(button.isMouseOver(20, 29));
        assertFalse(button.mouseClicked(20, 29, 0));
        assertTrue(button.isMouseOver(44.5, 53.5));

        button.setWidth(200);
        assertEquals(51, button.getWidth());
        button.setHeight(60);
        assertEquals(60, button.getWidth());
        button.setSize(200, 31);
        assertEquals(31, button.getWidth());
        Layout layout = new Layout(100, 200, 180, 80);
        layout.addChild(button, Constraint.length(30));
        button.setMessage(Component.literal("A description for narration, never a visible label"));
        assertEquals(30, button.getWidth());
        assertEquals(30, button.getHeight());
        button.render(graphics, -1, -1, 0);
        assertEquals(115f, button.iconDraw.x());
        assertEquals(215f, button.iconDraw.y());
    }

    @Test
    void keyboardFramesResetOnReleaseFocusLossUnmountAndReparenting() throws Exception {
        Probe button = new Probe(null, 0, 0, 40, 40);
        button.Icon(PLUS);
        int[] actions = {0};
        button.OnPress(() -> actions[0]++);
        RecordingGraphics graphics = graphics();
        int expected = 0;
        for (int key : new int[]{GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_SPACE, GLFW.GLFW_KEY_KP_ENTER}) {
            button.setFocused(true);
            assertTrue(button.keyPressed(key, 0, 0));
            button.render(graphics, -1, -1, 0);
            assertEquals(1, button.iconDraw.frame());
            assertEquals(++expected, actions[0]);
            button.keyReleased(key, 0, 0);
            button.render(graphics, -1, -1, 0);
            assertEquals(0, button.iconDraw.frame());
            assertEquals(expected, actions[0]);
        }

        button.keyPressed(GLFW.GLFW_KEY_SPACE, 0, 0);
        button.setFocused(false);
        button.setFocused(true);
        assertFalse(button.isPressed());
        button.keyPressed(GLFW.GLFW_KEY_SPACE, 0, 0);
        button.unmount();
        button.setFocused(true);
        assertFalse(button.isPressed());
        button.keyPressed(GLFW.GLFW_KEY_SPACE, 0, 0);
        button.setParent(new Block(0, 0, 100, 100, Component.empty()));
        button.setFocused(true);
        assertFalse(button.isPressed());

        KeyConsumer child = button.addChild(new KeyConsumer());
        button.setFocused(child);
        expected = actions[0];
        assertTrue(button.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0));
        assertFalse(button.isPressed());
        assertEquals(expected, actions[0], "A focused child's key must not activate its parent");
    }

    @Test
    void releaseWhileAnAncestorIsHiddenOrDisabledCannotReviveThePressedKey() throws Exception {
        for (boolean hideParent : new boolean[]{true, false}) {
            Block parent = new Block(0, 0, 100, 100, Component.empty());
            Probe button = new Probe(parent, 0, 0, 40, 40);
            button.Icon(PLUS);
            int[] actions = {0};
            button.OnPress(() -> actions[0]++);
            parent.setFocused(button);
            assertSame(button, parent.getFocused());
            assertTrue(parent.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0));
            RecordingGraphics graphics = graphics();
            button.render(graphics, -1, -1, 0);
            assertEquals(1, button.iconDraw.frame());
            assertEquals(1, actions[0]);

            if (hideParent) parent.visible = false;
            else parent.active = false;
            // Do not inspect or render the child while unavailable: that could mask a stale-key bug.
            assertFalse(parent.keyReleased(GLFW.GLFW_KEY_ENTER, 0, 0));
            parent.visible = true;
            parent.active = true;
            parent.setFocused(button);
            assertFalse(button.isPressed(), hideParent ? "Hidden ancestor retained the held key"
                    : "Disabled ancestor retained the held key");
            button.render(graphics, -1, -1, 0);
            assertEquals(0, button.iconDraw.frame());
            assertEquals(1, actions[0]);
            assertEquals(1, button.sounds);
        }
    }

    @Test
    void disabledStyleMatchesPressedWhileReenablingClearsMouseAndKeyboardState() throws Exception {
        Probe button = new Probe(null, 0, 0, 40, 40);
        button.Icon(PLUS).PressedFillColor(Color.fromRGBA(70, 90, 120, 180));
        button.setAlpha(0.5f);
        int[] actions = {0};
        button.OnPress(() -> actions[0]++);
        assertTrue(button.mouseClicked(20, 20, 0));
        assertTrue(button.isPressed());
        RecordingGraphics graphics = graphics();
        button.render(graphics, 20, 20, 0);
        int pressedBackground = button.background.toARGB();
        IconDraw pressedIcon = button.iconDraw;
        assertEquals(0x5A465A78, pressedBackground);
        assertEquals(128, pressedIcon.tint().Alpha());
        assertEquals(1, pressedIcon.frame());

        assertSame(button, button.Disable(true));
        assertTrue(button.isDisabled());
        assertFalse(button.active);
        assertFalse(button.isFocused());
        assertFalse(button.isDragging());
        button.render(graphics, -1, -1, 0);
        assertFalse(button.isPressed());
        assertEquals(pressedIcon.frame(), button.iconDraw.frame());
        assertEquals(pressedIcon.tint().toARGB(), button.iconDraw.tint().toARGB());
        assertEquals(pressedBackground, button.background.toARGB());
        assertFalse(button.mouseClicked(20, 20, 0));
        assertFalse(button.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0));
        assertFalse(button.mouseReleased(20, 20, 0));
        assertEquals(1, actions[0]);

        button.Disable(false);
        assertFalse(button.isDisabled());
        assertTrue(button.active);
        button.setFocused(true);
        assertFalse(button.isPressed(), "Reenabling and refocusing must not revive mouse capture");
        button.Icon(MINUS);
        button.render(graphics, -1, -1, 0);
        assertEquals(MINUS, button.iconDraw.texture());
        assertEquals(0, button.iconDraw.frame());
        assertEquals(0x80EDE5D8, button.background.toARGB());

        assertTrue(button.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0));
        assertTrue(button.isPressed());
        assertEquals(2, actions[0]);
        button.setDisabled(true);
        assertFalse(button.isPressed());
        assertFalse(button.keyPressed(GLFW.GLFW_KEY_SPACE, 0, 0));
        button.setDisabled(false);
        button.setFocused(true);
        assertFalse(button.isPressed(), "Reenabling and refocusing must not revive a held key");
        button.render(graphics, -1, -1, 0);
        assertEquals(0, button.iconDraw.frame());
        assertEquals(2, actions[0]);
        button.Icon(null);
        button.iconDraw = null;
        button.render(graphics, -1, -1, 0);
        assertNull(button.iconDraw);
        button.setSize(100, 0);
        button.backgroundBounds = null;
        button.render(graphics, -1, -1, 0);
        assertNull(button.backgroundBounds);
    }

    @Test
    void disabledAncestorUsesPressedAppearanceWithoutChangingTheLocalDisabledProperty() throws Exception {
        Block parent = new Block(0, 0, 100, 100, Component.empty());
        Probe button = new Probe(parent, 0, 0, 40, 40);
        button.Icon(PLUS).PressedFillColor(Color.fromRGBA(40, 80, 120, 160));
        button.setAlpha(0.5f);
        int[] actions = {0};
        button.OnPress(() -> actions[0]++);
        parent.setFocused(button);
        assertTrue(parent.keyPressed(GLFW.GLFW_KEY_SPACE, 0, 0));
        assertTrue(button.isPressed());
        parent.active = false;
        RecordingGraphics graphics = graphics();
        button.render(graphics, -1, -1, 0);
        assertFalse(button.isDisabled());
        assertTrue(button.active);
        assertFalse(button.isPressed());
        assertEquals(1, button.iconDraw.frame());
        assertEquals(128, button.iconDraw.tint().Alpha());
        assertEquals(0x50285078, button.background.toARGB());
        assertFalse(parent.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0));
        assertFalse(button.mouseClicked(20, 20, 0));

        parent.active = true;
        parent.setFocused(button);
        assertFalse(button.isPressed());
        button.render(graphics, -1, -1, 0);
        assertEquals(0, button.iconDraw.frame());
        assertEquals(1, actions[0]);
    }

    @Test
    void actionCanRemoveItsButtonWithoutLeavingMouseOrKeyboardPressedState() throws Exception {
        Block root = new Block(0, 0, 100, 100, Component.empty());
        Probe button = new Probe(root, 0, 0, 40, 40);
        button.Icon(PLUS);
        int[] actions = {0};
        button.OnPress(() -> {
            actions[0]++;
            root.removeChild(button);
        });
        assertTrue(button.mouseClicked(20, 20, 0));
        assertNull(button.getParent());
        assertFalse(button.isPressed());
        assertFalse(button.mouseReleased(20, 20, 0));
        assertEquals(1, actions[0]);

        root.addChild(button);
        button.setFocused(true);
        assertTrue(button.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0));
        assertNull(button.getParent());
        assertFalse(button.isPressed());
        button.setFocused(true);
        assertFalse(button.isPressed(), "Refocusing after removal must not revive a held key");
        button.render(graphics(), -1, -1, 0);
        assertEquals(0, button.iconDraw.frame());
        assertEquals(2, actions[0]);
    }

    private static RecordingGraphics graphics() throws Exception {
        RecordingGraphics graphics = (RecordingGraphics) unsafe.allocateInstance(RecordingGraphics.class);
        Field pose = GuiGraphics.class.getDeclaredField("pose");
        pose.setAccessible(true);
        pose.set(graphics, new PoseStack());
        return graphics;
    }

    private record IconDraw(ResourceLocation texture, int frame, float x, float y, float radius, Color tint) {}

    private static final class Probe extends IconButton {
        IconDraw iconDraw;
        Rect backgroundBounds;
        float backgroundRadius;
        Color background;
        int sounds;

        Probe(BaseWidget parent, int x, int y, int width, int height) {
            super(parent, x, y, width, height, Component.literal("Add"));
        }

        @Override public void playDownSound(SoundManager manager) { sounds++; }
        @Override protected void renderBackground(GuiGraphics graphics, Rect bounds, float radius, Color color) {
            backgroundBounds = bounds;
            backgroundRadius = radius;
            background = color;
        }
        @Override protected void renderIconFrame(GuiGraphics graphics, ResourceLocation texture, int frame,
                                                 float centerX, float centerY, float radius, Color tint) {
            iconDraw = new IconDraw(texture, frame, centerX, centerY, radius, tint);
        }
    }

    private static final class KeyConsumer extends BaseWidget {
        KeyConsumer() { super(0, 0, 8, 8, Component.empty()); }
        @Override protected void renderSelf(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {}
        @Override public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            return CommonInputs.selected(keyCode);
        }
    }

    private static final class RecordingGraphics extends GuiGraphics {
        RecordingGraphics() { super(Minecraft.getInstance(), null); }
        @Override public void flush() {}
        @Override public boolean containsPointInScissor(int x, int y) { return true; }
        @Override public int drawString(Font font, FormattedCharSequence text, int x, int y, int color, boolean shadow) {
            throw new AssertionError("IconButton must not draw its narration label");
        }
    }
}

package neuvillette.selene.widget;

import static org.junit.jupiter.api.Assertions.*;

import com.mojang.blaze3d.font.GlyphInfo;
import com.mojang.blaze3d.font.SheetGlyphInfo;
import com.mojang.blaze3d.vertex.PoseStack;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.font.FontSet;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import net.minecraft.client.gui.navigation.FocusNavigationEvent;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import neuvillette.selene.api.Color;
import neuvillette.selene.api.Rect;
import neuvillette.selene.layout.Constraint;
import org.joml.Vector4f;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;
import sun.misc.Unsafe;

class ButtonTest {
    private static final ResourceLocation ICON = ResourceLocation.withDefaultNamespace("textures/block/diamond_block.png");
    private static Unsafe unsafe;
    private static Field minecraftInstance;
    private static Object previousMinecraft;

    @BeforeAll
    static void installHeadlessMinecraftAndFont() throws Exception {
        Field access = Unsafe.class.getDeclaredField("theUnsafe");
        access.setAccessible(true);
        unsafe = (Unsafe) access.get(null);
        minecraftInstance = Minecraft.class.getDeclaredField("instance");
        minecraftInstance.setAccessible(true);
        previousMinecraft = minecraftInstance.get(null);
        Minecraft minecraft = (Minecraft) unsafe.allocateInstance(Minecraft.class);
        FontSet metrics = new FontSet(null, ResourceLocation.withDefaultNamespace("headless_button")) {
            @Override public GlyphInfo getGlyphInfo(int character, boolean filterFishyGlyphs) {
                return new MetricGlyph(6);
            }
        };
        Field font = Minecraft.class.getDeclaredField("font");
        font.setAccessible(true);
        font.set(minecraft, new Font(ignored -> metrics, false));
        minecraftInstance.set(null, minecraft);
    }

    @AfterAll
    static void restoreMinecraft() throws Exception {
        if (minecraftInstance != null) minecraftInstance.set(null, previousMinecraft);
    }

    @Test
    void addingAndRemovingTheIconReservesOnlyTheRequiredLabelSpace() throws Exception {
        Probe button = new Probe(null, 10, 20, 140, 50);
        RecordingGraphics graphics = graphics();
        button.render(graphics, -1, -1, 0);
        assertNull(button.iconDraw);
        assertEquals(68f, graphics.draws.getLast().x(), 0.0001f);

        button.Icon(ICON);
        button.render(graphics, -1, -1, 0);
        IconDraw icon = button.iconDraw;
        assertEquals(ICON, icon.texture());
        assertEquals(35f, icon.x(), 0.0001f);
        assertEquals(45f, icon.y(), 0.0001f);
        assertEquals(15f, icon.radius(), 0.0001f);
        Draw text = graphics.draws.getLast();
        assertEquals("fork", text.text());
        assertEquals(84f, text.x(), 0.0001f);
        assertTrue(text.clip().left() >= icon.x() + icon.radius() + 4);
        assertEquals(138, text.clip().right());

        button.Icon(null);
        button.iconDraw = null;
        button.render(graphics, -1, -1, 0);
        assertNull(button.iconDraw);
        assertEquals(68f, graphics.draws.getLast().x(), 0.0001f);
        assertNull(graphics.clip);
    }

    @Test
    void iconStaysConcentricThroughParentPaddingOddSizesScalingAndResize() throws Exception {
        Block parent = new Block(100, 200, 400, 200, Component.empty()).Padding(3, 7, 11, 13);
        Probe button = new Probe(parent, 5, 7, 141, 51);
        button.Icon(ICON).Padding(8, 2, 3, 19);
        RecordingGraphics graphics = graphics();
        graphics.pose().translate(5, 6, 0);
        graphics.pose().scale(2, 2, 1);
        button.render(graphics, -1, -1, 0);

        IconDraw icon = button.iconDraw;
        assertEquals(25.5f, button.outerRadius, 0.0001f);
        assertEquals(143.5f, icon.x(), 0.0001f);
        assertEquals(235.5f, icon.y(), 0.0001f);
        assertEquals(15.3f, icon.radius(), 0.0001f);
        assertEquals(292f, icon.transformedX(), 0.0001f);
        assertEquals(477f, icon.transformedY(), 0.0001f);
        assertEquals(30.6f, icon.transformedRadius(), 0.0001f);
        assertEquals(2f, graphics.pose().last().pose().m00());

        button.setSize(160, 60);
        button.IconScale(0.8f);
        button.render(graphics, -1, -1, 0);
        assertEquals(148f, button.iconDraw.x(), 0.0001f);
        assertEquals(240f, button.iconDraw.y(), 0.0001f);
        assertEquals(24f, button.iconDraw.radius(), 0.0001f);
    }

    @Test
    void narrowButtonsKeepTheIconInsideAndClipLongLabelsAfterTheGap() throws Exception {
        Probe button = new Probe(null, 0, 0, 80, 30);
        button.Icon(ICON).IconGap(6);
        button.setMessage(Component.literal("abcdefghijklmnop"));
        RecordingGraphics graphics = graphics();
        button.render(graphics, -1, -1, 0);
        Draw label = graphics.draws.getLast();
        assertEquals("abcdef", label.text());
        assertEquals(30, label.clip().left());
        assertEquals(68, label.clip().right());
        assertTrue(label.x() >= label.clip().left());
        assertTrue(label.x() + 36 <= label.clip().right());

        graphics.draws.clear();
        button.setSize(20, 60);
        button.render(graphics, -1, -1, 0);
        assertTrue(graphics.draws.isEmpty());
        assertEquals(10f, button.iconDraw.x(), 0.0001f);
        assertEquals(30f, button.iconDraw.y(), 0.0001f);
        assertEquals(6f, button.iconDraw.radius(), 0.0001f);

        button.setSize(0, 0);
        button.iconDraw = null;
        button.render(graphics, -1, -1, 0);
        assertNull(button.iconDraw);
    }

    @Test
    void iconSharesButtonOpacityEvenWhenTheLabelHasNoSpaceOrIsTransparent() throws Exception {
        Probe button = new Probe(null, 0, 0, 140, 50);
        button.Icon(ICON).Padding(100).TextColor(Color.fromRGBA(0, 0, 0, 0));
        button.active = false;
        button.setAlpha(0.5f);
        RecordingGraphics graphics = graphics();
        button.render(graphics, -1, -1, 0);
        assertTrue(graphics.draws.isEmpty());
        assertEquals(64, button.iconDraw.color().Alpha());
        assertEquals(0, button.background.Alpha());
        assertEquals(25f, button.iconDraw.x(), 0.0001f);
        assertEquals(25f, button.iconDraw.y(), 0.0001f);

        for (float scale : new float[]{0, -1, 1.1f, Float.NaN, Float.POSITIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> button.IconScale(scale));
        }
        assertThrows(IllegalArgumentException.class, () -> button.IconGap(-1));
        button.render(graphics, -1, -1, 0);
        assertEquals(15f, button.iconDraw.radius(), 0.0001f);
    }

    @Test
    void emptyMessageUsesHeightAsDiameterAndCentersTheIconWithoutDrawingText() throws Exception {
        Probe button = new Probe(null, 10, 20, 140, 51, Component.empty());
        button.Icon(ICON).Padding(100);
        RecordingGraphics graphics = graphics();
        button.render(graphics, -1, -1, 0);

        assertEquals(51, button.getWidth());
        assertEquals(51, button.getHeight());
        assertEquals(51, button.backgroundBounds.width());
        assertEquals(51, button.backgroundBounds.height());
        assertEquals(25.5f, button.outerRadius);
        assertEquals(35.5f, button.iconDraw.x());
        assertEquals(45.5f, button.iconDraw.y());
        assertEquals(15.3f, button.iconDraw.radius(), 0.0001f);
        assertTrue(graphics.draws.isEmpty());

        button.Icon(null);
        button.iconDraw = null;
        button.render(graphics, -1, -1, 0);
        assertNull(button.iconDraw);
        assertEquals(51, button.backgroundBounds.width());
        assertTrue(graphics.draws.isEmpty());
    }

    @Test
    void circularSizeTracksHeightAndRestoresTheLatestRequestedTextWidth() {
        Button button = new Button(0, 0, 120, 32, Component.empty());
        assertEquals(32, button.getWidth());
        button.setWidth(160);
        assertEquals(32, button.getWidth());
        button.setHeight(41);
        assertEquals(41, button.getWidth());
        button.setMessage(Component.literal("caption"));
        assertEquals(160, button.getWidth());
        button.setMessage(Component.literal(" \t\n"));
        button.setSize(200, 51);
        assertEquals(51, button.getWidth());
        button.setMessage(Component.empty().append(Component.literal("caption")));
        assertEquals(200, button.getWidth());

        Layout layout = new Layout(10, 20, 180, 80);
        layout.addChild(button, Constraint.length(30));
        button.setMessage(Component.empty());
        assertEquals(30, button.getWidth());
        assertEquals(30, button.getHeight());
        layout.setWidth(220);
        assertEquals(30, button.getWidth());
        button.setMessage(Component.literal("caption"));
        assertEquals(220, button.getWidth());

        ScrollList list = new ScrollList(0, 0, 160, 100);
        list.addChild(button, 40);
        button.setMessage(Component.empty());
        assertEquals(40, button.getWidth());
        assertEquals(40, button.getHeight());
        button.setMessage(Component.literal("caption"));
        assertEquals(160, button.getWidth());
    }

    @Test
    void circularHoverAndClicksRejectTheCornersAndTheOldCapsuleArea() throws Exception {
        Probe button = new Probe(null, 10, 20, 140, 40, Component.empty());
        int[] clicks = {0};
        button.OnPress(() -> clicks[0]++);
        assertFalse(button.isMouseOver(11, 21));
        assertFalse(button.mouseClicked(11, 21, 0));
        assertFalse(button.isMouseOver(100, 40));
        assertFalse(button.mouseClicked(100, 40, 0));
        assertTrue(button.isMouseOver(30, 40));
        assertTrue(button.mouseClicked(30, 40, 0));
        button.mouseReleased(30, 40, 0);
        assertEquals(1, clicks[0]);

        RecordingGraphics graphics = graphics();
        button.render(graphics, 11, 21, 0);
        assertFalse(button.isHovered());
        button.render(graphics, 30, 40, 0);
        assertTrue(button.isHovered());

        button.setMessage(Component.literal("caption"));
        assertTrue(button.mouseClicked(100, 40, 0));
        assertEquals(2, clicks[0]);
        button.active = false;
        assertFalse(button.mouseClicked(30, 40, 0));
    }

    @Test
    void disablingUsesFillColorForOutlineAndTextWithoutHalvingTheirOpacityAndRestoresTheStyle() throws Exception {
        Probe button = new Probe(null, 0, 0, 140, 50);
        button.FillColor(Color.fromRGBA(20, 60, 100, 128)).TextColor(Color.fromRGBA(210, 180, 150, 96));
        button.setAlpha(0.5f);
        RecordingGraphics graphics = graphics();
        button.render(graphics, -1, -1, 0);
        assertEquals(0x40143C64, button.background.toARGB());
        assertEquals(0x30D2B496, graphics.draws.getLast().color());
        assertEquals(0f, button.borderWidth);

        assertSame(button, button.Disable(true));
        assertTrue(button.isDisabled());
        assertFalse(button.active);
        button.render(graphics, 70, 25, 0);
        assertEquals(0, button.background.Alpha());
        assertEquals(1f, button.borderWidth);
        assertEquals(0x40143C64, button.border.toARGB());
        assertEquals(0x40143C64, graphics.draws.getLast().color());

        button.Disable(false);
        assertFalse(button.isDisabled());
        assertTrue(button.active);
        button.render(graphics, -1, -1, 0);
        assertEquals(0x40143C64, button.background.toARGB());
        assertEquals(0x30D2B496, graphics.draws.getLast().color());
        assertEquals(0f, button.borderWidth);
    }

    @Test
    void disabledAncestorOverridesExplicitTextColorsWithoutMutatingStylesAndAlsoOutlinesCircularButtons() throws Exception {
        Block parent = new Block(0, 0, 200, 100, Component.empty());
        Component message = Component.empty()
                .append(Component.literal("A").withStyle(Style.EMPTY.withColor(0xFF0000).withBold(true)))
                .append(Component.literal("B").withStyle(Style.EMPTY.withColor(0x00FF00).withItalic(true)));
        Probe button = new Probe(parent, 0, 0, 140, 50, message);
        button.FillColor(Color.fromRGBA(30, 80, 160, 200)).TextColor(Color.fromRGB(240, 230, 220));
        button.setAlpha(0.75f);
        parent.active = false;
        RecordingGraphics graphics = graphics();
        button.render(graphics, -1, -1, 0);
        Draw disabledText = graphics.draws.getLast();
        assertEquals("AB", disabledText.text());
        assertEquals(0, button.background.Alpha());
        assertEquals(1f, button.borderWidth);
        assertEquals(0x961E50A0, button.border.toARGB());
        assertEquals(0x961E50A0, disabledText.color());
        for (Style style : disabledText.styles()) {
            int effectiveColor = style.getColor() == null ? disabledText.color() & 0xFFFFFF : style.getColor().getValue();
            assertEquals(0x1E50A0, effectiveColor);
        }
        assertTrue(disabledText.styles().get(0).isBold());
        assertTrue(disabledText.styles().get(1).isItalic());
        assertSame(message, button.getMessage());

        parent.active = true;
        button.render(graphics, -1, -1, 0);
        Draw restoredText = graphics.draws.getLast();
        assertEquals(0f, button.borderWidth);
        assertEquals(0xFF0000, restoredText.styles().get(0).getColor().getValue());
        assertEquals(0x00FF00, restoredText.styles().get(1).getColor().getValue());
        assertEquals(0xBFF0E6DC, restoredText.color());

        button.setMessage(Component.empty());
        button.Disable(true);
        graphics.draws.clear();
        button.render(graphics, -1, -1, 0);
        assertTrue(graphics.draws.isEmpty());
        assertEquals(50, button.backgroundBounds.width());
        assertEquals(50, button.backgroundBounds.height());
        assertEquals(25f, button.outerRadius);
        assertEquals(0, button.background.Alpha());
        assertEquals(1f, button.borderWidth);
        assertEquals(0x961E50A0, button.border.toARGB());
    }

    @Test
    void disableSetterCancelsCapturedPressAndBlocksFocusKeyboardAndChildActivationUntilReenabled() throws Exception {
        Probe button = new Probe(null, 0, 0, 180, 80);
        Probe child = new Probe(button, 5, 5, 40, 30);
        int[] actions = {0, 0};
        button.OnPress(() -> actions[0]++);
        child.OnPress(() -> actions[1]++);
        assertTrue(button.mouseClicked(150, 50, 0));
        assertTrue(button.isDragging());
        button.setDisabled(true);
        assertFalse(button.isFocused());
        assertFalse(button.isDragging());
        assertFalse(button.mouseReleased(150, 50, 0));
        assertFalse(button.mouseClicked(150, 50, 0));
        assertFalse(button.isMouseOver(150, 50));
        assertFalse(button.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0));
        assertNull(button.nextFocusPath(new FocusNavigationEvent.TabNavigation(true)));
        assertEquals(1, actions[0]);

        button.setDisabled(false);
        int childX = child.getX() + 15;
        int childY = child.getY() + 15;
        assertTrue(button.mouseClicked(childX, childY, 0));
        assertSame(child, button.getFocused());
        assertTrue(child.isDragging());
        button.Disable(true);
        assertNull(button.getFocused());
        assertFalse(child.isFocused());
        assertFalse(child.isDragging());
        assertFalse(button.mouseClicked(childX, childY, 0));
        assertFalse(child.mouseClicked(childX, childY, 0));
        assertFalse(child.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0));
        assertEquals(1, actions[1]);

        button.Disable(false);
        button.setFocused(child);
        assertFalse(child.isDragging(), "Reenabling and refocusing must not revive the captured press");
        assertTrue(button.mouseClicked(150, 50, 0));
        assertEquals(2, actions[0]);
        assertEquals(1, actions[1]);
        assertTrue(button.isDragging());
        button.active = false;
        button.render(graphics(), -1, -1, 0);
        button.active = true;
        button.setFocused(true);
        assertFalse(button.isDragging(), "Rendering a directly disabled button must clear its captured press");
    }

    private static RecordingGraphics graphics() throws Exception {
        RecordingGraphics graphics = (RecordingGraphics) unsafe.allocateInstance(RecordingGraphics.class);
        Field pose = GuiGraphics.class.getDeclaredField("pose");
        pose.setAccessible(true);
        pose.set(graphics, new PoseStack());
        graphics.draws = new ArrayList<>();
        return graphics;
    }

    private record MetricGlyph(float advance) implements GlyphInfo {
        @Override public float getAdvance() { return advance; }
        @Override public BakedGlyph bake(Function<SheetGlyphInfo, BakedGlyph> provider) {
            throw new AssertionError("Metrics-only test must not access the GPU");
        }
    }

    private record IconDraw(ResourceLocation texture, float x, float y, float radius, Color color,
                            float transformedX, float transformedY, float transformedRadius) {}
    private record Bounds(int left, int top, int right, int bottom) {}
    private record Draw(String text, float x, Bounds clip, int color, List<Style> styles) {}

    private static final class Probe extends Button {
        IconDraw iconDraw;
        Color background;
        Color border;
        float borderWidth;
        Rect backgroundBounds;
        float outerRadius;

        Probe(BaseWidget parent, int x, int y, int width, int height) {
            this(parent, x, y, width, height, Component.literal("fork"));
        }

        Probe(BaseWidget parent, int x, int y, int width, int height, Component message) {
            super(parent, x, y, width, height, message);
        }

        @Override public void playDownSound(SoundManager manager) {}

        @Override protected void renderBackground(GuiGraphics graphics, Rect bounds, float radius, Color color) {
            outerRadius = radius;
            background = color;
            backgroundBounds = bounds;
        }

        @Override protected void renderBackground(GuiGraphics graphics, Rect bounds, float radius,
                                                   Color fill, Color border, float borderWidth) {
            this.border = border;
            this.borderWidth = borderWidth;
            renderBackground(graphics, bounds, radius, fill);
        }

        @Override protected void renderIcon(GuiGraphics graphics, ResourceLocation texture,
                                             float x, float y, float radius, Color tint) {
            Vector4f center = new Vector4f(x, y, 0, 1).mul(graphics.pose().last().pose());
            iconDraw = new IconDraw(texture, x, y, radius, tint, center.x(), center.y(),
                    radius * graphics.pose().last().pose().m00());
        }
    }

    private static final class RecordingGraphics extends GuiGraphics {
        List<Draw> draws;
        Bounds clip;
        RecordingGraphics() { super(Minecraft.getInstance(), null); }
        @Override public void flush() {}
        @Override public boolean containsPointInScissor(int x, int y) {
            return clip == null || x >= clip.left() && x < clip.right()
                    && y >= clip.top() && y < clip.bottom();
        }
        @Override public void enableScissor(int left, int top, int right, int bottom) {
            assertNull(clip);
            clip = new Bounds(left, top, right, bottom);
        }
        @Override public void disableScissor() { clip = null; }
        @Override public int drawString(Font font, FormattedCharSequence text, int x, int y, int color, boolean shadow) {
            StringBuilder content = new StringBuilder();
            List<Style> styles = new ArrayList<>();
            text.accept((index, style, codePoint) -> {
                content.appendCodePoint(codePoint);
                styles.add(style);
                return true;
            });
            Vector4f position = new Vector4f(x, y, 0, 1).mul(pose().last().pose());
            draws.add(new Draw(content.toString(), position.x(), clip, color, List.copyOf(styles)));
            return x + font.width(text);
        }
    }
}

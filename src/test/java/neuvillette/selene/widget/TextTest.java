package neuvillette.selene.widget;

import static org.junit.jupiter.api.Assertions.*;

import com.mojang.blaze3d.font.GlyphInfo;
import com.mojang.blaze3d.font.SheetGlyphInfo;
import com.mojang.blaze3d.vertex.PoseStack;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ComponentPath;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.font.FontSet;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import net.minecraft.client.gui.navigation.FocusNavigationEvent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import neuvillette.selene.api.Color;
import neuvillette.selene.api.Paint;
import neuvillette.selene.api.Rect;
import org.joml.Vector4f;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;
import sun.misc.Unsafe;

class TextTest {
    private static Unsafe unsafe;
    private static Field minecraftInstance;
    private static Object previousMinecraft;

    @BeforeAll
    static void installHeadlessMinecraftAndMetricsOnlyFont() throws Exception {
        Field access = Unsafe.class.getDeclaredField("theUnsafe");
        access.setAccessible(true);
        unsafe = (Unsafe) access.get(null);
        minecraftInstance = Minecraft.class.getDeclaredField("instance");
        minecraftInstance.setAccessible(true);
        previousMinecraft = minecraftInstance.get(null);
        Minecraft minecraft = (Minecraft) unsafe.allocateInstance(Minecraft.class);
        FontSet metrics = new FontSet(null, ResourceLocation.withDefaultNamespace("headless_text")) {
            @Override
            public GlyphInfo getGlyphInfo(int character, boolean filterFishyGlyphs) {
                return new MetricGlyph(character == ' ' ? 4 : 6);
            }
        };
        // Use the real Font and StringSplitter; only glyph metrics and the eventual draw are substituted.
        Font font = new Font(ignored -> metrics, false);
        Field fontField = Minecraft.class.getDeclaredField("font");
        fontField.setAccessible(true);
        fontField.set(minecraft, font);
        minecraftInstance.set(null, minecraft);
    }

    @AfterAll
    static void restoreMinecraft() throws Exception {
        if (minecraftInstance != null) minecraftInstance.set(null, previousMinecraft);
    }

    @Test
    void defaultWrappingUsesPaddedContentWidthAndKeepsTheFixedOuterSize() {
        Text text = new Text(10, 20, 36, 40, "aa bb cc").Padding(2);
        RecordingGraphics graphics = graphics();

        text.render(graphics, 0, 0, 0);

        assertEquals(List.of("aa bb", "cc"), drawnText(graphics));
        assertDrawAt(graphics.draws.get(0), 12, 22, 1);
        assertDrawAt(graphics.draws.get(1), 12, 31, 1);
        for (Draw draw : graphics.draws) {
            assertEquals(0xFFFFFFFF, draw.color());
            assertFalse(draw.shadow());
            assertEquals(new Bounds(12, 22, 44, 58), draw.clip());
        }
        assertEquals(36, text.getWidth());
        assertEquals(40, text.getHeight());
        assertNull(graphics.currentClip());
    }

    @Test
    void scalingChangesWrapWidthAndAlignmentWhileExtraLineSpacingStaysInGuiUnits() {
        Text text = new Text(10, 20, 52, 80, "aa bb cc").Padding(4)
                .TextScale(2).LineSpacing(3).Align(Text.HorizontalAlignment.CENTER)
                .VerticalAlign(Text.VerticalAlignment.CENTER);
        RecordingGraphics graphics = graphics();

        text.render(graphics, 0, 0, 0);

        assertEquals(List.of("aa", "bb", "cc"), drawnText(graphics));
        assertDrawAt(graphics.draws.get(0), 24, 30, 2);
        assertDrawAt(graphics.draws.get(1), 24, 51, 2);
        assertDrawAt(graphics.draws.get(2), 24, 72, 2);
        assertEquals(new Bounds(14, 24, 58, 96), graphics.draws.getFirst().clip());
        assertEquals(1f, graphics.pose().last().pose().m00());
        assertEquals(0f, graphics.pose().last().pose().m30());
    }

    @Test
    void disablingWrapKeepsExplicitNewlinesAndOversizedTextStartsAtTheContentEdge() {
        Text text = new Text(5, 7, 30, 30, Component.literal("abcdef\nxy")).Padding(2)
                .Wrap(false).Align(Text.HorizontalAlignment.RIGHT).VerticalAlign(Text.VerticalAlignment.BOTTOM);
        RecordingGraphics graphics = graphics();

        text.render(graphics, 0, 0, 0);

        assertEquals(List.of("abcdef", "xy"), drawnText(graphics));
        assertDrawAt(graphics.draws.get(0), 7, 17, 1);
        assertDrawAt(graphics.draws.get(1), 21, 26, 1);
        assertEquals(new Bounds(7, 9, 33, 35), graphics.draws.getFirst().clip());

        text.setHeight(12);
        graphics.draws.clear();
        text.render(graphics, 0, 0, 0);
        assertEquals("abcdef", graphics.draws.getFirst().text());
        assertDrawAt(graphics.draws.getFirst(), 7, 9, 1);
        assertEquals(new Bounds(7, 9, 33, 17), graphics.draws.getFirst().clip());
    }

    @Test
    void componentStylesColorAndShadowReachTheDrawAndVeryLowEffectiveAlphaSkipsIt() {
        Component message = Component.empty()
                .append(Component.literal("A").withStyle(ChatFormatting.BOLD))
                .append(Component.literal("B").withStyle(ChatFormatting.RED));
        Text text = Text.FromComponent(message).TextColor(Color.fromRGBA(20, 40, 60, 128)).Shadow(true);
        text.setSize(100, 30);
        assertSame(message, text.getMessage());
        assertThrows(NullPointerException.class, () -> Text.FromComponent(null));
        RecordingGraphics graphics = graphics();
        text.render(graphics, 0, 0, 0);
        Draw draw = graphics.draws.getFirst();
        assertEquals("AB", draw.text());
        assertEquals(0x8014283C, draw.color());
        assertTrue(draw.shadow());
        assertTrue(draw.styles().get(0).isBold());
        assertEquals(0xFF5555, draw.styles().get(1).getColor().getValue());

        text.TextColor(Color.fromRGBA(20, 40, 60, 3));
        graphics.draws.clear();
        text.render(graphics, 0, 0, 0);
        assertTrue(graphics.draws.isEmpty());
        text.TextColor(Color.fromRGBA(20, 40, 60, 128));
        text.setAlpha(0.01f);
        text.render(graphics, 0, 0, 0);
        assertTrue(graphics.draws.isEmpty());
        text.setAlpha(1);
        text.TextColor(Color.fromRGBA(20, 40, 60, 4));
        text.render(graphics, 0, 0, 0);
        assertEquals(4, graphics.draws.getFirst().color() >>> 24);
    }

    @Test
    void textClippingIntersectsParentViewportAndRestoresPoseAndScissorsWhenDrawingThrows() {
        HeadlessScrollView view = new HeadlessScrollView(10, 20, 80, 40);
        new Text(view, 5, 15, 90, 40, Component.literal("text")).Padding(3).TextScale(2);
        RecordingGraphics graphics = graphics();
        Bounds previous = new Bounds(0, 0, 70, 55);
        graphics.enableScissor(0, 0, 70, 55);
        graphics.pose().translate(7, 11, 0);
        RuntimeException failure = new IllegalStateException("text draw failed");
        graphics.drawFailure = failure;

        assertSame(failure, assertThrows(RuntimeException.class, () -> view.render(graphics, 0, 0, 0)));

        assertEquals(new Bounds(18, 38, 70, 55), graphics.draws.getFirst().clip());
        assertEquals(previous, graphics.currentClip());
        assertEquals(1, graphics.clips.size());
        assertEquals(2, graphics.disableCalls);
        assertEquals(7f, graphics.pose().last().pose().m30());
        assertEquals(11f, graphics.pose().last().pose().m31());
        assertEquals(1f, graphics.pose().last().pose().m00());
        assertEquals(1f, graphics.pose().last().pose().m11());
    }

    @Test
    void visibleLabelsUseTheSuppliedTextStyleWithoutChangingItsGeometryOrParent() {
        Text label = new Text(99, 101, 30, 40, "AB").Padding(2, 3).TextScale(2)
                .TextColor(Color.fromRGBA(40, 68, 96, 128)).Shadow(true)
                .Align(Text.HorizontalAlignment.RIGHT).VerticalAlign(Text.VerticalAlignment.BOTTOM);
        label.setAlpha(0.5f);
        List<BaseWidget> widgets = List.of(new LabelButton(label), new LabelCard(label), new LabelInfoCard(label));

        for (BaseWidget widget : widgets) {
            widget.setAlpha(0.5f);
            RecordingGraphics graphics = graphics();
            widget.render(graphics, -1, -1, 0);

            assertEquals(1, graphics.draws.size());
            Draw draw = graphics.draws.getFirst();
            assertEquals("AB", draw.text());
            assertEquals(0x20284460, draw.color());
            assertTrue(draw.shadow());
            assertEquals(2f, draw.scaleX());
            assertEquals(2f, draw.scaleY());
            assertEquals(draw.clip().right(), draw.x() + 24, 0.0001f);
            assertEquals(draw.clip().bottom(), draw.y() + 18, 0.0001f);
            assertSame(label.getMessage(), widget.getMessage());
            assertEquals(99, label.getX());
            assertEquals(101, label.getY());
            assertEquals(30, label.getWidth());
            assertEquals(40, label.getHeight());
            assertNull(label.getParent());
            assertTrue(widget.children().isEmpty());
            assertNull(graphics.currentClip());
        }
    }

    @Test
    void layoutResizingAndMessageChangesReflowTextWithoutChangingItsAssignedHeight() {
        Layout layout = new Layout(0, 0, 30, 60);
        Text text = new Text(layout, 0, 0, 0, 0, Component.literal("aa bb cc")).Padding(3);
        RecordingGraphics graphics = graphics();
        layout.render(graphics, 0, 0, 0);
        assertEquals(List.of("aa", "bb", "cc"), drawnText(graphics));
        assertEquals(60, text.getHeight());

        layout.setWidth(60);
        graphics.draws.clear();
        layout.render(graphics, 0, 0, 0);
        assertEquals(List.of("aa bb cc"), drawnText(graphics));
        assertEquals(60, text.getWidth());
        assertEquals(60, text.getHeight());
        text.setMessage(Component.literal("aa\nbb"));
        graphics.draws.clear();
        layout.render(graphics, 0, 0, 0);
        assertEquals(List.of("aa", "bb"), drawnText(graphics));
        assertEquals(60, text.getHeight());
    }

    @Test
    void overlappingTextDoesNotBlockSiblingClickScrollOrKeyboardFocus() {
        ContainerProbe root = new ContainerProbe();
        ButtonProbe button = root.addChild(new ButtonProbe());
        Text label = root.addChild(new Text(0, 0, 100, 40, "caption"));
        button.consumeScroll = true;
        Host screen = new Host(root);
        assertFalse(label.isMouseOver(10, 10));
        assertSame(button, root.getChildAt(10, 10).orElseThrow());
        assertNull(label.nextFocusPath(new FocusNavigationEvent.TabNavigation(true)));

        focus(screen);
        assertTrue(button.isFocused());
        assertFalse(label.isFocused());
        assertTrue(screen.mouseClicked(10, 10, 0));
        assertEquals(1, button.presses);
        assertTrue(screen.mouseScrolled(10, 10, 0, -1));
        assertEquals(1, button.scrolls);
        assertFalse(label.mouseClicked(10, 10, 0));
    }

    @Test
    void textInsideScrollViewLetsItsContainerConsumeWheelWithoutBecomingFocusable() {
        ScrollView view = new ScrollView(0, 0, 100, 40);
        Text text = view.addChild(new Text(0, 0, 80, 100, "long content"));
        Host screen = new Host(view);

        assertTrue(screen.mouseScrolled(5, 5, 0, -1));
        assertEquals(20, view.getScrollAmount());
        assertFalse(screen.mouseClicked(5, 5, 0));
        assertNull(screen.nextFocusPath(new FocusNavigationEvent.TabNavigation(true)));
        assertFalse(text.isFocused());
    }

    @Test
    void buttonWithOnlyTextChildrenStillReceivesTabEnterAndClicks() {
        ButtonProbe button = new ButtonProbe();
        Text label = new Text(button, 5, 5, 90, 30, Component.literal("label"));
        Host screen = new Host(button);

        focus(screen);
        assertTrue(button.isFocused());
        assertNull(button.getFocused());
        assertFalse(label.isFocused());
        assertTrue(screen.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0));
        assertEquals(1, button.presses);
        assertEquals(1, button.sounds);
        assertTrue(screen.mouseClicked(10, 10, 0));
        assertEquals(2, button.presses);
        assertTrue(screen.keyPressed(GLFW.GLFW_KEY_SPACE, 0, 0));
        assertEquals(3, button.presses);
        assertEquals(3, button.sounds);
        assertFalse(label.isFocused());
    }

    private static List<String> drawnText(RecordingGraphics graphics) {
        return graphics.draws.stream().map(Draw::text).toList();
    }

    private static void assertDrawAt(Draw draw, float x, float y, float scale) {
        assertEquals(x, draw.x(), 0.00001f);
        assertEquals(y, draw.y(), 0.00001f);
        assertEquals(scale, draw.scaleX(), 0.00001f);
        assertEquals(scale, draw.scaleY(), 0.00001f);
    }

    private static RecordingGraphics graphics() {
        try {
            RecordingGraphics graphics = (RecordingGraphics) unsafe.allocateInstance(RecordingGraphics.class);
            Field pose = GuiGraphics.class.getDeclaredField("pose");
            pose.setAccessible(true);
            pose.set(graphics, new PoseStack());
            graphics.clips = new ArrayList<>();
            graphics.draws = new ArrayList<>();
            return graphics;
        } catch (ReflectiveOperationException error) {
            throw new AssertionError("Cannot initialize headless text graphics", error);
        }
    }

    private static void focus(Host screen) {
        ComponentPath path = screen.nextFocusPath(new FocusNavigationEvent.TabNavigation(true));
        assertNotNull(path, "Expected an interactive widget in the focus path");
        ComponentPath previous = screen.getCurrentFocusPath();
        if (previous != null) previous.applyFocus(false);
        path.applyFocus(true);
    }

    private record MetricGlyph(float advance) implements GlyphInfo {
        @Override public float getAdvance() { return advance; }

        @Override
        public BakedGlyph bake(Function<SheetGlyphInfo, BakedGlyph> provider) {
            throw new AssertionError("Headless metrics must never bake a GPU glyph");
        }
    }

    private static final class Host extends Screen {
        Host(BaseWidget root) {
            super(Component.literal("Text test"));
            addRenderableWidget(root);
        }
    }

    private static final class ContainerProbe extends BaseWidget {
        ContainerProbe() { super(0, 0, 100, 40, Component.empty()); }
        @Override protected void renderSelf(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {}
        @Override public void playDownSound(SoundManager manager) {}
    }

    private static final class ButtonProbe extends BaseButton {
        int presses;
        int sounds;
        int scrolls;
        boolean consumeScroll;

        ButtonProbe() { super(0, 0, 100, 40, Component.literal("Button")); }
        @Override public void onPress() { presses++; }
        @Override public void playDownSound(SoundManager manager) { sounds++; }
        @Override protected void renderSelf(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {}

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
            if (super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) return true;
            if (!isActive() || !consumeScroll) return false;
            scrolls++;
            return true;
        }
    }

    private static final class HeadlessScrollView extends ScrollView {
        HeadlessScrollView(int x, int y, int width, int height) { super(x, y, width, height); }
        @Override protected void renderScrollbarThumb(GuiGraphics graphics, Rect bounds, int color) {}
    }

    private static final class LabelButton extends Button {
        LabelButton(Text text) { super(10, 20, 140, 60, text); Padding(0); }
        @Override protected void renderBackground(GuiGraphics graphics, Rect bounds, float radius, Color color) {}
    }

    private static final class LabelCard extends Card {
        LabelCard(Text text) { super(10, 20, 140, 60, text); }
        @Override protected void renderCardFrame(GuiGraphics graphics, Rect bounds, Color fill, Color border, float width) {}
    }

    private static final class LabelInfoCard extends InfoCard {
        LabelInfoCard(Text text) { super(10, 20, 140, 60, text); }
        @Override protected void renderCard(GuiGraphics graphics, Rect bounds, float radius, int footer,
                                            Paint.InfoCardColors colors, float opacity) {}
    }

    private record Bounds(int left, int top, int right, int bottom) {
        Bounds intersection(Bounds other) {
            int x = Math.max(left, other.left);
            int y = Math.max(top, other.top);
            return new Bounds(x, y, Math.max(x, Math.min(right, other.right)),
                    Math.max(y, Math.min(bottom, other.bottom)));
        }

        boolean contains(int x, int y) {
            return x >= left && x < right && y >= top && y < bottom;
        }
    }

    private record Draw(String text, float x, float y, float scaleX, float scaleY,
                        int color, boolean shadow, Bounds clip, List<Style> styles) {}

    private static final class RecordingGraphics extends GuiGraphics {
        List<Bounds> clips;
        List<Draw> draws;
        int disableCalls;
        RuntimeException drawFailure;

        RecordingGraphics() { super(Minecraft.getInstance(), null); }
        Bounds currentClip() { return clips.isEmpty() ? null : clips.getLast(); }

        @Override public void flush() {}

        @Override
        public void enableScissor(int left, int top, int right, int bottom) {
            Bounds next = new Bounds(left, top, right, bottom);
            Bounds current = currentClip();
            clips.add(current == null ? next : current.intersection(next));
        }

        @Override
        public void disableScissor() {
            assertFalse(clips.isEmpty(), "Scissor stack underflow");
            clips.removeLast();
            disableCalls++;
        }

        @Override
        public boolean containsPointInScissor(int x, int y) {
            return currentClip() == null || currentClip().contains(x, y);
        }

        @Override
        public int drawString(Font font, FormattedCharSequence text, int x, int y, int color, boolean shadow) {
            return recordDraw(font, text, x, y, color, shadow);
        }

        @Override
        public int drawString(Font font, FormattedCharSequence text, float x, float y, int color, boolean shadow) {
            return recordDraw(font, text, x, y, color, shadow);
        }

        private int recordDraw(Font font, FormattedCharSequence text, float x, float y, int color, boolean shadow) {
            StringBuilder content = new StringBuilder();
            List<Style> styles = new ArrayList<>();
            text.accept((index, style, codePoint) -> {
                content.appendCodePoint(codePoint);
                styles.add(style);
                return true;
            });
            var matrix = pose().last().pose();
            Vector4f position = new Vector4f(x, y, 0, 1).mul(matrix);
            draws.add(new Draw(content.toString(), position.x(), position.y(), matrix.m00(), matrix.m11(),
                    color, shadow, currentClip(), List.copyOf(styles)));
            if (drawFailure != null) throw drawFailure;
            return (int) x + font.width(text);
        }
    }
}

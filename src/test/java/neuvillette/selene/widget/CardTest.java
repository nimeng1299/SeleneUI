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
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import neuvillette.selene.api.Color;
import neuvillette.selene.api.Rect;
import org.joml.Vector4f;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

class CardTest {
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
        FontSet metrics = new FontSet(null, ResourceLocation.withDefaultNamespace("headless_card")) {
            @Override
            public GlyphInfo getGlyphInfo(int character, boolean filterFishyGlyphs) {
                return new MetricGlyph(character == ' ' ? 4 : 6);
            }
        };
        Field fontField = Minecraft.class.getDeclaredField("font");
        fontField.setAccessible(true);
        fontField.set(minecraft, new Font(ignored -> metrics, false));
        minecraftInstance.set(null, minecraft);
    }

    @AfterAll
    static void restoreMinecraft() throws Exception {
        if (minecraftInstance != null) minecraftInstance.set(null, previousMinecraft);
    }

    @Test
    void titleAndPaddingOffsetDescendantsWithoutChangingTheOuterBounds() {
        Card card = new Card(10, 20, 100, 80, "Heading").Padding(2, 3, 4, 5);
        Probe child = new Probe(card, 3, 7, 10, 11);
        Probe leaf = new Probe(child, 2, 4, 5, 5);

        assertEquals(9, card.getTitleHeight());
        assertEquals(15, card.getContentX());
        assertEquals(31, card.getContentY());
        assertEquals(92, card.getContentWidth());
        assertEquals(65, card.getContentHeight());
        assertEquals(18, child.getX());
        assertEquals(38, child.getY());
        assertEquals(42, leaf.getY());

        card.TitleScale(1.5f);
        assertEquals(14, card.getTitleHeight());
        assertEquals(36, card.getContentY());
        assertEquals(60, card.getContentHeight());
        assertEquals(43, child.getY());
        assertEquals(47, leaf.getY());

        card.Title(Component.empty());
        assertEquals(0, card.getTitleHeight());
        assertEquals(22, card.getContentY());
        assertEquals(74, card.getContentHeight());
        assertEquals(29, child.getY());
        assertEquals(33, leaf.getY());
        assertEquals(100, card.getWidth());
        assertEquals(80, card.getHeight());
    }

    @Test
    void deepTitleChangesUpdateScrollMeasurementAndClampTheExistingOffset() {
        ScrollView view = new ScrollView(0, 0, 100, 30);
        Probe container = new Probe(view, 0, 5, 90, 10);
        container.setPadding(2, 0, 0, 0);
        Card card = new Card(container, 0, 3, 80, 10, Component.literal("Heading")).Padding(4, 0, 0, 0);
        Probe leaf = new Probe(card, 0, 20, 10, 10);

        assertEquals(53, view.getScrollHeight());
        assertEquals(23, view.getMaxScroll());
        card.TitleScale(2);
        assertEquals(62, view.getScrollHeight());
        assertEquals(32, view.getMaxScroll());
        view.ensureVisible(leaf);
        assertEquals(32, view.getScrollAmount());
        assertEquals(20, leaf.getY());

        card.Title("");
        assertEquals(44, view.getScrollHeight());
        assertEquals(14, view.getMaxScroll());
        assertEquals(14, view.getScrollAmount());
        assertEquals(20, leaf.getY());
        card.Title("Restored");
        assertEquals(62, view.getScrollHeight());
        assertEquals(32, view.getMaxScroll());
    }

    @Test
    void framePartitionsLeaveOnlyTheTopTitleGapAndNeverDoubleBlendTranslucentPixels() {
        RecordingCard card = new RecordingCard(10, 20, 100, 60, "AB");
        card.Radius(8).BorderWidth(2)
                .FillColor(Color.fromRGBA(10, 20, 30, 96))
                .BorderColor(Color.fromRGBA(40, 50, 60, 128))
                .TitleColor(Color.fromRGBA(70, 80, 90, 128));
        card.setAlpha(0.5f);
        RecordingGraphics graphics = graphics();

        card.render(graphics, 0, 0, 0);

        Bounds frameBounds = new Bounds(10, 24, 110, 80);
        Bounds widgetBounds = new Bounds(10, 20, 110, 80);
        assertEquals(4, card.frames.size());
        for (Frame frame : card.frames) {
            assertEquals(frameBounds, frame.bounds());
            assertEquals(0x300A141E, frame.fill());
            assertEquals(frame.borderWidth() == 0 ? 0x300A141E : 0x4028323C, frame.border());
            assertInside(frame.clip(), widgetBounds);
        }
        assertEquals(1, card.frames.stream().filter(frame -> frame.borderWidth() == 0).count());
        assertEquals(0, frameAt(card.frames, 28, 24).borderWidth());
        assertEquals(2, frameAt(card.frames, 28, 79).borderWidth());
        assertEquals(2, frameAt(card.frames, 10, 24).borderWidth());
        assertEquals(2, frameAt(card.frames, 109, 24).borderWidth());
        for (int y = frameBounds.top(); y < frameBounds.bottom(); y++) {
            for (int x = frameBounds.left(); x < frameBounds.right(); x++) {
                int coverage = 0;
                for (Frame frame : card.frames) if (frame.clip().contains(x, y)) coverage++;
                assertEquals(1, coverage, "Overlapping or missing frame coverage at " + x + ", " + y);
            }
        }
        assertEquals(1, graphics.draws.size());
        Draw title = graphics.draws.getFirst();
        assertEquals("AB", title.text());
        assertEquals(22f, title.x());
        assertEquals(20f, title.y());
        assertEquals(0x4046505A, title.color());
        assertInside(title.clip(), widgetBounds);
        assertNull(graphics.currentClip());
    }

    @Test
    void longTitlesUseOnlyTheirFirstFittingLineAndSmallCardsKeepAllDrawingClipped() {
        RecordingCard card = new RecordingCard(10, 20, 40, 24, "abcdefghijklmnop\nsecond line");
        RecordingGraphics graphics = graphics();
        Bounds previous = new Bounds(0, 0, 45, 40);
        graphics.enableScissor(previous.left(), previous.top(), previous.right(), previous.bottom());

        card.render(graphics, 0, 0, 0);

        assertEquals(1, graphics.draws.size());
        Draw title = graphics.draws.getFirst();
        assertFalse(title.text().isEmpty());
        assertTrue("abcdefghijklmnop".startsWith(title.text()));
        assertTrue(title.text().length() < 16);
        Bounds visibleCard = new Bounds(10, 20, 45, 40);
        assertInside(title.clip(), visibleCard);
        for (Frame frame : card.frames) assertInside(frame.clip(), visibleCard);
        assertEquals(previous, graphics.currentClip());

        card.setSize(8, 3);
        card.frames.clear();
        graphics.draws.clear();
        card.render(graphics, 0, 0, 0);
        assertEquals(0, card.getContentHeight());
        assertTrue(graphics.draws.isEmpty());
        for (Frame frame : card.frames) assertInside(frame.clip(), new Bounds(10, 20, 18, 23));
        assertEquals(previous, graphics.currentClip());
        assertEquals(1, graphics.clips.size());
    }

    @Test
    void bothFrameAndTitleFailuresRestoreTheCallersScissorAndPose() {
        for (boolean failTitle : new boolean[] {false, true}) {
            RecordingCard card = new RecordingCard(10, 20, 100, 60, "AB");
            card.TitleScale(2);
            RecordingGraphics graphics = graphics();
            Bounds previous = new Bounds(0, 0, 90, 60);
            graphics.enableScissor(previous.left(), previous.top(), previous.right(), previous.bottom());
            graphics.pose().translate(7, 11, 0);
            RuntimeException failure = new IllegalStateException(failTitle ? "title failed" : "frame failed");
            if (failTitle) graphics.drawFailure = failure;
            else card.frameFailure = failure;

            assertSame(failure, assertThrows(RuntimeException.class, () -> card.render(graphics, 0, 0, 0)));

            assertEquals(previous, graphics.currentClip());
            assertEquals(1, graphics.clips.size());
            assertEquals(7f, graphics.pose().last().pose().m30());
            assertEquals(11f, graphics.pose().last().pose().m31());
            assertEquals(1f, graphics.pose().last().pose().m00());
            assertEquals(1f, graphics.pose().last().pose().m11());
            if (failTitle) {
                assertEquals(1, graphics.draws.size());
                assertInside(graphics.draws.getFirst().clip(), new Bounds(10, 20, 90, 60));
            } else {
                assertEquals(1, card.frames.size());
            }
        }
    }

    private static Frame frameAt(List<Frame> frames, int x, int y) {
        List<Frame> covering = frames.stream().filter(frame -> frame.clip().contains(x, y)).toList();
        assertEquals(1, covering.size());
        return covering.getFirst();
    }

    private static void assertInside(Bounds clip, Bounds outer) {
        assertNotNull(clip);
        assertTrue(clip.left() >= outer.left() && clip.top() >= outer.top()
                && clip.right() <= outer.right() && clip.bottom() <= outer.bottom(),
                () -> clip + " extends beyond " + outer);
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
            throw new AssertionError("Cannot initialize headless card graphics", error);
        }
    }

    private record MetricGlyph(float advance) implements GlyphInfo {
        @Override public float getAdvance() { return advance; }
        @Override public BakedGlyph bake(Function<SheetGlyphInfo, BakedGlyph> provider) {
            throw new AssertionError("Headless metrics must never bake a GPU glyph");
        }
    }

    private static final class Probe extends BaseWidget {
        Probe(BaseWidget parent, int x, int y, int width, int height) {
            super(parent, x, y, width, height, Component.empty());
        }
        @Override protected void renderSelf(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {}
    }

    private static final class RecordingCard extends Card {
        final List<Frame> frames = new ArrayList<>();
        RuntimeException frameFailure;

        RecordingCard(int x, int y, int width, int height, String title) {
            super(x, y, width, height, title);
        }

        @Override
        protected void renderCardFrame(GuiGraphics graphics, Rect bounds, Color fill, Color border, float width) {
            frames.add(new Frame(new Bounds(bounds.x(), bounds.y(), bounds.x2(), bounds.y2()),
                    fill.toARGB(), border.toARGB(), width, ((RecordingGraphics) graphics).currentClip()));
            if (frameFailure != null) throw frameFailure;
        }
    }

    private record Frame(Bounds bounds, int fill, int border, float borderWidth, Bounds clip) {}
    private record Draw(String text, float x, float y, int color, Bounds clip) {}

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

    private static final class RecordingGraphics extends GuiGraphics {
        List<Bounds> clips;
        List<Draw> draws;
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
        }

        @Override
        public boolean containsPointInScissor(int x, int y) {
            return currentClip() == null || currentClip().contains(x, y);
        }

        @Override
        public int drawString(Font font, FormattedCharSequence text, int x, int y, int color, boolean shadow) {
            return recordDraw(font, text, x, y, color);
        }

        @Override
        public int drawString(Font font, FormattedCharSequence text, float x, float y, int color, boolean shadow) {
            return recordDraw(font, text, x, y, color);
        }

        private int recordDraw(Font font, FormattedCharSequence text, float x, float y, int color) {
            StringBuilder content = new StringBuilder();
            text.accept((index, style, codePoint) -> {
                content.appendCodePoint(codePoint);
                return true;
            });
            Vector4f position = new Vector4f(x, y, 0, 1).mul(pose().last().pose());
            draws.add(new Draw(content.toString(), position.x(), position.y(), color, currentClip()));
            if (drawFailure != null) throw drawFailure;
            return (int) x + font.width(text);
        }
    }
}

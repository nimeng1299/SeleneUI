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
import neuvillette.selene.api.Paint;
import neuvillette.selene.api.Rect;
import org.joml.Vector4f;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

class InfoCardTest {
    private static Unsafe unsafe;
    private static Field minecraftInstance;
    private static Object previousMinecraft;

    @BeforeAll
    static void installHeadlessMinecraft() throws Exception {
        Field access = Unsafe.class.getDeclaredField("theUnsafe");
        access.setAccessible(true);
        unsafe = (Unsafe) access.get(null);
        minecraftInstance = Minecraft.class.getDeclaredField("instance");
        minecraftInstance.setAccessible(true);
        previousMinecraft = minecraftInstance.get(null);
        Minecraft minecraft = (Minecraft) unsafe.allocateInstance(Minecraft.class);
        FontSet metrics = new FontSet(null, ResourceLocation.withDefaultNamespace("headless_info_card")) {
            @Override public GlyphInfo getGlyphInfo(int character, boolean filter) {
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
    void footerReservesSpaceAcrossParentPaddingAndResizing() {
        Block parent = new Block(10, 20, 300, 250, Component.empty()).Padding(2, 3, 4, 5);
        InfoCard card = new InfoCard(parent, 7, 11, 120, 150, Component.literal("Lv.90"))
                .Padding(3, 4, 5, 6);
        BaseWidget child = card.addChild(new Block(null, Component.empty()));
        child.setRelativePosition(2, 4);
        assertEquals(22, card.getX());
        assertEquals(33, card.getY());
        assertEquals(30, card.getFooterHeight());
        assertEquals(120, card.getImageHeight());
        assertEquals(110, card.getContentWidth());
        assertEquals(112, card.getContentHeight());
        assertEquals(30, child.getX());
        assertEquals(40, child.getY());

        card.setHeight(18);
        assertEquals(18, card.getFooterHeight());
        assertEquals(0, card.getImageHeight());
        assertEquals(0, card.getContentHeight());
        card.setHeight(200);
        assertEquals(30, card.getFooterHeight());
        assertEquals(162, card.getContentHeight());
        card.FooterHeight(40);
        assertEquals(152, card.getContentHeight());
        card.FooterHeight(0);
        assertEquals(192, card.getContentHeight());
        parent.setPosition(20, 30);
        assertEquals(40, child.getX());
        assertEquals(50, child.getY());

        InfoCard empty = new InfoCard(parent, Component.empty());
        assertTrue(parent.children().contains(empty));
        assertEquals(0, empty.getWidth());
        assertEquals(0, empty.getContentHeight());
    }

    @Test
    void footerTextIsCenteredAndOpacityIsPassedOnceToTheCompositor() throws Exception {
        Probe card = new Probe(10, 20, 120, 150, "Lv.90");
        card.TextScale(2).TextColor(Color.fromRGBA(20, 40, 60, 128));
        card.setAlpha(0.5f);
        Graphics graphics = graphics();

        card.render(graphics, -1, -1, 0);

        assertEquals(new Bounds(10, 20, 130, 170), card.frame);
        assertEquals(8, card.radius);
        assertEquals(30, card.footer);
        assertNotEquals(card.colors.top().toARGB(), card.colors.bottom().toARGB());
        assertEquals(255, card.colors.top().Alpha());
        assertEquals(0.5f, card.opacity);
        assertEquals(1, graphics.draws.size());
        Draw draw = graphics.draws.getFirst();
        assertEquals("Lv.90", draw.text());
        assertEquals(40f, draw.x());
        assertEquals(146f, draw.y());
        assertEquals(0x4014283C, draw.color());
        assertEquals(new Bounds(18, 140, 122, 170), draw.clip());
        assertTrue(graphics.clips.isEmpty());
        assertEquals(1, graphics.pose().last().pose().m00());
    }

    @Test
    void longTextStaysInTheFooterAndRestoresAncestorClippingEvenOnFailure() throws Exception {
        Probe card = new Probe(10, 20, 40, 35, "abcdefghijklmnop\nsecond");
        card.FooterHeight(12).TextScale(2);
        Graphics graphics = graphics();
        Bounds previous = new Bounds(0, 0, 45, 50);
        graphics.enableScissor(0, 0, 45, 50);
        graphics.pose().translate(3, 4, 0);
        card.render(graphics, -1, -1, 0);
        assertEquals(1, graphics.draws.size());
        Draw draw = graphics.draws.getFirst();
        assertTrue("abcdefghijklmnop".startsWith(draw.text()));
        assertTrue(draw.text().length() < 16);
        assertEquals(new Bounds(18, 43, 42, 50), draw.clip());
        assertEquals(previous, graphics.clip());
        RuntimeException failure = new IllegalStateException("drawing failed");
        graphics.failure = failure;
        assertSame(failure, assertThrows(RuntimeException.class, () -> card.render(graphics, -1, -1, 0)));
        assertEquals(previous, graphics.clip());
        assertEquals(1, graphics.clips.size());
        assertEquals(3, graphics.pose().last().pose().m30());
        assertEquals(4, graphics.pose().last().pose().m31());
        assertEquals(1, graphics.pose().last().pose().m00());
    }

    @Test
    void emptySmallHiddenAndTransparentCardsDoNotDrawText() throws Exception {
        Probe card = new Probe(0, 0, 120, 150, "Lv.90");
        Graphics graphics = graphics();
        card.FooterHeight(0);
        card.render(graphics, -1, -1, 0);
        assertTrue(graphics.draws.isEmpty());
        card.FooterHeight(30).TextScale(Float.MAX_VALUE);
        card.render(graphics, -1, -1, 0);
        assertTrue(graphics.draws.isEmpty());
        card.TextScale(1);
        card.setSize(3, 2);
        card.render(graphics, -1, -1, 0);
        assertEquals(1, card.radius);
        assertEquals(2, card.footer);
        assertTrue(graphics.draws.isEmpty());
        card.setSize(120, 150);
        card.setMessage(Component.empty());
        card.render(graphics, -1, -1, 0);
        assertTrue(graphics.draws.isEmpty());
        int frames = card.frames;
        card.setAlpha(0);
        card.render(graphics, -1, -1, 0);
        assertEquals(frames, card.frames);
        card.setAlpha(1);
        card.visible = false;
        card.render(graphics, -1, -1, 0);
        assertEquals(frames, card.frames);
        assertTrue(graphics.clips.isEmpty());
    }

    @Test
    void invalidStylesAreRejected() {
        InfoCard card = new InfoCard(Component.empty());
        for (float value : new float[] {0, -1, Float.NaN, Float.POSITIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> card.ImageScale(value));
            assertThrows(IllegalArgumentException.class, () -> card.TextScale(value));
        }
        for (float value : new float[] {-1, 2, Float.NaN, Float.POSITIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> card.Grain(value));
        }
        assertThrows(IllegalArgumentException.class, () -> card.Radius(-1));
        assertThrows(IllegalArgumentException.class, () -> card.FooterHeight(-1));
        assertThrows(NullPointerException.class, () -> card.Gradient(null, Color.fromRGB(0, 0, 0)));
        assertDoesNotThrow(() -> card.Image(null).BackgroundTexture(null).Grain(0).FooterHeight(0).Radius(0));
    }

    private static Graphics graphics() throws Exception {
        Graphics graphics = (Graphics) unsafe.allocateInstance(Graphics.class);
        Field pose = GuiGraphics.class.getDeclaredField("pose");
        pose.setAccessible(true);
        pose.set(graphics, new PoseStack());
        graphics.clips = new ArrayList<>();
        graphics.draws = new ArrayList<>();
        return graphics;
    }

    private record MetricGlyph(float advance) implements GlyphInfo {
        @Override public float getAdvance() { return advance; }
        @Override public BakedGlyph bake(Function<SheetGlyphInfo, BakedGlyph> provider) {
            throw new AssertionError("Metrics only");
        }
    }

    private static class Probe extends InfoCard {
        Bounds frame;
        float radius;
        int footer;
        Paint.InfoCardColors colors;
        float opacity;
        int frames;

        Probe(int x, int y, int width, int height, String label) {
            super(x, y, width, height, Component.literal(label));
        }

        @Override protected void renderCard(GuiGraphics graphics, Rect rect, float radius, int footer,
                                             Paint.InfoCardColors colors, float opacity) {
            frame = new Bounds(rect.x(), rect.y(), rect.x2(), rect.y2());
            this.radius = radius;
            this.footer = footer;
            this.colors = colors;
            this.opacity = opacity;
            frames++;
        }
    }

    private record Bounds(int left, int top, int right, int bottom) {
        Bounds intersect(Bounds other) {
            int x = Math.max(left, other.left);
            int y = Math.max(top, other.top);
            return new Bounds(x, y, Math.max(x, Math.min(right, other.right)), Math.max(y, Math.min(bottom, other.bottom)));
        }
    }
    private record Draw(String text, float x, float y, int color, Bounds clip) {}

    private static class Graphics extends GuiGraphics {
        List<Bounds> clips;
        List<Draw> draws;
        RuntimeException failure;
        Graphics() { super(Minecraft.getInstance(), null); }
        Bounds clip() { return clips.isEmpty() ? null : clips.getLast(); }
        @Override public void flush() {}
        @Override public void enableScissor(int left, int top, int right, int bottom) {
            Bounds bounds = new Bounds(left, top, right, bottom);
            clips.add(clip() == null ? bounds : clip().intersect(bounds));
        }
        @Override public void disableScissor() { clips.removeLast(); }
        @Override public boolean containsPointInScissor(int x, int y) { return false; }
        @Override public int drawString(Font font, FormattedCharSequence label, int x, int y, int color, boolean shadow) {
            StringBuilder text = new StringBuilder();
            label.accept((index, style, codePoint) -> { text.appendCodePoint(codePoint); return true; });
            Vector4f position = new Vector4f(x, y, 0, 1).mul(pose().last().pose());
            draws.add(new Draw(text.toString(), position.x(), position.y(), color, clip()));
            if (failure != null) throw failure;
            return font.width(label);
        }
    }
}

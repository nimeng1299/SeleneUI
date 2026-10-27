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
import neuvillette.selene.layout.Constraint;
import neuvillette.selene.state.State;
import org.joml.Vector4f;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

class DetailCardTest {
    private static Unsafe unsafe;
    private static Field instance;
    private static Object previousMinecraft;
    private static final ResourceLocation BOW = ResourceLocation.withDefaultNamespace("textures/item/bow.png");

    @BeforeAll
    static void installHeadlessMinecraft() throws Exception {
        Field access = Unsafe.class.getDeclaredField("theUnsafe");
        access.setAccessible(true);
        unsafe = (Unsafe) access.get(null);
        instance = Minecraft.class.getDeclaredField("instance");
        instance.setAccessible(true);
        previousMinecraft = instance.get(null);
        Minecraft minecraft = (Minecraft) unsafe.allocateInstance(Minecraft.class);
        FontSet metrics = new FontSet(null, ResourceLocation.withDefaultNamespace("detail_card_test")) {
            @Override public GlyphInfo getGlyphInfo(int character, boolean filter) { return new MetricGlyph(6); }
        };
        Field font = Minecraft.class.getDeclaredField("font");
        font.setAccessible(true);
        font.set(minecraft, new Font(ignored -> metrics, false));
        instance.set(null, minecraft);
    }

    @AfterAll
    static void restoreMinecraft() throws Exception {
        if (instance != null) instance.set(null, previousMinecraft);
    }

    @Test
    void sectionsFollowParentPaddingResizingAndTheOptionalImageColumn() {
        Block parent = new Block(30, 40, 500, 500, Component.empty()).Padding(2, 3, 4, 5);
        DetailCard card = new DetailCard(parent, 7, 11, 240, 240, new Text("Title"))
                .BorderWidth(2).Padding(3, 4, 5, 6).HeaderHeight(24).BodyHeight(80).Image(BOW);
        Text stat = card.getBody().addChild(new Text("674"), Constraint.length(20));
        assertEquals(42, card.getX());
        assertEquals(53, card.getY());
        assertEquals(50, card.getContentX());
        assertEquals(58, card.getContentY());
        assertEquals(226, card.getContentWidth());
        assertEquals(228, card.getContentHeight());
        assertEquals(24, card.getHeader().getHeight());
        assertEquals(80, card.getBody().getHeight());
        assertEquals(124, card.getFooter().getHeight());
        assertEquals(146, card.getBody().getWidth());
        assertEquals(62, stat.getX());
        assertEquals(90, stat.getY());
        assertEquals(162, card.getFooter().getY());
        parent.setPosition(40, 50);
        assertEquals(72, stat.getX());
        assertEquals(100, stat.getY());

        card.setWidth(100);
        assertEquals(43, card.getImageWidth());
        assertEquals(43, card.getBody().getWidth());
        card.setHeight(30);
        assertEquals(18, card.getHeader().getHeight());
        assertEquals(0, card.getBody().getHeight());
        assertEquals(0, card.getFooter().getHeight());
        assertEquals(0, card.getImageWidth());
        card.setSize(240, 240);
        assertEquals(24, card.getHeader().getHeight());
        assertEquals(80, card.getBody().getHeight());
        card.Image(null);
        assertEquals(226, card.getBody().getWidth());
        card.Image(BOW).ImageWidth(1000);
        assertEquals(0, card.getBody().getWidth());
        card.ImageWidth(0);
        assertEquals(226, card.getBody().getWidth());
        card.setMessage(Component.literal("Changed"));
        assertEquals("Changed", card.getText().getMessage().getString());
    }

    @Test
    void multipleTextsUseTheirOwnStylesAndTheThreeBackgroundsStaySeparate() throws Exception {
        Probe card = new Probe(10, 20, 160, 200);
        card.HeaderHeight(24).BodyHeight(70).Image(BOW);
        card.getHeader().Padding(4).Horizontal();
        card.getHeader().addChild(new Text("sub"), Constraint.length(30));
        card.getBody().Padding(4).Spacing(2);
        card.getBody().addChild(new Text("22.1%"), Constraint.length(16));
        card.getBody().addChild(new Text("674").TextScale(1.5f), Constraint.fill(1));
        card.getFooter().Padding(4).Spacing(3);
        card.getFooter().addChild(new Text("Attack +33"), Constraint.length(15));
        card.getFooter().addChild(new Text("Long description wraps into multiple lines")
                .TextColor(Color.fromRGBA(10, 20, 30, 128)), Constraint.fill(1));
        card.setAlpha(0.5f);
        Graphics graphics = graphics();
        card.render(graphics, -1, -1, 0);

        assertEquals(2, card.images.size());
        ImageArea background = card.images.getFirst();
        ImageArea picture = card.images.getLast();
        assertEquals(new Bounds(11, 45, 169, 115), background.bounds());
        assertNotEquals(background.colors().top().toARGB(), background.colors().bottom().toARGB());
        assertTrue(background.grain() > 0);
        assertNull(background.image());
        assertEquals(0.5f, background.opacity());
        assertEquals(BOW, picture.image());
        assertEquals(new Bounds(99, 45, 169, 115), picture.bounds());
        assertEquals(0, picture.colors().top().Alpha());
        assertEquals(0, picture.colors().bottom().Alpha());
        assertEquals(0.5f, picture.opacity());
        assertEquals(1, graphics.draws.stream().filter(draw -> draw.text().equals("Title")).count());
        Draw attack = graphics.draws.stream().filter(draw -> draw.text().equals("Attack +33")).findFirst().orElseThrow();
        assertEquals(0x8028475F, attack.color());
        assertTrue(attack.clip().top() >= 115);
        assertTrue(graphics.draws.stream().anyMatch(draw -> draw.text().startsWith("Long") && draw.color() == 0x400A141E));
        for (Draw draw : graphics.draws) {
            assertTrue(draw.clip().left() >= 11);
            assertTrue(draw.clip().right() <= 169);
            assertTrue(draw.clip().top() >= 21);
            assertTrue(draw.clip().bottom() <= 219);
        }
        assertTrue(graphics.clips.isEmpty());
    }

    @Test
    void nestedLayoutsAndStateBoundTextsRemainMountedInTheirSection() throws Exception {
        State<String> state = new State<>("initial");
        Text bound = new Text("") {{ subscribe(state, value -> setMessage(Component.literal(value))); }};
        Probe card = new Probe(0, 0, 180, 180);
        Layout row = card.getBody().addChild(new Layout().Horizontal().Spacing(2), Constraint.length(20));
        row.addChild(bound, Constraint.fill(1));
        row.addChild(new Text("other"), Constraint.fill(1));
        card.mount();
        try {
            assertTrue(bound.isMounted());
            assertEquals("initial", bound.getMessage().getString());
            state.set("changed");
            assertEquals("changed", bound.getMessage().getString());
            card.render(graphics(), -1, -1, 0);
            assertSame(row, bound.getParent());
            assertTrue(bound.getWidth() > 0);
            assertTrue(bound.getY() >= card.getBody().getY());
        } finally {
            card.unmount();
        }
        state.set("unmounted");
        assertEquals("changed", bound.getMessage().getString());
        card.mount();
        try { assertEquals("unmounted", bound.getMessage().getString()); }
        finally { card.unmount(); }
    }

    @Test
    void sectionClippingAndPoseAreRestoredWhenTextDrawingFails() throws Exception {
        Probe card = new Probe(10, 20, 160, 200);
        Graphics graphics = graphics();
        Bounds previous = new Bounds(0, 0, 100, 100);
        graphics.enableScissor(0, 0, 100, 100);
        graphics.pose().translate(3, 4, 0);
        graphics.failure = new IllegalStateException("draw failed");
        assertSame(graphics.failure, assertThrows(IllegalStateException.class, () -> card.render(graphics, -1, -1, 0)));
        assertEquals(previous, graphics.clip());
        assertEquals(1, graphics.clips.size());
        assertEquals(3, graphics.pose().last().pose().m30());
        assertEquals(4, graphics.pose().last().pose().m31());
        assertEquals(1, graphics.pose().last().pose().m00());
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
        @Override public BakedGlyph bake(Function<SheetGlyphInfo, BakedGlyph> provider) { throw new AssertionError("Metrics only"); }
    }

    private static class Probe extends DetailCard {
        final List<ImageArea> images = new ArrayList<>();
        Probe(int x, int y, int width, int height) { super(x, y, width, height, new Text("Title")); }
        @Override protected void renderFlatArea(GuiGraphics graphics, Rect bounds, Color fill, Color border, int width) {}
        @Override protected void renderImageArea(GuiGraphics graphics, Rect bounds, Paint.InfoCardColors colors, float grain,
                                                  ResourceLocation image, float scale, ResourceLocation background, float opacity) {
            images.add(new ImageArea(new Bounds(bounds.x(), bounds.y(), bounds.x2(), bounds.y2()), colors, grain, image, opacity));
        }
    }

    private record ImageArea(Bounds bounds, Paint.InfoCardColors colors, float grain, ResourceLocation image, float opacity) {}
    private record Draw(String text, int color, Bounds clip) {}
    private record Bounds(int left, int top, int right, int bottom) {
        Bounds intersect(Bounds other) {
            int x = Math.max(left, other.left);
            int y = Math.max(top, other.top);
            return new Bounds(x, y, Math.max(x, Math.min(right, other.right)), Math.max(y, Math.min(bottom, other.bottom)));
        }
    }

    private static class Graphics extends GuiGraphics {
        List<Bounds> clips;
        List<Draw> draws;
        RuntimeException failure;
        Graphics() { super(Minecraft.getInstance(), null); }
        Bounds clip() { return clips.isEmpty() ? null : clips.getLast(); }
        @Override public void flush() {}
        @Override public void enableScissor(int left, int top, int right, int bottom) {
            Bounds next = new Bounds(left, top, right, bottom);
            clips.add(clip() == null ? next : clip().intersect(next));
        }
        @Override public void disableScissor() { clips.removeLast(); }
        @Override public boolean containsPointInScissor(int x, int y) { return false; }
        @Override public int drawString(Font font, FormattedCharSequence sequence, int x, int y, int color, boolean shadow) {
            StringBuilder text = new StringBuilder();
            sequence.accept((index, style, point) -> { text.appendCodePoint(point); return true; });
            draws.add(new Draw(text.toString(), color, clip()));
            if (failure != null) throw failure;
            return font.width(sequence);
        }
    }
}

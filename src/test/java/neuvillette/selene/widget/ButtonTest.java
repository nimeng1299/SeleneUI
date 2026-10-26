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
        assertEquals(64, button.background.Alpha());
        assertEquals(25f, button.iconDraw.x(), 0.0001f);
        assertEquals(25f, button.iconDraw.y(), 0.0001f);

        for (float scale : new float[]{0, -1, 1.1f, Float.NaN, Float.POSITIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> button.IconScale(scale));
        }
        assertThrows(IllegalArgumentException.class, () -> button.IconGap(-1));
        button.render(graphics, -1, -1, 0);
        assertEquals(15f, button.iconDraw.radius(), 0.0001f);
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
    private record Draw(String text, float x, Bounds clip) {}

    private static final class Probe extends Button {
        IconDraw iconDraw;
        Color background;
        float outerRadius;

        Probe(BaseWidget parent, int x, int y, int width, int height) {
            super(parent, x, y, width, height, Component.literal("fork"));
        }

        @Override protected void renderBackground(GuiGraphics graphics, Rect bounds, float radius, Color color) {
            outerRadius = radius;
            background = color;
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
            text.accept((index, style, codePoint) -> {
                content.appendCodePoint(codePoint);
                return true;
            });
            Vector4f position = new Vector4f(x, y, 0, 1).mul(pose().last().pose());
            draws.add(new Draw(content.toString(), position.x(), clip));
            return x + font.width(text);
        }
    }
}

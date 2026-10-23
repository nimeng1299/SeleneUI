package neuvillette.selene.widget;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ComponentPath;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.FocusNavigationEvent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import neuvillette.selene.layout.Constraint;
import neuvillette.selene.layout.Direction;
import neuvillette.selene.state.State;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

class ScrollViewTest {
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
    void automaticRangePreservesFreePositionsAndDimensionsWhileScrollOnlyChangesScreenY() {
        ScrollView view = new ScrollView(10, 20, 100, 80).Padding(5, 7, 9, 11);
        Probe first = view.addChild(new Probe(7, 12, 25, 30));
        Probe bottom = view.addChild(new Probe(40, 100, 15, 20));
        bottom.visible = false;

        assertEquals(120, view.getScrollHeight(), "Hidden descendants still reserve content space");
        assertEquals(120, view.getVirtualHeight());
        assertEquals(66, view.getViewportHeight());
        assertEquals(72, view.getViewportWidth());
        assertEquals(54, view.getMaxScroll());
        assertBounds(first, 28, 37, 25, 30);
        assertBounds(bottom, 61, 125, 15, 20);

        view.setScrollAmount(7.6);
        assertEquals(7.6, view.getScrollAmount(), 0.00001);
        assertBounds(first, 28, 29, 25, 30);
        assertBounds(bottom, 61, 117, 15, 20);
        assertEquals(7, first.getRelativeX());
        assertEquals(12, first.getRelativeY());
        assertEquals(100, bottom.getRelativeY());
    }

    @Test
    void screenAndRelativePositionSettersUseTheScrolledParentOrigin() {
        ScrollView view = new ScrollView(20, 30, 100, 80).Padding(5);
        view.addChild(new Probe(0, 300, 10, 20));
        Probe child = view.addChild(new Probe(8, 100, 20, 20));
        view.setScrollAmount(60);
        assertBounds(child, 33, 75, 20, 20);

        child.setY(90);
        assertEquals(115, child.getRelativeY());
        assertEquals(90, child.getY());
        child.setRelativePosition(12, 130);
        assertBounds(child, 37, 105, 20, 20);
        assertEquals(60, view.getScrollAmount());
        view.setY(50);
        assertBounds(child, 37, 125, 20, 20);

        child.setParent(null);
        assertBounds(child, 37, 125, 20, 20);
        view.setScrollAmount(100);
        assertBounds(child, 37, 125, 20, 20);
    }

    @Test
    void deepPaddingMovementAndRemovalUpdateOverflowRangeAndClampImmediately() {
        ScrollView view = new ScrollView(10, 20, 120, 80).Padding(5);
        Probe group = view.addChild(new Probe(4, 10, 50, 20));
        group.setPadding(3, 0, 7, 2);
        Probe leaf = group.addChild(new Probe(6, 30, 10, 40));
        assertEquals(83, view.getScrollHeight());
        assertEquals(13, view.getMaxScroll());
        group.setPadding(13, 0, 7, 2);
        assertEquals(93, view.getScrollHeight());

        leaf.setRelativePosition(6, 80);
        assertEquals(143, view.getScrollHeight());
        assertEquals(73, view.getMaxScroll());
        view.setScrollAmount(73);
        group.removeChild(leaf);
        assertEquals(30, view.getScrollHeight());
        assertEquals(70, view.getVirtualHeight());
        assertEquals(0, view.getMaxScroll());
        assertEquals(0, view.getScrollAmount());
    }

    @Test
    void descendantLayoutChangesPropagateToTheOuterRangeBeforeRendering() {
        ScrollView view = new ScrollView(0, 0, 200, 80);
        Layout layout = view.addChild(new Layout(0, 0, 100, 100));
        layout.addChild(new Probe(0, 0, 10, 10), Constraint.length(30));
        Probe group = layout.addChild(new Probe(0, 0, 10, 10));
        Probe leaf = group.addChild(new Probe(0, 80, 10, 50));
        assertEquals(160, view.getScrollHeight());
        view.setScrollAmount(80);

        layout.setDirection(Direction.HORIZONTAL);
        assertEquals(130, view.getScrollHeight());
        assertEquals(50, view.getMaxScroll());
        assertEquals(50, view.getScrollAmount());
        assertEquals(30, leaf.getY());
        group.setPadding(10, 0, 0, 0);
        assertEquals(140, view.getScrollHeight());
        leaf.setHeight(100);
        assertEquals(190, view.getScrollHeight());
        view.setScrollAmount(100);
        layout.removeChild(group);
        assertEquals(100, view.getScrollHeight());
        assertEquals(20, view.getScrollAmount());
    }

    @Test
    void nestedScrollerKeepsItsOwnContentRangeAndBubblesWheelAtItsBoundary() {
        ScrollView outer = new ScrollView(0, 0, 100, 60);
        ScrollView inner = outer.addChild(new ScrollView(0, 40, 100, 80));
        Probe innerContent = inner.addChild(new Probe(0, 0, 20, 300));
        Probe footer = outer.addChild(new Probe(0, 120, 10, 40));
        Host screen = new Host(outer);
        assertEquals(300, inner.getScrollHeight());
        assertEquals(160, outer.getScrollHeight(), "The inner viewport, not its content, defines outer extent");
        assertTrue(screen.mouseScrolled(5, 45, 0, -1));
        assertEquals(20, inner.getScrollAmount());
        assertEquals(0, outer.getScrollAmount());

        inner.setScrollAmount(inner.getMaxScroll());
        assertTrue(screen.mouseScrolled(5, 45, 0, -1));
        assertEquals(220, inner.getScrollAmount());
        assertEquals(20, outer.getScrollAmount());
        assertFalse(screen.mouseScrolled(5, 90, 0, -1));
        assertTrue(outer.getChildAt(5, 90).isEmpty());
        assertFalse(inner.isMouseOver(5, 90));
        assertFalse(innerContent.isMouseOver(5, 90));

        outer.removeChild(footer);
        assertEquals(120, outer.getScrollHeight());
        assertEquals(60, outer.getMaxScroll());
    }

    @Test
    void clippedInputCannotHitOverflowButCapturedRowDragStillLeavesTheViewport() {
        ScrollView view = new ScrollView(10, 20, 100, 60).Padding(5);
        Probe child = view.addChild(new Probe(0, 0, 80, 100));
        Host screen = new Host(view);
        view.setScrollAmount(20);
        assertEquals(5, child.getY());
        assertFalse(child.isMouseOver(20, 22));
        assertFalse(screen.mouseClicked(20, 22, 0));
        assertFalse(screen.mouseClicked(20, 77, 0));
        assertFalse(screen.mouseClicked(96, 30, 0));

        assertTrue(screen.mouseClicked(20, 30, 0));
        assertEquals(20, view.getScrollAmount(), "Focusing a partly visible oversized child must not jump");
        assertTrue(screen.mouseDragged(200, 200, 0, 180, 170));
        assertTrue(screen.mouseReleased(200, 200, 0));
        assertEquals(List.of("click", "drag", "release"), child.events);
    }

    @Test
    void tabFocusAndEnsureVisibleCanRevealAnyNestedDescendant() {
        ScrollView view = new ScrollView(0, 0, 100, 60);
        Probe group = view.addChild(new Probe(0, 0, 80, 200));
        Probe first = group.addChild(new Probe(0, 0, 20, 20));
        Probe middle = group.addChild(new Probe(0, 90, 20, 20));
        Probe last = group.addChild(new Probe(0, 180, 20, 20));
        Host screen = new Host(view);

        focus(screen);
        assertTrue(first.isFocused());
        focus(screen);
        assertTrue(middle.isFocused());
        assertEquals(50, view.getScrollAmount());
        focus(screen);
        assertTrue(last.isFocused());
        assertEquals(140, view.getScrollAmount());
        view.ensureVisible(first);
        assertEquals(0, view.getScrollAmount());
        view.ensureVisible(last);
        assertEquals(140, view.getScrollAmount());
        assertThrows(IllegalArgumentException.class, () -> view.ensureVisible(new Probe(0, 0, 10, 10)));
    }

    @Test
    void virtualMinimumReservesBlankSpaceWhileAutomaticContentCanExtendBeyondIt() {
        ScrollView empty = new ScrollView();
        assertEquals(0, empty.getScrollHeight());
        assertEquals(0, empty.getVirtualHeight());
        ScrollView view = new ScrollView(10, 20, 100, 80, Component.literal("View")).Padding(5);
        assertEquals(0, view.getScrollHeight());
        assertEquals(70, view.getVirtualHeight());
        assertEquals(90, view.getViewportWidth());
        assertSame(view, view.VirtualHeight(300));
        assertEquals(300, view.getScrollHeight());
        assertEquals(300, view.getVirtualHeight());
        assertEquals(230, view.getMaxScroll());
        assertEquals(80, view.getViewportWidth());

        Probe child = view.addChild(new Probe(0, 500, 10, 20));
        view.setVirtualHeight(100);
        assertEquals(520, view.getScrollHeight());
        assertEquals(520, view.getVirtualHeight());
        view.removeChild(child);
        assertEquals(100, view.getScrollHeight());
        assertEquals(30, view.getMaxScroll());
        view.setScrollAmount(20);
        view.setVirtualHeight(0);
        assertEquals(0, view.getScrollHeight());
        assertEquals(70, view.getVirtualHeight());
        assertEquals(0, view.getScrollAmount());
    }

    @Test
    void invalidParametersDoNotCorruptTheRangeAndFiniteOffsetsClamp() {
        ScrollView view = new ScrollView(0, 0, 100, 80).VirtualHeight(200).ScrollStep(12);
        assertThrows(IllegalArgumentException.class, () -> view.setVirtualHeight(-1));
        assertThrows(IllegalArgumentException.class, () -> view.setScrollStep(-1));
        assertEquals(200, view.getVirtualHeight());
        assertEquals(12, view.getScrollStep());
        for (double invalid : new double[] {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> view.setScrollAmount(invalid));
            assertThrows(IllegalArgumentException.class, () -> view.scrollBy(invalid));
            assertThrows(IllegalArgumentException.class, () -> view.setScrollStep(invalid));
        }
        view.setScrollAmount(-50);
        assertEquals(0, view.getScrollAmount());
        view.scrollBy(30);
        view.scrollBy(-5);
        assertEquals(25, view.getScrollAmount());
        view.setScrollAmount(500);
        assertEquals(120, view.getScrollAmount());
    }

    @Test
    void stateCallbackRemovalPausesChildBindingsAndReattachmentRestoresCurrentState() {
        Probe host = new Probe(0, 0, 200, 100);
        ScrollView view = new ScrollView(host, 0, 0, 100, 60, Component.empty());
        host.mount();
        Probe child = view.addChild(new Probe(5, 120, 10, 20));
        State<Integer> state = new State<>(0);
        List<Integer> values = new ArrayList<>();
        child.watch(state, value -> {
            values.add(value);
            if (value == 1) view.removeChild(child);
        });
        assertTrue(view.isMounted());
        assertEquals(List.of(0), values);
        assertEquals(140, view.getScrollHeight());
        view.setScrollAmount(80);

        state.set(1);
        assertNull(child.getParent());
        assertFalse(child.isMounted());
        assertEquals(0, view.getScrollHeight());
        assertEquals(0, view.getScrollAmount());
        state.set(2);
        assertEquals(List.of(0, 1), values);
        child.setRelativePosition(5, 120);
        view.addChild(child);
        assertTrue(child.isMounted());
        assertEquals(List.of(0, 1, 2), values);
        assertEquals(140, view.getScrollHeight());
    }

    private static void assertBounds(BaseWidget widget, int x, int y, int width, int height) {
        assertAll(() -> assertEquals(x, widget.getX(), "x"),
                () -> assertEquals(y, widget.getY(), "y"),
                () -> assertEquals(width, widget.getWidth(), "width"),
                () -> assertEquals(height, widget.getHeight(), "height"));
    }

    private static void focus(Host screen) {
        ComponentPath path = screen.nextFocusPath(new FocusNavigationEvent.TabNavigation(true));
        assertNotNull(path);
        ComponentPath previous = screen.getCurrentFocusPath();
        if (previous != null) previous.applyFocus(false);
        path.applyFocus(true);
    }

    private static final class Host extends Screen {
        Host(BaseWidget root) {
            super(Component.literal("Scroll view test"));
            addRenderableWidget(root);
        }
    }

    private static final class Probe extends BaseWidget {
        final List<String> events = new ArrayList<>();

        Probe(int x, int y, int width, int height) {
            super(x, y, width, height, Component.empty());
        }

        void watch(State<Integer> state, Consumer<Integer> observer) {
            subscribe(state, observer);
        }

        @Override protected void renderSelf(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {}
        @Override public void playDownSound(SoundManager manager) {}
        @Override public void onClick(double mouseX, double mouseY, int button) { events.add("click"); }
        @Override protected void onDrag(double mouseX, double mouseY, double dragX, double dragY) { events.add("drag"); }
        @Override public void onRelease(double mouseX, double mouseY) { events.add("release"); }
    }
}

package neuvillette.selene.widget;

import static org.junit.jupiter.api.Assertions.*;

import com.mojang.blaze3d.vertex.PoseStack;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ComponentPath;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.FocusNavigationEvent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import neuvillette.selene.api.Rect;
import neuvillette.selene.state.State;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;
import sun.misc.Unsafe;

class ScrollListTest {
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
        minecraftInstance.set(null, unsafe.allocateInstance(Minecraft.class));
    }

    @AfterAll
    static void restoreMinecraft() throws Exception {
        if (minecraftInstance != null) minecraftInstance.set(null, previousMinecraft);
    }

    @Test
    void naturalRowHeightsPaddingAndSpacingDetermineViewportAndOverflow() {
        ScrollList list = new ScrollList(10, 20, 100, 70).Padding(5, 7, 9, 11).Spacing(4);
        Probe first = list.addChild(new Probe(30));
        Probe second = list.addChild(new Probe(40));

        assertEquals(74, list.getScrollHeight());
        assertEquals(56, list.getViewportHeight());
        assertEquals(72, list.getViewportWidth());
        assertEquals(18, list.getMaxScroll());
        assertBounds(first, 21, 25, 72, 30);
        assertBounds(second, 21, 59, 72, 40);
        list.setScrollAmount(7.6);
        assertEquals(7.6, list.getScrollAmount(), 0.00001);
        assertBounds(first, 21, 17, 72, 30);
        assertBounds(second, 21, 51, 72, 40);
    }

    @Test
    void scrollbarWidthIsReservedOnlyWhenRowsOverflow() {
        ScrollList list = new ScrollList(0, 0, 100, 60).Padding(5).Spacing(5);
        Probe first = list.addChild(new Probe(20));
        Probe second = list.addChild(new Probe(25));
        assertEquals(50, list.getScrollHeight());
        assertEquals(0, list.getMaxScroll());
        assertEquals(90, list.getViewportWidth());
        assertEquals(90, first.getWidth());

        second.setHeight(26);
        assertEquals(1, list.getMaxScroll());
        assertEquals(80, list.getViewportWidth());
        assertEquals(80, first.getWidth());
        second.setHeight(25);
        assertEquals(90, first.getWidth());
        assertEquals(0, list.getMaxScroll());
    }

    @Test
    void explicitHeightIsAppliedBeforeMountedChildInitialCallback() {
        State<Integer> state = new State<>(0);
        ScrollList list = new ScrollList(0, 0, 100, 40);
        list.mount();
        Probe child = new Probe(7);
        List<Integer> observedHeights = new ArrayList<>();
        child.watch(state, value -> observedHeights.add(child.getHeight()));

        assertSame(child, list.addChild(child, 65));
        assertEquals(List.of(65), observedHeights);
        assertEquals(65, child.getHeight());
        assertEquals(65, list.getScrollHeight());
        child.setHeight(80);
        assertEquals(80, list.getScrollHeight());
        assertEquals(40, list.getMaxScroll());
    }

    @Test
    void explicitRowHeightSurvivesMovingFromALayoutThatPreviouslyControlledItsSize() {
        Layout previous = new Layout(0, 0, 100, 100);
        Probe child = previous.addChild(new Probe(7));
        assertEquals(100, child.getHeight());
        State<Integer> state = new State<>(0);
        List<Integer> observedHeights = new ArrayList<>();
        child.watch(state, value -> observedHeights.add(child.getHeight()));
        ScrollList list = new ScrollList(0, 0, 100, 60);
        list.mount();

        list.addChild(child, 30);

        assertTrue(previous.children().isEmpty());
        assertSame(list, child.getParent());
        assertEquals(List.of(30), observedHeights);
        assertEquals(30, child.getHeight());
        assertEquals(30, list.getScrollHeight());
        assertEquals(0, list.getMaxScroll());
    }

    @Test
    void resizeRowHeightChangesAndRemovalClampTheExistingScrollPosition() {
        ScrollList list = new ScrollList(0, 0, 100, 60);
        Probe first = list.addChild(new Probe(50));
        Probe second = list.addChild(new Probe(50));
        list.setScrollAmount(500);
        assertEquals(40, list.getScrollAmount());
        list.setHeight(140);
        assertEquals(0, list.getMaxScroll());
        assertEquals(0, list.getScrollAmount());
        assertEquals(100, second.getWidth());

        first.setHeight(200);
        assertEquals(250, list.getScrollHeight());
        assertEquals(110, list.getMaxScroll());
        list.setScrollAmount(110);
        assertEquals(90, second.getWidth());
        list.removeChild(first);
        assertEquals(50, list.getScrollHeight());
        assertEquals(0, list.getScrollAmount());
        assertBounds(second, 0, 0, 100, 50);
    }

    @Test
    void hiddenRowsKeepTheirSpaceButCannotReceiveInput() {
        ScrollList list = new ScrollList(0, 0, 100, 50);
        Probe first = list.addChild(new Probe(30));
        Probe second = list.addChild(new Probe(30));
        first.visible = false;
        Host screen = new Host(list);

        assertEquals(60, list.getScrollHeight());
        assertEquals(30, second.getY());
        assertFalse(screen.mouseClicked(5, 5, 0));
        assertTrue(screen.mouseClicked(5, 35, 0));
        assertTrue(first.events.isEmpty());
        assertEquals(List.of("click"), second.events);
    }

    @Test
    void emptyAndExhaustedViewportsHaveStableNonNegativeMetrics() {
        ScrollList list = new ScrollList(0, 0, 10, 10).Padding(20).Spacing(100);
        assertEquals(0, list.getScrollHeight());
        assertEquals(0, list.getViewportWidth());
        assertEquals(0, list.getViewportHeight());
        assertEquals(0, list.getMaxScroll());
        list.setScrollAmount(500);
        assertEquals(0, list.getScrollAmount());
        assertFalse(list.mouseScrolled(5, 5, 0, -1));

        Probe child = list.addChild(new Probe(30));
        assertEquals(30, child.getHeight());
        assertEquals(0, child.getWidth());
        assertEquals(30, list.getScrollHeight());
        assertFalse(list.mouseClicked(20, 20, 0));
        list.clearChildren();
        assertEquals(0, list.getScrollHeight());
        assertEquals(0, list.getScrollAmount());
    }

    @Test
    void wheelUsesConfiguredStepAndDoesNotConsumeAtAnUnchangedBoundary() {
        ScrollList list = new ScrollList(0, 0, 100, 60);
        list.addChild(new Probe(150));
        Host screen = new Host(list);
        assertEquals(20, list.getScrollStep());
        assertFalse(screen.mouseScrolled(5, 5, 0, 1));
        assertTrue(screen.mouseScrolled(5, 5, 0, -0.5));
        assertEquals(10, list.getScrollAmount());
        list.setScrollStep(7);
        assertTrue(screen.mouseScrolled(5, 5, 0, -1));
        assertEquals(17, list.getScrollAmount());
        list.setScrollAmount(list.getMaxScroll());
        assertFalse(screen.mouseScrolled(5, 5, 0, -1));
        assertTrue(screen.mouseScrolled(5, 5, 0, 1));
        assertEquals(83, list.getScrollAmount());
        assertFalse(screen.mouseScrolled(200, 200, 0, -1));
        assertEquals(83, list.getScrollAmount());
    }

    @Test
    void childGetsFirstOpportunityToConsumeWheel() {
        ScrollList list = new ScrollList(0, 0, 100, 60);
        Probe child = list.addChild(new Probe(100));
        child.consumeScroll = true;
        Host screen = new Host(list);

        assertTrue(screen.mouseScrolled(5, 5, 0, -1));
        assertEquals(List.of("scroll"), child.events);
        assertEquals(0, list.getScrollAmount());
        child.consumeScroll = false;
        assertTrue(screen.mouseScrolled(5, 5, 0, -1));
        assertEquals(20, list.getScrollAmount());
    }

    @Test
    void nestedListConsumesWheelUntilItsBoundaryThenOuterListScrolls() {
        ScrollList outer = new ScrollList(0, 0, 100, 60);
        outer.addChild(new Probe(40));
        ScrollList inner = outer.addChild(new ScrollList(0, 0, 100, 80));
        inner.addChild(new Probe(60));
        inner.addChild(new Probe(60));
        inner.addChild(new Probe(60));
        outer.addChild(new Probe(40));
        Host screen = new Host(outer);

        assertTrue(screen.mouseScrolled(5, 45, 0, -1));
        assertEquals(20, inner.getScrollAmount());
        assertEquals(0, outer.getScrollAmount());
        inner.setScrollAmount(inner.getMaxScroll());
        assertTrue(screen.mouseScrolled(5, 45, 0, -1));
        assertEquals(100, inner.getScrollAmount());
        assertEquals(20, outer.getScrollAmount());
        assertFalse(screen.mouseScrolled(5, 90, 0, -1));
        assertEquals(20, outer.getScrollAmount());
    }

    @Test
    void scrollingMovesClickTargetsAndPaddingOrOutsideViewportCannotHitRows() {
        ScrollList list = new ScrollList(10, 20, 100, 60).Padding(5);
        Probe first = list.addChild(new Probe(30));
        Probe second = list.addChild(new Probe(30));
        Probe third = list.addChild(new Probe(30));
        Host screen = new Host(list);
        list.setScrollAmount(30);

        assertTrue(screen.mouseClicked(20, 30, 0));
        assertEquals(List.of("click"), second.events);
        screen.mouseReleased(20, 30, 0);
        assertTrue(screen.mouseClicked(20, 70, 0));
        assertEquals(List.of("click"), third.events);
        assertFalse(screen.mouseClicked(20, 22, 0));
        assertFalse(screen.mouseClicked(20, 77, 0));
        assertFalse(screen.mouseClicked(96, 30, 0), "Scrollbar gap is outside the row viewport");
        assertTrue(first.events.isEmpty());
        assertFalse(first.isMouseOver(20, 22));
    }

    @Test
    void ancestorViewportClipsNestedHitTestingEvenWhenDescendantBoundsContainPoint() {
        ScrollList outer = new ScrollList(0, 0, 100, 60);
        outer.addChild(new Probe(40));
        ScrollList inner = outer.addChild(new ScrollList(0, 0, 100, 80));
        Probe leaf = inner.addChild(new Probe(80));
        Host screen = new Host(outer);

        assertEquals(40, leaf.getY());
        assertTrue(leaf.getY() + leaf.getHeight() > 70);
        assertTrue(outer.getChildAt(5, 70).isEmpty());
        assertTrue(inner.getChildAt(5, 70).isEmpty());
        assertFalse(inner.isMouseOver(5, 70));
        assertFalse(leaf.isMouseOver(5, 70));
        assertFalse(screen.mouseClicked(5, 70, 0));
        assertFalse(inner.mouseClicked(5, 70, 0));
        assertTrue(leaf.events.isEmpty());
    }

    @Test
    void ensureVisibleAndTabFocusRevealRowsIncludingOversizedRows() {
        ScrollList list = new ScrollList(0, 0, 100, 60);
        Probe first = list.addChild(new Probe(30));
        list.addChild(new Probe(30));
        Probe third = list.addChild(new Probe(30));
        Probe last = list.addChild(new Probe(30));
        Host screen = new Host(list);
        focus(screen, true);
        assertTrue(first.isFocused());
        assertEquals(0, list.getScrollAmount());
        focus(screen, true);
        focus(screen, true);
        assertTrue(third.isFocused());
        assertEquals(30, list.getScrollAmount());
        focus(screen, true);
        assertTrue(last.isFocused());
        assertEquals(60, list.getScrollAmount());
        list.ensureVisible(first);
        assertEquals(0, list.getScrollAmount());
        assertThrows(IllegalArgumentException.class, () -> list.ensureVisible(new Probe(10)));

        first.setHeight(120);
        list.ensureVisible(last);
        assertEquals(list.getMaxScroll(), list.getScrollAmount());
        list.ensureVisible(first);
        assertEquals(0, list.getScrollAmount(), "An oversized row should reveal its leading edge");
    }

    @Test
    void tabNavigationRevealsFocusedLeafInsideARowTallerThanTheViewport() {
        ScrollList list = new ScrollList(0, 0, 100, 60);
        Probe row = list.addChild(new Probe(200));
        Probe first = row.addChild(new Probe(20));
        Probe middle = row.addChild(new Probe(20));
        Probe last = row.addChild(new Probe(20));
        middle.setRelativePosition(0, 90);
        last.setRelativePosition(0, 180);
        Host screen = new Host(list);

        focus(screen, true);
        assertTrue(first.isFocused());
        assertEquals(0, list.getScrollAmount());
        focus(screen, true);
        assertTrue(middle.isFocused());
        assertEquals(50, list.getScrollAmount());
        focus(screen, true);
        assertTrue(last.isFocused());
        assertEquals(140, list.getScrollAmount());
        assertTrue(last.getY() >= list.getContentY());
        assertTrue(last.getY() + last.getHeight() <= list.getContentY() + list.getViewportHeight());
    }

    @Test
    void clickingVisibleMiddleOfAnOversizedRowKeepsTheCurrentScrollPosition() {
        ScrollList list = new ScrollList(0, 0, 100, 100);
        Probe row = list.addChild(new Probe(500));
        Host screen = new Host(list);
        list.setScrollAmount(300);

        assertTrue(screen.mouseClicked(5, 50, 0));

        assertEquals(300, list.getScrollAmount());
        assertEquals(List.of("click"), row.events);
        assertTrue(row.isFocused());
        assertTrue(screen.mouseReleased(5, 50, 0));
    }

    @Test
    void scrollbarTrackClickAndThumbDragUseScreenCaptureOutsideBounds() {
        ScrollList list = new QuietScrollList(0, 0, 100, 100);
        Probe child = list.addChild(new Probe(300));
        Host screen = new Host(list);

        assertTrue(screen.mouseClicked(96, 80, 0));
        assertTrue(list.getScrollAmount() > 0);
        assertTrue(screen.mouseReleased(96, 80, 0));
        assertTrue(child.events.isEmpty());
        list.setScrollAmount(0);
        assertTrue(screen.mouseClicked(96, 5, 0));
        assertTrue(screen.mouseDragged(200, 200, 0, 104, 195));
        assertEquals(list.getMaxScroll(), list.getScrollAmount());
        assertFalse(screen.mouseReleased(200, 200, 1));
        assertTrue(screen.mouseReleased(200, 200, 0));
        assertFalse(list.isDragging());
        assertFalse(screen.mouseDragged(200, 0, 0, 0, -200));
        assertTrue(child.events.isEmpty());
    }

    @Test
    void rowDragCaptureSurvivesLeavingViewportUntilRelease() {
        ScrollList list = new ScrollList(0, 0, 100, 50);
        Probe child = list.addChild(new Probe(100));
        Host screen = new Host(list);

        assertTrue(screen.mouseClicked(5, 5, 0));
        assertTrue(screen.mouseDragged(200, 200, 0, 195, 195));
        assertTrue(screen.mouseReleased(200, 200, 0));
        assertEquals(List.of("click", "drag", "release"), child.events);
    }

    @Test
    void nestedRenderingIntersectsScissorsAndRestoresOuterClipAfterChildFailure() {
        ScrollList outer = new HeadlessScrollList(10, 20, 100, 60);
        ScrollList inner = outer.addChild(new HeadlessScrollList(0, 0, 100, 80));
        Probe first = inner.addChild(new Probe(40));
        Probe failing = inner.addChild(new Probe(80));
        RuntimeException failure = new IllegalStateException("render failed");
        failing.renderAction = () -> { throw failure; };
        RecordingGraphics graphics = graphics();
        Bounds previousClip = new Bounds(0, 0, 95, 70);
        graphics.enableScissor(0, 0, 95, 70);

        assertSame(failure, assertThrows(RuntimeException.class, () -> outer.render(graphics, 15, 25, 0)));

        assertEquals(List.of(new Bounds(10, 20, 90, 70)), first.renderedClips);
        assertEquals(previousClip, graphics.currentClip());
        assertEquals(1, graphics.clips.size());
        assertEquals(2, graphics.disableCalls);
        assertEquals(0, graphics.pose().last().pose().m30());
        assertEquals(0, graphics.pose().last().pose().m31());
        graphics.disableScissor();
        assertNull(graphics.currentClip());
    }

    @Test
    void clippedRowsCannotKeepHoverOutsideTheViewport() {
        ScrollList list = new HeadlessScrollList(10, 20, 100, 50).Padding(5);
        Probe child = list.addChild(new Probe(100));
        list.setScrollAmount(20);
        RecordingGraphics graphics = graphics();

        list.render(graphics, 20, 22, 0);
        assertFalse(child.isHovered());
        list.render(graphics, 20, 30, 0);
        assertTrue(child.isHovered());
        assertNull(graphics.currentClip());
    }

    @Test
    void fullyClippedNestedRowsSkipRenderingAndClearHoverThroughoutTheirSubtree() {
        ScrollList outer = new HeadlessScrollList(0, 0, 100, 60);
        ScrollList inner = outer.addChild(new HeadlessScrollList(0, 0, 100, 100));
        inner.addChild(new Probe(30));
        inner.addChild(new Probe(30));
        Probe third = inner.addChild(new Probe(30));
        Probe leaf = third.addChild(new Probe(10));
        RecordingGraphics graphics = graphics();
        outer.setScrollAmount(40);
        outer.render(graphics, 5, 25, 0);
        assertTrue(leaf.isHovered());
        assertEquals(1, third.renderedClips.size());
        assertEquals(1, leaf.renderedClips.size());

        outer.setScrollAmount(0);
        outer.render(graphics, 5, 45, 0);

        assertEquals(1, third.renderedClips.size(), "Row lies outside the ancestor viewport");
        assertEquals(1, leaf.renderedClips.size());
        assertFalse(third.isHovered());
        assertFalse(leaf.isHovered());
        assertNull(graphics.currentClip());
    }

    @Test
    void visibleOverflowDescendantStillRendersWhenItsParentRowBoundsAreClippedOut() {
        ScrollList list = new HeadlessScrollList(0, 0, 100, 100);
        Probe row = list.addChild(new Probe(20));
        Probe overflow = row.addChild(new Probe(20));
        overflow.setRelativePosition(0, 50);
        Probe spacer = list.addChild(new Probe(200));
        spacer.visible = false;
        list.setScrollAmount(40);
        assertTrue(row.getY() + row.getHeight() < 0);
        assertEquals(10, overflow.getY());
        RecordingGraphics graphics = graphics();

        list.render(graphics, 5, 15, 0);

        assertEquals(1, overflow.renderedClips.size());
        assertTrue(overflow.isHovered());
        assertSame(row, list.getChildAt(5, 15).orElseThrow());
        Host screen = new Host(list);
        assertTrue(screen.mouseClicked(5, 15, 0));
        assertEquals(List.of("click"), overflow.events);
    }

    @Test
    void callbackRemovalClampsScrollAndPausesRemovedRowsManagedBindings() {
        ScrollList list = new ScrollList(0, 0, 100, 40);
        list.addChild(new Probe(30));
        list.addChild(new Probe(30));
        Probe removable = list.addChild(new Probe(30));
        State<Integer> state = new State<>(0);
        List<Integer> values = new ArrayList<>();
        removable.watch(state, value -> {
            values.add(value);
            if (value == 1) list.removeChild(removable);
        });
        list.mount();
        list.setScrollAmount(50);

        state.set(1);

        assertFalse(removable.isMounted());
        assertNull(removable.getParent());
        assertEquals(60, list.getScrollHeight());
        assertEquals(20, list.getScrollAmount());
        state.set(2);
        assertEquals(List.of(0, 1), values);
        list.addChild(removable);
        assertTrue(removable.isMounted());
        assertEquals(List.of(0, 1, 2), values);
        assertEquals(90, list.getScrollHeight());
    }

    @Test
    void initialCallbackCanRemoveExplicitHeightRowAndLaterReattachWithoutDuplicates() {
        ScrollList list = new ScrollList(0, 0, 100, 40);
        list.mount();
        Probe child = new Probe(10);
        State<Integer> state = new State<>(0);
        AtomicBoolean removeOnce = new AtomicBoolean(true);
        List<Integer> values = new ArrayList<>();
        child.watch(state, value -> {
            values.add(value);
            if (removeOnce.getAndSet(false)) list.removeChild(child);
        });

        list.addChild(child, 50);
        assertNull(child.getParent());
        assertFalse(child.isMounted());
        assertEquals(0, list.getScrollHeight());
        list.addChild(child);
        assertTrue(child.isMounted());
        assertEquals(50, list.getScrollHeight());
        state.set(1);
        assertEquals(List.of(0, 0, 1), values);
    }

    @Test
    void clickCallbackCanRemoveItsRowAndFollowingRowMovesIntoItsSlot() {
        ScrollList list = new ScrollList(0, 0, 100, 40);
        Probe removable = list.addChild(new Probe(30));
        Probe survivor = list.addChild(new Probe(30));
        removable.clickAction = () -> list.removeChild(removable);
        Host screen = new Host(list);

        assertTrue(screen.mouseClicked(5, 5, 0));
        assertNull(removable.getParent());
        assertFalse(removable.isFocused());
        assertEquals(30, list.getScrollHeight());
        assertBounds(survivor, 0, 0, 100, 30);
        assertFalse(screen.mouseDragged(200, 200, 0, 1, 1));
        assertTrue(screen.mouseClicked(5, 5, 0));
        assertEquals(List.of("click"), survivor.events);
    }

    @Test
    void invalidParametersAreRejectedAndFiniteScrollPositionsClamp() {
        ScrollList list = new ScrollList(0, 0, 100, 40).Spacing(5).ScrollStep(12);
        Probe child = list.addChild(new Probe(100));
        assertThrows(IllegalArgumentException.class, () -> list.setSpacing(-1));
        assertThrows(IllegalArgumentException.class, () -> list.setScrollStep(-1));
        assertEquals(5, list.getSpacing());
        assertEquals(12, list.getScrollStep());
        Probe rejected = new Probe(10);
        assertThrows(IllegalArgumentException.class, () -> list.addChild(rejected, -1));
        assertNull(rejected.getParent());
        assertEquals(10, rejected.getHeight());
        for (double invalid : new double[] {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> list.setScrollAmount(invalid));
            assertThrows(IllegalArgumentException.class, () -> list.scrollBy(invalid));
            assertThrows(IllegalArgumentException.class, () -> list.setScrollStep(invalid));
        }
        list.setScrollAmount(-50);
        assertEquals(0, list.getScrollAmount());
        list.scrollBy(25);
        assertEquals(25, list.getScrollAmount());
        list.scrollBy(-10);
        assertEquals(15, list.getScrollAmount());
        list.setScrollAmount(500);
        assertEquals(60, list.getScrollAmount());
        assertEquals(100, child.getHeight());
    }

    @Test
    void veryTallContentUsesSaturatingMetricsWithoutWrappingChildPositions() {
        ScrollList list = new ScrollList(0, 0, 100, 100).Spacing(5);
        Probe first = list.addChild(new Probe(Integer.MAX_VALUE));
        Probe second = list.addChild(new Probe(Integer.MAX_VALUE));
        assertEquals(Integer.MAX_VALUE, list.getScrollHeight());
        assertEquals(Integer.MAX_VALUE, list.getMaxScroll());

        list.setScrollAmount(Integer.MAX_VALUE);

        assertEquals(-Integer.MAX_VALUE, first.getY());
        assertEquals(5, second.getY());
        assertEquals(90, second.getWidth());
        assertEquals(Integer.MAX_VALUE, second.getHeight());
    }

    @Test
    void focusedListSupportsPagingAndHomeEndNavigationWithoutConsumingUnchangedBoundaries() {
        ScrollList list = new ScrollList(0, 0, 100, 60);
        list.addChild(new Probe(200));
        Host screen = new Host(list);
        screen.setFocused(list);

        assertTrue(screen.keyPressed(GLFW.GLFW_KEY_PAGE_DOWN, 0, 0));
        assertEquals(60, list.getScrollAmount());
        assertTrue(screen.keyPressed(GLFW.GLFW_KEY_PAGE_UP, 0, 0));
        assertEquals(0, list.getScrollAmount());
        assertTrue(screen.keyPressed(GLFW.GLFW_KEY_END, 0, 0));
        assertEquals(140, list.getScrollAmount());
        assertFalse(screen.keyPressed(GLFW.GLFW_KEY_END, 0, 0));
        assertTrue(screen.keyPressed(GLFW.GLFW_KEY_HOME, 0, 0));
        assertEquals(0, list.getScrollAmount());
        assertFalse(screen.keyPressed(GLFW.GLFW_KEY_HOME, 0, 0));
    }

    @Test
    void scrollbarStartsHiddenFadesAfterIdleAndOnlyActualScrollingShowsItAgain() {
        TimedScrollList list = new TimedScrollList();
        list.addChild(new Probe(300));
        RecordingGraphics graphics = graphics();
        assertTrue(frame(list, graphics, 96, 20).isEmpty(), "Hover alone must not reveal the scrollbar");

        list.setScrollAmount(20);
        int visibleAlpha = onlyThumb(frame(list, graphics, 96, 20)).alpha();
        assertTrue(visibleAlpha > 0);
        list.now = 10_800;
        assertEquals(visibleAlpha, onlyThumb(frame(list, graphics, 96, 20)).alpha());
        list.now = 10_900;
        int fadingAlpha = onlyThumb(frame(list, graphics, 96, 20)).alpha();
        assertTrue(fadingAlpha > 0 && fadingAlpha < visibleAlpha);
        list.now = 11_000;
        assertTrue(frame(list, graphics, 96, 20).isEmpty(), "Hover must not keep the scrollbar visible");

        list.setScrollAmount(20);
        assertTrue(frame(list, graphics, 96, 20).isEmpty(), "Assigning the same offset is not scrolling");
        list.scrollBy(5);
        assertEquals(visibleAlpha, onlyThumb(frame(list, graphics, 96, 20)).alpha());
    }

    @Test
    void heldScrollbarDragRemainsVisibleAndReleaseRestartsTheIdleTimer() {
        TimedScrollList list = new TimedScrollList();
        list.addChild(new Probe(300));
        Host screen = new Host(list);
        RecordingGraphics graphics = graphics();
        assertTrue(screen.mouseClicked(96, 5, 0));
        int heldAlpha = onlyThumb(frame(list, graphics, 96, 5)).alpha();
        list.now = 15_000;
        assertEquals(heldAlpha, onlyThumb(frame(list, graphics, 96, 5)).alpha());
        assertTrue(screen.mouseDragged(96, 30, 0, 0, 25));
        list.now = 20_000;
        assertEquals(heldAlpha, onlyThumb(frame(list, graphics, 96, 40)).alpha());

        assertTrue(screen.mouseReleased(200, 200, 0));
        list.now = 20_800;
        assertEquals(heldAlpha, onlyThumb(frame(list, graphics, 96, 40)).alpha());
        list.now = 20_900;
        int fadingAlpha = onlyThumb(frame(list, graphics, 96, 40)).alpha();
        assertTrue(fadingAlpha > 0 && fadingAlpha < heldAlpha);
        list.now = 21_000;
        assertTrue(frame(list, graphics, 96, 40).isEmpty());
    }

    @Test
    void thinScrollbarHasNoTrackAndKeepsItsFullHitAreaAndReservedLayoutWidth() {
        TimedScrollList list = new TimedScrollList();
        Probe child = list.addChild(new Probe(300));
        RecordingGraphics graphics = graphics();
        assertEquals(90, list.getViewportWidth());
        assertEquals(90, child.getWidth());
        assertTrue(frame(list, graphics, 0, 0).isEmpty());
        list.setScrollAmount(20);
        Fill thumb = onlyThumb(frame(list, graphics, 0, 0));
        assertEquals(1, thumb.right() - thumb.left());
        assertTrue(thumb.left() >= 94 && thumb.right() <= 100);
        assertTrue(thumb.bottom() - thumb.top() < 100, "Only the thumb is filled, without a track");
        assertEquals(90, child.getWidth());
        list.now = 11_000;
        assertTrue(frame(list, graphics, 0, 0).isEmpty());
        assertEquals(90, list.getViewportWidth());
        assertEquals(90, child.getWidth());

        int hitX = thumb.left() == 94 ? 99 : 94;
        Host screen = new Host(list);
        assertTrue(screen.mouseClicked(hitX, thumb.top() + 1, 0), "The hidden 6-unit hit area stays usable");
        Fill shownThumb = onlyThumb(frame(list, graphics, hitX, thumb.top() + 1));
        assertEquals(1, shownThumb.right() - shownThumb.left());
        assertEquals(90, child.getWidth());
        assertTrue(screen.mouseReleased(hitX, thumb.top() + 1, 0));
        child.setHeight(50);
        assertEquals(0, list.getMaxScroll());
        assertEquals(100, list.getViewportWidth());
        assertTrue(frame(list, graphics, hitX, 5).isEmpty(), "Non-overflowing content has no scrollbar");
    }

    private static void assertBounds(BaseWidget widget, int x, int y, int width, int height) {
        assertAll(() -> assertEquals(x, widget.getX(), "x"),
                () -> assertEquals(y, widget.getY(), "y"),
                () -> assertEquals(width, widget.getWidth(), "width"),
                () -> assertEquals(height, widget.getHeight(), "height"));
    }

    private static RecordingGraphics graphics() {
        try {
            // GuiGraphics' patched instance fields otherwise initialize the game's item registry.
            RecordingGraphics graphics = (RecordingGraphics) unsafe.allocateInstance(RecordingGraphics.class);
            Field pose = GuiGraphics.class.getDeclaredField("pose");
            pose.setAccessible(true);
            pose.set(graphics, new PoseStack());
            graphics.clips = new ArrayList<>();
            graphics.fills = new ArrayList<>();
            return graphics;
        } catch (ReflectiveOperationException error) {
            throw new AssertionError("Cannot initialize headless graphics", error);
        }
    }

    private static List<Fill> frame(ScrollList list, RecordingGraphics graphics, int mouseX, int mouseY) {
        graphics.fills.clear();
        list.render(graphics, mouseX, mouseY, 0);
        return List.copyOf(graphics.fills);
    }

    private static Fill onlyThumb(List<Fill> fills) {
        assertEquals(1, fills.size(), "Expected a single scrollbar thumb fill");
        return fills.getFirst();
    }

    private static void focus(Host screen, boolean forward) {
        ComponentPath path = screen.nextFocusPath(new FocusNavigationEvent.TabNavigation(forward));
        assertNotNull(path);
        ComponentPath previous = screen.getCurrentFocusPath();
        if (previous != null) previous.applyFocus(false);
        path.applyFocus(true);
    }

    private static final class Host extends Screen {
        Host(BaseWidget root) {
            super(Component.literal("Scroll test"));
            addRenderableWidget(root);
        }
    }

    private static class HeadlessScrollList extends ScrollList {
        HeadlessScrollList(int x, int y, int width, int height) {
            super(x, y, width, height);
        }

        @Override
        protected void renderScrollbarThumb(GuiGraphics graphics, Rect bounds, int color) {
            graphics.fill(bounds.x(), bounds.y(), bounds.x2(), bounds.y2(), color);
        }
    }

    private static final class QuietScrollList extends HeadlessScrollList {
        QuietScrollList(int x, int y, int width, int height) {
            super(x, y, width, height);
        }

        @Override public void playDownSound(SoundManager manager) {}
    }

    private static final class TimedScrollList extends HeadlessScrollList {
        long now = 10_000;

        TimedScrollList() {
            super(0, 0, 100, 100);
        }

        @Override long scrollbarTimeMillis() { return now; }
        @Override public void playDownSound(SoundManager manager) {}
    }

    private static final class Probe extends BaseWidget {
        final List<String> events = new ArrayList<>();
        final List<Bounds> renderedClips = new ArrayList<>();
        boolean consumeScroll;
        Runnable clickAction;
        Runnable renderAction;

        Probe(int height) {
            super(0, 0, 10, height, Component.empty());
        }

        void watch(State<Integer> state, Consumer<Integer> observer) {
            subscribe(state, observer);
        }

        @Override
        protected void renderSelf(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            if (graphics instanceof RecordingGraphics recording) renderedClips.add(recording.currentClip());
            graphics.pose().translate(2, 3, 0);
            if (renderAction != null) renderAction.run();
        }

        @Override public void playDownSound(SoundManager manager) {}

        @Override
        public void onClick(double mouseX, double mouseY, int button) {
            events.add("click");
            if (clickAction != null) clickAction.run();
        }

        @Override protected void onDrag(double mouseX, double mouseY, double dragX, double dragY) { events.add("drag"); }
        @Override public void onRelease(double mouseX, double mouseY) { events.add("release"); }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
            if (super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) return true;
            if (!isActive() || !consumeScroll) return false;
            events.add("scroll");
            return true;
        }
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

    private record Fill(int left, int top, int right, int bottom, int color) {
        int alpha() { return color >>> 24; }
    }

    /** Records clipping and suppresses GPU fills while using GuiGraphics' real pose stack. */
    private static final class RecordingGraphics extends GuiGraphics {
        List<Bounds> clips;
        List<Fill> fills;
        int disableCalls;

        RecordingGraphics() {
            super(Minecraft.getInstance(), null);
        }

        Bounds currentClip() { return clips.isEmpty() ? null : clips.getLast(); }

        @Override
        public void enableScissor(int minX, int minY, int maxX, int maxY) {
            Bounds next = new Bounds(minX, minY, maxX, maxY);
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

        @Override public void fill(int x1, int y1, int x2, int y2, int color) {
            fills.add(new Fill(x1, y1, x2, y2, color));
        }
        @Override public void fill(int x1, int y1, int x2, int y2, int z, int color) {
            fills.add(new Fill(x1, y1, x2, y2, color));
        }
        @Override public void fill(RenderType type, int x1, int y1, int x2, int y2, int color) {
            fills.add(new Fill(x1, y1, x2, y2, color));
        }
        @Override public void fill(RenderType type, int x1, int y1, int x2, int y2, int z, int color) {
            fills.add(new Fill(x1, y1, x2, y2, color));
        }
    }
}

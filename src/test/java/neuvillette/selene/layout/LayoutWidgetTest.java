package neuvillette.selene.layout;

import static org.junit.jupiter.api.Assertions.*;

import com.mojang.blaze3d.vertex.PoseStack;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ComponentPath;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.FocusNavigationEvent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import neuvillette.selene.gallery.GalleryScreen;
import neuvillette.selene.screen.SeleneScreen;
import neuvillette.selene.widget.BaseWidget;
import neuvillette.selene.widget.Layout;
import neuvillette.selene.widget.ScrollList;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

/** Exercises layout through real widget and Screen APIs without starting a game window. */
class LayoutWidgetTest {
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
        if (minecraftInstance != null) {
            minecraftInstance.set(null, previousMinecraft);
        }
    }

    @Test
    void horizontalLayoutUsesContentAreaSpacingAndWeightedFill() {
        Layout root = new Layout(40, 60, 140, 90)
                .Direction(Direction.HORIZONTAL).Spacing(4).Padding(5, 7, 11, 13);
        Probe first = root.addChild(new Probe("first"), Constraint.length(20));
        Probe second = root.addChild(new Probe("second"), Constraint.fill(1));
        Probe third = root.addChild(new Probe("third"), Constraint.fill(3));

        // Merely reading a child must be sufficient to obtain its final bounds.
        assertBounds(first, 53, 65, 20, 74);
        assertBounds(second, 77, 65, 23, 74);
        assertBounds(third, 104, 65, 69, 74);
        assertEquals(24, second.getRelativeX());
        assertEquals(0, second.getRelativeY());
        assertEquals(0, first.getRelativeX());
    }

    @Test
    void directionDimensionsPaddingAndPositionCanChangeAfterFirstLayout() {
        Layout root = new Layout(10, 20, 120, 90).Padding(5).Spacing(10);
        Probe first = root.addChild(new Probe("first"), Constraint.length(20));
        Probe second = root.addChild(new Probe("second"));
        assertEquals(Direction.VERTICAL, root.getDirection());
        assertEquals(Flex.START, root.getFlex());
        assertBounds(first, 15, 25, 110, 20);
        assertBounds(second, 15, 55, 110, 50);

        root.setDirection(Direction.HORIZONTAL);
        assertBounds(first, 15, 25, 20, 80);
        assertBounds(second, 45, 25, 80, 80);
        root.setWidth(200);
        root.setHeight(110);
        assertBounds(second, 45, 25, 160, 100);
        root.setPadding(2, 3, 4, 5);
        assertBounds(second, 45, 22, 162, 104);
        root.setPosition(50, 70);
        assertBounds(second, 85, 72, 162, 104);
    }

    @Test
    void deepGeometryQueryRefreshesAllAncestorLayouts() {
        Layout root = new Layout(0, 0, 120, 200).Padding(10).Spacing(10);
        root.addChild(new Probe("header"), Constraint.length(30));
        Layout row = root.addChild(new Layout().Direction(Direction.HORIZONTAL).Padding(4, 6).Spacing(4));
        row.addChild(new Probe("label"), Constraint.length(20));
        Probe field = row.addChild(new Probe("field"));
        assertBounds(field, 40, 54, 64, 132);

        root.setWidth(180);
        root.setHeight(260);
        assertEquals(124, field.getWidth(), "A width read must refresh ancestors before returning");
        assertEquals(192, field.getHeight());
        assertBounds(row, 10, 50, 160, 200);
        root.setPosition(30, 40);
        assertBounds(field, 70, 94, 124, 192);

        row.addChild(new Probe("suffix"), Constraint.length(20));
        assertEquals(100, field.getWidth(), "Adding a nested child invalidates its layout");
    }

    @Test
    void parentConstructorAndOrdinaryAddChildUseDefaultFill() {
        Layout root = new Layout(10, 20, 80, 120);
        Probe first = new Probe(root, "first");
        Probe second = root.addChild(new Probe("second"));
        assertEquals(List.of(first, second), root.children());
        assertBounds(first, 10, 20, 80, 60);
        assertBounds(second, 10, 80, 80, 60);

        root.addChild(first);
        assertEquals(List.of(first, second), root.children(), "Repeated registration keeps ordering");
        assertBounds(first, 10, 20, 80, 60);
        assertThrows(UnsupportedOperationException.class, () -> root.children().clear());
    }

    @Test
    void batchConstraintsValidateBeforeReplacingExistingConstraints() {
        Layout root = new Layout(0, 0, 120, 30).Direction(Direction.HORIZONTAL);
        Probe first = root.addChild(new Probe("first"));
        Probe second = root.addChild(new Probe("second"));
        Probe third = root.addChild(new Probe("third"));
        root.setConstraints(Constraint.length(20), Constraint.length(30), Constraint.fill(1));
        assertBounds(first, 0, 0, 20, 30);
        assertBounds(second, 20, 0, 30, 30);
        assertBounds(third, 50, 0, 70, 30);

        assertThrows(IllegalArgumentException.class, () -> root.setConstraints(Constraint.length(99)));
        assertBounds(first, 0, 0, 20, 30);
        assertBounds(second, 20, 0, 30, 30);
        Constraint replacement = Constraint.length(40);
        root.setConstraint(first, replacement);
        assertEquals(replacement, root.getConstraint(first));
        assertBounds(second, 40, 0, 30, 30);
        assertBounds(third, 70, 0, 50, 30);
    }

    @Test
    void flexAndSpacingChangesRepositionExistingChildren() {
        Layout root = new Layout(0, 0, 130, 20).Direction(Direction.HORIZONTAL).Spacing(10);
        Probe first = root.addChild(new Probe("first"), Constraint.length(20));
        Probe second = root.addChild(new Probe("second"), Constraint.length(20));
        assertBounds(first, 0, 0, 20, 20);
        assertBounds(second, 30, 0, 20, 20);
        root.setFlex(Flex.END);
        assertBounds(first, 80, 0, 20, 20);
        assertBounds(second, 110, 0, 20, 20);
        root.setFlex(Flex.CENTER);
        assertBounds(first, 40, 0, 20, 20);
        assertBounds(second, 70, 0, 20, 20);
        root.setFlex(Flex.SPACE_BETWEEN);
        assertBounds(first, 0, 0, 20, 20);
        assertBounds(second, 110, 0, 20, 20);
        root.setFlex(Flex.SPACE_AROUND);
        assertBounds(first, 20, 0, 20, 20);
        assertBounds(second, 90, 0, 20, 20);
        root.setFlex(Flex.START);
        root.setSpacing(20);
        assertBounds(second, 40, 0, 20, 20);
    }

    @Test
    void managedChildGeometryIsRestoredAfterManualChanges() {
        Layout root = new Layout(10, 20, 100, 40).Direction(Direction.HORIZONTAL);
        Probe child = root.addChild(new Probe("child"), Constraint.length(30));
        assertBounds(child, 10, 20, 30, 40);
        child.setSize(999, 888);
        assertBounds(child, 10, 20, 30, 40);
        child.setRelativePosition(100, 200);
        assertEquals(0, child.getRelativeX());
        assertEquals(0, child.getRelativeY());
        child.setPosition(500, 600);
        assertBounds(child, 10, 20, 30, 40);
    }

    @Test
    void removingAndReparentingReflowsSiblingsAndDropsOldConstraints() {
        Layout root = new Layout(100, 200, 100, 30).Direction(Direction.HORIZONTAL);
        Probe first = root.addChild(new Probe("first"), Constraint.length(30));
        Probe second = root.addChild(new Probe("second"));
        assertBounds(first, 100, 200, 30, 30);
        assertBounds(second, 130, 200, 70, 30);

        assertTrue(root.removeChild(first));
        assertNull(first.getParent());
        assertBounds(first, 100, 200, 30, 30);
        assertBounds(second, 100, 200, 100, 30);
        assertFalse(root.removeChild(first));
        assertThrows(IllegalArgumentException.class, () -> root.setConstraint(first, Constraint.length(5)));

        Layout other = new Layout(400, 500, 200, 40).Direction(Direction.HORIZONTAL);
        first.setParent(other);
        assertBounds(first, 400, 500, 200, 40);
        root.addChild(first);
        assertTrue(other.children().isEmpty());
        assertBounds(second, 100, 200, 50, 30);
        assertBounds(first, 150, 200, 50, 30);

        root.clearChildren();
        assertTrue(root.children().isEmpty());
        assertNull(first.getParent());
        assertNull(second.getParent());
        root.addChild(first);
        assertBounds(first, 100, 200, 100, 30);
    }

    @Test
    void hiddenChildrenKeepTheirSlotWithoutReceivingInputOrRendering() throws Exception {
        Layout root = new Layout(0, 0, 100, 40).Direction(Direction.HORIZONTAL);
        Probe first = root.addChild(new Probe("first"), Constraint.length(40));
        Probe second = root.addChild(new Probe("second"));
        first.visible = false;
        Host screen = new Host(root);
        root.render(graphics(), 45, 10, 0);

        assertBounds(second, 40, 0, 60, 40);
        assertEquals(0, first.renderCount);
        assertEquals(1, second.renderCount);
        assertFalse(screen.mouseClicked(5, 5, 0));
        assertTrue(screen.mouseClicked(45, 5, 0));
        assertEquals(List.of("click"), second.events);
        assertTrue(first.events.isEmpty());
    }

    @Test
    void rootRegisteredWithScreenDispatchesNestedInputAndCaptureAfterResize() {
        Layout root = new Layout(10, 20, 100, 80).Padding(5);
        Layout row = root.addChild(new Layout().Direction(Direction.HORIZONTAL).Spacing(10));
        Probe first = row.addChild(new Probe("first"), Constraint.length(20));
        Probe second = row.addChild(new Probe("second"));
        second.consumeKeys = true;
        second.consumeScroll = true;
        Host screen = new Host(root);
        assertEquals(List.of(root), screen.children());

        for (int i = 0; i < 2; i++) {
            assertTrue(screen.mouseClicked(46, 26, 0));
            assertSame(row, root.getFocused());
            assertSame(second, row.getFocused());
            assertTrue(second.isFocused());
            assertTrue(screen.keyPressed(65, 0, 0));
            assertTrue(screen.keyReleased(65, 0, 0));
            assertTrue(screen.charTyped('a', 0));
            assertTrue(screen.mouseDragged(900, 900, 0, 5, 6));
            assertFalse(screen.mouseReleased(900, 900, 1));
            assertTrue(screen.mouseReleased(900, 900, 0));
            assertEquals(List.of("click", "key", "keyUp", "char", "drag", "release"), second.events);
            second.events.clear();
        }
        assertTrue(first.events.isEmpty());
        assertTrue(screen.mouseScrolled(46, 26, 0, 1));
        assertEquals(List.of("scroll"), second.events);
        second.events.clear();

        root.setPosition(100, 200);
        root.setWidth(200);
        assertFalse(screen.mouseClicked(46, 26, 0));
        assertTrue(screen.mouseClicked(290, 206, 0), "Input must force layout before the next render");
        assertEquals(List.of("click"), second.events);
        assertBounds(second, 135, 205, 160, 70);
    }

    @Test
    void paddingSpacingAndEmptyLayoutRemainTransparentToClicks() {
        Layout root = new Layout(10, 20, 100, 80).Padding(5).Direction(Direction.HORIZONTAL).Spacing(10);
        root.addChild(new Probe("first"), Constraint.length(20));
        root.addChild(new Probe("second"), Constraint.length(20));
        Host screen = new Host(root);

        assertFalse(screen.mouseClicked(11, 21, 0), "Padding must not act like a button");
        assertFalse(screen.mouseClicked(40, 30, 0), "Spacing must not act like a button");
        assertFalse(screen.mouseClicked(95, 30, 0), "Unallocated content must not act like a button");
        assertNull(root.getFocused());
        assertFalse(root.isDragging());
        root.clearChildren();
        assertFalse(screen.mouseClicked(30, 30, 0));
        assertNull(root.nextFocusPath(new FocusNavigationEvent.TabNavigation(true)));
    }

    @Test
    void blankLayoutOverlayAllowsSiblingHoverAndScrollWhileChildrenRemainInteractive() throws Exception {
        Probe root = new Probe("root");
        root.setRectangle(100, 80, 0, 0);
        Probe behind = root.addChild(new Probe("behind"));
        behind.setRelativePosition(0, 0);
        behind.setSize(100, 80);
        behind.consumeScroll = true;
        Layout overlay = root.addChild(new Layout(0, 0, 100, 80).Direction(Direction.HORIZONTAL));
        Probe front = overlay.addChild(new Probe("front"), Constraint.length(20));
        front.consumeScroll = true;
        Host screen = new Host(root);

        assertSame(behind, root.getChildAt(60, 10).orElseThrow());
        assertFalse(overlay.isMouseOver(60, 10));
        assertTrue(screen.mouseScrolled(60, 10, 0, 1));
        assertEquals(List.of("scroll"), behind.events);
        assertTrue(front.events.isEmpty());
        root.render(graphics(), 60, 10, 0);
        assertTrue(behind.isHovered(), "Layout whitespace must not cover the lower sibling's hover");
        assertFalse(front.isHovered());

        assertSame(overlay, root.getChildAt(5, 5).orElseThrow());
        assertSame(front, overlay.getChildAt(5, 5).orElseThrow());
        assertTrue(screen.mouseScrolled(5, 5, 0, 1));
        assertEquals(List.of("scroll"), behind.events);
        assertEquals(List.of("scroll"), front.events);
        root.render(graphics(), 5, 5, 0);
        assertFalse(behind.isHovered());
        assertTrue(front.isHovered());
    }

    @Test
    void tabNavigationTraversesLeavesAndSkipsEmptyLayout() {
        Layout root = new Layout(0, 0, 100, 100);
        Probe first = root.addChild(new Probe("first"));
        root.addChild(new Layout());
        Layout group = root.addChild(new Layout().Direction(Direction.HORIZONTAL));
        Probe second = group.addChild(new Probe("second"));
        Probe third = group.addChild(new Probe("third"));
        Host screen = new Host(root);

        focus(screen, true);
        assertTrue(first.isFocused());
        focus(screen, true);
        assertTrue(second.isFocused());
        assertFalse(first.isFocused());
        focus(screen, true);
        assertTrue(third.isFocused());
        focus(screen, false);
        assertTrue(second.isFocused());
        assertFalse(third.isFocused());
    }

    @Test
    void renderingRefreshesBoundsAndKeepsTransformsIsolated() throws Exception {
        Layout root = new Layout(30, 40, 100, 60).Direction(Direction.HORIZONTAL).Padding(5);
        Probe first = root.addChild(new Probe("first"));
        Probe second = root.addChild(new Probe("second"));
        GuiGraphics graphics = graphics();
        root.layout();
        root.setWidth(150);
        root.render(graphics, 110, 50, 0);

        assertEquals(List.of(35, 45, 70, 50), first.renderedBounds);
        assertEquals(List.of(105, 45, 70, 50), second.renderedBounds);
        assertFalse(first.isHovered());
        assertTrue(second.isHovered());
        assertEquals(0, graphics.pose().last().pose().m30());
        assertEquals(0, graphics.pose().last().pose().m31());
    }

    @Test
    void clickCallbackCanRemoveItsOwnChildWithoutRestoringFocusOrCapture() {
        Layout root = new Layout(0, 0, 100, 40).Direction(Direction.HORIZONTAL);
        Probe removable = root.addChild(new Probe("removable"));
        Probe survivor = root.addChild(new Probe("survivor"));
        removable.clickAction = () -> root.removeChild(removable);
        Host screen = new Host(root);

        assertTrue(screen.mouseClicked(5, 5, 0));
        assertNull(removable.getParent());
        assertFalse(removable.isFocused());
        assertNull(root.getFocused());
        assertFalse(root.isDragging());
        assertFalse(screen.mouseDragged(200, 200, 0, 1, 1));
        assertFalse(screen.mouseReleased(200, 200, 0));
        assertBounds(survivor, 0, 0, 100, 40);
        assertTrue(screen.mouseClicked(5, 5, 0));
        assertEquals(List.of("click"), survivor.events);
    }

    @Test
    void disabledOrHiddenAncestorBlocksItsWholeSubtree() throws Exception {
        Layout root = new Layout(0, 0, 100, 40);
        Probe child = root.addChild(new Probe("child"));
        Host screen = new Host(root);
        root.active = false;
        assertFalse(screen.mouseClicked(5, 5, 0));
        assertFalse(child.isActive());
        root.render(graphics(), 5, 5, 0);
        assertEquals(1, child.renderCount);
        root.active = true;
        root.visible = false;
        assertFalse(screen.mouseClicked(5, 5, 0));
        root.render(graphics(), 5, 5, 0);
        assertEquals(1, child.renderCount);
        assertTrue(child.events.isEmpty());
    }

    @Test
    void exhaustedContentAndSpacingNeverProduceNegativeWidgetSizes() {
        Layout root = new Layout(0, 0, 10, 10).Padding(20).Spacing(100);
        Probe first = root.addChild(new Probe("first"), Constraint.length(50));
        Probe second = root.addChild(new Probe("second"));
        assertEquals(0, first.getWidth());
        assertEquals(0, first.getHeight());
        assertEquals(0, second.getWidth());
        assertEquals(0, second.getHeight());
        assertFalse(root.mouseClicked(20, 20, 0));
    }

    @Test
    void fittedScreenInitializesAndResizesTheRootWithoutDuplicateRegistration() {
        Layout root = new Layout(30, 40, 100, 60).Direction(Direction.HORIZONTAL).Padding(10).Spacing(10);
        Probe first = root.addChild(new Probe("first"), Constraint.length(40));
        Probe second = root.addChild(new Probe("second"));
        HeadlessSeleneScreen screen = new HeadlessSeleneScreen(root, true);
        screen.open(320, 180);

        assertEquals(List.of(root), screen.children());
        assertBounds(root, 0, 0, 320, 180);
        assertBounds(first, 10, 10, 40, 160);
        assertBounds(second, 60, 10, 250, 160);
        assertTrue(screen.mouseClicked(65, 15, 0));

        screen.resize(Minecraft.getInstance(), 500, 240);
        assertEquals(500, screen.width);
        assertEquals(240, screen.height);
        assertEquals(List.of(root), screen.children(), "A resize must replace the old registration");
        assertBounds(root, 0, 0, 500, 240);
        assertBounds(first, 10, 10, 40, 220);
        assertBounds(second, 60, 10, 430, 220);
        assertTrue(screen.mouseClicked(480, 200, 0), "Input follows the expanded layout immediately");

        screen.resize(Minecraft.getInstance(), 120, 80);
        assertEquals(List.of(root), screen.children());
        assertBounds(root, 0, 0, 120, 80);
        assertBounds(second, 60, 10, 50, 60);
        assertFalse(screen.mouseClicked(480, 200, 0));
    }

    @Test
    void twoArgumentScreenKeepsExplicitRootBoundsAcrossWindowResizes() {
        Layout root = new Layout(20, 30, 200, 100).Direction(Direction.HORIZONTAL).Padding(5);
        Probe first = root.addChild(new Probe("first"), Constraint.length(25));
        Probe second = root.addChild(new Probe("second"));
        HeadlessSeleneScreen screen = new HeadlessSeleneScreen(root);
        screen.open(320, 180);

        assertBounds(root, 20, 30, 200, 100);
        assertBounds(first, 25, 35, 25, 90);
        assertBounds(second, 50, 35, 165, 90);
        screen.resize(Minecraft.getInstance(), 640, 360);
        assertEquals(List.of(root), screen.children());
        assertBounds(root, 20, 30, 200, 100);
        assertBounds(first, 25, 35, 25, 90);
        assertBounds(second, 50, 35, 165, 90);
        assertTrue(screen.mouseClicked(200, 50, 0));
        assertFalse(screen.mouseClicked(400, 50, 0));
    }

    @Test
    void galleryContentFillsItsBlockOnOpenAndResize() {
        HeadlessGalleryScreen screen = new HeadlessGalleryScreen();
        try {
            screen.open(320, 180);
            BaseWidget content = screen.getRoot().children().getFirst();
            ScrollList navigation = (ScrollList) content.children().getFirst();
            BaseWidget panel = content.children().get(1);
            assertBounds(screen.getRoot(), 0, 0, 320, 180);
            assertBounds(content, 0, 0, 320, 180);
            assertBounds(navigation, 0, 8, 90, 164);
            assertBounds(panel, 98, 8, 214, 164);
            assertEquals(40, navigation.children().size());
            BaseWidget firstRow = navigation.children().getFirst();
            assertEquals(0, navigation.getContentX(), "The clip must include the screen's left edge");
            assertEquals(0, firstRow.getX(), "The selection marker follows the row's left edge");
            assertEquals(24, firstRow.getContentX(), "Keep the original text indentation");

            screen.resize(Minecraft.getInstance(), 500, 240);
            assertEquals(List.of(screen.getRoot()), screen.children());
            assertBounds(content, 0, 0, 500, 240);
            assertBounds(navigation, 0, 8, 90, 224);
            assertBounds(panel, 98, 8, 394, 224);
            navigation.setScrollAmount(8);
            assertEquals(8, firstRow.getY());
            assertEquals(0, firstRow.getX());
            assertEquals(24, firstRow.getContentX());
        } finally {
            screen.removed();
        }
    }

    private static void assertBounds(BaseWidget widget, int x, int y, int width, int height) {
        assertAll(widget.getMessage().getString(),
                () -> assertEquals(x, widget.getX(), "x"),
                () -> assertEquals(y, widget.getY(), "y"),
                () -> assertEquals(width, widget.getWidth(), "width"),
                () -> assertEquals(height, widget.getHeight(), "height"));
    }

    private static GuiGraphics graphics() throws Exception {
        GuiGraphics graphics = (GuiGraphics) unsafe.allocateInstance(GuiGraphics.class);
        Field pose = GuiGraphics.class.getDeclaredField("pose");
        pose.setAccessible(true);
        pose.set(graphics, new PoseStack());
        Field scissor = GuiGraphics.class.getDeclaredField("scissorStack");
        scissor.setAccessible(true);
        var constructor = scissor.getType().getDeclaredConstructor();
        constructor.setAccessible(true);
        scissor.set(graphics, constructor.newInstance());
        return graphics;
    }

    private static void focus(Host screen, boolean forward) {
        ComponentPath path = screen.nextFocusPath(new FocusNavigationEvent.TabNavigation(forward));
        assertNotNull(path, "Expected a focusable leaf");
        ComponentPath previous = screen.getCurrentFocusPath();
        if (previous != null) {
            previous.applyFocus(false);
        }
        path.applyFocus(true);
    }

    private static final class Host extends Screen {
        Host(BaseWidget root) {
            super(Component.literal("Layout test"));
            addRenderableWidget(root);
        }
    }

    /** Keeps the real resize dispatch while omitting NeoForge bus and narrator startup. */
    private static final class HeadlessSeleneScreen extends SeleneScreen {
        HeadlessSeleneScreen(BaseWidget root) {
            super(Component.literal("Fixed screen test"), root);
        }

        HeadlessSeleneScreen(BaseWidget root, boolean fitToScreen) {
            super(Component.literal("Fitted screen test"), root, fitToScreen);
        }

        void open(int width, int height) {
            this.minecraft = Minecraft.getInstance();
            this.width = width;
            this.height = height;
            init();
        }

        @Override
        protected void rebuildWidgets() {
            clearWidgets();
            clearFocus();
            init();
        }
    }

    private static final class HeadlessGalleryScreen extends GalleryScreen {
        void open(int width, int height) {
            this.minecraft = Minecraft.getInstance();
            this.width = width;
            this.height = height;
            init();
        }

        @Override
        protected void rebuildWidgets() {
            clearWidgets();
            clearFocus();
            init();
        }
    }

    private static final class Probe extends BaseWidget {
        final List<String> events = new ArrayList<>();
        List<Integer> renderedBounds = List.of();
        Runnable clickAction;
        boolean consumeKeys;
        boolean consumeScroll;
        int renderCount;

        Probe(String name) {
            super(2, 3, 7, 9, Component.literal(name));
        }

        Probe(BaseWidget parent, String name) {
            super(parent, 2, 3, 7, 9, Component.literal(name));
        }

        @Override
        protected void renderSelf(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            renderCount++;
            renderedBounds = List.of(getX(), getY(), getWidth(), getHeight());
            graphics.pose().translate(2, 3, 0);
        }

        @Override
        public void playDownSound(SoundManager manager) {}

        @Override
        public void onClick(double mouseX, double mouseY, int button) {
            events.add("click");
            if (clickAction != null) {
                clickAction.run();
            }
        }

        @Override
        protected void onDrag(double mouseX, double mouseY, double dragX, double dragY) {
            events.add("drag");
        }

        @Override
        public void onRelease(double mouseX, double mouseY) {
            events.add("release");
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (super.keyPressed(keyCode, scanCode, modifiers)) return true;
            if (!isActive() || !isFocused() || !consumeKeys) return false;
            events.add("key");
            return true;
        }

        @Override
        public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
            if (super.keyReleased(keyCode, scanCode, modifiers)) return true;
            if (!isActive() || !isFocused() || !consumeKeys) return false;
            events.add("keyUp");
            return true;
        }

        @Override
        public boolean charTyped(char codePoint, int modifiers) {
            if (super.charTyped(codePoint, modifiers)) return true;
            if (!isActive() || !isFocused() || !consumeKeys) return false;
            events.add("char");
            return true;
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
            if (super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) return true;
            if (!isActive() || !consumeScroll) return false;
            events.add("scroll");
            return true;
        }
    }
}

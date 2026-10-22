package neuvillette.selene.state;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import neuvillette.selene.layout.Constraint;
import neuvillette.selene.layout.Direction;
import neuvillette.selene.screen.StateDemoScreen;
import neuvillette.selene.widget.BaseWidget;
import neuvillette.selene.widget.Layout;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

/** Verifies shared state using real widget trees, lazy layout, and Screen dispatch. */
class StateWidgetTest {
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
        if (minecraftInstance != null) {
            minecraftInstance.set(null, previousMinecraft);
        }
    }

    @Test
    void componentsInDifferentBranchesObserveUpdatesFromEitherTheModelOrAnInputCallback() {
        State<Integer> count = new State<>(3);
        Layout root = new Layout(10, 20, 200, 80)
                .Direction(Direction.HORIZONTAL).Padding(5).Spacing(10);
        Layout left = root.addChild(new Layout());
        Layout right = root.addChild(new Layout());
        Probe summary = left.addChild(new Probe("summary"));
        Probe increment = right.addChild(new Probe("increment"));

        try (Subscription summaryBinding = count.subscribe(value ->
                     summary.setMessage(Component.literal("Count: " + value)));
             Subscription incrementBinding = count.subscribe(value ->
                     increment.setMessage(Component.literal("Increment " + value)))) {
            assertEquals("Count: 3", summary.getMessage().getString(), "Binding immediately initializes a component");
            assertEquals("Increment 3", increment.getMessage().getString());

            count.set(9);
            assertEquals("Count: 9", summary.getMessage().getString());
            assertEquals("Increment 9", increment.getMessage().getString());

            increment.clickAction = () -> count.update(value -> value + 1);
            Host screen = new Host(root);
            assertEquals(1, screen.children().size(), "Only the common root is registered on Screen");
            assertTrue(screen.mouseClicked(116, 26, 0));
            assertEquals(10, count.get());
            assertEquals("Count: 10", summary.getMessage().getString());
            assertEquals("Increment 10", increment.getMessage().getString());
            assertEquals(0, summary.clickCount);
            assertEquals(1, increment.clickCount);
            assertTrue(screen.mouseReleased(116, 26, 0));
        }
    }

    @Test
    void aStateDrivenConstraintRefreshesGeometryAndHitTestingBeforeRendering() {
        State<Integer> width = new State<>(20);
        Layout root = new Layout(10, 20, 200, 60)
                .Direction(Direction.HORIZONTAL).Padding(5).Spacing(5);
        Probe fixed = root.addChild(new Probe("fixed"));
        Probe remainder = root.addChild(new Probe("remainder"));
        Host screen = new Host(root);

        try (Subscription binding = width.subscribe(value ->
                     root.setConstraint(fixed, Constraint.length(value)))) {
            assertEquals(20, fixed.getWidth());
            assertEquals(40, remainder.getX());
            assertEquals(165, remainder.getWidth());

            width.set(70);
            assertEquals(70, fixed.getWidth(), "A geometry query applies the state-driven constraint");
            assertEquals(90, remainder.getX());
            assertEquals(115, remainder.getWidth());
            assertEquals(90, remainder.getRectangle().left());

            width.set(40);
            // No geometry query or render occurs between changing state and dispatching input.
            assertTrue(screen.mouseClicked(65, 26, 0));
            assertEquals(0, fixed.clickCount, "The old larger fixed rectangle must stop receiving clicks");
            assertEquals(1, remainder.clickCount);
            assertTrue(screen.mouseReleased(65, 26, 0));
            assertEquals(60, remainder.getX());

            width.update(value -> value + 50);
            assertTrue(screen.mouseClicked(65, 26, 0));
            assertEquals(1, fixed.clickCount, "The expanded fixed rectangle immediately owns input");
            assertEquals(1, remainder.clickCount);
            assertTrue(screen.mouseReleased(65, 26, 0));
            assertEquals(90, fixed.getWidth());
            assertEquals(110, remainder.getX());
        }
    }

    @Test
    void cancellingOneComponentBindingLeavesTheOtherBindingActive() {
        State<String> text = new State<>("initial");
        Layout root = new Layout(0, 0, 200, 80).Direction(Direction.HORIZONTAL);
        Layout left = root.addChild(new Layout());
        Layout right = root.addChild(new Layout());
        Probe first = left.addChild(new Probe("first"));
        Probe second = right.addChild(new Probe("second"));
        Subscription firstBinding = text.subscribe(value -> first.setMessage(Component.literal(value)));
        Subscription secondBinding = text.subscribe(value -> second.setMessage(Component.literal(value)));
        try {
            assertEquals("initial", first.getMessage().getString());
            assertEquals("initial", second.getMessage().getString());
            firstBinding.unsubscribe();
            text.set("updated");
            assertEquals("initial", first.getMessage().getString());
            assertEquals("updated", second.getMessage().getString(), "Cancellation belongs to one binding only");

            secondBinding.close();
            text.set("after both cancelled");
            assertEquals("initial", first.getMessage().getString());
            assertEquals("updated", second.getMessage().getString());
        } finally {
            firstBinding.unsubscribe();
            secondBinding.unsubscribe();
        }
    }

    @Test
    void removingAndRemountingAWidgetDoesNotImplicitlyDisposeItsBinding() {
        State<String> text = new State<>("mounted");
        Layout firstParent = new Layout(10, 20, 100, 40);
        Layout secondParent = new Layout(200, 20, 100, 40);
        Probe widget = firstParent.addChild(new Probe("movable"));

        try (Subscription binding = text.subscribe(value -> widget.setMessage(Component.literal(value)))) {
            assertEquals("mounted", widget.getMessage().getString());
            assertTrue(firstParent.removeChild(widget));
            assertNull(widget.getParent());
            text.set("detached");
            assertEquals("detached", widget.getMessage().getString(), "Tree removal is distinct from subscription disposal");

            secondParent.addChild(widget);
            text.set("remounted");
            assertSame(secondParent, widget.getParent());
            assertEquals("remounted", widget.getMessage().getString());
            assertEquals(200, widget.getX());
        }
        text.set("after close");
        assertEquals("remounted", widget.getMessage().getString());
    }

    @Test
    void demoRebindsAfterResizeAndCancelsBindingsWhileTheScreenIsClosed() {
        HeadlessStateDemoScreen screen = new HeadlessStateDemoScreen();
        screen.open(320, 180);
        assertEquals(1, screen.children().size());
        BaseWidget root = (BaseWidget) screen.children().getFirst();
        BaseWidget left = root.children().get(0);
        BaseWidget right = root.children().get(1);
        assertEquals("Left: 0", left.getMessage().getString());
        assertEquals("Right: 0", right.getMessage().getString());

        // Invoke the real panel callback without requiring a native sound system.
        left.onClick(0, 0, 0);
        assertEquals("Left: -1", left.getMessage().getString());
        assertEquals("Right: -1", right.getMessage().getString());
        right.onClick(0, 0, 0);
        assertEquals("Left: 0", left.getMessage().getString());
        assertEquals("Right: 0", right.getMessage().getString());

        screen.resize(Minecraft.getInstance(), 500, 240);
        assertEquals(1, screen.children().size(), "Resize replaces the Screen registration");
        assertSame(root, screen.children().getFirst());
        assertEquals(500, root.getWidth());
        assertEquals("Left: 0", left.getMessage().getString(), "Resize preserves shared state");
        assertEquals("Right: 0", right.getMessage().getString());
        right.onClick(0, 0, 0);
        assertEquals("Left: 1", left.getMessage().getString());
        assertEquals("Right: 1", right.getMessage().getString());

        screen.removed();
        right.onClick(0, 0, 0);
        right.onClick(0, 0, 0);
        assertEquals("Left: 1", left.getMessage().getString(), "Closed screens must release panel bindings");
        assertEquals("Right: 1", right.getMessage().getString());

        screen.open(320, 180);
        assertEquals(1, screen.children().size());
        assertSame(root, screen.children().getFirst());
        assertEquals("Left: 3", left.getMessage().getString(), "Reopening binds to the latest model value");
        assertEquals("Right: 3", right.getMessage().getString());
        left.onClick(0, 0, 0);
        assertEquals("Left: 2", left.getMessage().getString());
        assertEquals("Right: 2", right.getMessage().getString());
        screen.removed();
    }

    private static final class Host extends Screen {
        Host(BaseWidget root) {
            super(Component.literal("State integration test"));
            addRenderableWidget(root);
        }
    }

    private static final class HeadlessStateDemoScreen extends StateDemoScreen {
        void open(int width, int height) {
            this.minecraft = Minecraft.getInstance();
            this.width = width;
            this.height = height;
            rebuildWidgets();
        }

        @Override
        protected void rebuildWidgets() {
            clearWidgets();
            clearFocus();
            init();
        }
    }

    private static final class Probe extends BaseWidget {
        Runnable clickAction;
        int clickCount;

        Probe(String name) {
            super(0, 0, 10, 10, Component.literal(name));
        }

        @Override
        protected void renderSelf(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {}

        @Override
        public void playDownSound(SoundManager manager) {}

        @Override
        public void onClick(double mouseX, double mouseY, int button) {
            clickCount++;
            if (clickAction != null) {
                clickAction.run();
            }
        }
    }
}

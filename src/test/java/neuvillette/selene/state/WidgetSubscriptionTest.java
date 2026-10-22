package neuvillette.selene.state;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import neuvillette.selene.layout.Constraint;
import neuvillette.selene.layout.Direction;
import neuvillette.selene.screen.SeleneScreen;
import neuvillette.selene.widget.BaseWidget;
import neuvillette.selene.widget.Layout;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

class WidgetSubscriptionTest {
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
    void componentDeclaresMultipleBindingsInternallyAndReceivesLatestValuesOnMount() {
        State<Integer> number = new State<>(0);
        State<String> label = new State<>("initial");
        BoundComponent component = new BoundComponent(number, label);
        assertFalse(component.isMounted());
        assertTrue(component.numbers.isEmpty());
        assertTrue(component.labels.isEmpty());
        number.set(5);
        label.set("ready");
        assertTrue(component.numbers.isEmpty());

        component.mount();
        assertEquals(List.of(5), component.numbers);
        assertEquals(List.of("ready"), component.labels);
        number.update(value -> value + 1);
        label.set("updated");
        assertEquals(List.of(5, 6), component.numbers);
        assertEquals(List.of("ready", "updated"), component.labels);
    }

    @Test
    void mountingAndUnmountingTheTreeAreIdempotentAndRemountUsesCurrentValues() {
        State<Integer> state = new State<>(0);
        Probe root = new Probe();
        Probe child = root.addChild(new Probe());
        Probe leaf = child.addChild(new Probe());
        List<Integer> rootValues = new ArrayList<>();
        List<Integer> childValues = new ArrayList<>();
        List<Integer> leafValues = new ArrayList<>();
        root.watch(state, rootValues::add);
        child.watch(state, childValues::add);
        leaf.watch(state, leafValues::add);

        root.mount();
        root.mount();
        assertTrue(root.isMounted());
        assertTrue(child.isMounted());
        assertTrue(leaf.isMounted());
        state.set(1);
        assertEquals(List.of(0, 1), rootValues);
        assertEquals(List.of(0, 1), childValues);
        assertEquals(List.of(0, 1), leafValues);

        root.unmount();
        root.unmount();
        assertFalse(root.isMounted());
        assertFalse(child.isMounted());
        assertFalse(leaf.isMounted());
        state.set(2);
        state.set(3);
        root.mount();
        assertEquals(List.of(0, 1, 3), rootValues);
        assertEquals(List.of(0, 1, 3), childValues);
        assertEquals(List.of(0, 1, 3), leafValues);
    }

    @Test
    void addingToMountedParentActivatesExistingBindingsAndParentConstructorBindings() {
        State<Integer> state = new State<>(10);
        Probe root = new Probe();
        root.mount();
        Probe child = new Probe();
        List<Integer> childValues = new ArrayList<>();
        child.watch(state, childValues::add);
        root.addChild(child);
        assertTrue(child.isMounted());
        assertEquals(List.of(10), childValues);
        root.addChild(child);
        assertEquals(List.of(10), childValues);

        Probe constructedChild = new Probe(root);
        List<Integer> constructedValues = new ArrayList<>();
        constructedChild.watch(state, constructedValues::add);
        assertTrue(constructedChild.isMounted());
        assertEquals(List.of(10), constructedValues);
        state.set(11);
        assertEquals(List.of(10, 11), childValues);
        assertEquals(List.of(10, 11), constructedValues);
    }

    @Test
    void removingSubtreePausesItsBindingsAndAddingItBackResumesThem() {
        State<Integer> state = new State<>(0);
        Probe root = new Probe();
        Probe child = root.addChild(new Probe());
        Probe leaf = child.addChild(new Probe());
        List<Integer> childValues = new ArrayList<>();
        List<Integer> leafValues = new ArrayList<>();
        child.watch(state, childValues::add);
        leaf.watch(state, leafValues::add);
        root.mount();

        assertTrue(root.removeChild(child));
        assertTrue(root.isMounted());
        assertFalse(child.isMounted());
        assertFalse(leaf.isMounted());
        state.set(1);
        assertEquals(List.of(0), childValues);
        assertEquals(List.of(0), leafValues);
        root.addChild(child);
        assertTrue(child.isMounted());
        assertTrue(leaf.isMounted());
        assertEquals(List.of(0, 1), childValues);
        assertEquals(List.of(0, 1), leafValues);
        state.set(2);
        assertEquals(List.of(0, 1, 2), childValues);
        assertEquals(List.of(0, 1, 2), leafValues);
    }

    @Test
    void reparentingBetweenMountedTreesPreservesActiveSubscriptionsWithoutResynchronizing() {
        State<Integer> state = new State<>(0);
        Probe firstRoot = new Probe();
        Probe secondRoot = new Probe();
        Probe child = firstRoot.addChild(new Probe());
        Probe leaf = child.addChild(new Probe());
        List<Integer> values = new ArrayList<>();
        leaf.watch(state, values::add);
        firstRoot.mount();
        secondRoot.mount();

        child.setParent(secondRoot);
        assertTrue(child.isMounted());
        assertTrue(leaf.isMounted());
        assertEquals(List.of(0), values);
        assertTrue(firstRoot.children().isEmpty());
        assertSame(secondRoot, child.getParent());
        firstRoot.unmount();
        state.set(1);
        assertEquals(List.of(0, 1), values);
        secondRoot.unmount();
        state.set(2);
        assertEquals(List.of(0, 1), values);
    }

    @Test
    void reparentingToAnUnmountedTreePausesUntilThatTreeIsMounted() {
        State<Integer> state = new State<>(0);
        Probe activeRoot = new Probe();
        Probe dormantRoot = new Probe();
        Probe child = activeRoot.addChild(new Probe());
        List<Integer> values = new ArrayList<>();
        child.watch(state, values::add);
        activeRoot.mount();

        dormantRoot.addChild(child);
        assertFalse(child.isMounted());
        state.set(1);
        assertEquals(List.of(0), values);
        dormantRoot.mount();
        assertTrue(child.isMounted());
        assertEquals(List.of(0, 1), values);
        state.set(2);
        assertEquals(List.of(0, 1, 2), values);
    }

    @Test
    void cancellationPermanentlyRemovesBindingBeforeOrAfterActivation() {
        State<Integer> state = new State<>(0);
        Probe widget = new Probe();
        List<Integer> cancelledBeforeMount = new ArrayList<>();
        List<Integer> cancelledAfterMount = new ArrayList<>();
        List<Integer> retained = new ArrayList<>();
        var first = widget.watch(state, cancelledBeforeMount::add);
        first.unsubscribe();
        first.close();
        var second = widget.watch(state, cancelledAfterMount::add);
        widget.watch(state, retained::add);
        widget.mount();
        second.close();
        second.unsubscribe();
        state.set(1);
        widget.unmount();
        state.set(2);
        widget.mount();

        assertTrue(cancelledBeforeMount.isEmpty());
        assertEquals(List.of(0), cancelledAfterMount);
        assertEquals(List.of(0, 1, 2), retained);
    }

    @Test
    void bindingsAddedWhileMountedSyncImmediatelyAndManualSubscriptionsStayIndependent() {
        State<Integer> state = new State<>(0);
        Probe widget = new Probe();
        List<Integer> managed = new ArrayList<>();
        List<Integer> manual = new ArrayList<>();
        widget.mount();
        widget.watch(state, managed::add);
        var directSubscription = state.subscribe(manual::add);
        assertEquals(List.of(0), managed);
        widget.unmount();
        state.set(1);
        assertEquals(List.of(0), managed);
        assertEquals(List.of(0, 1), manual);
        widget.mount();
        assertEquals(List.of(0, 1), managed);
        assertEquals(List.of(0, 1), manual);
        directSubscription.close();
        state.set(2);
        assertEquals(List.of(0, 1, 2), managed);
        assertEquals(List.of(0, 1), manual);
    }

    @Test
    void unmountDuringInitialSynchronizationPreventsLaterBindingsAndHandleLeaks() {
        State<Integer> state = new State<>(0);
        Probe widget = new Probe();
        List<Integer> firstValues = new ArrayList<>();
        List<Integer> secondValues = new ArrayList<>();
        widget.watch(state, value -> {
            firstValues.add(value);
            if (value == 0) widget.unmount();
        });
        widget.watch(state, secondValues::add);

        widget.mount();
        assertFalse(widget.isMounted());
        assertEquals(List.of(0), firstValues);
        assertTrue(secondValues.isEmpty());
        state.set(1);
        assertEquals(List.of(0), firstValues);
        widget.mount();
        assertTrue(widget.isMounted());
        assertEquals(List.of(0, 1), firstValues);
        assertEquals(List.of(1), secondValues);
        state.set(2);
        assertEquals(List.of(0, 1, 2), firstValues);
        assertEquals(List.of(1, 2), secondValues);
    }

    @Test
    void unmountDuringNotificationSkipsLaterListenersInTheCurrentStateSnapshot() {
        State<Integer> state = new State<>(0);
        Probe widget = new Probe();
        List<Integer> firstValues = new ArrayList<>();
        List<Integer> secondValues = new ArrayList<>();
        widget.watch(state, value -> {
            firstValues.add(value);
            if (value == 1) widget.unmount();
        });
        widget.watch(state, secondValues::add);
        widget.mount();

        state.set(1);
        state.set(2);
        assertFalse(widget.isMounted());
        assertEquals(List.of(0, 1), firstValues);
        assertEquals(List.of(0), secondValues);
        widget.mount();
        assertEquals(List.of(0, 1, 2), firstValues);
        assertEquals(List.of(0, 2), secondValues);
    }

    @Test
    void removingSelfDuringInitialSynchronizationDoesNotStopSiblingMounting() {
        State<Integer> state = new State<>(0);
        Probe root = new Probe();
        Probe removable = root.addChild(new Probe());
        Probe sibling = root.addChild(new Probe());
        List<Integer> removedValues = new ArrayList<>();
        List<Integer> siblingValues = new ArrayList<>();
        removable.watch(state, value -> {
            removedValues.add(value);
            root.removeChild(removable);
        });
        sibling.watch(state, siblingValues::add);

        root.mount();
        assertTrue(root.isMounted());
        assertNull(removable.getParent());
        assertFalse(removable.isMounted());
        assertTrue(sibling.isMounted());
        state.set(1);
        assertEquals(List.of(0), removedValues);
        assertEquals(List.of(0, 1), siblingValues);
    }

    @Test
    void addingToMountedLayoutPublishesExplicitConstraintBeforeInitialSynchronization() {
        State<Integer> state = new State<>(0);
        Layout root = new Layout(0, 0, 120, 40).Direction(Direction.HORIZONTAL);
        root.mount();
        Probe child = new Probe();
        Constraint length = Constraint.length(20);
        List<Integer> observedWidths = new ArrayList<>();
        child.watch(state, value -> {
            assertEquals(length, root.getConstraint(child));
            observedWidths.add(child.getWidth());
        });

        assertSame(child, root.addChild(child, length));
        assertTrue(child.isMounted());
        assertEquals(List.of(20), observedWidths);
        state.set(1);
        assertEquals(List.of(20, 20), observedWidths);

        assertThrows(IllegalArgumentException.class, () -> root.addChild(root, Constraint.length(7)));
        assertEquals(List.of(child), root.children());
        assertNull(root.getParent());
        Probe rejected = new Probe();
        assertThrows(NullPointerException.class, () -> root.addChild(rejected, null));
        assertNull(rejected.getParent());
        root.addChild(rejected);
        assertEquals(Constraint.fill(1), root.getConstraint(rejected));
        assertEquals(100, rejected.getWidth());
    }

    @Test
    void initialRemovalFromMountedLayoutDoesNotLeaveConstraintOnLaterDefaultAdd() {
        State<Integer> state = new State<>(0);
        Layout root = new Layout(0, 0, 100, 40).Direction(Direction.HORIZONTAL);
        root.mount();
        Probe child = new Probe();
        AtomicBoolean removeOnce = new AtomicBoolean(true);
        List<Integer> values = new ArrayList<>();
        child.watch(state, value -> {
            values.add(value);
            if (removeOnce.getAndSet(false)) root.removeChild(child);
        });

        assertSame(child, root.addChild(child, Constraint.length(20)));
        assertNull(child.getParent());
        assertFalse(child.isMounted());
        assertTrue(root.children().isEmpty());
        root.addChild(child);
        assertTrue(child.isMounted());
        assertEquals(Constraint.fill(1), root.getConstraint(child));
        assertEquals(100, child.getWidth());
        assertEquals(List.of(0, 0), values);
        state.set(1);
        assertEquals(List.of(0, 0, 1), values);
    }

    @Test
    void removingAnotherChildDuringNotificationCancelsItsPendingDelivery() {
        State<Integer> state = new State<>(0);
        Probe root = new Probe();
        Probe first = root.addChild(new Probe());
        Probe second = root.addChild(new Probe());
        List<Integer> secondValues = new ArrayList<>();
        first.watch(state, value -> {
            if (value == 1) root.removeChild(second);
        });
        second.watch(state, secondValues::add);
        root.mount();

        state.set(1);
        state.set(2);
        assertFalse(second.isMounted());
        assertEquals(List.of(0), secondValues);
        assertEquals(List.of(first), root.children());
    }

    @Test
    void initialCallbackCanPermanentlyCancelItsOwnBinding() {
        State<Integer> state = new State<>(0);
        Probe widget = new Probe();
        AtomicReference<Subscription> handle = new AtomicReference<>();
        List<Integer> values = new ArrayList<>();
        handle.set(widget.watch(state, value -> {
            values.add(value);
            handle.get().unsubscribe();
        }));

        widget.mount();
        assertTrue(widget.isMounted());
        state.set(1);
        widget.unmount();
        widget.mount();
        state.set(2);
        assertEquals(List.of(0), values);
    }

    @Test
    void initialCallbackCanUnmountAndRemountWithoutLeavingDuplicateSubscriptions() {
        State<Integer> state = new State<>(0);
        Probe widget = new Probe();
        AtomicBoolean remounted = new AtomicBoolean();
        List<Integer> firstValues = new ArrayList<>();
        List<Integer> secondValues = new ArrayList<>();
        widget.watch(state, value -> {
            firstValues.add(value);
            if (remounted.compareAndSet(false, true)) {
                widget.unmount();
                widget.mount();
            }
        });
        widget.watch(state, secondValues::add);

        assertDoesNotThrow(widget::mount);
        assertTrue(widget.isMounted());
        assertEquals(List.of(0, 0), firstValues);
        assertEquals(List.of(0), secondValues);
        state.set(1);
        assertEquals(List.of(0, 0, 1), firstValues);
        assertEquals(List.of(0, 1), secondValues);
        widget.unmount();
        state.set(2);
        assertEquals(List.of(0, 0, 1), firstValues);
        assertEquals(List.of(0, 1), secondValues);
    }

    @Test
    void failedInitialSynchronizationRollsBackAndPreservesDefinitionsForRetry() {
        State<Integer> state = new State<>(0);
        Probe widget = new Probe();
        List<Integer> firstValues = new ArrayList<>();
        List<Integer> secondValues = new ArrayList<>();
        AtomicBoolean failOnce = new AtomicBoolean(true);
        RuntimeException failure = new IllegalStateException("initial binding failure");
        widget.watch(state, firstValues::add);
        widget.watch(state, value -> {
            secondValues.add(value);
            if (failOnce.getAndSet(false)) throw failure;
        });

        assertSame(failure, assertThrows(RuntimeException.class, widget::mount));
        assertFalse(widget.isMounted());
        state.set(1);
        assertEquals(List.of(0), firstValues);
        assertEquals(List.of(0), secondValues);
        assertDoesNotThrow(widget::mount);
        assertTrue(widget.isMounted());
        assertEquals(List.of(0, 1), firstValues);
        assertEquals(List.of(0, 1), secondValues);
        state.set(2);
        assertEquals(List.of(0, 1, 2), firstValues);
        assertEquals(List.of(0, 1, 2), secondValues);
    }

    @Test
    void failingDescendantRollsBackTheWholeMountedTreeAndCanBeRetried() {
        State<Integer> state = new State<>(0);
        Probe root = new Probe();
        Probe child = root.addChild(new Probe());
        Probe leaf = child.addChild(new Probe());
        List<Integer> rootValues = new ArrayList<>();
        List<Integer> leafValues = new ArrayList<>();
        AtomicBoolean failOnce = new AtomicBoolean(true);
        AssertionError failure = new AssertionError("initial descendant failure");
        root.watch(state, rootValues::add);
        leaf.watch(state, value -> {
            leafValues.add(value);
            if (failOnce.getAndSet(false)) throw failure;
        });

        assertSame(failure, assertThrows(AssertionError.class, root::mount));
        assertFalse(root.isMounted());
        assertFalse(child.isMounted());
        assertFalse(leaf.isMounted());
        state.set(1);
        assertEquals(List.of(0), rootValues);
        assertEquals(List.of(0), leafValues);
        assertDoesNotThrow(root::mount);
        assertTrue(root.isMounted());
        assertTrue(child.isMounted());
        assertTrue(leaf.isMounted());
        assertEquals(List.of(0, 1), rootValues);
        assertEquals(List.of(0, 1), leafValues);
    }

    @Test
    void failureDuringAnOrdinaryUpdateDoesNotSilentlyDropTheManagedBinding() {
        State<Integer> state = new State<>(0);
        Probe widget = new Probe();
        List<Integer> firstValues = new ArrayList<>();
        List<Integer> secondValues = new ArrayList<>();
        RuntimeException failure = new IllegalStateException("update failure");
        widget.watch(state, value -> {
            firstValues.add(value);
            if (value == 1) throw failure;
        });
        widget.watch(state, secondValues::add);
        widget.mount();

        assertSame(failure, assertThrows(RuntimeException.class, () -> state.set(1)));
        assertTrue(widget.isMounted());
        assertDoesNotThrow(() -> state.set(2));
        assertEquals(List.of(0, 1, 2), firstValues);
        assertEquals(List.of(0, 1, 2), secondValues);
    }

    @Test
    void screenOpenResizeCloseAndReopenControlSubscriptionsWithoutDuplicateDelivery() {
        State<Integer> state = new State<>(0);
        Probe root = new Probe();
        Probe child = root.addChild(new Probe());
        List<Integer> values = new ArrayList<>();
        child.watch(state, values::add);
        HeadlessSeleneScreen screen = new HeadlessSeleneScreen(root);

        screen.open(320, 180);
        assertTrue(root.isMounted());
        assertTrue(child.isMounted());
        assertEquals(List.of(0), values);
        screen.resize(Minecraft.getInstance(), 640, 360);
        screen.resize(Minecraft.getInstance(), 480, 270);
        assertEquals(List.of(root), screen.children());
        assertEquals(List.of(0), values);
        state.set(1);
        assertEquals(List.of(0, 1), values);
        screen.removed();
        screen.removed();
        assertFalse(root.isMounted());
        assertFalse(child.isMounted());
        state.set(2);
        assertEquals(List.of(0, 1), values);
        screen.open(320, 180);
        assertTrue(child.isMounted());
        assertEquals(List.of(root), screen.children());
        assertEquals(List.of(0, 1, 2), values);
        state.set(3);
        assertEquals(List.of(0, 1, 2, 3), values);
        screen.removed();
    }

    @Test
    void invalidBindingArgumentsAreRejectedBeforeMounting() {
        Probe widget = new Probe();
        State<Integer> state = new State<>(0);
        List<Integer> values = new ArrayList<>();
        assertThrows(NullPointerException.class, () -> widget.<Integer>watch(null, values::add));
        assertThrows(NullPointerException.class, () -> widget.watch(state, null));
        widget.watch(state, values::add);
        assertDoesNotThrow(widget::mount);
        assertEquals(List.of(0), values);
    }

    private static class Probe extends BaseWidget {
        Probe() {
            super(0, 0, 100, 40, Component.empty());
        }

        Probe(BaseWidget parent) {
            super(parent, 0, 0, 100, 40, Component.empty());
        }

        <T> Subscription watch(State<T> state, Consumer<? super T> listener) {
            return subscribe(state, listener);
        }

        @Override
        protected void renderSelf(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {}
    }

    private static final class BoundComponent extends Probe {
        final List<Integer> numbers = new ArrayList<>();
        final List<String> labels = new ArrayList<>();

        BoundComponent(State<Integer> number, State<String> label) {
            watch(number, numbers::add);
            watch(label, labels::add);
        }
    }

    /** Exercises Screen.resize and SeleneScreen hooks without the NeoForge event bus or narrator. */
    private static final class HeadlessSeleneScreen extends SeleneScreen {
        HeadlessSeleneScreen(BaseWidget root) {
            super(Component.literal("Subscription screen test"), root, true);
        }

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
}

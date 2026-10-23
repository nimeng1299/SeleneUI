package neuvillette.selene.widget;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import neuvillette.selene.layout.Constraint;
import neuvillette.selene.state.State;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

class ChildFactoryTest {
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
    void factoryReceivesParentOnceAndReturnsTheCreatedSubtype() {
        Probe parent = new Probe(40);
        parent.setPosition(10, 20);
        parent.setPadding(4);
        AtomicInteger calls = new AtomicInteger();
        AtomicReference<Probe> created = new AtomicReference<>();

        Probe child = parent.addChild(self -> {
            assertSame(parent, self);
            assertTrue(self.children().isEmpty());
            calls.incrementAndGet();
            Probe result = new Probe(12);
            result.setRelativePosition(3, 5);
            created.set(result);
            return result;
        });

        assertSame(created.get(), child);
        assertSame(parent, child.getParent());
        assertEquals(List.of(child), parent.children());
        assertEquals(1, calls.get());
        assertEquals(17, child.getX());
        assertEquals(29, child.getY());
        assertEquals(12, child.getHeight());
    }

    @Test
    void rejectedFactoryInputsAndFailuresDoNotAutomaticallyChangeTheTree() {
        Probe parent = new Probe(40);
        Probe existing = parent.addChild(new Probe(10));
        parent.mount();
        Probe unattached = new Probe(12);
        IllegalStateException failure = new IllegalStateException("factory failed");

        assertSame(failure, assertThrows(IllegalStateException.class, () -> parent.addChild(self -> {
            unattached.setMessage(Component.literal("configured before failure"));
            throw failure;
        })));
        assertThrows(NullPointerException.class,
                () -> parent.addChild((Function<BaseWidget, Probe>) null));
        assertThrows(NullPointerException.class, () -> parent.addChild(self -> null));
        assertEquals(List.of(existing), parent.children());
        assertTrue(existing.isMounted());
        assertNull(unattached.getParent());
        assertFalse(unattached.isMounted());

        Layout layout = new Layout(0, 0, 100, 40);
        ScrollList list = new ScrollList(0, 0, 100, 40);
        AtomicInteger factoryCalls = new AtomicInteger();
        assertThrows(NullPointerException.class, () -> layout.addChild(self -> {
            factoryCalls.incrementAndGet();
            return new Probe(10);
        }, (Constraint) null));
        assertThrows(IllegalArgumentException.class, () -> list.addChild(self -> {
            factoryCalls.incrementAndGet();
            return new Probe(10);
        }, -1));
        assertThrows(NullPointerException.class,
                () -> layout.addChild((Function<Layout, Probe>) null, Constraint.length(10)));
        assertThrows(NullPointerException.class,
                () -> list.addChild((Function<ScrollList, Probe>) null, 10));
        assertEquals(0, factoryCalls.get());
        assertTrue(layout.children().isEmpty());
        assertTrue(list.children().isEmpty());
    }

    @Test
    void mountedLayoutAppliesConstraintBeforeTheConfiguredChildFirstSubscribes() {
        Layout parent = new Layout(10, 20, 100, 80).Padding(5);
        Constraint constraint = Constraint.length(24);
        State<Integer> state = new State<>(0);
        List<Constraint> observedConstraints = new ArrayList<>();
        List<List<Integer>> observedSizes = new ArrayList<>();
        parent.mount();

        Probe child = parent.addChild(self -> {
            assertSame(parent, self);
            Probe result = new Probe(7);
            result.watch(state, value -> {
                assertEquals("ready", result.getMessage().getString());
                observedConstraints.add(self.getConstraint(result));
                observedSizes.add(List.of(result.getWidth(), result.getHeight()));
            });
            assertTrue(observedSizes.isEmpty());
            result.setMessage(Component.literal("ready"));
            return result;
        }, constraint);

        assertEquals(List.of(constraint), observedConstraints);
        assertEquals(List.of(List.of(90, 24)), observedSizes);
        assertSame(parent, child.getParent());
        assertTrue(child.isMounted());
        assertEquals(List.of(child), parent.children());
    }

    @Test
    void mountedScrollListAppliesRowHeightBeforeTheConfiguredChildFirstSubscribes() {
        ScrollList parent = new ScrollList(10, 20, 100, 40).Padding(5);
        State<Integer> state = new State<>(0);
        List<List<Integer>> observedSizes = new ArrayList<>();
        parent.mount();

        Probe child = parent.addChild(self -> {
            assertSame(parent, self);
            Probe result = new Probe(7);
            result.watch(state, value -> {
                assertEquals("ready", result.getMessage().getString());
                observedSizes.add(List.of(result.getWidth(), result.getHeight(), self.getScrollHeight()));
            });
            assertTrue(observedSizes.isEmpty());
            result.setMessage(Component.literal("ready"));
            return result;
        }, 65);

        assertEquals(List.of(List.of(80, 65, 65)), observedSizes);
        assertSame(parent, child.getParent());
        assertTrue(child.isMounted());
        assertEquals(List.of(child), parent.children());
        assertEquals(35, parent.getMaxScroll());
    }

    @Test
    void childAlreadyBoundByItsParentConstructorIsNotAddedOrSubscribedTwice() {
        Probe parent = new Probe(40);
        Probe first = parent.addChild(new Probe(5));
        State<Integer> state = new State<>(0);
        List<Integer> values = new ArrayList<>();
        parent.mount();

        Probe child = parent.addChild(self -> {
            Probe result = new Probe(self, 12);
            result.watch(state, values::add);
            assertTrue(result.isMounted());
            assertEquals(List.of(0), values);
            return result;
        });

        assertSame(parent, child.getParent());
        assertEquals(List.of(first, child), parent.children());
        assertEquals(List.of(0), values);
        state.set(1);
        assertEquals(List.of(0, 1), values);
        parent.unmount();
        state.set(2);
        assertEquals(List.of(0, 1), values);
    }

    private static final class Probe extends BaseWidget {
        Probe(int height) {
            super(0, 0, 10, height, Component.empty());
        }

        Probe(BaseWidget parent, int height) {
            super(parent, 0, 0, 10, height, Component.empty());
        }

        void watch(State<Integer> state, Consumer<Integer> observer) {
            subscribe(state, observer);
        }

        @Override
        protected void renderSelf(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {}
    }
}

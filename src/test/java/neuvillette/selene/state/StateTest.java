package neuvillette.selene.state;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class StateTest {
    @Test
    void subscriptionImmediatelyReceivesCurrentValueAndNullIsAValidValue() {
        State<String> state = new State<>(null);
        List<String> values = new ArrayList<>();
        assertNull(state.get());
        state.subscribe(values::add);
        assertEquals(Arrays.asList((String) null), values);

        state.set("ready");
        assertEquals("ready", state.get());
        state.set(null);
        assertNull(state.get());
        assertEquals(Arrays.asList(null, "ready", null), values);
    }

    @Test
    void equalValuesDoNotNotifyAndUpdateUsesTheCurrentValue() {
        State<String> state = new State<>("one");
        List<String> values = new ArrayList<>();
        state.subscribe(values::add);
        state.set(new String("one"));
        state.update(value -> new String(value));
        assertEquals(List.of("one"), values);

        state.update(value -> value + " two");
        assertEquals("one two", state.get());
        assertEquals(List.of("one", "one two"), values);
        state.update(value -> null);
        state.set(null);
        assertEquals(Arrays.asList("one", "one two", null), values);
    }

    @Test
    void notificationsRunSynchronouslyInSubscriptionOrder() {
        State<Integer> state = new State<>(0);
        List<String> events = new ArrayList<>();
        state.subscribe(value -> events.add("first:" + value));
        assertEquals(List.of("first:0"), events);
        state.subscribe(value -> events.add("second:" + value));
        assertEquals(List.of("first:0", "second:0"), events);

        events.clear();
        state.set(1);
        assertEquals(List.of("first:1", "second:1"), events);
    }

    @Test
    void duplicateConsumerSubscriptionsCanBeCancelledIndependently() {
        State<Integer> state = new State<>(0);
        List<Integer> values = new ArrayList<>();
        Consumer<Integer> listener = values::add;
        var first = state.subscribe(listener);
        var second = state.subscribe(listener);
        state.set(1);
        assertEquals(List.of(0, 0, 1, 1), values);

        first.unsubscribe();
        first.unsubscribe();
        first.close();
        state.set(2);
        assertEquals(List.of(0, 0, 1, 1, 2), values);
        second.close();
        second.close();
        second.unsubscribe();
        state.set(3);
        assertEquals(List.of(0, 0, 1, 1, 2), values);
        assertEquals(3, state.get());
    }

    @Test
    void cancelledListenerCanBeSubscribedAgainAsANewSubscription() {
        State<Integer> state = new State<>(0);
        List<Integer> values = new ArrayList<>();
        Consumer<Integer> listener = values::add;
        var old = state.subscribe(listener);
        old.close();
        state.set(1);
        var current = state.subscribe(listener);
        old.unsubscribe();
        state.set(2);
        assertEquals(List.of(0, 1, 2), values);
        current.close();
    }

    @Test
    void reentrantChangesUpdateGetImmediatelyButNotifyAfterTheCurrentRound() {
        State<Integer> state = new State<>(0);
        List<String> events = new ArrayList<>();
        state.subscribe(value -> {
            events.add("first:" + value);
            if (value == 1) {
                state.set(2);
                assertEquals(2, state.get());
                state.update(current -> current + 1);
                assertEquals(3, state.get());
            }
        });
        state.subscribe(value -> events.add("second:" + value + "/current:" + state.get()));
        events.clear();

        state.set(1);

        assertEquals(List.of("first:1", "second:1/current:3", "first:2", "second:2/current:3",
                "first:3", "second:3/current:3"), events);
        assertEquals(3, state.get());
    }

    @Test
    void queuedEqualUpdatesAreSkippedButReturningToAnEarlierValueStillNotifies() {
        State<Integer> state = new State<>(0);
        List<Integer> values = new ArrayList<>();
        state.subscribe(value -> {
            if (value == 1) {
                state.set(2);
                state.set(2);
                state.set(0);
                state.set(0);
            }
        });
        state.subscribe(values::add);
        values.clear();

        state.set(1);

        assertEquals(List.of(1, 2, 0), values);
        assertEquals(0, state.get());
    }

    @Test
    void aNewSubscriberGetsCurrentValueWithoutReplayingAlreadyQueuedChanges() {
        State<Integer> state = new State<>(0);
        List<String> events = new ArrayList<>();
        state.subscribe(value -> {
            events.add("first:" + value);
            if (value == 1) {
                state.set(2);
                state.subscribe(current -> events.add("new:" + current));
                state.set(3);
            }
        });
        state.subscribe(value -> events.add("second:" + value));
        events.clear();

        state.set(1);

        assertEquals(List.of("first:1", "new:2", "second:1", "first:2", "second:2",
                "first:3", "second:3", "new:3"), events);
    }

    @Test
    void cancellationInsideACallbackSkipsBothCurrentAndAlreadyQueuedSnapshots() {
        State<Integer> state = new State<>(0);
        List<String> events = new ArrayList<>();
        AtomicReference<Runnable> cancelSecond = new AtomicReference<>();
        state.subscribe(value -> {
            events.add("first:" + value);
            if (value == 1) {
                state.set(2);
                cancelSecond.get().run();
            }
        });
        var second = state.subscribe(value -> events.add("second:" + value));
        cancelSecond.set(second::unsubscribe);
        events.clear();

        state.set(1);

        assertEquals(List.of("first:1", "first:2"), events);
    }

    @Test
    void listenerCanCancelItselfAndOtherListenersStillReceiveQueuedChanges() {
        State<Integer> state = new State<>(0);
        List<String> events = new ArrayList<>();
        AtomicReference<Runnable> cancelSelf = new AtomicReference<>();
        var first = state.subscribe(value -> {
            events.add("first:" + value);
            if (value == 1) {
                cancelSelf.get().run();
                state.set(2);
            }
        });
        cancelSelf.set(first::close);
        state.subscribe(value -> events.add("second:" + value));
        events.clear();

        state.set(1);

        assertEquals(List.of("first:1", "second:1", "second:2"), events);
    }

    @Test
    void deepReentrantChangesAreDrainedWithoutGrowingTheCallStack() {
        State<Integer> state = new State<>(0);
        List<Integer> observed = new ArrayList<>();
        int limit = 20_000;
        state.subscribe(value -> {
            if (value > 0 && value < limit) {
                state.set(value + 1);
            }
        });
        state.subscribe(observed::add);

        assertDoesNotThrow(() -> state.set(1));

        assertEquals(limit, state.get());
        assertEquals(IntStream.rangeClosed(0, limit).boxed().toList(), observed);
    }

    @Test
    void callbackFailuresAreAggregatedAfterAllListenersAndQueuedChangesRun() {
        State<Integer> state = new State<>(0);
        List<String> events = new ArrayList<>();
        RuntimeException firstFailure = new IllegalStateException("first round, first listener");
        RuntimeException secondFailure = new IllegalArgumentException("first round, second listener");
        RuntimeException queuedFailure = new IllegalStateException("queued round");
        state.subscribe(value -> {
            events.add("first:" + value);
            if (value == 1) {
                state.set(2);
                throw firstFailure;
            }
            if (value == 2) {
                throw queuedFailure;
            }
        });
        state.subscribe(value -> {
            events.add("second:" + value);
            if (value == 1) {
                throw secondFailure;
            }
        });
        events.clear();

        RuntimeException thrown = assertThrows(RuntimeException.class, () -> state.set(1));

        assertSame(firstFailure, thrown);
        assertArrayEquals(new Throwable[] {secondFailure, queuedFailure}, thrown.getSuppressed());
        assertEquals(List.of("first:1", "second:1", "first:2", "second:2"), events);
        assertEquals(2, state.get());
        events.clear();
        assertDoesNotThrow(() -> state.set(3));
        assertEquals(List.of("first:3", "second:3"), events);
        assertEquals(3, state.get());
    }

    @Test
    void repeatedlyThrowingTheSameExceptionDoesNotCauseSelfSuppression() {
        State<Integer> state = new State<>(0);
        List<String> events = new ArrayList<>();
        RuntimeException failure = new IllegalStateException("shared failure instance");
        state.subscribe(value -> {
            events.add("first:" + value);
            if (value == 1) state.set(2);
            if (value > 0) throw failure;
        });
        state.subscribe(value -> {
            events.add("second:" + value);
            if (value > 0) throw failure;
        });
        events.clear();

        RuntimeException thrown = assertThrows(RuntimeException.class, () -> state.set(1));

        assertSame(failure, thrown);
        assertEquals(0, thrown.getSuppressed().length);
        assertEquals(List.of("first:1", "second:1", "first:2", "second:2"), events);
        assertEquals(2, state.get());
    }

    @Test
    void failingInitialNotificationAutomaticallyRemovesItsSubscription() {
        State<Integer> state = new State<>(0);
        AtomicInteger calls = new AtomicInteger();
        RuntimeException failure = new IllegalStateException("initial delivery");
        List<Integer> existing = new ArrayList<>();
        state.subscribe(existing::add);

        RuntimeException thrown = assertThrows(RuntimeException.class, () -> state.subscribe(value -> {
            calls.incrementAndGet();
            throw failure;
        }));

        assertSame(failure, thrown);
        assertDoesNotThrow(() -> state.set(1));
        assertEquals(1, calls.get());
        assertEquals(List.of(0, 1), existing);
    }

    @Test
    void initialFailureDuringDispatchAlsoRemovesSubscriberFromQueuedSnapshots() {
        State<Integer> state = new State<>(0);
        List<String> events = new ArrayList<>();
        RuntimeException failure = new IllegalStateException("new listener initial delivery");
        state.subscribe(value -> {
            events.add("first:" + value);
            if (value == 1) {
                state.subscribe(current -> {
                    events.add("new:" + current);
                    state.set(2);
                    throw failure;
                });
            }
        });
        state.subscribe(value -> events.add("second:" + value));
        events.clear();

        RuntimeException thrown = assertThrows(RuntimeException.class, () -> state.set(1));

        assertSame(failure, thrown);
        assertEquals(List.of("first:1", "new:1", "second:1", "first:2", "second:2"), events);
        assertEquals(2, state.get());
        events.clear();
        assertDoesNotThrow(() -> state.set(3));
        assertEquals(List.of("first:3", "second:3"), events);
    }

    @Test
    void throwingUpdateFunctionLeavesValueAndSubscriptionsUsable() {
        State<Integer> state = new State<>(10);
        List<Integer> values = new ArrayList<>();
        state.subscribe(values::add);
        RuntimeException failure = new IllegalArgumentException("update failed");

        RuntimeException thrown = assertThrows(RuntimeException.class, () -> state.update(value -> {
            throw failure;
        }));

        assertSame(failure, thrown);
        assertEquals(10, state.get());
        assertEquals(List.of(10), values);
        state.update(value -> value + 1);
        assertEquals(List.of(10, 11), values);
    }

    @Test
    void changesDuringInitialDeliveryWaitUntilThatCallbackReturns() {
        State<Integer> state = new State<>(0);
        List<String> events = new ArrayList<>();
        state.subscribe(value -> events.add("existing:" + value));

        state.subscribe(value -> {
            events.add("start:" + value);
            if (value == 0) state.set(1);
            events.add("end:" + value);
        });

        assertEquals(List.of("existing:0", "start:0", "end:0", "existing:1", "start:1", "end:1"), events);
        assertEquals(1, state.get());
    }

    @Test
    void subscriptionIsCancelledIfAnotherListenerPreventsItsHandleFromBeingReturned() {
        State<Integer> state = new State<>(0);
        RuntimeException failure = new IllegalStateException("existing listener failed");
        List<Integer> newListenerValues = new ArrayList<>();
        state.subscribe(value -> {
            if (value == 1) throw failure;
        });

        assertSame(failure, assertThrows(RuntimeException.class, () -> state.subscribe(value -> {
            newListenerValues.add(value);
            if (value == 0) state.set(1);
        })));

        assertEquals(List.of(0, 1), newListenerValues);
        assertDoesNotThrow(() -> state.set(2));
        assertEquals(List.of(0, 1), newListenerValues, "No listener may remain without a returned cancellation handle");
    }

    @Test
    void fatalCallbackErrorDropsPendingNotificationsAndRestoresDispatch() {
        State<Integer> state = new State<>(0);
        AssertionError failure = new AssertionError("fatal callback");
        List<Integer> values = new ArrayList<>();
        state.subscribe(value -> {
            if (value == 1) {
                state.set(2);
                throw failure;
            }
        });
        state.subscribe(values::add);

        assertSame(failure, assertThrows(AssertionError.class, () -> state.set(1)));
        assertEquals(2, state.get());
        assertEquals(List.of(0), values);
        assertDoesNotThrow(() -> state.set(3));
        assertEquals(List.of(0, 3), values, "Aborted notifications must not be replayed by later updates");
    }

    @Test
    void nullCallbacksAreRejectedWithoutChangingTheState() {
        State<Integer> state = new State<>(1);
        List<Integer> values = new ArrayList<>();
        state.subscribe(values::add);

        assertThrows(NullPointerException.class, () -> state.subscribe(null));
        assertThrows(NullPointerException.class, () -> state.update(null));
        assertEquals(1, state.get());
        state.set(2);
        assertEquals(List.of(1, 2), values);
    }
}

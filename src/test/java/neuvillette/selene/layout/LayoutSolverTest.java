package neuvillette.selene.layout;

import static neuvillette.selene.layout.Constraint.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class LayoutSolverTest {
    @Test
    void combinesFixedPercentageAndMinimumRequestsLikeTheReferenceExample() {
        assertSizes(100, Flex.START, List.of(length(10), percentage(70), min(5)), 10, 70, 20);
        assertSizes(100, Flex.START, List.of(length(70), percentage(80), ratio(1, 1)), 70, 30, 0);
    }

    @Test
    void minimumTargetsTakePriorityAndSamePriorityShortagesRemainProportional() {
        assertSizes(100, Flex.START, List.of(min(80), length(50), percentage(50)), 80, 20, 0);
        assertSizes(100, Flex.START, List.of(min(200), min(100)), 67, 33);
        assertSizes(100, Flex.START, List.of(length(1000), length(100)), 91, 9);
        assertSizes(50, Flex.START, List.of(ratio(2, 1), ratio(1, 1)), 33, 17);
    }

    @Test
    void fillWeightsShareTheRemainderBeforeMinimumsExpand() {
        assertSizes(100, Flex.START, List.of(min(5), fill(1)), 5, 95);
        assertSizes(100, Flex.START, List.of(min(5), min(5)), 50, 50);
        assertSizes(10, Flex.START, List.of(fill(1), fill(2)), 3, 7);
        assertSizes(5, Flex.START, List.of(fill(1), fill(1)), 3, 2);
        assertSizes(100, Flex.START, List.of(fill(0), fill(1)), 0, 100);
        assertSizes(100, Flex.LEGACY, List.of(fill(0), fill(0)), 0, 0);
        assertSizes(100, Flex.LEGACY, List.of(length(10), fill(0)), 100, 0);
    }

    @Test
    void maximumsUseSpaceAfterExplicitRequestsAndNeverExceedTheirCap() {
        assertSizes(100, Flex.START, List.of(max(60), length(70), fill(1)), 30, 70, 0);
        assertSizes(100, Flex.START, List.of(max(10), max(20), fill(1)), 10, 20, 70);
        assertSizes(100, Flex.LEGACY, List.of(max(10), max(20)), 10, 20);
        assertSizes(100, Flex.LEGACY, List.of(length(10), max(20)), 80, 20);
    }

    @Test
    void percentageAndRatioFractionsRoundCollectivelyWithoutLosingPixels() {
        assertSizes(50, Flex.START, List.of(percentage(75)), 38);
        assertSizes(5, Flex.START, List.of(percentage(50), percentage(50)), 3, 2);
        assertSizes(50, Flex.START,
                List.of(ratio(1, 4), ratio(1, 4), ratio(1, 4), ratio(1, 4)), 13, 13, 12, 12);
        assertSizes(50, Flex.START, List.of(ratio(1, 3), ratio(1, 3), ratio(1, 3)), 17, 17, 16);
        assertSizes(100, Flex.START, List.of(length(20), percentage(50), fill(1)), 20, 50, 30);
        assertSizes(100, Flex.START, List.of(length(20), ratio(1, 2), fill(1)), 20, 50, 30);
        assertSpans(100, 5, Flex.START, List.of(percentage(50), percentage(50)), 0, 48, 53, 47);
    }

    @Test
    void allFlexModesPlaceExtraSpaceWhilePreservingExplicitSpacing() {
        List<Constraint> fixed = List.of(length(10), length(10));
        assertSpans(100, 4, Flex.LEGACY, fixed, 0, 10, 14, 86);
        assertSpans(100, 4, Flex.START, fixed, 0, 10, 14, 10);
        assertSpans(100, 4, Flex.END, fixed, 76, 10, 90, 10);
        assertSpans(100, 4, Flex.CENTER, fixed, 38, 10, 52, 10);
        assertSpans(100, 4, Flex.SPACE_BETWEEN, fixed, 0, 10, 90, 10);
        assertSpans(100, 4, Flex.SPACE_AROUND, fixed, 19, 10, 71, 10);
    }

    @Test
    void alignmentHandlesOddRemaindersAndSingleChildren() {
        assertSpans(13, 0, Flex.SPACE_AROUND, List.of(length(5), length(5)), 1, 5, 7, 5);
        assertSpans(11, 0, Flex.CENTER, List.of(length(4)), 3, 4);
        assertSpans(11, 0, Flex.SPACE_BETWEEN, List.of(length(4)), 0, 4);
        assertSpans(12, 0, Flex.SPACE_AROUND, List.of(length(4)), 4, 4);
    }

    @Test
    void oversizedSpacingIsCompressedInsideTheAxis() {
        assertSpans(3, Integer.MAX_VALUE, Flex.START,
                List.of(fill(1), fill(1), fill(1)), 0, 0, 2, 0, 3, 0);
        assertSpans(0, Integer.MAX_VALUE, Flex.END,
                List.of(min(5), length(6), fill(1)), 0, 0, 0, 0, 0, 0);
        assertSpans(10, Integer.MAX_VALUE, Flex.START, List.of(fill(1)), 0, 10);
        assertTrue(LayoutSolver.split(100, List.of(), Integer.MAX_VALUE, Flex.START).isEmpty());
    }

    @Test
    void largeWeightsRatiosAndLengthsDoNotOverflow() {
        assertSizes(Integer.MAX_VALUE, Flex.START,
                List.of(fill(Integer.MAX_VALUE), fill(Integer.MAX_VALUE)), 1073741824, 1073741823);
        assertSizes(Integer.MAX_VALUE, Flex.START,
                List.of(ratio(Integer.MAX_VALUE, 1), ratio(Integer.MAX_VALUE, 1)), 1073741824, 1073741823);
        assertSizes(Integer.MAX_VALUE, Flex.START,
                List.of(length(Integer.MAX_VALUE), length(Integer.MAX_VALUE)), 1073741824, 1073741823);
    }

    @Test
    void rejectsInvalidConstraintValuesAndLayoutArguments() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class, () -> length(-1)),
                () -> assertThrows(IllegalArgumentException.class, () -> min(-1)),
                () -> assertThrows(IllegalArgumentException.class, () -> max(-1)),
                () -> assertThrows(IllegalArgumentException.class, () -> fill(-1)),
                () -> assertThrows(IllegalArgumentException.class, () -> percentage(-1)),
                () -> assertThrows(IllegalArgumentException.class, () -> percentage(101)),
                () -> assertThrows(IllegalArgumentException.class, () -> ratio(-1, 1)),
                () -> assertThrows(IllegalArgumentException.class, () -> ratio(1, 0)),
                () -> assertThrows(IllegalArgumentException.class, () -> ratio(1, -1)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> LayoutSolver.split(-1, List.of(), 0, Flex.START)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> LayoutSolver.split(1, List.of(), -1, Flex.START)),
                () -> assertThrows(NullPointerException.class,
                        () -> LayoutSolver.split(1, null, 0, Flex.START)),
                () -> assertThrows(NullPointerException.class,
                        () -> LayoutSolver.split(1, List.of(), 0, null)),
                () -> assertThrows(NullPointerException.class,
                        () -> LayoutSolver.split(1, Arrays.asList((Constraint) null), 0, Flex.START)));
    }

    @Test
    void returnedSpansAreImmutable() {
        List<LayoutSolver.Span> spans = LayoutSolver.split(10, List.of(fill(1)), 0, Flex.START);
        assertThrows(UnsupportedOperationException.class, () -> spans.add(new LayoutSolver.Span(0, 0)));
        assertThrows(UnsupportedOperationException.class, () -> spans.set(0, new LayoutSolver.Span(0, 0)));
        assertThrows(IllegalArgumentException.class, () -> new LayoutSolver.Span(-1, 0));
        assertThrows(IllegalArgumentException.class, () -> new LayoutSolver.Span(0, -1));
    }

    @Test
    void seededMixedRequestsAlwaysRemainOrderedBoundedAndDeterministic() {
        Random random = new Random(20261022);
        for (int iteration = 0; iteration < 250; iteration++) {
            int axis = random.nextBoolean() ? random.nextInt(1000) : random.nextInt(Integer.MAX_VALUE);
            int gap = random.nextInt(5) == 0 ? Integer.MAX_VALUE : random.nextInt(20);
            Flex flex = Flex.values()[iteration % Flex.values().length];
            List<Constraint> constraints = new ArrayList<>();
            for (int n = random.nextInt(12); n > 0; n--) {
                int value = random.nextBoolean() ? random.nextInt(1000) : random.nextInt(Integer.MAX_VALUE);
                constraints.add(switch (random.nextInt(6)) {
                    case 0 -> min(value);
                    case 1 -> max(value);
                    case 2 -> length(value);
                    case 3 -> percentage(random.nextInt(101));
                    case 4 -> ratio(value, 1 + random.nextInt(Integer.MAX_VALUE - 1));
                    default -> fill(random.nextBoolean() ? 0 : value);
                });
            }
            String context = "case " + iteration + ": " + axis + ", " + flex + ", " + constraints;
            List<LayoutSolver.Span> spans = LayoutSolver.split(axis, constraints, gap, flex);
            assertEquals(constraints.size(), spans.size(), context);
            long previousEnd = 0;
            for (int i = 0; i < spans.size(); i++) {
                LayoutSolver.Span span = spans.get(i);
                assertTrue(span.offset() >= previousEnd, "Ordered, nonoverlapping spans: " + context);
                assertTrue(span.length() >= 0, "Nonnegative sizes: " + context);
                previousEnd = (long) span.offset() + span.length();
                assertTrue(previousEnd <= axis, "Endpoint inside axis: " + context);
                Constraint constraint = constraints.get(i);
                if (constraint.kind() == Constraint.Kind.MAX) {
                    assertTrue(span.length() <= constraint.value(), "Max remains a cap: " + context);
                }
                if (constraint.kind() == Constraint.Kind.FILL && constraint.value() == 0) {
                    assertEquals(0, span.length(), "Zero Fill stays empty: " + context);
                }
            }
            assertEquals(spans, LayoutSolver.split(axis, constraints, gap, flex), context);
        }
    }

    private static void assertSizes(int axis, Flex flex, List<Constraint> constraints, int... expected) {
        int[] actual = LayoutSolver.split(axis, constraints, 0, flex).stream()
                .mapToInt(LayoutSolver.Span::length).toArray();
        assertArrayEquals(expected, actual, constraints.toString());
    }

    private static void assertSpans(int axis, int spacing, Flex flex, List<Constraint> constraints,
                                    int... expected) {
        List<LayoutSolver.Span> spans = new ArrayList<>();
        for (int i = 0; i < expected.length; i += 2) {
            spans.add(new LayoutSolver.Span(expected[i], expected[i + 1]));
        }
        assertEquals(spans, LayoutSolver.split(axis, constraints, spacing, flex), flex.toString());
    }
}

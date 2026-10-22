package neuvillette.selene.layout;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Deterministic integer layout inspired by Ratatui, without a Cassowary dependency.
 * Explicit spacing is reserved first and compressed evenly if it exceeds the axis.
 * Minimum requests are satisfied before Length, Percentage, then Ratio requests.
 * Max is always an upper bound, and its target is filled only after those requests;
 * unlike a solver strength, it does not preempt another child's fixed size.
 * Equal-priority requests are proportionally reduced when space is insufficient.
 *
 * <p>Positive Fill weights share all remaining space; if none exist, Min children
 * share it equally. Otherwise Flex places the remaining space. LEGACY grows the
 * last eligible child, even a fixed-size child, while respecting Max and Fill(0).
 * Percentage/ratio targets retain fractions: their group total is rounded to the
 * nearest pixel before allocation. Weighted allocations use largest remainders,
 * breaking ties in child order. These explicit
 * rules intentionally do not reproduce every Cassowary conflict or rounding result.
 */
public final class LayoutSolver {
    private LayoutSolver() {
    }

    /** A child's nonnegative offset and size within the supplied axis. */
    public record Span(int offset, int length) {
        public Span {
            if (offset < 0 || length < 0) {
                throw new IllegalArgumentException("Span offset and length must be nonnegative");
            }
        }
    }

    /**
     * Split an axis into nonoverlapping spans in child order. Results are immutable.
     * All offsets and endpoints stay within {@code length}, even for oversized
     * requests or spacing. Both {@code length} and {@code spacing} must be nonnegative.
     */
    public static List<Span> split(int length, List<Constraint> constraints, int spacing, Flex flex) {
        if (length < 0 || spacing < 0) {
            throw new IllegalArgumentException("Layout length and spacing must be nonnegative");
        }
        Objects.requireNonNull(flex, "flex");
        List<Constraint> requests = List.copyOf(constraints);
        int count = requests.size();
        if (count == 0) {
            return List.of();
        }

        int gapCount = count - 1;
        int gapTotal = (int) Math.min(length, (long) spacing * gapCount);
        int[] gaps = evenShares(gapTotal, gapCount);
        int[] sizes = new int[count];
        int remaining = length - gapTotal;

        for (Constraint.Kind kind : new Constraint.Kind[]{Constraint.Kind.MIN,
                Constraint.Kind.LENGTH, Constraint.Kind.PERCENTAGE, Constraint.Kind.RATIO,
                Constraint.Kind.MAX}) {
            if (kind == Constraint.Kind.PERCENTAGE || kind == Constraint.Kind.RATIO) {
                remaining -= allocateFractions(remaining, length, requests, kind, sizes);
                continue;
            }
            int[] targets = new int[count];
            for (int i = 0; i < count; i++) {
                Constraint request = requests.get(i);
                if (request.kind() == kind) {
                    targets[i] = request.value();
                }
            }
            remaining -= allocateTargets(remaining, targets, sizes);
        }

        int[] weights = new int[count];
        boolean hasFill = false;
        for (int i = 0; i < count; i++) {
            Constraint request = requests.get(i);
            if (request.kind() == Constraint.Kind.FILL && request.value() > 0) {
                weights[i] = request.value();
                hasFill = true;
            }
        }
        if (!hasFill) {
            for (int i = 0; i < count; i++) {
                if (requests.get(i).kind() == Constraint.Kind.MIN) {
                    weights[i] = 1;
                }
            }
        }
        remaining -= distribute(remaining, weights, sizes);

        int leading = 0;
        if (remaining > 0) {
            switch (flex) {
                case LEGACY -> {
                    for (int i = count - 1; i >= 0 && remaining > 0; i--) {
                        Constraint request = requests.get(i);
                        if (request.kind() == Constraint.Kind.FILL && request.value() == 0) {
                            continue;
                        }
                        int capacity = request.kind() == Constraint.Kind.MAX
                                ? request.value() - sizes[i] : remaining;
                        int addition = Math.min(remaining, capacity);
                        sizes[i] += addition;
                        remaining -= addition;
                    }
                }
                case START -> { }
                case END -> leading = remaining;
                case CENTER -> leading = remaining / 2;
                case SPACE_BETWEEN -> {
                    int[] additions = evenShares(remaining, gapCount);
                    for (int i = 0; i < gapCount; i++) {
                        gaps[i] += additions[i];
                    }
                }
                case SPACE_AROUND -> {
                    int[] aroundWeights = new int[count + 1];
                    Arrays.fill(aroundWeights, 2);
                    aroundWeights[0] = 1;
                    aroundWeights[count] = 1;
                    int[] spaces = new int[count + 1];
                    distribute(remaining, aroundWeights, spaces);
                    leading = spaces[0];
                    for (int i = 0; i < gapCount; i++) {
                        gaps[i] += spaces[i + 1];
                    }
                }
            }
        }

        List<Span> spans = new ArrayList<>(count);
        int cursor = leading;
        for (int i = 0; i < count; i++) {
            spans.add(new Span(cursor, sizes[i]));
            cursor += sizes[i];
            if (i < gapCount) {
                cursor += gaps[i];
            }
        }
        return List.copyOf(spans);
    }

    private static int allocateFractions(int available, int length, List<Constraint> requests,
                                         Constraint.Kind kind, int[] sizes) {
        if (available == 0 || length == 0) {
            return 0;
        }
        double[] targets = new double[requests.size()];
        double targetTotal = 0;
        double compensation = 0;
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < requests.size(); i++) {
            Constraint request = requests.get(i);
            if (request.kind() == kind && request.value() > 0) {
                targets[i] = (long) length * request.value() / (double) request.denominator();
                // Compensated summation avoids losing small targets beside large ratios.
                double adjusted = targets[i] - compensation;
                double sum = targetTotal + adjusted;
                compensation = (sum - targetTotal) - adjusted;
                targetTotal = sum;
                order.add(i);
            }
        }
        int total = (int) Math.min(available, Math.round(targetTotal));
        if (total == 0) {
            return 0;
        }
        double[] remainders = new double[requests.size()];
        int allocated = 0;
        for (int i : order) {
            double quota = targets[i] / targetTotal * total;
            int share = (int) Math.min(total - allocated, Math.floor(quota));
            sizes[i] += share;
            allocated += share;
            remainders[i] = quota - Math.floor(quota);
        }
        order.sort(Comparator.<Integer>comparingDouble(i -> remainders[i]).reversed()
                .thenComparingInt(i -> i));
        int remainder = total - allocated;
        // Normally fewer than order.size() pixels remain. Bulk shares also bound
        // this work by child count if floating-point roundoff accumulates.
        int extra = remainder / order.size();
        int extraCount = remainder % order.size();
        for (int i = 0; i < order.size(); i++) {
            sizes[order.get(i)] += extra + (i < extraCount ? 1 : 0);
        }
        return total;
    }

    private static int allocateTargets(int available, int[] targets, int[] sizes) {
        long total = 0;
        for (int target : targets) {
            total += target;
        }
        if (total > available) {
            return distribute(available, targets, sizes);
        }
        for (int i = 0; i < sizes.length; i++) {
            sizes[i] += targets[i];
        }
        return (int) total;
    }

    private static int[] evenShares(int total, int count) {
        int[] shares = new int[count];
        if (count > 0) {
            int base = total / count;
            int remainder = total % count;
            for (int i = 0; i < count; i++) {
                shares[i] = base + (i < remainder ? 1 : 0);
            }
        }
        return shares;
    }

    /** Add an exact weighted allocation in O(n log n), independent of pixel count. */
    private static int distribute(int total, int[] weights, int[] result) {
        long weightTotal = 0;
        for (int weight : weights) {
            weightTotal += weight;
        }
        if (total == 0 || weightTotal == 0) {
            return 0;
        }
        long[] remainders = new long[weights.length];
        List<Integer> order = new ArrayList<>();
        int allocated = 0;
        for (int i = 0; i < weights.length; i++) {
            long product = (long) total * weights[i];
            int share = (int) (product / weightTotal);
            result[i] += share;
            allocated += share;
            remainders[i] = product % weightTotal;
            if (weights[i] > 0) {
                order.add(i);
            }
        }
        order.sort(Comparator.<Integer>comparingLong(i -> remainders[i]).reversed()
                .thenComparingInt(i -> i));
        int remainder = total - allocated;
        for (int i = 0; i < remainder; i++) {
            result[order.get(i)]++;
        }
        return total;
    }
}

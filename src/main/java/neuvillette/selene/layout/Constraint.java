package neuvillette.selene.layout;

import java.util.Objects;

/**
 * Immutable size request for one child along the layout's main axis.
 * Percentage and ratio refer to the entire parent content axis, including spacing.
 * See {@link LayoutSolver} for behavior when requests compete for limited space.
 */
public final class Constraint {
    public enum Kind {
        MIN, MAX, LENGTH, PERCENTAGE, RATIO, FILL
    }

    private final Kind kind;
    private final int value;
    private final int denominator;

    private Constraint(Kind kind, int value, int denominator) {
        if (value < 0) {
            throw new IllegalArgumentException("Constraint value must be nonnegative");
        }
        this.kind = kind;
        this.value = value;
        this.denominator = denominator;
    }

    /** Request a fixed number of pixels, subject to available space. */
    public static Constraint length(int length) {
        return new Constraint(Kind.LENGTH, length, 1);
    }

    /** Request 0 through 100 percent of the parent's content axis. */
    public static Constraint percentage(int percentage) {
        if (percentage > 100) {
            throw new IllegalArgumentException("Percentage must be between 0 and 100");
        }
        return new Constraint(Kind.PERCENTAGE, percentage, 100);
    }

    /** Request a fraction of the parent's content axis; the denominator must be positive. */
    public static Constraint ratio(int numerator, int denominator) {
        if (denominator <= 0) {
            throw new IllegalArgumentException("Ratio denominator must be positive");
        }
        return new Constraint(Kind.RATIO, numerator, denominator);
    }

    /** Request a minimum, then share unused space if no positive Fill is present. */
    public static Constraint min(int minimum) {
        return new Constraint(Kind.MIN, minimum, 1);
    }

    /** Use space remaining after explicit size requests, up to this hard upper bound. */
    public static Constraint max(int maximum) {
        return new Constraint(Kind.MAX, maximum, 1);
    }

    /** Share remaining space by weight; a weight of zero receives no space. */
    public static Constraint fill(int weight) {
        return new Constraint(Kind.FILL, weight, 1);
    }

    public Kind kind() {
        return kind;
    }

    public int value() {
        return value;
    }

    public int denominator() {
        return denominator;
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof Constraint constraint
                && kind == constraint.kind && value == constraint.value
                && denominator == constraint.denominator;
    }

    @Override
    public int hashCode() {
        return Objects.hash(kind, value, denominator);
    }

    @Override
    public String toString() {
        return kind + "(" + value + (kind == Kind.RATIO ? "/" + denominator : "") + ")";
    }
}

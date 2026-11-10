/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.semantics.expression;

public class RangeEvaluation implements ExpressionEvaluation {
    // the bounds of an int range lie within int, so these markers cannot collide with a bound
    public static final long INFINITY = Long.MAX_VALUE;
    public static final long NEGATIVE_INFINITY = Long.MIN_VALUE;

    public static final RangeEvaluation UNBOUNDED = new RangeEvaluation(NEGATIVE_INFINITY, INFINITY);
    public static final RangeEvaluation NONE = new RangeEvaluation(0, 0);
    public static final RangeEvaluation ONE = new RangeEvaluation(1, 1);
    public static final RangeEvaluation OPTIONAL = new RangeEvaluation(0, 1);
    public static final RangeEvaluation SOME = new RangeEvaluation(0, INFINITY);
    public static final RangeEvaluation MANY = new RangeEvaluation(1, INFINITY);

    private final long lowerBound;
    private final long upperBound;

    protected RangeEvaluation(long lowerBound, long upperBound) {
        this.lowerBound = lowerBound;
        this.upperBound = upperBound;
    }

    public long getLowerBound() {
        return lowerBound;
    }

    public long getUpperBound() {
        return upperBound;
    }

    public boolean isBounded() {
        return lowerBound != NEGATIVE_INFINITY && upperBound != INFINITY;
    }

    public static RangeEvaluation of(ExpressionEvaluation lowerBound, ExpressionEvaluation upperBound, boolean exclusive) {
        long leftValue;
        if (lowerBound instanceof IntegerEvaluation(int lowerValue)) {
            leftValue = lowerValue;
        } else if (lowerBound instanceof InfinityEvaluation) {
            leftValue = NEGATIVE_INFINITY;
        } else {
            throw new IllegalArgumentException("Lower bound is not of a legal type!");
        }

        long rightValue;
        if (upperBound instanceof IntegerEvaluation(int upperValue)) {
            rightValue = exclusive ? (long) upperValue - 1 : upperValue;
        } else if (upperBound instanceof InfinityEvaluation) {
            rightValue = INFINITY;
        } else {
            throw new IllegalArgumentException("Upper bound is not of a legal type!");
        }

        return of(leftValue, rightValue);
    }

    public static RangeEvaluation of(long count) {
        return of(count, count);
    }

    public static RangeEvaluation of(long lowerBound, long upperBound) {
        for (var known : new RangeEvaluation[] {UNBOUNDED, NONE, ONE, OPTIONAL, SOME, MANY}) {
            if (lowerBound == known.lowerBound && upperBound == known.upperBound) {
                return known;
            }
        }

        return new RangeEvaluation(lowerBound, upperBound);
    }
}

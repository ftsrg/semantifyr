/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.semantics.expression;

public class RangeEvaluation implements ExpressionEvaluation {
    public static final int INFINITY = -1;

    public static final RangeEvaluation UNBOUNDED = new RangeEvaluation(INFINITY, INFINITY);
    public static final RangeEvaluation NONE = new RangeEvaluation(0, 0);
    public static final RangeEvaluation ONE = new RangeEvaluation(1, 1);
    public static final RangeEvaluation OPTIONAL = new RangeEvaluation(0, 1);
    public static final RangeEvaluation SOME = new RangeEvaluation(0, INFINITY);
    public static final RangeEvaluation MANY = new RangeEvaluation(1, INFINITY);

    private final int lowerBound;
    private final int upperBound;

    protected RangeEvaluation(int lowerBound, int upperBound) {
        this.lowerBound = lowerBound;
        this.upperBound = upperBound;
    }

    public int getLowerBound() {
        return lowerBound;
    }

    public int getUpperBound() {
        return upperBound;
    }

    public static RangeEvaluation of(ExpressionEvaluation lowerBound, ExpressionEvaluation upperBound) {
        int leftValue;
        if (lowerBound instanceof IntegerEvaluation(int lowerValue)) {
            leftValue = lowerValue;
        } else if (lowerBound instanceof InfinityEvaluation) {
            leftValue = INFINITY;
        } else {
            throw new IllegalArgumentException("Lower bound is not of a legal type!");
        }

        int rightValue;
        if (upperBound instanceof IntegerEvaluation(int upperValue)) {
            rightValue = upperValue;
        } else if (upperBound instanceof InfinityEvaluation) {
            rightValue = INFINITY;
        } else {
            throw new IllegalArgumentException("Lower bound is not of a legal type!");
        }

        return new RangeEvaluation(leftValue, rightValue);
    }

    // TODO: should this be protected?
    public static RangeEvaluation of(int lowerBound, int upperBound) {
        if (lowerBound == UNBOUNDED.getLowerBound() && upperBound == UNBOUNDED.getUpperBound()) {
            return UNBOUNDED;
        }
        if (lowerBound == NONE.getLowerBound() && upperBound == NONE.getUpperBound()) {
            return NONE;
        }
        if (lowerBound == ONE.getLowerBound() && upperBound == ONE.getUpperBound()) {
            return ONE;
        }
        if (lowerBound == OPTIONAL.getLowerBound() && upperBound == OPTIONAL.getUpperBound()) {
            return OPTIONAL;
        }
        if (lowerBound == SOME.getLowerBound() && upperBound == SOME.getUpperBound()) {
            return SOME;
        }
        if (lowerBound == MANY.getLowerBound() && upperBound == MANY.getUpperBound()) {
            return MANY;
        }

        return new RangeEvaluation(lowerBound, upperBound);
    }

}

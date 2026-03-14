/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.utils;

import hu.bme.mit.semantifyr.oxsts.lang.semantics.expression.*;

public abstract class ExpressionEvaluationVisitor<T> {

    protected T visit(ExpressionEvaluation evaluation) {
        return switch (evaluation) {
            case ArrayEvaluation arrayEvaluation -> visit(arrayEvaluation);
            case BooleanEvaluation booleanEvaluation -> visit(booleanEvaluation);
            case EnumLiteralEvaluation enumLiteralEvaluation -> visit(enumLiteralEvaluation);
            case InfinityEvaluation infinityEvaluation -> visit(infinityEvaluation);
            case IntegerEvaluation integerEvaluation -> visit(integerEvaluation);
            case NothingEvaluation nothingEvaluation -> visit(nothingEvaluation);
            case RangeEvaluation rangeEvaluation -> visit(rangeEvaluation);
            case RealEvaluation realEvaluation -> visit(realEvaluation);
            case StringEvaluation stringEvaluation -> visit(stringEvaluation);
            default -> throw new IllegalStateException("Unexpected value: " + evaluation);
        };
    }

    protected abstract T visit(ArrayEvaluation evaluation);
    protected abstract T visit(BooleanEvaluation evaluation);
    protected abstract T visit(EnumLiteralEvaluation evaluation);
    protected abstract T visit(InfinityEvaluation evaluation);
    protected abstract T visit(IntegerEvaluation evaluation);
    protected abstract T visit(NothingEvaluation evaluation);
    protected abstract T visit(RangeEvaluation evaluation);
    protected abstract T visit(RealEvaluation evaluation);
    protected abstract T visit(StringEvaluation evaluation);

}

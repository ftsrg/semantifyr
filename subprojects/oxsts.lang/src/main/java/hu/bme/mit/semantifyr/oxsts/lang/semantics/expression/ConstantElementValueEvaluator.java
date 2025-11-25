/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.semantics.expression;

import hu.bme.mit.semantifyr.oxsts.model.oxsts.Element;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.EnumLiteral;

public class ConstantElementValueEvaluator extends ElementValueEvaluator<ExpressionEvaluation> {

    @Override
    protected ExpressionEvaluation visit(Element element) {
        //noinspection SwitchStatementWithTooFewBranches
        return switch (element) {
            case EnumLiteral enumLiteral -> new EnumLiteralEvaluation(enumLiteral);
            default -> throw new IllegalArgumentException("Unsupported type of element!");
        };
    }

}

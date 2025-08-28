/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.semantics.expression;

import java.util.List;

public record ArrayEvaluation(List<ExpressionEvaluation> elements) implements ExpressionEvaluation {

}

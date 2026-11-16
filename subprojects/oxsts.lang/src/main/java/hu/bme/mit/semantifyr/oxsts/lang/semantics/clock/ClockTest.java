/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.semantics.clock;

import hu.bme.mit.semantifyr.oxsts.model.oxsts.ComparisonOp;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ComparisonOperator;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Expression;
import java.util.List;

// the operator reads with the clock on its left; clockLeaves is null when a branch of the clock side holds no clock
public record ClockTest(
        ComparisonOperator comparison,
        Expression clock,
        List<Expression> clockLeaves,
        Expression bound,
        ComparisonOp operator) {}

/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.expression

import hu.bme.mit.semantifyr.compiler.pipeline.instantiation.Instance
import hu.bme.mit.semantifyr.oxsts.lang.semantics.expression.ExpressionEvaluation

class InstanceEvaluation(
    val instances: Set<Instance>,
) : ExpressionEvaluation {
    constructor(instance: Instance) : this(setOf(instance))
}

/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.optimization.patterns.expression

import hu.bme.mit.semantifyr.compiler.pipeline.optimization.CompositeOptimizationPattern
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.OptimizationPattern

class NegationNormalizationPatterns : CompositeOptimizationPattern() {

    override val patterns: Collection<OptimizationPattern> = listOf(
        DoubleNegationPattern(),
        DeMorganPattern(),
        BubbleNotEFPattern(),
        BubbleNotAGPattern(),
    )

}

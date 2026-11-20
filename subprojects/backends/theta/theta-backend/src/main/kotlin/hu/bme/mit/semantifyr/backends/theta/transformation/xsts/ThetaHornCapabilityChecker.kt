/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.theta.transformation.xsts

import com.google.inject.Inject
import hu.bme.mit.semantifyr.backend.UnsupportedConstruct
import hu.bme.mit.semantifyr.backend.transformation.WholeDelayLowering
import hu.bme.mit.semantifyr.compiler.pipeline.context.TransitionCallTraceMap
import hu.bme.mit.semantifyr.oxsts.lang.semantics.clock.ClockTestAnalysis
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts

// whole delays under-approximate a model with a clock test that is not closed, and a run is never over- and under-approximating at once
class ThetaHornCapabilityChecker @Inject constructor(
    thetaDomainTransformer: ThetaDomainTransformer,
    wholeDelayLowering: WholeDelayLowering,
    private val clockTestAnalysis: ClockTestAnalysis,
) : ThetaCapabilityChecker(thetaDomainTransformer, wholeDelayLowering) {

    override fun checkModel(inlinedOxsts: InlinedOxsts, transitionCallTraces: TransitionCallTraceMap): List<UnsupportedConstruct> {
        val unclosedTests = clockTestAnalysis.unclosedTests(inlinedOxsts.mainTransition).map {
            UnsupportedConstruct(it, "clock tests that are not closed")
        }
        return super.checkModel(inlinedOxsts, transitionCallTraces) + unclosedTests
    }
}

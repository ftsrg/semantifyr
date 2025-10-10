/*
 * SPDX-FileCopyrightText: 2023-2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.theta.verification.transformation.xsts

import com.google.inject.Inject
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import hu.bme.mit.semantifyr.oxsts.model.oxsts.TransitionDeclaration
import hu.bme.mit.semantifyr.semantics.transformation.injection.scope.CompilationScoped

@CompilationScoped
class ChoiceElseRewriter {

    @Inject
    private lateinit var operationChoiceElseRewriter: OperationChoiceElseRewriter

    fun rewriteChoiceElse(inlinedOxsts: InlinedOxsts) {
        rewriteChoiceElse(inlinedOxsts.initTransition)
        rewriteChoiceElse(inlinedOxsts.mainTransition)
    }

    private fun rewriteChoiceElse(transitionDeclaration: TransitionDeclaration) {
        for (branch in transitionDeclaration.branches) {
            operationChoiceElseRewriter.rewriteChoiceElse(branch)
        }
    }

}

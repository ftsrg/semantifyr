/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.semantics.verification

import com.google.inject.Inject
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ClassDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import hu.bme.mit.semantifyr.semantics.transformation.OxstsClassInliner
import hu.bme.mit.semantifyr.semantics.transformation.ProgressContext

data class VerificationCaseRunResult(
    val result: VerificationResult,
    val message: String? = null
)

enum class VerificationResult {
    Passed, Failed
}

interface OxstsVerifier {

    fun verify(progressContext: ProgressContext, classDeclaration: ClassDeclaration): VerificationCaseRunResult

}

abstract class AbstractOxstsVerifier : OxstsVerifier {

    @Inject
    private lateinit var oxstsClassInliner: OxstsClassInliner

    open fun inlineClass(progressContext: ProgressContext, classDeclaration: ClassDeclaration): InlinedOxsts {
        return oxstsClassInliner.inline(progressContext, classDeclaration)
    }

}

/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.semantics.verification

import hu.bme.mit.semantifyr.oxsts.model.oxsts.ClassDeclaration
import hu.bme.mit.semantifyr.semantics.transformation.ProgressContext

interface OxstsVerifier {

    fun verify(progressContext: ProgressContext, classDeclaration: ClassDeclaration)

}

abstract class AbstractOxstsVerifier : OxstsVerifier {

}

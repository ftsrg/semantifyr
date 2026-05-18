/*
 * SPDX-FileCopyrightText: 2023-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.utils

import hu.bme.mit.semantifyr.oxsts.lang.semantics.expression.EvaluationFailureException
import hu.bme.mit.semantifyr.oxsts.lang.utils.SourceLocation
import org.eclipse.emf.ecore.EObject
import org.eclipse.xtext.EcoreUtil2

fun <T : EObject> T.copy(): T {
    return EcoreUtil2.copy(this)
}

inline fun <reified T : EObject> EObject.eAllOfType(): Sequence<T> {
    return EcoreUtil2.eAll(this).asSequence().filterIsInstance<T>()
}

fun sourceError(eObject: EObject, message: String): Nothing {
    throw IllegalStateException("${SourceLocation.prefixFor(eObject)} $message")
}

fun evaluationFailure(eObject: EObject, message: String): Nothing {
    throw EvaluationFailureException.at(eObject, message)
}

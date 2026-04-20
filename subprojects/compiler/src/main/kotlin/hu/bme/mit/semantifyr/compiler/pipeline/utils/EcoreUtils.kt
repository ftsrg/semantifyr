/*
 * SPDX-FileCopyrightText: 2023-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.utils

import org.eclipse.emf.ecore.EObject
import org.eclipse.xtext.EcoreUtil2
import org.eclipse.xtext.nodemodel.util.NodeModelUtils

fun <T : EObject> T.copy(): T {
    return EcoreUtil2.copy(this)
}

inline fun <reified T : EObject> EObject.eAllOfType(): Sequence<T> {
    return EcoreUtil2.eAll(this).asSequence().filterIsInstance<T>()
}

fun sourceLocationPrefix(eObject: EObject): String {
    val node = NodeModelUtils.findActualNodeFor(eObject)
    val resource = eObject.eResource()
    val fileName = resource?.uri?.lastSegment()
    if (node == null && fileName == null) {
        return ""
    }
    val file = fileName ?: "<unknown>"
    val line = node?.startLine?.toString() ?: "?"
    return "$file:$line: "
}

fun sourceError(eObject: EObject, message: String): Nothing {
    error("${sourceLocationPrefix(eObject)}$message")
}

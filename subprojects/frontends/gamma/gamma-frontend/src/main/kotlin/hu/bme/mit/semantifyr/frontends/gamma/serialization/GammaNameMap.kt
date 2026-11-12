/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.frontends.gamma.serialization

import org.eclipse.emf.ecore.EObject

// library members and OXSTS keywords never start with an underscore, so no marked name equals one
fun markName(name: String): String {
    return "_$name"
}

fun markQualifiedName(qualifiedName: String): String {
    return qualifiedName.split("::").joinToString("::") {
        markName(it)
    }
}

class GammaNameMap {
    private val generatedNames = mutableMapOf<EObject, String>()
    private val members = mutableMapOf<EObject, MutableMap<String, EObject>>()
    private val types = mutableMapOf<EObject, EObject>()

    fun declare(
        scope: EObject,
        element: EObject,
        name: String,
    ): String {
        val scopeMembers = members.getOrPut(scope) {
            LinkedHashMap()
        }
        val declared = scopeMembers.putIfAbsent(name, element)
        check(declared == null || declared == element) {
            "Two Gamma elements are emitted as '$name' in one OXSTS scope"
        }
        return name
    }

    fun declareTyped(
        scope: EObject,
        element: EObject,
        name: String,
        type: EObject,
    ): String {
        types[element] = type
        return declare(scope, element, name)
    }

    fun declareGenerated(
        scope: EObject,
        element: EObject,
        derivedName: String,
    ): String {
        val name = generatedNames.getOrPut(element) {
            val taken = members[scope].orEmpty()
            var candidate = derivedName
            var number = 2
            while (candidate in taken) {
                candidate = "${derivedName}_$number"
                number++
            }
            candidate
        }
        return declare(scope, element, name)
    }

    fun resolveOrNull(
        root: EObject,
        names: List<String>,
    ): List<EObject>? {
        val elements = mutableListOf<EObject>()
        var current = root
        for (name in names) {
            current = members[types[current] ?: current]?.get(name) ?: return null
            elements += current
        }
        return elements
    }
}

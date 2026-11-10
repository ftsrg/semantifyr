/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.frontends.gamma.serialization

import org.eclipse.emf.ecore.EObject

class GammaNameStore {
    private val generatedNames = mutableMapOf<EObject, String>()
    private val members = mutableMapOf<EObject, MutableMap<String, EObject>>()
    private val types = mutableMapOf<EObject, EObject>()

    fun declare(
        scope: EObject,
        element: EObject,
        name: String,
    ): String {
        members.getOrPut(scope) {
            LinkedHashMap()
        }[name] = element
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

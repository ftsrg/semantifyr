/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.frontends.gamma.semantics.serialization

import org.eclipse.emf.ecore.EObject
import kotlin.reflect.KProperty

class EObjectNameProvider(
    private val prefix: String
) {
    private var number = 1
    private val nameMap = mutableMapOf<EObject, String>()

    fun getName(eObject: EObject) = nameMap.getOrPut(eObject) {
        prefix + number++
    }

    operator fun getValue(eObject: EObject, property: KProperty<*>) = getName(eObject)
}

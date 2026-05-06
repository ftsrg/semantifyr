/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.frontends.gamma.serialization

import org.eclipse.emf.ecore.EObject
import kotlin.reflect.KProperty

class EObjectNameProvider(
    private val prefix: String,
) {
    private var number = 1
    private val nameMap = mutableMapOf<EObject, String>()

    fun getName(eObject: EObject): String {
        return nameMap.getOrPut(eObject) {
            "$prefix${number++}"
        }
    }

    operator fun getValue(
        eObject: EObject,
        property: KProperty<*>,
    ): String {
        return getName(eObject)
    }
}

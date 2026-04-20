/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.optimization

import com.google.inject.Inject
import java.lang.reflect.Modifier

/**
 * Walks every `@Inject`-annotated field on [target] (including inherited ones)
 * and asserts the Xtext injection extension actually populated each one. A
 * missing Guice binding otherwise manifests as an
 * `UninitializedPropertyAccessException` at the first use site, which hides
 * the real cause. This check reports "Unbound dependency: <field>" at setUp
 * time instead.
 *
 * Uses plain Java reflection so the check does not require `kotlin-reflect`
 * on the test classpath.
 */
fun verifyInjectedDependenciesAreBound(target: Any) {
    val targetClass = target::class.java
    var current: Class<*>? = targetClass
    while (current != null && current != Any::class.java) {
        for (field in current.declaredFields) {
            if (Modifier.isStatic(field.modifiers)) continue
            if (!field.isAnnotationPresent(Inject::class.java)) continue
            field.isAccessible = true
            val value = field.get(target)
            check(value != null) {
                "Unbound dependency: ${targetClass.simpleName}.${field.name} was not set by the injector. " +
                    "Check that ${field.type.simpleName} has a Guice binding in the test module."
            }
        }
        current = current.superclass
    }
}

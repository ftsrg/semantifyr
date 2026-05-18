/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline.instantiation

import com.google.inject.Inject
import hu.bme.mit.semantifyr.compiler.pipeline.utils.treeSequence
import hu.bme.mit.semantifyr.compiler.scopes.CompilationScoped
import hu.bme.mit.semantifyr.oxsts.lang.semantics.InheritanceHandler
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ClassDeclaration

@CompilationScoped
class InstanceCollector @Inject constructor(
    private val inheritanceHandler: InheritanceHandler,
) {

    private val cache = mutableMapOf<Pair<Instance, ClassDeclaration>, Set<Instance>>()

    fun instancesOfType(instance: Instance, classDeclaration: ClassDeclaration): Set<Instance> {
        return cache.getOrPut(Pair(instance, classDeclaration)) {
            collectInstancesOfType(instance, classDeclaration)
        }
    }

    private fun collectInstancesOfType(instance: Instance, classDeclaration: ClassDeclaration): Set<Instance> {
        return instance.treeSequence().filter {
            inheritanceHandler.getTransitiveSuperDomains(it.domain).any {
                it == classDeclaration
            }
        }.toSet()
    }

}

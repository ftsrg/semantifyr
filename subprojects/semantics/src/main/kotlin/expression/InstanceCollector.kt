/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.semantics.expression

import com.google.inject.Inject
import hu.bme.mit.semantifyr.oxsts.lang.semantics.InheritanceHandler
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ClassDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Instance
import hu.bme.mit.semantifyr.semantics.transformation.injection.scope.CompilationScoped
import hu.bme.mit.semantifyr.semantics.utils.treeSequence

@CompilationScoped
class InstanceCollector {

    private val cache = mutableMapOf<Pair<Instance, ClassDeclaration>, Set<Instance>>()

    // TODO: will be replaced by a query-based collector (Refinery)

    @Inject
    private lateinit var inheritanceHandler: InheritanceHandler

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

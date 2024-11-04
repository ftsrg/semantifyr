/*
 * SPDX-FileCopyrightText: 2023-2024 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.semantifyr.transformation.pattern

import hu.bme.mit.semantifyr.oxsts.model.oxsts.Instance
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Pattern
import hu.bme.mit.semantifyr.oxsts.semantifyr.utils.fullyQualifiedName
import org.eclipse.emf.ecore.resource.ResourceSet
import org.eclipse.viatra.query.runtime.api.ViatraQueryEngine
import org.eclipse.viatra.query.runtime.emf.EMFScope

class PatternRunner(
    resourceSet: ResourceSet
) {

    private val engine = ViatraQueryEngine.on(EMFScope(resourceSet))

    fun execute(instance: Instance, pattern: Pattern): List<Instance> {
        val query = PatternTransformer.transform(pattern)

        check(query.parameters.size == 2) {
            "Derived feature must be set by a pattern with two parameters!"
        }

        val matcher = engine.getMatcher(query)
        val template = matcher.newMatch(instance, null)
        val matches = matcher.getAllMatches(template)

        return matches.map {
            it.get(1) as Instance
        }.toSet().toList().sortedBy {
            it.fullyQualifiedName
        }
    }

}

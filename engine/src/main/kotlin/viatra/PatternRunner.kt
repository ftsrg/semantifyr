package hu.bme.mit.gamma.oxsts.engine.viatra

import hu.bme.mit.gamma.oxsts.model.oxsts.Instance
import hu.bme.mit.gamma.oxsts.model.oxsts.Pattern
import org.eclipse.emf.ecore.resource.ResourceSet
import org.eclipse.viatra.query.runtime.api.ViatraQueryEngine
import org.eclipse.viatra.query.runtime.emf.EMFScope

class PatternRunner(
    resourceSet: ResourceSet
) {

    private val engine = ViatraQueryEngine.on(EMFScope(resourceSet))

    fun execute(instance: Instance, pattern: Pattern): List<Instance> {
        val query = ViatraTransformer.transform(pattern)

        check(query.parameters.size == 2) {
            "Derived feature must be set by a pattern with two parameters!"
        }

        val matcher = engine.getMatcher(query)
        val template = matcher.newMatch(instance, null)
        val matches = matcher.getAllMatches(template)

        return matches.map {
            it.get(1) as Instance
        }.toSet().toList()
    }

}

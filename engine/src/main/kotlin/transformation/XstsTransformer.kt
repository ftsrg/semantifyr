package hu.bme.mit.gamma.oxsts.engine.transformation

import hu.bme.mit.gamma.oxsts.model.oxsts.CompositeOperation
import hu.bme.mit.gamma.oxsts.model.oxsts.Enum
import hu.bme.mit.gamma.oxsts.model.oxsts.InlineOperation
import hu.bme.mit.gamma.oxsts.model.oxsts.Target
import hu.bme.mit.gamma.oxsts.model.oxsts.Transition
import hu.bme.mit.gamma.oxsts.model.oxsts.VariableTypeReference
import hu.bme.mit.gamma.oxsts.model.oxsts.XSTS
import org.eclipse.xtext.EcoreUtil2
import java.util.LinkedList

class XstsTransformer {
    fun transform(target: Target): XSTS {
        val instantiator = Instantiator()
        val rootInstance = instantiator.instantiate(target)

        target.init.inlineOperations(rootInstance)
        target.transition.inlineOperations(rootInstance)

        val xsts = OxstsFactory.createXSTS()

        val enums = target.variables.map {
            it.typing
        }.filterIsInstance<VariableTypeReference>().map {
            it.reference
        }.filterIsInstance<Enum>()

        xsts.enums.addAll(enums.copy())
        xsts.variables.addAll(target.variables.copy())
        xsts.init = target.init.copy()
        xsts.transition = target.transition.copy()
        xsts.property = target.property.copy()

        return xsts
    }

    fun Transition.inlineOperations(rootInstance: InstanceObject) {
        val processorQueue = LinkedList(operation)

        while (processorQueue.any()) {
            val operation = processorQueue.removeFirst()

            when (operation) {
                is InlineOperation -> {
                    val inlined = rootInstance.operationEvaluator.inlineTransition(operation)
                    EcoreUtil2.replace(operation, inlined)
                    processorQueue.add(inlined)
                }
                is CompositeOperation -> {
                    processorQueue.addAll(operation.operation)
                }
            }
        }
    }

}

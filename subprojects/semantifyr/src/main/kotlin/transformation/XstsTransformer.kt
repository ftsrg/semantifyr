/*
 * SPDX-FileCopyrightText: 2023-2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.semantifyr.transformation

import hu.bme.mit.semantifyr.oxsts.model.oxsts.Enum
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ReferenceTyping
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Target
import hu.bme.mit.semantifyr.oxsts.model.oxsts.XSTS
import hu.bme.mit.semantifyr.oxsts.semantifyr.reader.OxstsReader
import hu.bme.mit.semantifyr.oxsts.semantifyr.transformation.instantiation.Instantiator
import hu.bme.mit.semantifyr.oxsts.semantifyr.transformation.optimization.OperationOptimizer.optimize
import hu.bme.mit.semantifyr.oxsts.semantifyr.transformation.pattern.ConstraintChecker
import hu.bme.mit.semantifyr.oxsts.semantifyr.transformation.rewrite.ChoiceElseRewriter.rewriteChoiceElse
import hu.bme.mit.semantifyr.oxsts.semantifyr.transformation.rewrite.ExpressionRewriter.rewriteReferences
import hu.bme.mit.semantifyr.oxsts.semantifyr.transformation.rewrite.PropertyAccessInliner.inlinePropertyAccesses
import hu.bme.mit.semantifyr.oxsts.semantifyr.transformation.rewrite.inlineOperations
import hu.bme.mit.semantifyr.oxsts.semantifyr.utils.OxstsFactory
import hu.bme.mit.semantifyr.oxsts.semantifyr.utils.contextualEvaluator
import hu.bme.mit.semantifyr.oxsts.semantifyr.utils.copy
import hu.bme.mit.semantifyr.oxsts.semantifyr.utils.findInitTransition
import hu.bme.mit.semantifyr.oxsts.semantifyr.utils.findMainTransition
import hu.bme.mit.semantifyr.oxsts.semantifyr.utils.findProperty
import hu.bme.mit.semantifyr.oxsts.semantifyr.utils.loggerFactory
import hu.bme.mit.semantifyr.oxsts.semantifyr.utils.referencedElementOrNull
import hu.bme.mit.semantifyr.oxsts.semantifyr.utils.type

class XstsTransformer(
    val reader: OxstsReader
) {
    val logger by loggerFactory()

    fun transform(typeName: String, rewriteChoice: Boolean = false): XSTS {
        val type = reader.rootElements.flatMap { it.types }.filterIsInstance<Target>().first {
            it.name == typeName
        }

        return transform(type, rewriteChoice)
    }

    fun transform(target: Target, rewriteChoice: Boolean = false): XSTS {
        val xsts = OxstsFactory.createXSTS()

        logger.info("Instantiating target ${target.name}")

        val rootInstance = Instantiator.instantiateTree(target)

        DefaultArtifactManager.instancePersistor.persist(rootInstance)

        ConstraintChecker.checkConstraints(rootInstance)

        val variables = Instantiator.instantiateVariablesTree(rootInstance)

        logger.info("Transforming transitions")

        val init = rootInstance.contextualEvaluator.evaluateTransition(OxstsFactory.createChainReferenceExpression(rootInstance.type.findInitTransition()))
        val tran = rootInstance.contextualEvaluator.evaluateTransition(OxstsFactory.createChainReferenceExpression(rootInstance.type.findMainTransition()))
        val property = target.findProperty()

        xsts.init = init.copy() // FIXME: consider non-existent tran as empty tran
        xsts.transition = tran.copy() // FIXME: consider non-existent tran as empty tran
        xsts.property = property.copy() // FIXME: consider non-existent prop as empty prop

        xsts.init.inlineOperations(rootInstance)
        xsts.transition.inlineOperations(rootInstance)

        logger.info("Rewriting properties")

        xsts.init.inlinePropertyAccesses(rootInstance)
        xsts.transition.inlinePropertyAccesses(rootInstance)
        xsts.property.inlinePropertyAccesses(rootInstance)

        logger.info("Rewriting operations")

        xsts.variables += variables
        xsts.rewriteReferences(rootInstance)

        xsts.enums += xsts.variables.asSequence().map {
            it.typing
        }.filterIsInstance<ReferenceTyping>().map {
            it.referencedElementOrNull()
        }.filterIsInstance<Enum>().toSet()

        DefaultArtifactManager.intermediateXstsPersistor.persist(xsts)

        logger.info("Optimizing XSTS model")

        xsts.optimize()

        if (rewriteChoice) {
            logger.info("Rewriting choice-else operations")

            xsts.init.rewriteChoiceElse()
            xsts.transition.rewriteChoiceElse()

            DefaultArtifactManager.intermediateXstsPersistor.persist(xsts)

            logger.info("Optimizing final XSTS model")

            xsts.optimize()
        }

        logger.info("Transformation done!")

        return xsts
    }

    private fun XSTS.optimize() {
        init.optimize()
        transition.optimize()
        property.optimize()
    }

}

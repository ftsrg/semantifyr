/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.theta.verification.transformation.xsts

import com.google.inject.Inject
import hu.bme.mit.semantifyr.oxsts.model.oxsts.EnumDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.GuardOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import hu.bme.mit.semantifyr.oxsts.model.oxsts.PropertyDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.TransitionDeclaration
import hu.bme.mit.semantifyr.semantics.optimization.InlinedOxstsOperationOptimizer
import hu.bme.mit.semantifyr.semantics.transformation.injection.scope.CompilationScoped
import hu.bme.mit.semantifyr.semantics.utils.OxstsFactory
import hu.bme.mit.semantifyr.semantics.utils.eAllOfType
import hu.bme.mit.semantifyr.xsts.lang.XstsStandaloneSetup
import hu.bme.mit.semantifyr.xsts.lang.xsts.XstsModel
import org.eclipse.emf.common.util.URI
import org.eclipse.xtext.EcoreUtil2

private typealias XstsTransition = hu.bme.mit.semantifyr.xsts.lang.xsts.Transition
private typealias XstsProperty = hu.bme.mit.semantifyr.xsts.lang.xsts.Property
private typealias XstsSequenceOperation = hu.bme.mit.semantifyr.xsts.lang.xsts.SequenceOperation

@CompilationScoped
class OxstsTransformer {

    @Inject
    private lateinit var choiceElseRewriter: ChoiceElseRewriter

    @Inject
    private lateinit var oxstsOperationTransformer: OxstsOperationTransformer

    @Inject
    private lateinit var oxstsExpressionTransformer: OxstsExpressionTransformer

    @Inject
    private lateinit var oxstsDomainTransformer: OxstsDomainTransformer

    @Inject
    private lateinit var oxstsVariableTransformer: OxstsVariableTransformer

    @Inject
    private lateinit var inlinedOxstsOperationOptimizer: InlinedOxstsOperationOptimizer

    fun transform(inlinedOxsts: InlinedOxsts, rewriteChoice: Boolean): XstsModel {
        if (rewriteChoice) {
            choiceElseRewriter.rewriteChoiceElse(inlinedOxsts)

            val guardOperations = inlinedOxsts.eAllOfType<GuardOperation>().toList()

            for (guardOperation in guardOperations) {
                val assumptionOperation = OxstsFactory.createAssumptionOperation(guardOperation.expression)
                EcoreUtil2.replace(guardOperation, assumptionOperation)
            }

            inlinedOxstsOperationOptimizer.optimize(inlinedOxsts)
        }

        return createXsts(inlinedOxsts)
    }

    private fun createEmptyXsts(inlinedOxsts: InlinedOxsts): XstsModel {
        XstsStandaloneSetup.doSetup();

        val resourceSet = inlinedOxsts.eResource().resourceSet
        val path = inlinedOxsts.eResource().uri.toString().replace(".oxsts", ".xsts")
        val uri = URI.createURI(path)

        val xstsModel = XstsFactory.createXstsModel()

        resourceSet.getResource(uri, false)?.delete(mutableMapOf<Any, Any>())
        resourceSet.createResource(uri).contents += xstsModel

        return xstsModel
    }

    private fun createXsts(inlinedOxsts: InlinedOxsts): XstsModel {
        val xsts = createEmptyXsts(inlinedOxsts)

        for (variableDeclaration in inlinedOxsts.variables) {
            xsts.variableDeclarations += oxstsVariableTransformer.transformTopLevel(variableDeclaration)
        }

        xsts.env = XstsFactory.createTransition().also {
            it.branches += XstsFactory.createSequenceOperation()
        }
        xsts.tran = transform(inlinedOxsts.mainTransition)
        xsts.init = transform(inlinedOxsts.initTransition)
        xsts.property = transform(inlinedOxsts.property)

        xsts.enumDeclarations += inlinedOxsts.variables.asSequence().map {
            it.type
        }.filterIsInstance<EnumDeclaration>().map {
            oxstsDomainTransformer.transform(it)
        }.distinct()

        return xsts
    }

    private fun transform(transitionDeclaration: TransitionDeclaration): XstsTransition {
        return XstsFactory.createTransition().also {
            for (branch in transitionDeclaration.branches) {
                it.branches += oxstsOperationTransformer.transform(branch) as XstsSequenceOperation
            }
        }
    }

    private fun transform(propertyDeclaration: PropertyDeclaration): XstsProperty {
        return XstsFactory.createProperty().also {
            it.invariant = oxstsExpressionTransformer.transform(propertyDeclaration.expression)
        }
    }

}

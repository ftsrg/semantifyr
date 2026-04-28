/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.theta.transformation.xsts

import com.google.inject.Inject
import hu.bme.mit.semantifyr.backend.scopes.VerificationScoped
import hu.bme.mit.semantifyr.backends.theta.artifacts.ThetaArtifactManager
import hu.bme.mit.semantifyr.oxsts.model.oxsts.EnumDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import hu.bme.mit.semantifyr.oxsts.model.oxsts.PropertyDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.TransitionDeclaration
import hu.bme.mit.semantifyr.xsts.lang.XstsStandaloneSetup
import hu.bme.mit.semantifyr.xsts.lang.xsts.Property
import hu.bme.mit.semantifyr.xsts.lang.xsts.SequenceOperation
import hu.bme.mit.semantifyr.xsts.lang.xsts.Transition
import hu.bme.mit.semantifyr.xsts.lang.xsts.XstsModel
import org.eclipse.xtext.resource.XtextResourceSet

private typealias XstsTransition = Transition
private typealias XstsProperty = Property
private typealias XstsSequenceOperation = SequenceOperation

@VerificationScoped
class OxstsTransformer {

    private val xstsInjector by lazy {
        XstsStandaloneSetup().createInjectorAndDoEMFRegistration()
    }

    private val resourceSetProvider by lazy {
        xstsInjector.getProvider(XtextResourceSet::class.java)
    }

    @Inject
    private lateinit var oxstsOperationTransformer: OxstsOperationTransformer

    @Inject
    private lateinit var oxstsExpressionTransformer: OxstsExpressionTransformer

    @Inject
    private lateinit var oxstsDomainTransformer: OxstsDomainTransformer

    @Inject
    private lateinit var oxstsVariableTransformer: OxstsVariableTransformer

    @Inject
    private lateinit var traceOperationTransformer: TraceOperationTransformer

    @Inject
    private lateinit var thetaArtifactManager: ThetaArtifactManager

    fun transform(inlinedOxsts: InlinedOxsts): XstsModel {
        val xsts = createEmptyXsts()

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
            it.typeSpecification.domain
        }.filterIsInstance<EnumDeclaration>().map {
            oxstsDomainTransformer.transform(it)
        }.distinct()

        traceOperationTransformer.finalizeTransformedTraceOperations(xsts)

        return xsts
    }

    private fun createEmptyXsts(): XstsModel {
        val resourceSet = resourceSetProvider.get()
        val resource = resourceSet.createResource(thetaArtifactManager.xstsUri)

        val xstsModel = XstsFactory.createXstsModel()
        resource.contents += xstsModel

        return xstsModel
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

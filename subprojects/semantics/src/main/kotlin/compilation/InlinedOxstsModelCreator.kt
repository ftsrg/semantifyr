/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.semantics.compilation

import com.google.inject.Inject
import hu.bme.mit.semantifyr.oxsts.lang.library.builtin.BuiltinSymbolResolver
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ClassDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.FeatureKind
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import hu.bme.mit.semantifyr.oxsts.model.oxsts.PropertyDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.TransitionDeclaration
import hu.bme.mit.semantifyr.semantics.artifact.ArtifactKind
import hu.bme.mit.semantifyr.semantics.artifact.ArtifactManager
import hu.bme.mit.semantifyr.semantics.compilation.instantiation.InstanceManager
import hu.bme.mit.semantifyr.semantics.compilation.expression.RedefinitionAwareReferenceResolver
import hu.bme.mit.semantifyr.semantics.scope.CompilationScoped
import hu.bme.mit.semantifyr.semantics.utils.OxstsFactory

@CompilationScoped
class InlinedOxstsModelCreator @Inject constructor(
    private val instanceManager: InstanceManager,
    private val builtinSymbolResolver: BuiltinSymbolResolver,
    private val redefinitionAwareReferenceResolver: RedefinitionAwareReferenceResolver,
    private val artifactManager: ArtifactManager,
) {

    fun createInlinedOxsts(classDeclaration: ClassDeclaration): InlinedOxsts {
        val resourceSet = classDeclaration.eResource().resourceSet
        val uri = artifactManager.resolveUri(ArtifactKindFiles.inlinedModel)

        val inlinedOxsts = OxstsFactory.createInlinedOxsts()
        inlinedOxsts.classDeclaration = classDeclaration

        resourceSet.createResource(uri).contents += inlinedOxsts

        initializeInlinedOxstsModel(inlinedOxsts)

        return inlinedOxsts
    }

    private fun initializeInlinedOxstsModel(inlinedOxsts: InlinedOxsts) {
        inlinedOxsts.rootFeature = OxstsFactory.createFeatureDeclaration().also {
            it.kind = FeatureKind.CONTAINMENT
            it.typeSpecification = OxstsFactory.createTypeSpecification().also {
                it.domain = inlinedOxsts.classDeclaration
            }
            it.name = "root"
        }

        inlinedOxsts.rootInstance = instanceManager.createInstance(inlinedOxsts)
        inlinedOxsts.initTransition = createTransitionDeclaration(inlinedOxsts, builtinSymbolResolver.anythingInitTransition(inlinedOxsts))
        inlinedOxsts.mainTransition = createTransitionDeclaration(inlinedOxsts, builtinSymbolResolver.anythingMainTransition(inlinedOxsts))
        inlinedOxsts.property = createPropertyDeclaration(inlinedOxsts)
    }

    private fun createTransitionDeclaration(inlinedOxsts: InlinedOxsts, transitionDeclaration: TransitionDeclaration): TransitionDeclaration {
        val transition = redefinitionAwareReferenceResolver.resolve(inlinedOxsts.rootFeature, transitionDeclaration)

        return OxstsFactory.createTransitionDeclaration().also {
            it.kind = transitionDeclaration.kind
            it.branches += OxstsFactory.createSequenceOperation().also {
                it.steps += OxstsFactory.createInlineCall().also {
                    it.callExpression = OxstsFactory.createCallSuffixExpression().also {
                        it.primary = OxstsFactory.createNavigationSuffixExpression().also {
                            it.primary = OxstsFactory.createElementReference().also {
                                it.element = inlinedOxsts.rootFeature
                            }
                            it.member = transition
                        }
                    }
                }
            }
        }
    }

    private fun createPropertyDeclaration(inlinedOxsts: InlinedOxsts): PropertyDeclaration {
        val property = redefinitionAwareReferenceResolver.resolveOrNull(inlinedOxsts.rootFeature, "prop") as? PropertyDeclaration

        if (property == null) {
            return OxstsFactory.createPropertyDeclaration().also {
                it.expression = OxstsFactory.createLiteralBoolean(true)
            }
        }

        return OxstsFactory.createPropertyDeclaration().also {
            it.expression = OxstsFactory.createCallSuffixExpression().also {
                it.primary = OxstsFactory.createNavigationSuffixExpression().also {
                    it.primary = OxstsFactory.createElementReference().also {
                        it.element = inlinedOxsts.rootFeature
                    }
                    it.member = property
                }
            }
        }
    }

}

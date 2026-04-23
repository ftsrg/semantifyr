/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline

import com.google.inject.Inject
import hu.bme.mit.semantifyr.compiler.pipeline.context.CreatedCompilationContext
import hu.bme.mit.semantifyr.oxsts.lang.library.builtin.BuiltinSymbolResolver
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ClassDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.FeatureKind
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import hu.bme.mit.semantifyr.oxsts.model.oxsts.PropertyDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.TransitionDeclaration
import hu.bme.mit.semantifyr.compiler.pipeline.artifact.ArtifactKind
import hu.bme.mit.semantifyr.compiler.pipeline.artifact.ArtifactManager
import hu.bme.mit.semantifyr.compiler.pipeline.utils.sourceError
import hu.bme.mit.semantifyr.compiler.pipeline.expression.RedefinitionAwareReferenceResolver
import hu.bme.mit.semantifyr.compiler.pipeline.utils.OxstsFactory

class InlinedOxstsModelCreator @Inject constructor(
    private val builtinSymbolResolver: BuiltinSymbolResolver,
    private val redefinitionAwareReferenceResolver: RedefinitionAwareReferenceResolver,
    private val artifactManager: ArtifactManager,
) {

    fun create(classDeclaration: ClassDeclaration): CreatedCompilationContext {
        val resourceSet = classDeclaration.eResource().resourceSet
        val uri = artifactManager.resolveUri(ArtifactKind.OutputModel)

        val inlinedOxsts = OxstsFactory.createInlinedOxsts()
        inlinedOxsts.classDeclaration = classDeclaration

        resourceSet.createResource(uri).contents += inlinedOxsts

        initializeInlinedOxstsModel(inlinedOxsts)

        return CreatedCompilationContext(inlinedOxsts)
    }

    private fun initializeInlinedOxstsModel(inlinedOxsts: InlinedOxsts) {
        inlinedOxsts.rootFeature = OxstsFactory.createFeatureDeclaration().also {
            it.kind = FeatureKind.CONTAINMENT
            it.typeSpecification = OxstsFactory.createTypeSpecification().also {
                it.domain = inlinedOxsts.classDeclaration
            }
            it.name = "root"
        }

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
        val resolved = redefinitionAwareReferenceResolver.resolveOrNull(inlinedOxsts.rootFeature, "prop")
        if (resolved != null && resolved !is PropertyDeclaration) {
            sourceError(
                resolved,
                "Expected an element named 'prop' to be a property declaration, found ${resolved::class.simpleName}. Verification cases identify their property by the name 'prop'.",
            )
        }

        if (resolved == null) {
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
                    it.member = resolved
                }
            }
        }
    }

}

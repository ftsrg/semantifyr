/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler.pipeline

import com.google.inject.Inject
import hu.bme.mit.semantifyr.compiler.pipeline.context.CreatedCompilationContext
import hu.bme.mit.semantifyr.compiler.pipeline.expression.RedefinitionAwareReferenceResolver
import hu.bme.mit.semantifyr.compiler.pipeline.instantiation.InstanceNames
import hu.bme.mit.semantifyr.compiler.pipeline.utils.OxstsFactory
import hu.bme.mit.semantifyr.oxsts.lang.library.builtin.BuiltinSymbolResolver
import hu.bme.mit.semantifyr.oxsts.lang.utils.SelfContainedResource
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ClassDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.FeatureKind
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import hu.bme.mit.semantifyr.oxsts.model.oxsts.PropertyDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.TransitionDeclaration
import org.eclipse.emf.common.util.URI
import org.eclipse.xtext.resource.FileExtensionProvider
import java.nio.file.Path

class InlinedOxstsModelCreator @Inject constructor(
    private val builtinSymbolResolver: BuiltinSymbolResolver,
    private val redefinitionAwareReferenceResolver: RedefinitionAwareReferenceResolver,
    private val fileExtensionProvider: FileExtensionProvider,
) {

    fun create(classDeclaration: ClassDeclaration, outputDirectory: Path): CreatedCompilationContext {
        val resourceSet = classDeclaration.eResource().resourceSet
        val uri = URI.createFileURI(outputDirectory.resolve("inlined.${fileExtensionProvider.primaryFileExtension}").toAbsolutePath().toString())

        val inlinedOxsts = OxstsFactory.createInlinedOxsts()
        inlinedOxsts.classDeclaration = classDeclaration

        val resource = resourceSet.createResource(uri)
        // an inlined model exports no name and nothing imports it, so only its own cache entries can go stale
        SelfContainedResource.mark(resource)
        resource.contents += inlinedOxsts

        initializeInlinedOxstsModel(inlinedOxsts)

        return CreatedCompilationContext(inlinedOxsts)
    }

    private fun initializeInlinedOxstsModel(inlinedOxsts: InlinedOxsts) {
        inlinedOxsts.rootFeature = OxstsFactory.createFeatureDeclaration().also {
            it.kind = FeatureKind.CONTAINMENT
            it.typeSpecification = OxstsFactory.createTypeSpecification().also {
                it.domain = inlinedOxsts.classDeclaration
            }
            it.name = InstanceNames.ROOT_FEATURE_NAME
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
        val property = redefinitionAwareReferenceResolver.resolve(inlinedOxsts.rootFeature, builtinSymbolResolver.anythingProperty(inlinedOxsts))

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

/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.semantics.transformation

import com.google.inject.Inject
import hu.bme.mit.semantifyr.oxsts.lang.library.builtin.BuiltinSymbolResolver
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ClassDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Expression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.FeatureKind
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import hu.bme.mit.semantifyr.oxsts.model.oxsts.PropertyDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.TransitionDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.TransitionKind
import hu.bme.mit.semantifyr.semantics.expression.RedefinitionAwareReferenceResolver
import hu.bme.mit.semantifyr.semantics.transformation.injection.scope.CompilationScoped
import hu.bme.mit.semantifyr.semantics.transformation.instantiation.InstanceManager
import hu.bme.mit.semantifyr.semantics.utils.OxstsFactory
import org.eclipse.emf.common.util.URI
import java.io.File

@CompilationScoped
class InlinedOxstsModelManager {

    @Inject
    private lateinit var instanceManager: InstanceManager

    @Inject
    private lateinit var builtinSymbolResolver: BuiltinSymbolResolver

    @Inject
    private lateinit var redefinitionAwareReferenceResolver: RedefinitionAwareReferenceResolver

    fun createInlinedOxsts(classDeclaration: ClassDeclaration): InlinedOxsts {
        val resourceSet = classDeclaration.eResource().resourceSet
        val path = classDeclaration.eResource().uri.toString().replace(".oxsts", "${File.separator}${classDeclaration.name}${File.separator}inlined.oxsts")
        val uri = URI.createURI(path)

        val inlinedOxsts = OxstsFactory.createInlinedOxsts()
        inlinedOxsts.classDeclaration = classDeclaration

        resourceSet.getResource(uri, false)?.delete(mutableMapOf<Any, Any>())
        resourceSet.createResource(uri).contents += inlinedOxsts

        initializeInlinedOxstsModel(inlinedOxsts)

        return inlinedOxsts
    }

    private fun initializeInlinedOxstsModel(inlinedOxsts: InlinedOxsts) {
        inlinedOxsts.rootFeature = OxstsFactory.createFeatureDeclaration().also {
            it.kind = FeatureKind.CONTAINMENT
            it.type = inlinedOxsts.classDeclaration
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

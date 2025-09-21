/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.semantics.transformation

import com.google.inject.Inject
import com.google.inject.Singleton
import hu.bme.mit.semantifyr.oxsts.lang.library.builtin.BuiltinSymbolResolver
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ClassDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.FeatureKind
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InstanceModel
import hu.bme.mit.semantifyr.oxsts.model.oxsts.PropertyDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.TransitionDeclaration
import hu.bme.mit.semantifyr.semantics.expression.MetaStaticExpressionEvaluatorProvider
import hu.bme.mit.semantifyr.semantics.expression.RedefinitionAwareReferenceResolver
import hu.bme.mit.semantifyr.semantics.expression.evaluateTyped
import hu.bme.mit.semantifyr.semantics.transformation.instantiation.InstanceManager
import hu.bme.mit.semantifyr.semantics.utils.OxstsFactory
import hu.bme.mit.semantifyr.semantics.utils.copy
import org.eclipse.emf.common.util.URI
import org.eclipse.emf.ecore.resource.Resource

@Singleton
class InlinedOxstsModelManager {

    @Inject
    lateinit var builtinSymbolResolver: BuiltinSymbolResolver

    @Inject
    lateinit var metaStaticExpressionEvaluatorProvider: MetaStaticExpressionEvaluatorProvider

    @Inject
    private lateinit var instanceManager: InstanceManager

    @Inject
    private lateinit var redefinitionAwareReferenceResolver: RedefinitionAwareReferenceResolver

    private val resourceMap = mutableMapOf<InlinedOxsts, Resource>()
    private var resourceId = 0

    private fun createInstanceModel(classDeclaration: ClassDeclaration): InstanceModel {
        val rootFeature = OxstsFactory.createFeatureDeclaration().also {
            it.kind = FeatureKind.CONTAINMENT
            it.type = classDeclaration
            it.name = classDeclaration.name
        }
        val instanceModel = OxstsFactory.createInstanceModel().also {
            it.rootFeature = rootFeature
        }

        return instanceModel
    }

    private fun createInlinedOxsts(classDeclaration: ClassDeclaration): InlinedOxsts {
        val resourceSet = classDeclaration.eResource().resourceSet
        val path = classDeclaration.eResource().uri.toString().replace(".oxsts", "_${classDeclaration.name}.oxsts")

        val resource = resourceSet.createResource(URI.createURI(path))
        val inlinedOxsts = OxstsFactory.createInlinedOxsts()

        resourceMap[inlinedOxsts] = resource
        resource.contents += inlinedOxsts

        inlinedOxsts.classDeclaration = classDeclaration

        return inlinedOxsts
    }

    private fun initializeInlinedOxstsModel(inlinedOxsts: InlinedOxsts) {
        inlinedOxsts.instanceModel = createInstanceModel(inlinedOxsts.classDeclaration)
        inlinedOxsts.instanceModel.rootInstance = instanceManager.createInstance(inlinedOxsts.instanceModel.rootFeature)

        val builtinInit = OxstsFactory.createElementReference().also {
            it.element = builtinSymbolResolver.anythingInitTransition(inlinedOxsts.classDeclaration)
        }
        val builtinMain = OxstsFactory.createElementReference().also {
            it.element = builtinSymbolResolver.anythingMainTransition(inlinedOxsts.classDeclaration)
        }

        val metaEvaluator = metaStaticExpressionEvaluatorProvider.getEvaluator(inlinedOxsts.instanceModel.rootInstance)

        inlinedOxsts.initTransition = metaEvaluator.evaluateTyped(TransitionDeclaration::class.java, builtinInit).copy()
        inlinedOxsts.initTransition.isRedefine = false
        inlinedOxsts.mainTransition = metaEvaluator.evaluateTyped(TransitionDeclaration::class.java, builtinMain).copy()
        inlinedOxsts.mainTransition.isRedefine = false
        inlinedOxsts.property = redefinitionAwareReferenceResolver.resolve(inlinedOxsts.instanceModel.rootInstance, "prop") as PropertyDeclaration
    }

    private fun cleanInlinedModel(inlinedOxsts: InlinedOxsts) {
        val resource = resourceMap[inlinedOxsts]
        resource?.resourceSet?.resources?.remove(resource)
        resourceMap.remove(inlinedOxsts)
    }

    fun <T> useInlinedModel(classDeclaration: ClassDeclaration, block: (InlinedOxsts) -> T): T {
        val inlinedOxsts = createInlinedOxsts(classDeclaration)

        try {
            initializeInlinedOxstsModel(inlinedOxsts)
            return block(inlinedOxsts)
        } finally {
//            inlinedOxsts.eResource().save(mutableMapOf<Any, Any>())
            cleanInlinedModel(inlinedOxsts)
        }
    }

}

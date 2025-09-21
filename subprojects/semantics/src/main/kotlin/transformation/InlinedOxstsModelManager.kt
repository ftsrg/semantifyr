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
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Expression
import hu.bme.mit.semantifyr.oxsts.model.oxsts.FeatureKind
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import hu.bme.mit.semantifyr.oxsts.model.oxsts.PropertyDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.TransitionDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.TransitionKind
import hu.bme.mit.semantifyr.semantics.expression.MetaStaticExpressionEvaluatorProvider
import hu.bme.mit.semantifyr.semantics.expression.RedefinitionAwareReferenceResolver
import hu.bme.mit.semantifyr.semantics.expression.evaluateTyped
import hu.bme.mit.semantifyr.semantics.transformation.instantiation.InstanceManager
import hu.bme.mit.semantifyr.semantics.utils.OxstsFactory
import org.eclipse.emf.common.util.URI

@Singleton
class InlinedOxstsModelManager {

    @Inject
    private lateinit var instanceManager: InstanceManager

    fun createInlinedOxsts(classDeclaration: ClassDeclaration): InlinedOxsts {
        val resourceSet = classDeclaration.eResource().resourceSet
        val path = classDeclaration.eResource().uri.toString().replace(".oxsts", "_${classDeclaration.name}.inlined.oxsts")
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
            it.annotation = OxstsFactory.createAnnotationContainer()
            it.kind = FeatureKind.CONTAINMENT
            it.type = inlinedOxsts.classDeclaration
            it.name = "_${inlinedOxsts.classDeclaration.name}"
        }

        inlinedOxsts.rootInstance = instanceManager.createInstance(inlinedOxsts)
    }

}

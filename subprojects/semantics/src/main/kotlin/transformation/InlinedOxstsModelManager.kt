/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.semantics.transformation

import com.google.inject.Inject
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ClassDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.FeatureKind
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import hu.bme.mit.semantifyr.semantics.transformation.injection.scope.CompilationScoped
import hu.bme.mit.semantifyr.semantics.transformation.instantiation.InstanceManager
import hu.bme.mit.semantifyr.semantics.utils.OxstsFactory
import org.eclipse.emf.common.util.URI
import java.io.File

@CompilationScoped
class InlinedOxstsModelManager {

    @Inject
    private lateinit var instanceManager: InstanceManager

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
    }

}

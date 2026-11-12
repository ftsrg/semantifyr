/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.model.metamodel

import org.eclipse.emf.codegen.ecore.genmodel.GenModelFactory
import org.eclipse.emf.common.util.URI
import org.eclipse.emf.ecore.plugin.EcorePlugin
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl
import org.eclipse.emf.ecore.xmi.impl.EcoreResourceFactoryImpl
import org.eclipse.emf.mwe2.ecore.EcoreGenerator
import java.io.File

fun main(args: Array<String>) {
    generateModel(Oxsts, File(args[0]).absoluteFile, File(args[1]).absoluteFile)
}

fun generateModel(metaPackage: MetaPackage, sourceDirectory: File, modelDirectory: File) {
    val ePackage = metaPackage.toEPackage()

    EcorePlugin.getPlatformResourceMap()["emf-gen"] = URI.createFileURI("$sourceDirectory/")
    val resourceSet = ResourceSetImpl()
    resourceSet.resourceFactoryRegistry.extensionToFactoryMap["*"] = EcoreResourceFactoryImpl()

    val ecoreResource = resourceSet.createResource(URI.createFileURI("$modelDirectory/${ePackage.name}.ecore"))
    ecoreResource.contents += ePackage
    val genModel = GenModelFactory.eINSTANCE.createGenModel()
    val genModelResource = resourceSet.createResource(URI.createFileURI("$modelDirectory/${ePackage.name}.genmodel"))
    genModelResource.contents += genModel
    genModel.initialize(listOf(ePackage))
    genModel.prelinkInitialize(true).forEach { it.run() }
    genModel.modelDirectory = "/emf-gen"
    ecoreResource.save(null)
    genModelResource.save(null)

    sourceDirectory.deleteRecursively()
    val generator = EcoreGenerator()
    generator.setResourceSet(resourceSet)
    generator.setGenModel(genModelResource.uri.toString())
    generator.invoke(null)
}

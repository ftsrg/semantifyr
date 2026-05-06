/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.frontends.gamma.reader

import com.google.inject.Inject
import com.google.inject.Provider
import hu.bme.mit.semantifyr.compiler.reader.ResourceSetLoader
import hu.bme.mit.semantifyr.frontends.gamma.lang.gamma.GammaModelPackage
import hu.bme.mit.semantifyr.logging.info
import hu.bme.mit.semantifyr.logging.loggerFactory
import org.eclipse.emf.common.util.URI
import org.eclipse.emf.ecore.resource.Resource
import org.eclipse.emf.ecore.resource.ResourceSet
import org.eclipse.xtext.resource.XtextResourceSet
import java.io.File
import java.nio.file.Path
import kotlin.io.path.absolutePathString

class GammaReader @Inject constructor(
    private val resourceSetLoader: ResourceSetLoader,
    private val resourceSetProvider: Provider<XtextResourceSet>,
) {

    private val logger by loggerFactory()

    fun readGammaFile(file: File): GammaModelPackage {
        logger.info { "Reading Gamma source '$file'" }
        val resource = loadStandaloneModel(file.toPath())

        return resource.contents.single() as GammaModelPackage
    }

    private fun loadStandaloneModel(model: Path): Resource {
        val resourceSet = resourceSetProvider.get()
        val resource = loadFile(resourceSet, model)
        resourceSetLoader.resolveAllAndValidate(resourceSet)
        return resource
    }

    private fun loadFile(
        resourceSet: ResourceSet,
        path: Path,
    ): Resource {
        val resource = resourceSet.getResource(URI.createFileURI(path.absolutePathString()), true)
        resourceSet.resources += resource
        return resource
    }

}

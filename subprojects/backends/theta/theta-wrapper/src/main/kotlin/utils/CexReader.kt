/*
 * SPDX-FileCopyrightText: 2023-2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.theta.wrapper.utils

import hu.bme.mit.semantifyr.cex.lang.CexStandaloneSetup
import hu.bme.mit.semantifyr.cex.lang.cex.CexModel
import org.eclipse.emf.common.util.URI
import org.eclipse.emf.ecore.resource.Resource
import org.eclipse.emf.ecore.resource.ResourceSet
import org.eclipse.xtext.resource.XtextResourceSet
import java.nio.file.Path
import kotlin.io.path.absolutePathString

class CexReader {

    private val cexInjector = CexStandaloneSetup().createInjectorAndDoEMFRegistration()
    private val resourceSetProvider = cexInjector.getProvider(XtextResourceSet::class.java)

    private fun loadFile(resourceSet: ResourceSet, path: Path): Resource {
        return resourceSet.getResource(URI.createFileURI(path.absolutePathString()), true)
    }

    fun loadCexModel(model: Path): CexModel {
        val resourceSet = resourceSetProvider.get()
        val resource = loadFile(resourceSet, model)
        return resource.contents.single() as CexModel
    }

}

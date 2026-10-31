/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.theta.backannotation

import hu.bme.mit.semantifyr.backend.scopes.VerificationScoped
import hu.bme.mit.semantifyr.cex.lang.CexStandaloneSetup
import hu.bme.mit.semantifyr.cex.lang.cex.CexModel
import org.eclipse.emf.common.util.URI
import org.eclipse.emf.ecore.resource.Resource
import org.eclipse.emf.ecore.resource.ResourceSet
import org.eclipse.xtext.resource.IResourceServiceProvider
import org.eclipse.xtext.resource.XtextResourceSet
import java.nio.file.Path
import kotlin.io.path.absolutePathString

// one injector per JVM, and one parse at a time: Xtext registers global EMF state and initializes
// its parser lazily, neither safe for racing legs
private val cexSetup = CexStandaloneSetup()
private val cexInjector by lazy {
    cexSetup.createInjectorAndDoEMFRegistration()
}

@VerificationScoped
class CexReader {

    private fun loadFile(resourceSet: ResourceSet, path: Path): Resource {
        return resourceSet.getResource(URI.createFileURI(path.absolutePathString()), true)
    }

    fun loadCexModel(model: Path): CexModel {
        synchronized(cexInjector) {
            // test injectors restore the global EMF registries after every test, dropping the registration
            if ("cex" !in IResourceServiceProvider.Registry.INSTANCE.extensionToFactoryMap) {
                cexSetup.register(cexInjector)
            }
            val resourceSet = cexInjector.getInstance(XtextResourceSet::class.java)
            val resource = loadFile(resourceSet, model)
            return resource.contents.single() as CexModel
        }
    }
}

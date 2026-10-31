/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.theta.transformation.xsts

import hu.bme.mit.semantifyr.backend.scopes.VerificationScoped
import hu.bme.mit.semantifyr.xsts.lang.XstsStandaloneSetup
import hu.bme.mit.semantifyr.xsts.lang.xsts.XstsModel
import org.eclipse.emf.common.util.URI
import org.eclipse.xtext.resource.XtextResourceSet

// one injector per JVM, and one serialization at a time: Xtext registers global EMF state and fills
// its serializer caches lazily, neither safe for racing legs
private val xstsInjector by lazy {
    XstsStandaloneSetup().createInjectorAndDoEMFRegistration()
}

@VerificationScoped
class XstsModelCreator {

    fun createEmptyXsts(xstsUri: URI): XstsModel {
        val resourceSet = xstsInjector.getInstance(XtextResourceSet::class.java)
        val resource = resourceSet.createResource(xstsUri)

        val xstsModel = XstsFactory.createXstsModel()
        resource.contents += xstsModel

        return xstsModel
    }

    fun save(xstsModel: XstsModel) {
        synchronized(xstsInjector) {
            xstsModel.eResource().save(emptyMap<Any, Any>())
        }
    }
}

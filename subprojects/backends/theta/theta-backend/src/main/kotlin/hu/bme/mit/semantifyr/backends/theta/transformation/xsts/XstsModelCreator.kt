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

@VerificationScoped
class XstsModelCreator {

    private val xstsInjector by lazy {
        XstsStandaloneSetup().createInjectorAndDoEMFRegistration()
    }

    private val resourceSetProvider by lazy {
        xstsInjector.getProvider(XtextResourceSet::class.java)
    }

    fun createEmptyXsts(xstsUri: URI): XstsModel {
        val resourceSet = resourceSetProvider.get()
        val resource = resourceSet.createResource(xstsUri)

        val xstsModel = XstsFactory.createXstsModel()
        resource.contents += xstsModel

        return xstsModel
    }

}

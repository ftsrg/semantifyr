/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.theta.transformation.xsts

import com.google.inject.Inject
import com.google.inject.Injector
import hu.bme.mit.semantifyr.backend.BackendTestModels
import hu.bme.mit.semantifyr.backend.scopes.withVerificationScope
import hu.bme.mit.semantifyr.backends.theta.ThetaBackendModule
import hu.bme.mit.semantifyr.oxsts.lang.tests.InjectWithOxsts
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import hu.bme.mit.semantifyr.xsts.lang.xsts.XstsModel
import org.eclipse.emf.common.util.URI
import java.io.ByteArrayOutputStream
import java.io.File

@InjectWithOxsts
abstract class ThetaTransformationTestBase {

    @Inject
    private lateinit var models: BackendTestModels

    @Inject
    private lateinit var oxstsInjector: Injector

    private val childInjector by lazy {
        oxstsInjector.createChildInjector(ThetaBackendModule())
    }

    protected val testXstsUri: URI by lazy {
        URI.createFileURI(File("build/test-tmp/model.xsts").absolutePath)
    }

    protected fun capabilityChecker(): ThetaCapabilityChecker {
        return childInjector.getInstance(ThetaCapabilityChecker::class.java)
    }

    protected fun parse(source: String, vararg packages: String): InlinedOxsts {
        return models.parse(source, *packages)
    }

    protected fun serialize(xsts: XstsModel): String {
        val output = ByteArrayOutputStream()
        xsts.eResource().save(output, emptyMap<Any, Any>())
        return output.toString(Charsets.UTF_8)
    }

    protected suspend fun <T> withTransformer(block: suspend (ThetaModelTransformer) -> T): T {
        return withVerificationScope {
            block(childInjector.getInstance(ThetaModelTransformer::class.java))
        }
    }
}

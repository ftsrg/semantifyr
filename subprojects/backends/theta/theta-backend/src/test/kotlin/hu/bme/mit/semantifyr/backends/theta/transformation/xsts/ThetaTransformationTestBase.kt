/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.theta.transformation.xsts

import com.google.inject.Inject
import com.google.inject.Injector
import hu.bme.mit.semantifyr.backend.scopes.withVerificationScope
import hu.bme.mit.semantifyr.backends.theta.ThetaBackendModule
import hu.bme.mit.semantifyr.oxsts.lang.tests.InjectWithOxsts
import hu.bme.mit.semantifyr.oxsts.lang.tests.utils.InlinedOxstsParseHelper
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import org.eclipse.emf.common.util.URI
import java.io.File

@InjectWithOxsts
abstract class ThetaTransformationTestBase {

    @Inject
    protected lateinit var inlinedOxstsParseHelper: InlinedOxstsParseHelper

    @Inject
    protected lateinit var oxstsInjector: Injector

    private val childInjector by lazy {
        oxstsInjector.createChildInjector(ThetaBackendModule())
    }

    protected val testXstsUri: URI by lazy {
        URI.createFileURI(File("build/test-tmp/model.xsts").absolutePath)
    }

    protected fun parse(source: String): InlinedOxsts {
        return inlinedOxstsParseHelper.parse(source.trimIndent())
    }

    protected suspend fun <T> withTransformer(block: suspend (ThetaModelTransformer) -> T): T {
        return withVerificationScope {
            block(childInjector.getInstance(ThetaModelTransformer::class.java))
        }
    }
}

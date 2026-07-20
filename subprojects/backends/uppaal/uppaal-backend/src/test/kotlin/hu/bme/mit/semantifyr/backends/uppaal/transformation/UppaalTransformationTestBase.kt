/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.uppaal.transformation

import com.google.inject.Inject
import com.google.inject.Injector
import hu.bme.mit.semantifyr.backend.scopes.withVerificationScope
import hu.bme.mit.semantifyr.backends.uppaal.verification.UppaalBackendModule
import hu.bme.mit.semantifyr.oxsts.lang.tests.InjectWithOxsts
import hu.bme.mit.semantifyr.oxsts.lang.tests.utils.InlinedOxstsParseHelper
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts

@InjectWithOxsts
abstract class UppaalTransformationTestBase {

    @Inject
    protected lateinit var inlinedOxstsParseHelper: InlinedOxstsParseHelper

    @Inject
    protected lateinit var oxstsInjector: Injector

    private val childInjector by lazy {
        oxstsInjector.createChildInjector(UppaalBackendModule())
    }

    protected fun parse(source: String): InlinedOxsts {
        return inlinedOxstsParseHelper.parse(source.trimIndent())
    }

    protected suspend fun <T> withTransformer(block: suspend (UppaalModelTransformer) -> T): T {
        return withVerificationScope {
            val transformer = childInjector.getInstance(UppaalModelTransformer::class.java)
            block(transformer)
        }
    }
}

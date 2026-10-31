/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.uppaal.transformation

import com.google.inject.Inject
import com.google.inject.Injector
import hu.bme.mit.semantifyr.backend.BackendTestModels
import hu.bme.mit.semantifyr.backend.scopes.withVerificationScope
import hu.bme.mit.semantifyr.backends.uppaal.verification.UppaalBackendModule
import hu.bme.mit.semantifyr.oxsts.lang.tests.InjectWithOxsts
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts

@InjectWithOxsts
abstract class UppaalTransformationTestBase {

    @Inject
    private lateinit var models: BackendTestModels

    @Inject
    private lateinit var oxstsInjector: Injector

    private val childInjector by lazy {
        oxstsInjector.createChildInjector(UppaalBackendModule())
    }

    protected fun capabilityChecker(): UppaalCapabilityChecker {
        return childInjector.getInstance(UppaalCapabilityChecker::class.java)
    }

    protected fun parse(source: String, vararg packages: String): InlinedOxsts {
        return models.parse(source, *packages)
    }

    protected suspend fun <T> withTransformer(block: suspend (UppaalModelTransformer) -> T): T {
        return withVerificationScope {
            val transformer = childInjector.getInstance(UppaalModelTransformer::class.java)
            block(transformer)
        }
    }
}

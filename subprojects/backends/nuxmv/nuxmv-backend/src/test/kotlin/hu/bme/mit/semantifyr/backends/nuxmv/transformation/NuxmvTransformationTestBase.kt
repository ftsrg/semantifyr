/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.nuxmv.transformation

import com.google.inject.Inject
import com.google.inject.Injector
import hu.bme.mit.semantifyr.backend.BackendTestModels
import hu.bme.mit.semantifyr.backend.scopes.withVerificationScope
import hu.bme.mit.semantifyr.backends.nuxmv.verification.NuxmvBackendModule
import hu.bme.mit.semantifyr.oxsts.lang.tests.InjectWithOxsts
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts

@InjectWithOxsts
abstract class NuxmvTransformationTestBase {

    @Inject
    private lateinit var models: BackendTestModels

    @Inject
    private lateinit var oxstsInjector: Injector

    private val childInjector by lazy {
        oxstsInjector.createChildInjector(NuxmvBackendModule())
    }

    protected fun capabilityChecker(): NuxmvCapabilityChecker {
        return childInjector.getInstance(NuxmvCapabilityChecker::class.java)
    }

    protected fun parse(source: String, vararg packages: String): InlinedOxsts {
        return models.parse(source, *packages)
    }

    protected suspend fun <T> withTransformer(block: suspend (NuxmvModelTransformer) -> T): T {
        return withVerificationScope {
            val transformer = childInjector.getInstance(NuxmvModelTransformer::class.java)
            block(transformer)
        }
    }
}

/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.verifier

import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.InvocationInterceptor
import org.junit.jupiter.api.extension.ReflectiveInvocationContext
import org.opentest4j.TestAbortedException
import java.lang.reflect.Method

// a case whose manifest gives it the nightly's tier
interface TieredCase {
    val heavy: Boolean
}

// found by JUnit's extension detection, so that no test names it and no list restates the manifests
class HeavyCases : InvocationInterceptor {
    override fun interceptTestTemplateMethod(
        invocation: InvocationInterceptor.Invocation<Void?>,
        invocationContext: ReflectiveInvocationContext<Method>,
        extensionContext: ExtensionContext,
    ) {
        val heavy = invocationContext.arguments.any {
            it is TieredCase && it.heavy
        }
        if (heavy && !System.getProperty("semantifyr.heavyCases").toBoolean()) {
            invocation.skip()
            throw TestAbortedException("a heavy case runs in the nightly alone")
        }
        invocation.proceed()
    }
}

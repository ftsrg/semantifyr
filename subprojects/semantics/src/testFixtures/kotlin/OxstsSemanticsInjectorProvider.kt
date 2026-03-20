/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.semantics

import com.google.inject.Guice
import com.google.inject.Injector
import hu.bme.mit.semantifyr.oxsts.lang.tests.OxstsInjectorProvider
import org.eclipse.xtext.testing.InjectWith
import org.eclipse.xtext.testing.extensions.InjectionExtension
import org.junit.jupiter.api.extension.ExtendWith

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@ExtendWith(InjectionExtension::class)
@InjectWith(OxstsSemanticsInjectorProvider::class)
annotation class InjectWithOxstsSemantics

class OxstsSemanticsInjectorProvider : OxstsInjectorProvider() {

    override fun internalCreateInjector(): Injector {
        return object : OxstsSemanticsStandaloneSetup() {
            override fun createInjector(): Injector {
                return Guice.createInjector(createRuntimeModule())
            }
        }.createInjectorAndDoEMFRegistration()
    }

    override fun createRuntimeModule(): OxstsSemanticsRuntimeModule {
        // make it work also with Maven/Tycho and OSGI
        // see https://bugs.eclipse.org/bugs/show_bug.cgi?id=493672
        // allows for bindClassLoaderToInstance to get the class loader of the bundle
        // containing the instance of the injector provider (possibly inherited)
        return object : OxstsSemanticsRuntimeModule() {
            override fun bindClassLoaderToInstance(): ClassLoader {
                return this@OxstsSemanticsInjectorProvider.javaClass.classLoader
            }
        }
    }

}

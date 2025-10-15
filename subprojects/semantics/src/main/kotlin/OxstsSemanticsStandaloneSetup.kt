/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.semantics

import com.google.inject.Binder
import com.google.inject.Guice
import com.google.inject.Injector
import com.google.inject.Provides
import com.google.inject.name.Named
import hu.bme.mit.semantifyr.oxsts.lang.OxstsRuntimeModule
import hu.bme.mit.semantifyr.oxsts.lang.OxstsStandaloneSetup
import hu.bme.mit.semantifyr.semantics.transformation.injection.scope.CompilationScope
import hu.bme.mit.semantifyr.semantics.transformation.injection.scope.CompilationScoped

class OxstsSemanticsModule : OxstsRuntimeModule() {

    private val compilationScope = CompilationScope()

    override fun configure(binder: Binder) {
        binder.bindScope(CompilationScoped::class.java, compilationScope)
        super.configure(binder)
    }

    @Provides
    @Named("compilationScope")
    fun provideCompilationScope(): CompilationScope {
        return compilationScope
    }

}

class OxstsSemanticsStandaloneSetup : OxstsStandaloneSetup() {

    override fun createInjector(): Injector {
        return Guice.createInjector(OxstsSemanticsModule())
    }

}

object StandaloneSemantifyrModule {

    val injector: Injector by lazy {
        OxstsSemanticsStandaloneSetup().createInjectorAndDoEMFRegistration()
    }

    inline fun <reified T> getInstance(): T {
        return injector.getInstance(T::class.java)
    }

}

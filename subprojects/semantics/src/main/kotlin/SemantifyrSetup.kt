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

class SemantifyrRuntimeModule : OxstsRuntimeModule() {

    private val compilationScope = CompilationScope()

    override fun configure(binder: Binder) {
        binder.bindScope(CompilationScoped::class.java, compilationScope)
        super.configure(binder)
    }

    @Provides
    @Named("compilationScope")
    fun provideBatchScope(): CompilationScope {
        return compilationScope
    }

}

class SemantifyrSetup : OxstsStandaloneSetup() {

    override fun createInjector(): Injector {
        return Guice.createInjector(SemantifyrRuntimeModule())
    }

}

object StandaloneSemantifyrModule {

    val injector: Injector by lazy {
        SemantifyrSetup().createInjectorAndDoEMFRegistration()
    }

    inline fun <reified T> getInstance(): T {
        return injector.getInstance(T::class.java)
    }

}

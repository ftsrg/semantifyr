/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.semantics

import com.google.inject.Binder
import com.google.inject.Guice
import com.google.inject.Injector
import com.google.inject.assistedinject.FactoryModuleBuilder
import hu.bme.mit.semantifyr.oxsts.lang.OxstsRuntimeModule
import hu.bme.mit.semantifyr.oxsts.lang.OxstsStandaloneSetup
import hu.bme.mit.semantifyr.semantics.expression.MetaStaticExpressionEvaluator
import hu.bme.mit.semantifyr.semantics.expression.StaticElementValueEvaluator
import hu.bme.mit.semantifyr.semantics.expression.StaticExpressionEvaluator
import hu.bme.mit.semantifyr.semantics.transformation.injection.scope.CompilationScoped
import hu.bme.mit.semantifyr.semantics.transformation.injection.scope.SemantifyrScopes
import hu.bme.mit.semantifyr.semantics.transformation.inliner.ExpressionCallInliner
import hu.bme.mit.semantifyr.semantics.transformation.inliner.OperationCallInliner

class OxstsSemanticsModule : OxstsRuntimeModule() {

    override fun configure(binder: Binder) {
        binder.bindScope(CompilationScoped::class.java, SemantifyrScopes.COMPILATION)
        binder.install(FactoryModuleBuilder().build(StaticExpressionEvaluator.Factory::class.java))
        binder.install(FactoryModuleBuilder().build(MetaStaticExpressionEvaluator.Factory::class.java))
        binder.install(FactoryModuleBuilder().build(StaticElementValueEvaluator.Factory::class.java))
        binder.install(FactoryModuleBuilder().build(ExpressionCallInliner.Factory::class.java))
        binder.install(FactoryModuleBuilder().build(OperationCallInliner.Factory::class.java))

        super.configure(binder)
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

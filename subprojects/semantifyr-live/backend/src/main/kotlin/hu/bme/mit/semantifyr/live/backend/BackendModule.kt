/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend

import com.google.inject.AbstractModule
import com.google.inject.Provides
import com.google.inject.assistedinject.FactoryModuleBuilder
import hu.bme.mit.semantifyr.live.backend.session.LiveSession
import com.google.inject.Singleton

class BackendModule(
    private val config: BackendConfig,
) : AbstractModule() {

    override fun configure() {
        install(FactoryModuleBuilder().build(LiveSession.Factory::class.java))
    }

    @Provides
    @Singleton
    fun provideConfig(): BackendConfig {
        return config
    }

}

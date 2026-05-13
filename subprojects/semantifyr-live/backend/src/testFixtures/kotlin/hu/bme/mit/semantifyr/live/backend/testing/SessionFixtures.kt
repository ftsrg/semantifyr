/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.live.backend.testing

import com.google.inject.AbstractModule
import com.google.inject.Guice
import com.google.inject.Injector
import hu.bme.mit.semantifyr.live.backend.Flavor
import hu.bme.mit.semantifyr.live.backend.Language
import hu.bme.mit.semantifyr.live.backend.WorkspaceLayout
import hu.bme.mit.semantifyr.live.backend.lsp.session.SessionScope
import hu.bme.mit.semantifyr.live.backend.lsp.session.SessionScoped

fun sessionScopedTestParentInjector(): Injector {
    return Guice.createInjector(
        object : AbstractModule() {
            override fun configure() {
                bindScope(SessionScoped::class.java, SessionScope)
            }
        },
    )
}

fun testFlavor(
    id: String = "oxsts",
    fileName: String = "snippet.oxsts",
    language: Language = Language.Oxsts,
    workspaceLayout: WorkspaceLayout = WorkspaceLayout.SingleFile,
    verificationCommand: String = "oxsts.case.verify",
    validateWitnessCommand: String? = "oxsts.case.validateWitness",
    discoveryCommand: String = "oxsts.case.discover",
): Flavor {
    return Flavor(
        id = id,
        displayName = "Semantifyr",
        fileName = fileName,
        language = language,
        workspaceLayout = workspaceLayout,
        verificationCommand = verificationCommand,
        validateWitnessCommand = validateWitnessCommand,
        discoveryCommand = discoveryCommand,
    )
}

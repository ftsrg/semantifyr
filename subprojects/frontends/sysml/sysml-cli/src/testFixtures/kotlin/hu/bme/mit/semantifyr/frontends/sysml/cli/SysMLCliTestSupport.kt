/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.frontends.sysml.cli

import com.github.ajalt.clikt.testing.CliktCommandTestResult
import com.github.ajalt.clikt.testing.test
import com.google.inject.Module
import hu.bme.mit.semantifyr.cli.common.execute
import hu.bme.mit.semantifyr.frontends.sysml.wrapper.SysMLv2Compiler
import hu.bme.mit.semantifyr.oxsts.lang.OxstsStandaloneSetup
import hu.bme.mit.semantifyr.portfolios.Portfolios
import hu.bme.mit.semantifyr.verifier.portfolio.VerificationPortfolio
import org.eclipse.xtext.resource.FileExtensionProvider
import kotlin.io.path.Path

val tutorialModel = Path("../models/tutorial/room.sysml")
val semanticModels = Path("../../../../oxsts-test-models/semantic")

private val injector by lazy {
    OxstsStandaloneSetup().createInjectorAndDoEMFRegistration()
}

val oxstsExtensions by lazy {
    injector.getInstance(FileExtensionProvider::class.java).fileExtensions
}

suspend fun runSysMLCli(
    vararg arguments: String,
    portfolios: List<VerificationPortfolio> = Portfolios.all,
    compiler: SysMLv2Compiler? = null,
): CliktCommandTestResult {
    val commandInjector = if (compiler == null) {
        injector
    } else {
        // a child's binding blocks the same binding in its parent, so the shared injector gets no child
        OxstsStandaloneSetup().createInjectorAndDoEMFRegistration().createChildInjector(
            Module {
                it.bind(SysMLv2Compiler::class.java).toInstance(compiler)
            },
        )
    }
    val command = commandInjector.getInstance(SemantifyrSysMLCommand::class.java)
    command.registeredSubcommands().filterIsInstance<SysMLVerifyCommand>().single().portfolios = portfolios
    return command.test(arguments.toList()) {
        command.execute(it)
    }
}

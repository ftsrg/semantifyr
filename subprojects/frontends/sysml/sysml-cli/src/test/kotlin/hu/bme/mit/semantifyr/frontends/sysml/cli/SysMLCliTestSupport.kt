/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.frontends.sysml.cli

import com.github.ajalt.clikt.testing.CliktCommandTestResult
import com.github.ajalt.clikt.testing.test
import hu.bme.mit.semantifyr.cli.common.execute
import hu.bme.mit.semantifyr.oxsts.lang.OxstsStandaloneSetup
import hu.bme.mit.semantifyr.portfolios.Portfolios
import hu.bme.mit.semantifyr.verifier.portfolio.VerificationPortfolio
import kotlin.io.path.Path

val tutorialModel = Path("../models/tutorial/room.sysml")
val semanticModels = Path("../../../../oxsts-test-models/semantic")

private val injector by lazy {
    OxstsStandaloneSetup().createInjectorAndDoEMFRegistration()
}

suspend fun runSysMLCli(
    vararg arguments: String,
    portfolios: List<VerificationPortfolio> = Portfolios.all,
): CliktCommandTestResult {
    val command = injector.getInstance(SemantifyrSysMLCommand::class.java)
    command.registeredSubcommands().filterIsInstance<SysMLVerifyCommand>().single().portfolios = portfolios
    return command.test(arguments.toList()) {
        command.execute(it)
    }
}

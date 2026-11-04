/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.cli

import com.github.ajalt.clikt.testing.CliktCommandTestResult
import com.github.ajalt.clikt.testing.test
import hu.bme.mit.semantifyr.cli.commands.VerifyCommand
import hu.bme.mit.semantifyr.cli.common.execute
import hu.bme.mit.semantifyr.oxsts.lang.OxstsStandaloneSetup
import hu.bme.mit.semantifyr.portfolios.Portfolios
import hu.bme.mit.semantifyr.verifier.portfolio.VerificationPortfolio
import kotlin.io.path.Path

val testModels = Path("../../oxsts-test-models")
val smokeDirectory = testModels.resolve("smoke")
val smokeModel = smokeDirectory.resolve("smoke.oxsts")
const val SMOKE_CASE = "semantifyr::verification::smoke::CounterReachesThree"

private val injector by lazy {
    OxstsStandaloneSetup().createInjectorAndDoEMFRegistration()
}

suspend fun runCli(
    vararg arguments: String,
    portfolios: List<VerificationPortfolio> = Portfolios.all,
): CliktCommandTestResult {
    val command = injector.getInstance(SemantifyrCommand::class.java)
    command.registeredSubcommands().filterIsInstance<VerifyCommand>().single().portfolios = portfolios
    return command.test(arguments.toList()) {
        command.execute(it)
    }
}

/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.frontends.gamma.cli

import com.github.ajalt.clikt.testing.CliktCommandTestResult
import com.github.ajalt.clikt.testing.test
import hu.bme.mit.semantifyr.cli.common.execute
import hu.bme.mit.semantifyr.frontends.gamma.lang.GammaStandaloneSetup
import hu.bme.mit.semantifyr.oxsts.lang.OxstsStandaloneSetup
import hu.bme.mit.semantifyr.portfolios.Portfolios
import hu.bme.mit.semantifyr.verifier.portfolio.VerificationPortfolio
import kotlin.io.path.Path

val simpleModel = Path("../models/examples/Simple.gamma")
const val SIMPLE_CASE = "Simple.WorkerStatechartIdleReachable"

private val gammaInjector by lazy {
    GammaStandaloneSetup().createInjectorAndDoEMFRegistration()
}

private val oxstsInjector by lazy {
    OxstsStandaloneSetup().createInjectorAndDoEMFRegistration()
}

suspend fun runGammaCli(
    vararg arguments: String,
    portfolios: List<VerificationPortfolio> = Portfolios.all,
): CliktCommandTestResult {
    val command = SemantifyrGammaCommand(gammaInjector, oxstsInjector)
    command.registeredSubcommands().filterIsInstance<GammaVerifyCommand>().single().portfolios = portfolios
    return command.test(arguments.toList()) {
        command.execute(it)
    }
}

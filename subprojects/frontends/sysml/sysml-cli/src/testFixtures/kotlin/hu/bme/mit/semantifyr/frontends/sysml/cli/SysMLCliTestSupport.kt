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
import org.eclipse.xtext.resource.FileExtensionProvider
import kotlin.io.path.Path

val smokeModel = Path("../models/smoke/smoke.sysml")

val oxstsInjector by lazy {
    OxstsStandaloneSetup().createInjectorAndDoEMFRegistration()
}

val oxstsExtensions by lazy {
    oxstsInjector.getInstance(FileExtensionProvider::class.java).fileExtensions
}

suspend fun runSysMLCli(
    vararg arguments: String,
    portfolios: List<VerificationPortfolio> = Portfolios.all,
): CliktCommandTestResult {
    val command = oxstsInjector.getInstance(SemantifyrSysMLCommand::class.java)
    command.registeredSubcommands().filterIsInstance<SysMLVerifyCommand>().single().portfolios = portfolios
    return command.test(arguments.toList()) {
        command.execute(it)
    }
}

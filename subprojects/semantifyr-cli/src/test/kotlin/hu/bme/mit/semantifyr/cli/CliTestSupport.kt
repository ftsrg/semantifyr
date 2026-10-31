/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.cli

import com.github.ajalt.clikt.testing.CliktCommandTestResult
import com.github.ajalt.clikt.testing.test
import hu.bme.mit.semantifyr.backend.BackendVerificationResult
import hu.bme.mit.semantifyr.backend.VerificationVerdict
import hu.bme.mit.semantifyr.cli.commands.VerifyCommand
import hu.bme.mit.semantifyr.oxsts.lang.OxstsStandaloneSetup
import hu.bme.mit.semantifyr.portfolios.Portfolios
import hu.bme.mit.semantifyr.verifier.FakeBackend
import hu.bme.mit.semantifyr.verifier.FakeConfig
import hu.bme.mit.semantifyr.verifier.fakeMetadata
import hu.bme.mit.semantifyr.verifier.portfolio.SingleBackendPortfolio
import hu.bme.mit.semantifyr.verifier.portfolio.VerificationPortfolio
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
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

fun fakePortfolio(id: String, vararg verdicts: VerificationVerdict): SingleBackendPortfolio<FakeConfig> {
    val remaining = ArrayDeque(verdicts.toList())
    val backend = FakeBackend(id) {
        BackendVerificationResult(verdict = remaining.removeFirst(), metadata = fakeMetadata(id))
    }
    return SingleBackendPortfolio(backend, FakeConfig(id))
}

fun jsonLines(output: String): List<JsonObject> {
    return output.lines().filter { it.isNotBlank() }.map {
        Json.parseToJsonElement(it).jsonObject
    }
}

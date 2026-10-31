/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.cli

import com.github.ajalt.clikt.command.test
import com.github.ajalt.clikt.testing.CliktCommandTestResult
import hu.bme.mit.semantifyr.oxsts.lang.OxstsStandaloneSetup
import kotlin.io.path.Path

val testModels = Path("../../oxsts-test-models")
val smokeDirectory = testModels.resolve("smoke")
val smokeModel = smokeDirectory.resolve("smoke.oxsts")
const val SMOKE_CASE = "semantifyr::verification::smoke::CounterReachesThree"

private val injector by lazy {
    OxstsStandaloneSetup().createInjectorAndDoEMFRegistration()
}

suspend fun runCli(vararg arguments: String): CliktCommandTestResult {
    val command = injector.getInstance(SemantifyrCommand::class.java)
    return command.test(arguments.toList())
}

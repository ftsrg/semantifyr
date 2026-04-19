/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.cli.commands

import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.groups.provideDelegate
import com.google.inject.Inject
import hu.bme.mit.semantifyr.cli.commands.options.VerificationCaseSpecificationOptionGroup
import hu.bme.mit.semantifyr.compiler.reader.SemantifyrLoader
import hu.bme.mit.semantifyr.logging.info
import hu.bme.mit.semantifyr.logging.loggerFactory
import hu.bme.mit.semantifyr.verification.discovery.VerificationCaseDiscoverer

class ListCommand @Inject constructor(
    semantifyrLoader: SemantifyrLoader,
    verificationCaseDiscoverer: VerificationCaseDiscoverer,
) : BaseSemantifyrCommand("list", semantifyrLoader) {

    private val logger by loggerFactory()

    private val caseSpecificationOptions by VerificationCaseSpecificationOptionGroup(verificationCaseDiscoverer)

    override fun help(context: Context): String {
        return "List the verification cases declared in the given OXSTS model."
    }

    override suspend fun run() {
        logger.info { "list model=$model libraries=$libraries" }

        val semantifyrModelContext = readModelContext()
        val cases = caseSpecificationOptions.collectVerificationCases(semantifyrModelContext)
        if (cases.isEmpty()) {
            echo("No verification case found.")
            return
        }

        echo("Found ${cases.size} verification case(s):")
        for (case in cases) {
            echo("  $case")
        }
    }

}

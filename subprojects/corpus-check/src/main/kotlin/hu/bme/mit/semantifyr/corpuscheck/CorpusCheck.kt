/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.corpuscheck

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.long
import com.github.ajalt.clikt.parameters.types.path
import com.github.ajalt.clikt.parameters.types.restrictTo
import java.nio.file.Files
import kotlin.io.path.createDirectories

class CorpusCheckCommand : CliktCommand("corpus-check") {
    private val launcher by option("--launcher")
        .path(mustExist = true, canBeDir = false)
        .required()
        .help("The launcher to verify with, an installed semantifyr-gamma or semantifyr-sysml.")

    private val corpora by option("--corpus")
        .path(mustExist = true, canBeFile = false)
        .multiple(required = true)
        .help("A directory holding a manifest.json and the models it lists, repeatable.")

    private val caseNamespace by option("--case-namespace")
        .help("The prefix the launcher qualifies a case name with; given, each run names the cases it verifies.")

    private val timeoutSeconds by option("-t", "--timeout-seconds")
        .long()
        .restrictTo(min = 1L)
        .help("Timeout per case, in seconds; the launcher's default when not given.")

    private val artifactDirectory by option("-d", "--artifact-directory")
        .path(canBeFile = false)
        .help("Where the launcher writes each model's artifacts; a new temporary directory by default.")

    override fun help(context: Context): String {
        return "Verify the cases of corpus manifests with a launcher and compare each verdict with the manifest's."
    }

    override fun run() {
        val artifactRoot = artifactDirectory?.createDirectories() ?: Files.createTempDirectory("corpus-check-")
        echo("artifacts: $artifactRoot")
        val checker = CorpusChecker(ProcessLauncher(launcher, artifactRoot, timeoutSeconds), caseNamespace)
        var failed = 0
        var total = 0
        for (corpus in corpora) {
            for (selection in checker.select(corpus)) {
                for (check in checker.check(selection)) {
                    echo(check)
                    total++
                    if (!check.passing) {
                        failed++
                    }
                }
            }
        }
        echo("$failed of $total cases failed")
        if (failed > 0) {
            throw ProgramResult(1)
        }
    }
}

fun main(arguments: Array<String>) {
    CorpusCheckCommand().main(arguments)
}

/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.benchmark.sources

import com.google.inject.Injector
import hu.bme.mit.semantifyr.backend.VerificationVerdict
import hu.bme.mit.semantifyr.benchmark.verification.BenchmarkCase
import hu.bme.mit.semantifyr.benchmark.verification.BenchmarkModel
import hu.bme.mit.semantifyr.benchmark.verification.PreparedModel
import hu.bme.mit.semantifyr.compiler.reader.SemantifyrLoader
import hu.bme.mit.semantifyr.verifier.VerificationCase
import hu.bme.mit.semantifyr.verifier.discovery.VerificationCaseDiscoverer
import java.nio.file.Path
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name

class OxstsModelSource(
    injector: Injector,
    private val directory: Path,
) {
    private val loader = injector.getInstance(SemantifyrLoader::class.java)
    private val discoverer = injector.getInstance(VerificationCaseDiscoverer::class.java)

    val models: List<BenchmarkModel> by lazy {
        directory.listDirectoryEntries("*.oxsts").sorted().map {
            OxstsModel(it)
        }
    }

    private inner class OxstsModel(
        private val path: Path,
    ) : BenchmarkModel {
        override val name: String = path.name

        private val verificationCases by lazy {
            discoverer.discover(loader.loadStandaloneModel(path)).associateBy {
                it.qualifiedName.substringAfterLast("::")
            }
        }

        override val cases: List<BenchmarkCase> by lazy {
            verificationCases.map { (name, case) ->
                BenchmarkCase(name, expectedVerdict(case))
            }
        }

        override fun prepare(workDirectory: Path): PreparedModel {
            return PreparedModel(verificationCases)
        }
    }

    private fun expectedVerdict(case: VerificationCase): VerificationVerdict? {
        return when {
            "expect-pass" in case.tags -> VerificationVerdict.Passed
            "expect-fail" in case.tags -> VerificationVerdict.Failed
            else -> null
        }
    }
}

/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.benchmark.verification

import hu.bme.mit.semantifyr.backend.VerificationVerdict
import hu.bme.mit.semantifyr.backend.execution.ExecutorKey
import hu.bme.mit.semantifyr.backends.nuxmv.NuxmvExecutorKey
import hu.bme.mit.semantifyr.backends.spin.SpinExecutorKey
import hu.bme.mit.semantifyr.backends.theta.ThetaExecutorKey
import hu.bme.mit.semantifyr.backends.uppaal.UppaalExecutorKey
import hu.bme.mit.semantifyr.benchmark.Axis
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.OptimizationConfig
import hu.bme.mit.semantifyr.portfolios.Portfolios
import hu.bme.mit.semantifyr.verifier.VerificationCase
import hu.bme.mit.semantifyr.verifier.portfolio.VerificationPortfolio
import kotlinx.serialization.json.JsonElement
import java.nio.file.Path

enum class Engine(
    val executorKey: ExecutorKey<*>,
) {
    Theta(ThetaExecutorKey),
    Spin(SpinExecutorKey),
    Nuxmv(NuxmvExecutorKey),
    Uppaal(UppaalExecutorKey),
    ;

    val id: String
        get() = executorKey.name

    val portfolios: List<VerificationPortfolio>
        get() = Portfolios.all.filter {
            it.executorKeys == setOf(executorKey)
        }
}

class OptimizationLevel(
    val name: String,
    val config: OptimizationConfig,
) {
    companion object {
        val None = OptimizationLevel("none", OptimizationConfig.NONE)
        val All = OptimizationLevel("all", OptimizationConfig.ALL)
    }
}

data class BenchmarkCase(
    val name: String,
    val expected: VerificationVerdict?,
    val falsification: Boolean = false,
    val nightly: Boolean = false,
)

class PreparedModel(
    val cases: Map<String, VerificationCase>,
    val extensions: Map<String, JsonElement> = emptyMap(),
)

interface BenchmarkModel {
    val name: String
    val cases: List<BenchmarkCase>

    fun prepare(workDirectory: Path): PreparedModel
}

object VerificationAxes {
    val model = Axis<BenchmarkModel>("model") { it.name }
    val case = Axis<BenchmarkCase>("case") { it.name }
    val engine = Axis<Engine>("engine") { it.id }
    val backendConfig = Axis<VerificationPortfolio>("backend") { it.id }
    val optimization = Axis<OptimizationLevel>("optimization") { it.name }
}

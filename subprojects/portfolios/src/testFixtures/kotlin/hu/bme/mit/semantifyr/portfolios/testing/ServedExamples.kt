/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.portfolios.testing

import hu.bme.mit.semantifyr.backend.execution.ExecutionEnvironment
import hu.bme.mit.semantifyr.backends.nuxmv.NuxmvExecutionResult
import hu.bme.mit.semantifyr.backends.nuxmv.NuxmvExecutionSpecification
import hu.bme.mit.semantifyr.backends.nuxmv.NuxmvExecutor
import hu.bme.mit.semantifyr.backends.nuxmv.NuxmvExecutorKey
import hu.bme.mit.semantifyr.backends.uppaal.UppaalExecutionResult
import hu.bme.mit.semantifyr.backends.uppaal.UppaalExecutionSpecification
import hu.bme.mit.semantifyr.backends.uppaal.UppaalExecutor
import hu.bme.mit.semantifyr.backends.uppaal.UppaalExecutorKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.readText

@Serializable
enum class ExampleLanguage {
    @SerialName("sysml")
    Sysml,

    @SerialName("gamma")
    Gamma,

    @SerialName("oxsts")
    Oxsts,
}

@Serializable
data class ServedLanguage(
    val page: String,
    val blank: String,
)

@Serializable
data class ServedExample(
    val id: String,
    val language: ExampleLanguage,
    val file: String,
    val label: String,
    val reason: String,
    val origin: String,
    val holder: String,
    val licence: String,
    val page: String,
    val cases: List<String>,
    val hidden: Boolean = false,
) {
    val path: Path
        get() {
            return ServedExamples.repository.resolve(file)
        }

    override fun toString(): String {
        return "/${language.name.lowercase()}/$id"
    }
}

@Serializable
data class ServedExamples(
    val languages: Map<ExampleLanguage, ServedLanguage>,
    val examples: List<ServedExample>,
) {
    fun of(language: ExampleLanguage): List<ServedExample> {
        return examples.filter {
            it.language == language
        }
    }

    companion object {
        // the build names the repository's root relative to the module, see build-logic's readsServedExamples
        val repository: Path
            get() {
                return Path(System.getProperty("semantifyr.repository") ?: error("The build names no repository root"))
            }

        fun load(): ServedExamples {
            return Json.decodeFromString(repository.resolve(REGISTRY).readText())
        }

        private const val REGISTRY = "subprojects/semantifyr-editor-common/src/examples/examples.json"
    }
}

// live's image holds Theta and Spin alone, so a served case is decided by them whatever else the machine holds
val liveBackends: ExecutionEnvironment = ExecutionEnvironment.builder()
    .put(NuxmvExecutorKey) {
        AbsentNuxmv
    }
    .put(UppaalExecutorKey) {
        AbsentUppaal
    }
    .build()

private object AbsentNuxmv : NuxmvExecutor {
    override fun isAvailable(): Boolean {
        return false
    }

    override suspend fun execute(nuxmvExecutionSpecification: NuxmvExecutionSpecification): NuxmvExecutionResult {
        error("nuXmv is not among live's backends")
    }
}

private object AbsentUppaal : UppaalExecutor {
    override fun isAvailable(): Boolean {
        return false
    }

    override suspend fun execute(uppaalExecutionSpecification: UppaalExecutionSpecification): UppaalExecutionResult {
        error("Uppaal is not among live's backends")
    }
}

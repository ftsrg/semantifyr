/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.verifier.internal

import com.google.inject.Inject
import com.google.inject.Injector
import hu.bme.mit.semantifyr.backend.BackendCapabilityChecker
import hu.bme.mit.semantifyr.backend.BackendMetrics
import hu.bme.mit.semantifyr.backend.BackendUnsupportedException
import hu.bme.mit.semantifyr.backend.BackendVerificationRequest
import hu.bme.mit.semantifyr.backend.BackendVerificationResult
import hu.bme.mit.semantifyr.backend.SoundnessDirection
import hu.bme.mit.semantifyr.backend.VerificationContext
import hu.bme.mit.semantifyr.backend.VerificationMetadata
import hu.bme.mit.semantifyr.backend.VerificationVerdict
import hu.bme.mit.semantifyr.backend.execution.AvailabilityReport
import hu.bme.mit.semantifyr.backend.execution.BackendExecutor
import hu.bme.mit.semantifyr.backend.execution.ExecutionEnvironment
import hu.bme.mit.semantifyr.backend.execution.ExecutorKey
import hu.bme.mit.semantifyr.backend.witness.InlinedWitness
import hu.bme.mit.semantifyr.compiler.pipeline.artifact.ArtifactConfig
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.OptimizationConfig
import hu.bme.mit.semantifyr.compiler.reader.SemantifyrLoader
import hu.bme.mit.semantifyr.oxsts.lang.tests.InjectWithOxsts
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ClassDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import hu.bme.mit.semantifyr.oxsts.model.oxsts.OxstsModelPackage
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration
import hu.bme.mit.semantifyr.verifier.BackAnnotationException
import hu.bme.mit.semantifyr.verifier.ProgressContext
import hu.bme.mit.semantifyr.verifier.SemantifyrVerifier
import hu.bme.mit.semantifyr.verifier.VerificationCase
import hu.bme.mit.semantifyr.verifier.VerificationReport
import hu.bme.mit.semantifyr.verifier.caseDirectoryName
import hu.bme.mit.semantifyr.verifier.caseReportPath
import hu.bme.mit.semantifyr.verifier.portfolio.ConcurrencyGate
import hu.bme.mit.semantifyr.verifier.portfolio.PortfolioLeg
import hu.bme.mit.semantifyr.verifier.portfolio.VerificationPortfolio
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.eclipse.emf.common.util.URI
import org.eclipse.emf.ecore.EObject
import org.eclipse.emf.ecore.util.EcoreUtil
import org.eclipse.xtext.serializer.ISerializer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import java.nio.file.Path
import kotlin.coroutines.cancellation.CancellationException
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@InjectWithOxsts
class SemantifyrVerifierImplTest {

    @Inject
    private lateinit var injector: Injector

    @Inject
    private lateinit var loader: SemantifyrLoader

    @Inject
    private lateinit var serializer: ISerializer

    private val modelPath = Path("../../oxsts-test-models/simple/simple.oxsts")

    @Test
    suspend fun `successful portfolio verdict propagates to VerificationResult`(@TempDir output: Path) {
        val backendResult = decisiveResult(VerificationVerdict.Passed, "fake-backend")
        val portfolio = ScriptedPortfolio { _, _ -> backendResult }

        val result = build(portfolio, output)
            .verify(loadFirstCase())

        assertThat(result.verdict).isEqualTo(VerificationVerdict.Passed)
        assertThat(result.metadata.backendId).isEqualTo("fake-backend")
        assertThat(portfolio.invocations).isEqualTo(1)
    }

    @Test
    fun `verifier timeout maps to Timeout`(@TempDir output: Path) = runTest {
        val portfolio = ScriptedPortfolio { _, _ ->
            delay(10.seconds)
            decisiveResult(VerificationVerdict.Passed)
        }

        val result = build(portfolio, output, timeout = 50.milliseconds)
            .verify(loadFirstCase())

        assertThat(result.verdict).isEqualTo(VerificationVerdict.Timeout)
        assertThat(result.message).startsWith("Timeout").endsWith("exceeded.").doesNotContain("back-annotating")
        assertThat(result.metadata.backendId).isNull()
    }

    @Test
    suspend fun `the portfolio gets the time the compilation left`(@TempDir output: Path) {
        var portfolioTimeout: Duration? = null
        val portfolio = ScriptedPortfolio { request, _ ->
            portfolioTimeout = request.timeout
            decisiveResult(VerificationVerdict.Passed)
        }

        build(portfolio, output).verify(loadFirstCase())

        val timeout = portfolioTimeout!!
        assertThat(timeout.isPositive() && timeout < 30.seconds).describedAs("$timeout").isTrue
    }

    @Test
    suspend fun `a compilation that outlasts the timeout is a Timeout and runs no portfolio`(@TempDir output: Path) {
        val portfolio = ScriptedPortfolio { _, _ ->
            decisiveResult(VerificationVerdict.Passed)
        }
        val case = loadFirstCase()

        val result = build(portfolio, output, timeout = 1.milliseconds).verify(case)

        assertThat(result.verdict).isEqualTo(VerificationVerdict.Timeout)
        assertThat(result.message).isEqualTo("Timeout (1ms) exceeded while compiling.")
        assertThat(portfolio.invocations).isZero
        assertThat(caseReportPath(output, case)).exists()
    }

    @Test
    suspend fun `NotSupported names a flattened variable as declared in the source`(@TempDir output: Path) {
        val portfolio = ScriptedPortfolio { request, _ ->
            RejectingVariablesContext(request).execute()
        }

        val result = build(portfolio, output).verify(loadFirstCase())

        assertThat(result.verdict).isEqualTo(VerificationVerdict.NotSupported)
        assertThat(result.message).isEqualTo("scripted does not support simple.oxsts:12: variables of type int (x)")
    }

    @Test
    suspend fun `BackendUnsupportedException from the portfolio yields NotSupported`(@TempDir output: Path) {
        val portfolio = ScriptedPortfolio { _, _ ->
            throw BackendUnsupportedException("engine cannot represent this model")
        }

        val result = build(portfolio, output)
            .verify(loadFirstCase())

        assertThat(result.verdict).isEqualTo(VerificationVerdict.NotSupported)
        assertThat(result.message).contains("engine cannot represent")
    }

    @Test
    suspend fun `arbitrary exception from the portfolio yields Errored`(@TempDir output: Path) {
        val portfolio = ScriptedPortfolio { _, _ ->
            error("portfolio blew up")
        }

        val result = build(portfolio, output)
            .verify(loadFirstCase())

        assertThat(result.verdict).isEqualTo(VerificationVerdict.Errored)
        assertThat(result.message).contains("portfolio blew up")
    }

    @Test
    fun `CancellationException from the portfolio is rethrown`(@TempDir output: Path) = runTest {
        val portfolio = ScriptedPortfolio { _, _ ->
            throw CancellationException("caller cancelled")
        }

        assertThatThrownBy {
            kotlinx.coroutines.runBlocking {
                build(portfolio, output).verify(loadFirstCase())
            }
        }.isInstanceOf(CancellationException::class.java)
    }

    @Test
    suspend fun `metrics record compilation portfolio and total durations`(@TempDir output: Path) {
        val portfolio = ScriptedPortfolio { _, _ ->
            delay(20.milliseconds)
            decisiveResult(VerificationVerdict.Passed)
        }

        val result = build(portfolio, output)
            .verify(loadFirstCase())

        assertThat(result.metrics.totalDuration).isGreaterThan(Duration.ZERO)
        assertThat(result.metrics.verifier.compilationDuration).isGreaterThan(Duration.ZERO)
        assertThat(result.metrics.verifier.portfolioDuration).isGreaterThan(Duration.ZERO)
        assertThat(result.metrics.backend).isNotNull
    }

    @Test
    suspend fun `failed metadata sets backend to null when the portfolio errors`(@TempDir output: Path) {
        val portfolio = ScriptedPortfolio { _, _ -> error("boom") }

        val result = build(portfolio, output)
            .verify(loadFirstCase())

        assertThat(result.metrics.backend).isNull()
    }

    @Test
    suspend fun `report json is written to a directory named after the model file and the qualified name of the case`(@TempDir output: Path) {
        val portfolio = ScriptedPortfolio { _, _ -> decisiveResult(VerificationVerdict.Passed) }
        val case = loadFirstCase()

        build(portfolio, output, artifacts = ArtifactConfig.ALL).verify(case)

        val caseDirectory = output.resolve("simple.${case.qualifiedName.replace("::", ".")}")
        val reportFile = caseDirectory.resolve("report.json")
        assertThat(reportFile.exists()).isTrue
    }

    @Test
    fun `a witness that fails to back-annotate throws a back-annotation exception`(@TempDir output: Path) {
        val witness = mock<InlinedWitness> {
            on {
                initializedState
            } doThrow IllegalStateException("broken witness")
        }
        val portfolio = ScriptedPortfolio { _, _ ->
            decisiveResult(VerificationVerdict.Failed).copy(inlinedWitness = witness)
        }
        val verifier = build(portfolio, output)
        val case = loadFirstCase()

        assertThatThrownBy {
            kotlinx.coroutines.runBlocking {
                verifier.verify(case)
            }
        }.isInstanceOf(BackAnnotationException::class.java).hasMessage("broken witness")
    }

    @Test
    suspend fun `report json carries phase durations, optimizer stats and model sizes`(@TempDir output: Path) {
        val portfolio = ScriptedPortfolio { _, _ -> decisiveResult(VerificationVerdict.Passed) }
        val case = loadFirstCase()

        val result = build(portfolio, output, artifacts = ArtifactConfig.ALL).verify(case)

        val report = Json.decodeFromString<VerificationReport>(caseReportPath(output, case).readText())
        assertThat(report.result.metrics.verifier.compilation).isEqualTo(result.metrics.verifier.compilation)
        assertThat(result.metrics.verifier.compilationDuration).isEqualTo(result.metrics.verifier.compilation.unfoldingDuration)
    }

    @Test
    suspend fun `report json carries the verdict's soundness direction and whether it is trusted`(@TempDir output: Path) {
        val portfolio = ScriptedPortfolio { _, _ ->
            decisiveResult(VerificationVerdict.Passed).copy(
                soundness = SoundnessDirection.UnderApproximating,
                isTrusted = false,
            )
        }
        val case = loadFirstCase()

        build(portfolio, output, artifacts = ArtifactConfig.ALL).verify(case)

        val result = Json.decodeFromString<VerificationReport>(caseReportPath(output, case).readText()).result
        assertThat(result.verdict).isEqualTo(VerificationVerdict.Passed)
        assertThat(result.soundness).isEqualTo(SoundnessDirection.UnderApproximating)
        assertThat(result.isTrusted).isFalse
    }

    @Test
    suspend fun `re-verifying a witness keeps the verify's artifacts and leaves the witness model unchanged`(@TempDir output: Path) {
        val portfolio = ScriptedPortfolio { _, _ -> decisiveResult(VerificationVerdict.Passed) }
        val verifier = build(portfolio, output)
        val case = loadFirstCase()
        verifier.verify(case)
        val caseDirectory = output.resolve(caseDirectoryName(case))
        val witness = loadWitness(case, caseDirectory.resolve("witness.oxsts"))
        val before = serializer.serialize(witness)

        val first = verifier.verify(witness)
        val second = verifier.verify(witness)

        assertThat(first.verdict).isEqualTo(VerificationVerdict.Passed)
        assertThat(second.verdict).isEqualTo(VerificationVerdict.Passed)
        assertThat(serializer.serialize(witness)).isEqualTo(before)
        assertThat(caseDirectory.resolve("report.json").exists()).isTrue
        assertThat(caseDirectory.resolve("witness.oxsts").exists()).isTrue
        assertThat(caseDirectory.resolve("validation").resolve("report.json").exists()).isTrue
    }

    private fun build(
        portfolio: VerificationPortfolio,
        output: Path,
        timeout: Duration = 30.seconds,
        artifacts: ArtifactConfig = ArtifactConfig.NONE,
    ): SemantifyrVerifier {
        return SemantifyrVerifier.builder()
            .injector(injector)
            .portfolio(portfolio)
            .artifacts(artifacts)
            .outputDirectory(output)
            .timeout(timeout)
            .optimization(OptimizationConfig.NONE)
            .build()
    }

    private fun loadFirstCase(): VerificationCase {
        val context = loader.loadStandaloneModel(modelPath)
        val classDeclaration = context.modelResources.asSequence()
            .flatMap { it.contents }
            .filterIsInstance<OxstsModelPackage>()
            .flatMap { it.declarations }
            .filterIsInstance<ClassDeclaration>()
            .first()
        return VerificationCase(
            qualifiedName = "semantifyr::verification::simple::${classDeclaration.name}",
            classDeclaration = classDeclaration,
        )
    }

    private fun loadWitness(case: VerificationCase, witnessPath: Path): InlinedOxsts {
        witnessPath.writeText(
            """
            witness of semantifyr::verification::simple::Incrementing

            var step: int := 0

            contains root: semantifyr::verification::simple::Incrementing

            init {
                assume(step == 0)
                inline root.init()
                assume(root.x == 1)
                step := 1
            }

            tran {
                assume(step == 1)
                inline root.main()
                assume(root.x == 2)
                step := 2
            }

            prop {
                root.prop()
            }
            """.trimIndent(),
        )
        val resourceSet = case.classDeclaration.eResource().resourceSet
        val resource = resourceSet.getResource(URI.createFileURI(witnessPath.toAbsolutePath().toString()), true)
        EcoreUtil.resolveAll(resourceSet)
        return resource.contents.filterIsInstance<InlinedOxsts>().single()
    }

    private fun decisiveResult(
        verdict: VerificationVerdict,
        backendId: String = "fake-backend",
    ): BackendVerificationResult {
        return BackendVerificationResult(
            metadata = VerificationMetadata(backendId = backendId, startedAt = Clock.System.now()),
            verdict = verdict,
            metrics = BackendMetrics(),
        )
    }

    private class RejectingVariablesContext(
        request: BackendVerificationRequest,
    ) : VerificationContext<Unit, Unit>("scripted", SoundnessDirection.Exact, request) {
        override val executor = object : BackendExecutor {
            override fun isAvailable(): Boolean {
                return true
            }
        }

        override val capabilityChecker = object : BackendCapabilityChecker() {
            override fun describeUnsupported(element: EObject): String? {
                if (element is VariableDeclaration) {
                    return "variables of type int"
                }
                return null
            }
        }

        override suspend fun prepare() {
        }

        override suspend fun run(artifacts: Unit): Unit? {
            return null
        }

        override fun interpret(rawVerdict: Unit?, artifacts: Unit): VerificationVerdict {
            return VerificationVerdict.Inconclusive
        }
    }

    private class ScriptedPortfolio(
        private val behavior: suspend (BackendVerificationRequest, ProgressContext) -> BackendVerificationResult,
    ) : VerificationPortfolio() {
        override val id: String = "scripted"
        override val displayName: String = "scripted"
        override val description: String = "test-only"
        override val executorKeys: Set<ExecutorKey<*>> = emptySet()
        override val legs: List<PortfolioLeg> = emptyList()

        var invocations: Int = 0
            private set

        override fun availability(environment: ExecutionEnvironment): AvailabilityReport {
            return AvailabilityReport.Available
        }

        override suspend fun verify(
            parentInjector: Injector,
            request: BackendVerificationRequest,
            gate: ConcurrencyGate,
            environment: ExecutionEnvironment,
            progress: ProgressContext,
        ): BackendVerificationResult {
            invocations++
            return behavior(request, progress)
        }
    }
}

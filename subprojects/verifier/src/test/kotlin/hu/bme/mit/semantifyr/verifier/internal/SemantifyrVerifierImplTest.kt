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
import hu.bme.mit.semantifyr.backend.witness.Fraction
import hu.bme.mit.semantifyr.backend.witness.InlinedWitness
import hu.bme.mit.semantifyr.backend.witness.WitnessState
import hu.bme.mit.semantifyr.backend.witness.linearInlinedWitness
import hu.bme.mit.semantifyr.compiler.pipeline.artifact.ArtifactConfig
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.OptimizationConfig
import hu.bme.mit.semantifyr.compiler.reader.SemantifyrLoader
import hu.bme.mit.semantifyr.oxsts.lang.tests.InjectWithOxsts
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ClassDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import hu.bme.mit.semantifyr.oxsts.model.oxsts.OxstsModelPackage
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration
import hu.bme.mit.semantifyr.verifier.FakeBackend
import hu.bme.mit.semantifyr.verifier.FakeConfig
import hu.bme.mit.semantifyr.verifier.ProgressContext
import hu.bme.mit.semantifyr.verifier.ReportKind
import hu.bme.mit.semantifyr.verifier.SemantifyrVerifier
import hu.bme.mit.semantifyr.verifier.VerificationCase
import hu.bme.mit.semantifyr.verifier.VerificationReport
import hu.bme.mit.semantifyr.verifier.caseDirectoryName
import hu.bme.mit.semantifyr.verifier.portfolio.ConcurrencyGate
import hu.bme.mit.semantifyr.verifier.portfolio.PortfolioLeg
import hu.bme.mit.semantifyr.verifier.portfolio.PortfolioTask
import hu.bme.mit.semantifyr.verifier.portfolio.SingleBackendPortfolio
import hu.bme.mit.semantifyr.verifier.portfolio.VerificationPortfolio
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.assertj.core.api.Assertions.assertThat
import org.eclipse.emf.common.util.URI
import org.eclipse.emf.ecore.EObject
import org.eclipse.emf.ecore.util.EcoreUtil
import org.eclipse.xtext.serializer.ISerializer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import java.nio.file.Path
import kotlin.coroutines.cancellation.CancellationException
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
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

    @TempDir
    lateinit var modelDirectory: Path

    @Test
    suspend fun `successful portfolio verdict propagates to VerificationResult`(@TempDir output: Path) {
        val backendResult = decisiveResult(VerificationVerdict.Passed, "fake-backend")
        val portfolio = ScriptedPortfolio { _, _ ->
            backendResult
        }

        val result = build(portfolio, output)
            .verify(loadFirstCase()).report.result

        assertThat(result.verdict).isEqualTo(VerificationVerdict.Passed)
        assertThat(result.metadata.backendId).isEqualTo("fake-backend")
        assertThat(portfolio.invocations).isEqualTo(1)
    }

    @Test
    fun `verifier timeout maps to Timeout`(@TempDir output: Path) = runTest {
        val portfolio = ScriptedPortfolio { _, _ ->
            delay(1.hours)
            decisiveResult(VerificationVerdict.Passed)
        }

        val result = build(portfolio, output, timeout = 10.seconds)
            .verify(loadFirstCase()).report.result

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

        val outcome = build(portfolio, output, timeout = 1.milliseconds).verify(case)
        val result = outcome.report.result

        assertThat(outcome.reportPath).exists()
        assertThat(result.verdict).isEqualTo(VerificationVerdict.Timeout)
        assertThat(result.message).isEqualTo("Timeout (1ms) exceeded while compiling.")
        assertThat(portfolio.invocations).isZero
    }

    @Test
    suspend fun `NotSupported names a flattened variable as declared in the source`(@TempDir output: Path) {
        val portfolio = ScriptedPortfolio { request, _ ->
            RejectingVariablesContext(request).execute()
        }

        val result = build(portfolio, output).verify(loadFirstCase()).report.result

        assertThat(result.verdict).isEqualTo(VerificationVerdict.NotSupported)
        assertThat(result.message).isEqualTo("scripted does not support simple.semantifyr:5: variables of type int (x)")
    }

    @Test
    suspend fun `BackendUnsupportedException from the portfolio yields NotSupported`(@TempDir output: Path) {
        val portfolio = ScriptedPortfolio { _, _ ->
            throw BackendUnsupportedException("engine cannot represent this model")
        }

        val result = build(portfolio, output)
            .verify(loadFirstCase()).report.result

        assertThat(result.verdict).isEqualTo(VerificationVerdict.NotSupported)
        assertThat(result.message).contains("engine cannot represent")
    }

    @Test
    suspend fun `arbitrary exception from the portfolio yields Errored`(@TempDir output: Path) {
        val portfolio = ScriptedPortfolio { _, _ ->
            error("portfolio blew up")
        }

        val result = build(portfolio, output)
            .verify(loadFirstCase()).report.result

        assertThat(result.verdict).isEqualTo(VerificationVerdict.Errored)
        assertThat(result.message).contains("portfolio blew up")
    }

    @Test
    suspend fun `CancellationException from the portfolio is rethrown`(@TempDir output: Path) {
        val portfolio = ScriptedPortfolio { _, _ ->
            throw CancellationException("caller cancelled")
        }

        val exception = assertThrows<CancellationException> {
            build(portfolio, output).verify(loadFirstCase())
        }

        assertThat(exception).hasMessage("caller cancelled")
    }

    @Test
    fun `a cancel through the progress cancels the running portfolio and ends the verification`(@TempDir output: Path) = runTest {
        val started = CompletableDeferred<Unit>()
        val portfolioCancelled = CompletableDeferred<Unit>()
        val portfolio = ScriptedPortfolio { _, _ ->
            started.complete(Unit)
            try {
                awaitCancellation()
            } catch (cancellation: CancellationException) {
                portfolioCancelled.complete(Unit)
                throw cancellation
            }
        }
        val progress = object : ProgressContext {
            override fun checkIsCancelled() {
                if (started.isCompleted) {
                    throw CancellationException("cancelled by the user")
                }
            }

            override fun reportProgress(message: String) {
            }
        }

        val exception = assertThrows<CancellationException> {
            build(portfolio, output).verify(loadFirstCase(), progress)
        }

        assertThat(exception).hasMessage("cancelled by the user")
        assertThat(portfolioCancelled.isCompleted).isTrue
    }

    @Test
    suspend fun `metrics record compilation portfolio and total durations`(@TempDir output: Path) {
        val portfolio = ScriptedPortfolio { _, _ ->
            delay(20.milliseconds)
            decisiveResult(VerificationVerdict.Passed)
        }

        val result = build(portfolio, output)
            .verify(loadFirstCase()).report.result

        assertThat(result.metrics.totalDuration).isGreaterThan(Duration.ZERO)
        assertThat(result.metrics.verifier.compilationDuration).isGreaterThan(Duration.ZERO)
        assertThat(result.metrics.verifier.portfolioDuration).isGreaterThan(Duration.ZERO)
        assertThat(result.metrics.backend).isNotNull
    }

    @Test
    suspend fun `failed metadata sets backend to null when the portfolio errors`(@TempDir output: Path) {
        val portfolio = ScriptedPortfolio { _, _ ->
            error("boom")
        }

        val result = build(portfolio, output)
            .verify(loadFirstCase()).report.result

        assertThat(result.metrics.backend).isNull()
    }

    @Test
    suspend fun `report json is written to a directory named after the model file and the qualified name of the case`(@TempDir output: Path) {
        val portfolio = ScriptedPortfolio { _, _ ->
            decisiveResult(VerificationVerdict.Passed)
        }
        val case = loadFirstCase()

        val outcome = build(portfolio, output, artifacts = ArtifactConfig.ALL).verify(case)

        assertThat(outcome.reportPath).isEqualTo(output.resolve("simple.${case.qualifiedName.replace("::", ".")}").resolve("report.json"))
    }

    @Test
    suspend fun `a verdict with a witness returns the run's report with its trace and writes the witness`(@TempDir output: Path) {
        val portfolio = ScriptedPortfolio { request, _ ->
            decisiveResult(VerificationVerdict.Passed).copy(
                inlinedWitness = linearInlinedWitness(request.inlinedOxsts, listOf(WitnessState(emptyList(), emptyList(), Fraction.ZERO), WitnessState(emptyList(), emptyList(), Fraction.ZERO))),
            )
        }

        val outcome = build(portfolio, output).verify(loadFirstCase())

        assertThat(outcome.report.kind).isEqualTo(ReportKind.Run)
        assertThat(outcome.report.trace?.steps).hasSize(1)
        assertThat(outcome.witness?.path).exists()
        assertThat(Json.decodeFromString<VerificationReport>(outcome.reportPath.readText())).isEqualTo(outcome.report)
    }

    @Test
    suspend fun `a run whose witness cannot be read back fails and the portfolio's other run answers`(@TempDir output: Path) {
        val readBackFailed = CompletableDeferred<Unit>()
        val broken = FakeBackend("broken") {
            decisiveResult(VerificationVerdict.Failed, "broken").copy(isTrusted = true, inlinedWitness = brokenWitness(readBackFailed))
        }
        val answering = FakeBackend("answering") {
            readBackFailed.await()
            decisiveResult(VerificationVerdict.Passed, "answering").copy(isTrusted = true)
        }
        val portfolio = RacingPortfolio(
            listOf(
                PortfolioTask(broken, FakeConfig("broken"), "broken"),
                PortfolioTask(answering, FakeConfig("answering"), "answering"),
            ),
        )

        val outcome = build(portfolio, output).verify(loadFirstCase())

        assertThat(outcome.report.result.verdict).isEqualTo(VerificationVerdict.Passed)
        assertThat(outcome.report.result.metadata.backendId).isEqualTo("answering")
        assertThat(outcome.reportPath).exists()
    }

    @Test
    suspend fun `a decided witness that cannot be read back fails the case with a report`(@TempDir output: Path) {
        val portfolio = ScriptedPortfolio { _, _ ->
            decisiveResult(VerificationVerdict.Failed).copy(inlinedWitness = brokenWitness())
        }

        val outcome = build(portfolio, output).verify(loadFirstCase())

        assertThat(outcome.report.result.verdict).isEqualTo(VerificationVerdict.Errored)
        assertThat(outcome.report.result.message).contains("broken witness")
        assertThat(outcome.report.trace).isNull()
        assertThat(outcome.witness).isNull()
        assertThat(outcome.reportPath).exists()
    }

    @Test
    suspend fun `a single run whose witness cannot be read back fails the case with a report`(@TempDir output: Path) {
        val broken = FakeBackend("broken") {
            decisiveResult(VerificationVerdict.Failed, "broken").copy(inlinedWitness = brokenWitness())
        }

        val outcome = build(SingleBackendPortfolio(broken, FakeConfig("broken")), output).verify(loadFirstCase())

        assertThat(outcome.report.result.verdict).isEqualTo(VerificationVerdict.Errored)
        assertThat(outcome.report.result.message).contains("could not be read back", "broken witness")
        assertThat(outcome.report.trace).isNull()
        assertThat(outcome.reportPath).exists()
    }

    @Test
    suspend fun `a read-back that outlasts the timeout is a Timeout without a trace`(@TempDir output: Path) {
        val portfolio = ScriptedPortfolio { _, _ ->
            decisiveResult(VerificationVerdict.Failed).copy(inlinedWitness = endlessWitness())
        }

        val outcome = build(portfolio, output, timeout = 5.seconds).verify(loadFirstCase())

        assertThat(outcome.report.result.verdict).isEqualTo(VerificationVerdict.Timeout)
        assertThat(outcome.report.result.message).contains("while back-annotating the witness of fake-backend's Failed verdict")
        assertThat(outcome.report.trace).isNull()
        assertThat(outcome.witness).isNull()
    }

    @Test
    suspend fun `the compilation duration is the compilation's unfolding duration`(@TempDir output: Path) {
        val portfolio = ScriptedPortfolio { _, _ ->
            decisiveResult(VerificationVerdict.Passed)
        }

        val result = build(portfolio, output, artifacts = ArtifactConfig.ALL).verify(loadFirstCase()).report.result

        assertThat(result.metrics.verifier.compilationDuration).isEqualTo(result.metrics.verifier.compilation.unfoldingDuration)
    }

    @Test
    suspend fun `the report carries the verdict's soundness direction and whether it is trusted`(@TempDir output: Path) {
        val portfolio = ScriptedPortfolio { _, _ ->
            decisiveResult(VerificationVerdict.Passed).copy(
                soundness = SoundnessDirection.UnderApproximating,
                isTrusted = false,
            )
        }

        val result = build(portfolio, output, artifacts = ArtifactConfig.ALL).verify(loadFirstCase()).report.result

        assertThat(result.verdict).isEqualTo(VerificationVerdict.Passed)
        assertThat(result.soundness).isEqualTo(SoundnessDirection.UnderApproximating)
        assertThat(result.isTrusted).isFalse
    }

    @Test
    suspend fun `a model that names its root instance otherwise fails the read-back of its witness`(@TempDir output: Path) {
        val portfolio = ScriptedPortfolio { request, _ ->
            decisiveResult(VerificationVerdict.Passed).copy(
                inlinedWitness = linearInlinedWitness(request.inlinedOxsts, listOf(WitnessState(emptyList(), emptyList(), Fraction.ZERO))),
            )
        }
        val case = loadFirstCase()
        val witness = loadWitness(case, output.resolve("renamed.semantifyr"), rootName = "system")

        val outcome = build(portfolio, output).verify(witness)

        assertThat(outcome.report.result.verdict).isEqualTo(VerificationVerdict.Errored)
        assertThat(outcome.report.result.message).contains("the root instance is named root by convention")
        assertThat(outcome.report.trace).isNull()
    }

    @Test
    suspend fun `re-verifying a witness keeps the verify's artifacts and leaves the witness model unchanged`(@TempDir output: Path) {
        val portfolio = ScriptedPortfolio { _, _ ->
            decisiveResult(VerificationVerdict.Passed)
        }
        val verifier = build(portfolio, output)
        val case = loadFirstCase()
        verifier.verify(case)
        val caseDirectory = output.resolve(caseDirectoryName(case))
        val witness = loadWitness(case, caseDirectory.resolve("witness.semantifyr"))
        val before = serializer.serialize(witness)

        val first = verifier.verify(witness)
        val second = verifier.verify(witness)

        assertThat(first.report.result.verdict).isEqualTo(VerificationVerdict.Passed)
        assertThat(first.report.kind).isEqualTo(ReportKind.Recheck)
        assertThat(second.report.result.verdict).isEqualTo(VerificationVerdict.Passed)
        assertThat(serializer.serialize(witness)).isEqualTo(before)
        assertThat(caseDirectory.resolve("report.json").exists()).isTrue
        assertThat(caseDirectory.resolve("witness.semantifyr").exists()).isTrue
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
        val modelPath = modelDirectory.resolve("simple.semantifyr")
        modelPath.writeText(MODEL)
        val context = loader.loadStandaloneModel(modelPath)
        val classDeclaration = context.modelResources.asSequence()
            .flatMap {
                it.contents
            }
            .filterIsInstance<OxstsModelPackage>()
            .flatMap {
                it.declarations
            }
            .filterIsInstance<ClassDeclaration>()
            .first()
        return VerificationCase(
            qualifiedName = "semantifyr::verification::simple::${classDeclaration.name}",
            classDeclaration = classDeclaration,
        )
    }

    private fun loadWitness(
        case: VerificationCase,
        witnessPath: Path,
        rootName: String = "root",
    ): InlinedOxsts {
        witnessPath.writeText(
            """
            witness of semantifyr::verification::simple::Incrementing

            var step: int := 0

            contains $rootName: semantifyr::verification::simple::Incrementing

            init {
                assume(step == 0)
                inline $rootName.init()
                assume($rootName.x == 1)
                step := 1
            }

            tran {
                assume(step == 1)
                inline $rootName.main()
                assume($rootName.x == 2)
                step := 2
            }

            prop {
                $rootName.prop()
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

    private class RejectingVariables : BackendCapabilityChecker() {
        override fun describeUnsupported(element: EObject): String? {
            if (element is VariableDeclaration) {
                return "variables of type int"
            }
            return null
        }
    }

    private class RejectingVariablesContext(
        request: BackendVerificationRequest,
    ) : VerificationContext<Unit, Unit>("scripted", SoundnessDirection.Exact, request, RejectingVariables()) {
        override val executor = object : BackendExecutor {
            override fun isAvailable(): Boolean {
                return true
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

    private fun brokenWitness(readBackFailed: CompletableDeferred<Unit> = CompletableDeferred()): InlinedWitness {
        return mock<InlinedWitness> {
            on {
                initializedState
            } doAnswer {
                readBackFailed.complete(Unit)
                throw IllegalStateException("broken witness")
            }
        }
    }

    private fun endlessWitness(): InlinedWitness {
        return mock<InlinedWitness> {
            on {
                initializedState
            } doAnswer {
                Thread.sleep(Long.MAX_VALUE)
                error("the read-back was not interrupted")
            }
        }
    }

    private class RacingPortfolio(
        private val tasks: List<PortfolioTask<*>>,
    ) : VerificationPortfolio() {
        override val id: String = "racing"
        override val displayName: String = "racing"
        override val description: String = "test-only"
        override val legs = tasks.map {
            it.leg
        }

        override fun availability(environment: ExecutionEnvironment): AvailabilityReport {
            return AvailabilityReport.Available
        }

        override suspend fun verify(
            parentInjector: Injector,
            request: BackendVerificationRequest,
            gate: ConcurrencyGate,
            environment: ExecutionEnvironment,
            progress: ProgressContext,
            readBack: suspend (BackendVerificationResult) -> BackendVerificationResult,
        ): BackendVerificationResult {
            val outcome = firstDecisive(startCase(parentInjector, request, gate, environment, progress, readBack)) {
                legs(tasks)
            }
            return outcome.toBackendVerificationResult(Clock.System.now())
        }
    }

    private class ScriptedPortfolio(
        private val behavior: suspend (BackendVerificationRequest, ProgressContext) -> BackendVerificationResult,
    ) : VerificationPortfolio() {
        override val id: String = "scripted"
        override val displayName: String = "scripted"
        override val description: String = "test-only"
        override val legs: List<PortfolioLeg<*>> = emptyList()

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
            readBack: suspend (BackendVerificationResult) -> BackendVerificationResult,
        ): BackendVerificationResult {
            invocations++
            return behavior(request, progress)
        }
    }

    companion object {
        private val MODEL = """
            package semantifyr::verification::simple

            @VerificationCase
            class Incrementing {
                var x: int := 0

                redefine init {
                    x := 1
                }

                redefine tran {
                    x := x + 1
                }

                redefine prop {
                    return EF x == 10
                }
            }
        """.trimIndent()
    }
}

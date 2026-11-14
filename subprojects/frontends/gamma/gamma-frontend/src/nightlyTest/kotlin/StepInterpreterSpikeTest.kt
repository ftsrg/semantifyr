/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import hu.bme.mit.semantifyr.backend.witness.InlinedWitness
import hu.bme.mit.semantifyr.backend.witness.InterpretedStep
import hu.bme.mit.semantifyr.backend.witness.StepInterpreter
import hu.bme.mit.semantifyr.backend.witness.WitnessState
import hu.bme.mit.semantifyr.compiler.pipeline.artifact.ArtifactConfig
import hu.bme.mit.semantifyr.compiler.pipeline.context.TransitionCallTraceMap
import hu.bme.mit.semantifyr.compiler.pipeline.expression.ExpressionEvaluationSerializer
import hu.bme.mit.semantifyr.compiler.pipeline.inlining.TransitionTracerNames
import hu.bme.mit.semantifyr.compiler.reader.SemantifyrLoader
import hu.bme.mit.semantifyr.oxsts.lang.OxstsStandaloneSetup
import hu.bme.mit.semantifyr.oxsts.lang.serializer.ExpressionSerializer
import hu.bme.mit.semantifyr.oxsts.model.oxsts.AssignmentOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.AssumptionOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ChoiceOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ClassDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.HavocOperation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Operation
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LiteralBoolean
import hu.bme.mit.semantifyr.oxsts.model.oxsts.VariableDeclaration
import hu.bme.mit.semantifyr.portfolios.Portfolios
import hu.bme.mit.semantifyr.verifier.SemantifyrVerifier
import hu.bme.mit.semantifyr.verifier.VerificationCase
import hu.bme.mit.semantifyr.verifier.portfolio.VerificationPortfolio
import org.eclipse.emf.ecore.resource.Resource
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeLines
import kotlin.time.Duration.Companion.minutes
import kotlin.time.measureTime

class StepInterpreterSpikeTest {

    private val injector = OxstsStandaloneSetup().createInjectorAndDoEMFRegistration()
    private val loader = injector.getInstance(SemantifyrLoader::class.java)
    private val expressionEvaluationSerializer = injector.getInstance(ExpressionEvaluationSerializer::class.java)
    private val expressionSerializer = injector.getInstance(ExpressionSerializer::class.java)
    private val outputDirectory = Path("build", "test-artifacts", "StepInterpreterSpikeTest")
    private val lines = mutableListOf<String>()
    private val exercised = sortedSetOf<String>()
    private val portfolios = mapOf(
        "theta-cegar-expl" to Portfolios.ThetaCegarExpl,
        "spin-safe-dfs" to Portfolios.SpinSafeDfs,
    )

    @Test
    suspend fun `interprets the steps of results without trace variables`() {
        val sysml = Path("build", "sysml-examples")
        val sysmlResource = load(sysml.resolve("library"), sysml.resolve("crossroads.oxsts"))
        val gammaResource = load(Path("..", "models", "libraries", "default"), Path("build", "compiled-examples", "Crossroads.oxsts"))
        for ((name, portfolio) in portfolios) {
            run("sysml", sysmlResource, "TrafficLightABehaviorNormalGreenUnreachableViolated", name, portfolio)
            run("gamma", gammaResource, "ACanBeGreen", name, portfolio)
        }
        lines += "exercised: ${exercised.sorted()}"
        outputDirectory.createDirectories()
        outputDirectory.resolve("result.txt").writeLines(lines)
    }

    private fun load(library: Path, model: Path): Resource {
        return loader.startContext().loadLibraries(library).loadModel(model).buildAndResolve().modelResources.single()
    }

    private class Run(
        val witness: InlinedWitness,
        val traces: TransitionCallTraceMap?,
    )

    private suspend fun verify(resource: Resource, case: String, portfolio: VerificationPortfolio, backend: String, tracing: String): Run {
        System.setProperty("semantifyr.spike.tracing", tracing)
        val caseClass = resource.allContents.asSequence().filterIsInstance<ClassDeclaration>().single {
            it.name == case
        }
        val verifier = SemantifyrVerifier.builder()
            .injector(injector)
            .portfolio(portfolio)
            .artifacts(ArtifactConfig.NONE)
            .timeout(5.minutes)
            .outputDirectory(outputDirectory.resolve(backend).resolve(tracing))
            .build()
        val result = verifier.verify(VerificationCase(caseClass.name, caseClass))
        val trace = checkNotNull(result.trace) {
            "no trace for $case with $tracing: ${result.verdict} ${result.message}"
        }
        return Run(checkNotNull(trace.inlinedWitness), trace.transitionCallTraces)
    }

    private fun states(witness: InlinedWitness): List<WitnessState> {
        return listOf(witness.initializedState) + witness.transitionStates
    }

    private suspend fun run(frontend: String, resource: Resource, case: String, backend: String, portfolio: VerificationPortfolio) {
        val prefix = "$frontend $case $backend"
        val untraced = verify(resource, case, portfolio, backend, "none")
        val traced = verify(resource, case, portfolio, backend, "library")
        System.clearProperty("semantifyr.spike.tracing")
        val model = traced.witness.inlinedOxsts
        val interpreter = injector.getInstance(StepInterpreter::class.java)
        val byName = model.variables.associateBy {
            it.name
        }
        val tracers = model.variables.filter {
            TransitionTracerNames.isTracerName(it.name)
        }
        val stateVariables = model.variables - tracers.toSet()
        val recorded = states(untraced.witness).map { state ->
            state.values.associate {
                val variable = byName[it.variable.name] ?: error("${it.variable.name} is not in the traced model")
                variable to interpreter.evaluateConstant(it.value)
            }
        }
        val recordedVariables = recorded.flatMap {
            it.keys
        }.toSet()
        lines += "$prefix: ${recorded.size} states, ${untraced.witness.inlinedOxsts.variables.size} variables without trace variables, ${stateVariables.size} state and ${tracers.size} trace variables with them, recorded ${recordedVariables.size}, unrecorded ${(stateVariables - recordedVariables).map { it.name }}"
        val tracedStates = states(traced.witness)
        val sameStates = tracedStates.size == recorded.size && tracedStates.zip(recorded).all { (tracedState, recordedState) ->
            tracedState.values.filter {
                !TransitionTracerNames.isTracerName(it.variable.name)
            }.all {
                recordedState[it.variable] == interpreter.evaluateConstant(it.value)
            }
        }
        lines += "$prefix: the traced run has ${tracedStates.size} states, the same states as the untraced run: $sameStates"
        val tracedCalls = tracedStates.map { state ->
            state.values.filter {
                TransitionTracerNames.isTracerName(it.variable.name) && (it.value as LiteralBoolean).isValue
            }.map {
                it.variable
            }.toSet()
        }
        var steps: List<InterpretedStep> = emptyList()
        val first = measureTime {
            steps = interpreter.interpret(model, recorded)
        }
        exercised += interpreter.exercised
        val repeated = List(3) {
            measureTime {
                interpreter.interpret(model, recorded)
            }
        }.sorted()
        val decided = steps.count {
            it.distinctSets.size == 1 && it.undecided == null
        }
        val ambiguous = steps.count {
            it.distinctSets.size > 1
        }
        val failed = steps.count {
            it.fittingPaths.isEmpty()
        }
        val undecided = steps.count {
            it.undecided != null
        }
        lines += "$prefix: steps ${steps.size} of ${recorded.size}, decided $decided, ambiguous $ambiguous, no fitting path $failed, with an undecided path $undecided, explored ${steps.sumOf { it.exploredPaths }} in all, most ${steps.maxOf { it.exploredPaths }} in a step, first run $first, median of three after it ${repeated[1]}"
        for (step in steps) {
            val tracedSet = if (sameStates) {
                tracedCalls[step.index]
            } else {
                null
            }
            val matches = tracedSet != null && tracedSet in step.distinctSets
            lines += "  step ${step.index}: sources ${step.sources}, explored ${step.exploredPaths}, fitting ${step.fittingPaths.size}, distinct sequences ${step.distinctSequences.size}, distinct sets ${step.distinctSets.size}, next states ${step.nextStates.size}, traced run's set among them $matches${step.undecided?.let { ", undecided: $it" } ?: ""}"
            if (step.distinctSets.size == 1 && tracedSet != null && !matches) {
                lines += "    interpreted ${describe(step.distinctSets.single(), traced.traces)}"
                lines += "    traced run  ${describe(tracedSet, traced.traces)}"
            }
            if (step.distinctSequences.size > 1) {
                val common = step.distinctSets.reduce { left, right ->
                    left intersect right
                }
                for ((index, sequence) in step.distinctSequences.withIndex()) {
                    val own = sequence.filter {
                        it !in common
                    }
                    val isTraced = sequence.toSet() == tracedSet
                    lines += "    sequence $index${if (isTraced) " (traced run's set)" else ""}: beyond the common ${common.size}: ${describe(own, traced.traces)}; order: ${sequence.joinToString(" ") { it.name.removePrefix("__transition_tracer") }}"
                }
            }
            if (step.fittingPaths.size > 1) {
                val decisionsByOperation = step.fittingPaths.map { path ->
                    path.decisions.toMap()
                }
                val operations = decisionsByOperation.flatMap {
                    it.keys
                }.distinct()
                val differing = operations.filter { operation ->
                    decisionsByOperation.map {
                        it[operation]
                    }.distinct().size > 1
                }
                for (operation in differing) {
                    val taken = decisionsByOperation.map {
                        it[operation]?.toString() ?: "-"
                    }
                    lines += "    differs at ${describeOperation(operation)}: ${taken.joinToString(" ")}"
                }
            }
        }
    }

    private fun describeOperation(operation: Operation): String {
        if (operation is HavocOperation) {
            return "havoc ${expressionSerializer.serialize(operation.reference)}"
        }
        val choice = operation as ChoiceOperation
        return "choice of ${choice.branches.size}: " + choice.branches.joinToString(" | ") { branch ->
            val first = branch.eAllContents().asSequence().filterIsInstance<Operation>().firstOrNull {
                it is AssumptionOperation || it is AssignmentOperation
            }
            when (first) {
                is AssumptionOperation -> "assume ${expressionSerializer.serialize(first.expression)}"
                is AssignmentOperation -> "${expressionSerializer.serialize(first.reference)} := ${expressionSerializer.serialize(first.expression)}"
                else -> "empty"
            }.take(160)
        }
    }

    private fun describe(calls: Collection<VariableDeclaration>, traces: TransitionCallTraceMap?): String {
        if (traces == null) {
            return calls.joinToString(", ") {
                it.name
            }
        }
        return calls.joinToString(", ") {
            val trace = traces.getTransitionCallTrace(it)
            val arguments = trace.arguments.joinToString(", ") { argument ->
                expressionEvaluationSerializer.serialize(argument.evaluation)
            }
            "${expressionEvaluationSerializer.serialize(trace.self)}.${trace.transitionDeclaration.name}($arguments)"
        }
    }
}

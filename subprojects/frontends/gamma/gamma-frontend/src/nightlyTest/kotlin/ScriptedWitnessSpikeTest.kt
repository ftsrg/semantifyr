/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import hu.bme.mit.semantifyr.backend.VerificationVerdict
import hu.bme.mit.semantifyr.compiler.pipeline.artifact.ArtifactConfig
import hu.bme.mit.semantifyr.compiler.pipeline.inlining.TransitionTracerNames
import hu.bme.mit.semantifyr.compiler.pipeline.optimization.OptimizationConfig
import hu.bme.mit.semantifyr.compiler.reader.SemantifyrLoader
import hu.bme.mit.semantifyr.oxsts.lang.OxstsStandaloneSetup
import hu.bme.mit.semantifyr.oxsts.lang.naming.OxstsQualifiedNameProvider
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ClassDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import hu.bme.mit.semantifyr.portfolios.Portfolios
import hu.bme.mit.semantifyr.verifier.SemantifyrVerifier
import hu.bme.mit.semantifyr.verifier.VerificationCase
import hu.bme.mit.semantifyr.verifier.assumePortfolioAvailable
import hu.bme.mit.semantifyr.verifier.portfolio.VerificationPortfolio
import hu.bme.mit.semantifyr.verifier.witness.CallTrace
import hu.bme.mit.semantifyr.verifier.witness.TraceEntry
import hu.bme.mit.semantifyr.verifier.witness.WitnessState
import hu.bme.mit.semantifyr.verifier.witness.WitnessStateStep
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.copyTo
import kotlin.io.path.createDirectories
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.io.path.writeLines
import kotlin.io.path.writeText

class ScriptedWitnessSpikeTest {

    private val injector = OxstsStandaloneSetup().createInjectorAndDoEMFRegistration()
    private val loader = injector.getInstance(SemantifyrLoader::class.java)
    private val qualifiedNameProvider = injector.getInstance(OxstsQualifiedNameProvider::class.java)
    private val outputDirectory = Path("build", "test-artifacts", "ScriptedWitnessSpikeTest")

    private class Patch(
        val file: String,
        val before: String,
        val after: String,
    )

    private class Round(
        val name: String,
        val patches: List<Patch>,
        val soundness: Boolean = false,
    )

    private class ScriptedCase(
        val label: String,
        val library: Path,
        val model: Path,
        val className: String,
        val portfolio: VerificationPortfolio,
    )

    private class Reached(
        val witnessState: WitnessState,
        val callTrace: CallTrace,
    )

    private class Prefix(
        val reachedStep: Int,
        val reached: Reached,
    )

    private class IllegalScript(
        val description: String,
        val calls: List<List<TraceEntry>>,
    )

    @Test
    suspend fun `scripts the SysML crossroads witness`() {
        val scriptedCase = ScriptedCase(
            "sysml-crossroads",
            Path("build", "sysml-crossroads", "library"),
            Path("build", "sysml-crossroads", "crossroads.oxsts"),
            "TrafficLightABehaviorNormalGreenUnreachableViolated",
            Portfolios.ThetaCegarExpl,
        )
        val resetsTraced = listOf(
            traced("Ports.oxsts", "    tran resetIncomingItem() {"),
            traced("Ports.oxsts", "    tran resetOutgoingItem() {"),
        )
        val flowTransfer = Patch(
            "Parts.oxsts",
            """
            redefine tran {
                if (inputPort.hasOutgoing()) {
                    inline outputPort.setIncomingItem(inputPort.outgoingItem as Item)
                }
            }
            """.trimIndent().prependIndent("    "),
            """
            redefine tran {
                if (inputPort.hasOutgoing()) {
                    inline transfer()
                }
            }

            @Trace
            tran transfer() {
                inline outputPort.setIncomingItem(inputPort.outgoingItem as Item)
            }
            """.trimIndent().prependIndent("    "),
        )
        val rounds = listOf(
            Round("recorded", emptyList(), soundness = true),
            Round("runtime-argument", listOf(traced("Ports.oxsts", "    tran setIncomingItem(item: Item) {"))),
            Round("resets", resetsTraced),
            Round("closed", resetsTraced + flowTransfer, soundness = true),
            Round("closed-environment", resetsTraced + flowTransfer + traced("Ports.oxsts", "    tran havocIncomingItem() {")),
        )
        script(scriptedCase, rounds)
    }

    @Test
    suspend fun `scripts the Gamma crossroads witness`() {
        val scriptedCase = ScriptedCase(
            "gamma-crossroads",
            Path("..", "models", "libraries", "default"),
            Path("build", "compiled-examples", "Crossroads.oxsts"),
            "ControllerOperatingStoppingAReachable",
            Portfolios.ThetaBoundedKInduction,
        )
        val resetsTraced = listOf(
            traced("Ports.oxsts", "    tran resetInputEvent() {"),
            traced("Ports.oxsts", "    tran resetOutputEvent() {"),
        )
        val rounds = listOf(
            Round("recorded", emptyList(), soundness = true),
            Round("runtime-argument", listOf(traced("Ports.oxsts", "    tran setInputEvent(event: Event) {"))),
            Round("resets", resetsTraced),
            Round("closed", resetsTraced + traced("Components.oxsts", "    tran doTransferEvent() {"), soundness = true),
        )
        script(scriptedCase, rounds)
    }

    private fun traced(file: String, declaration: String): Patch {
        return Patch(file, declaration, "    @Trace\n$declaration")
    }

    private suspend fun script(scriptedCase: ScriptedCase, rounds: List<Round>) {
        assumePortfolioAvailable(scriptedCase.portfolio)
        val lines = mutableListOf<String>()
        for (round in rounds) {
            lines += ""
            lines += "== round ${round.name}: ${round.patches.size} patches"
            runRound(scriptedCase, round, lines)
            outputDirectory.createDirectories()
            outputDirectory.resolve("${scriptedCase.label}.txt").writeLines(lines)
        }
        println(lines.joinToString("\n"))
    }

    private suspend fun runRound(scriptedCase: ScriptedCase, round: Round, lines: MutableList<String>) {
        val roundDirectory = outputDirectory.resolve(scriptedCase.label).resolve(round.name)
        val library = patchedLibrary(scriptedCase.library, roundDirectory.resolve("library"), round.patches)
        val loaded = try {
            loader.startContext()
                .loadLibraries(library)
                .loadModel(scriptedCase.model)
                .buildAndResolve()
        } catch (exception: IllegalStateException) {
            lines += "library rejected: ${exception.message?.lines()?.last()?.substringAfter(": ERROR ")}"
            return
        }
        val caseClass = loaded.modelResources
            .single()
            .allContents
            .asSequence()
            .filterIsInstance<ClassDeclaration>()
            .single {
                it.name == scriptedCase.className
            }
        val className = qualifiedNameProvider.getFullyQualifiedNameString(caseClass)
        val result = verifier(scriptedCase.portfolio, roundDirectory.resolve("original"), OptimizationConfig.ALL)
            .verify(VerificationCase(caseClass.name, caseClass))
        val trace = checkNotNull(result.trace) {
            "no trace: ${result.verdict} ${result.message}"
        }
        val tracers = checkNotNull(trace.inlinedWitness).inlinedOxsts.variables.count {
            TransitionTracerNames.isTracerName(it.name)
        }
        lines += "original: ${result.verdict}, $tracers tracers in the flattened model, ${trace.callTrace.steps.size} steps"
        val recordedStates = listOf(trace.witnessState.initializedStep) + trace.witnessState.steps
        val recordedCalls = trace.callTrace.steps.map {
            it.traces
        }
        for ((index, calls) in recordedCalls.withIndex()) {
            lines += "  step ${index + 1} recorded: ${callsLine(calls)}"
        }
        val variables = recordedStates.first().values.map {
            it.variable
        }

        val stepCount = recordedCalls.size
        roundDirectory.createDirectories()
        roundDirectory.resolve("scripted.witness.oxsts").writeText(scriptText(className, scriptSteps(recordedCalls), null, stepCount + 1))
        val scriptVerifier = verifier(scriptedCase.portfolio, roundDirectory.resolve("script"), OptimizationConfig.NONE)
        val prefix = longestPrefix(scriptVerifier, library, scriptedCase.model, roundDirectory, className, scriptSteps(recordedCalls))
        val reached = prefix.reached
        val executedSteps = prefix.reachedStep - 1
        lines += "bare script: runs $executedSteps of $stepCount steps"
        if (executedSteps < stepCount) {
            lines += "  step ${executedSteps + 1} cannot run: ${callsLine(recordedCalls[executedSteps])}"
        }
        val scriptedStates = listOf(reached.witnessState.initializedStep) + reached.witnessState.steps
        for (index in 0..executedSteps) {
            val differences = differences(recordedStates[index], scriptedStates[index], variables)
            val calls = if (index == 0) {
                ""
            } else {
                val scriptedCalls = reached.callTrace.steps[index - 1].traces
                if (scriptedCalls == recordedCalls[index - 1]) {
                    ", calls as recorded"
                } else {
                    ", calls differ: ${callsLine(scriptedCalls)}"
                }
            }
            if (differences.isEmpty()) {
                lines += "  state $index: as recorded$calls"
            } else {
                lines += "  state $index: ${differences.joinToString("; ")}$calls"
            }
        }

        val pinned = reach(scriptVerifier, library, scriptedCase.model, roundDirectory.resolve("pinned"), className, scriptSteps(recordedCalls), recordedStates, stepCount + 1)
        lines += "script pinned to the recorded states: ${reachedText(pinned)}"

        if (round.soundness) {
            soundness(scriptedCase, library, roundDirectory, className, recordedCalls, recordedStates, variables, scriptVerifier, lines)
        }
    }

    private suspend fun soundness(
        scriptedCase: ScriptedCase,
        library: Path,
        roundDirectory: Path,
        className: String,
        recordedCalls: List<List<TraceEntry>>,
        recordedStates: List<WitnessStateStep>,
        variables: List<String>,
        scriptVerifier: SemantifyrVerifier,
        lines: MutableList<String>,
    ) {
        val firstFiring = recordedCalls.indexOfFirst {
            it.any { entry ->
                isFire(entry)
            }
        }
        val dropped = recordedCalls.take(firstFiring) + listOf(
            recordedCalls[firstFiring].filterNot {
                isFire(it)
            },
        )
        val laterFire = recordedCalls.drop(1).flatten().first { entry ->
            isFire(entry) && recordedCalls[0].none {
                it.self == entry.self
            }
        }
        val added = listOf(listOf(laterFire) + recordedCalls[0])
        val illegalScripts = listOf(
            IllegalScript("fires of step ${firstFiring + 1} dropped", dropped),
            IllegalScript("step 1 with ${laterFire.self} fired", added),
        )
        for ((index, illegal) in illegalScripts.withIndex()) {
            val calls = illegal.calls
            val directory = roundDirectory.resolve("illegal-$index")
            val scripted = reach(scriptVerifier, library, scriptedCase.model, directory.resolve("script"), className, scriptSteps(calls), null, calls.size + 1)
            lines += "illegal script (${illegal.description}): ${reachedText(scripted)}"
            if (scripted == null) {
                continue
            }
            val scriptedStates = listOf(scripted.witnessState.initializedStep) + scripted.witnessState.steps
            val illegalStates = scriptedStates.map { state ->
                WitnessStateStep(
                    state.values.filter {
                        it.variable in variables
                    },
                )
            }
            val lastDifferences = differences(recordedStates[calls.size], illegalStates[calls.size], variables)
            lines += "  its last state against the recorded one: ${lastDifferences.joinToString("; ").ifEmpty {
                "equal"
            }}"
            val mainSteps = List(calls.size) {
                listOf("inline root.main()")
            }
            val byMain = reach(scriptVerifier, library, scriptedCase.model, directory.resolve("main"), className, mainSteps, illegalStates, calls.size + 1)
            lines += "  main through its states: ${reachedText(byMain)}"
        }
        val mainSteps = List(firstFiring + 1) {
            listOf("inline root.main()")
        }
        val control = reach(scriptVerifier, library, scriptedCase.model, roundDirectory.resolve("control"), className, mainSteps, recordedStates, firstFiring + 2)
        lines += "control, main through the recorded states of ${firstFiring + 1} steps: ${reachedText(control)}"
    }

    private fun isFire(entry: TraceEntry): Boolean {
        return entry.calledTransition.endsWith("::fire")
    }

    private fun reachedText(reached: Reached?): String {
        return if (reached == null) {
            "unreachable"
        } else {
            "reachable"
        }
    }

    private fun differences(recorded: WitnessStateStep, scripted: WitnessStateStep, variables: List<String>): List<String> {
        val recordedValues = recorded.values.associate {
            it.variable to it.value
        }
        val scriptedValues = scripted.values.associate {
            it.variable to it.value
        }
        return variables.mapNotNull {
            val recordedValue = recordedValues[it]
            val scriptedValue = scriptedValues[it]
            if (recordedValue == scriptedValue) {
                null
            } else {
                "$it recorded $recordedValue scripted $scriptedValue"
            }
        }
    }

    private fun scriptSteps(calls: List<List<TraceEntry>>): List<List<String>> {
        return calls.map { step ->
            step.map {
                "inline ${callLine(it)}"
            }
        }
    }

    private fun callsLine(calls: List<TraceEntry>): String {
        return calls.joinToString(" ; ") {
            callLine(it)
        }
    }

    private fun callLine(entry: TraceEntry): String {
        val arguments = entry.arguments.joinToString(", ") {
            it.value
        }
        return "${entry.self}.${entry.calledTransition.substringAfterLast("::")}($arguments)"
    }

    private suspend fun longestPrefix(
        scriptVerifier: SemantifyrVerifier,
        library: Path,
        model: Path,
        roundDirectory: Path,
        className: String,
        steps: List<List<String>>,
    ): Prefix {
        val results = mutableMapOf<Int, Reached>()
        var low = 1
        var high = steps.size + 1
        while (low < high) {
            val middle = (low + high + 1) / 2
            val reached = reach(scriptVerifier, library, model, roundDirectory.resolve("bare-$middle"), className, steps, null, middle)
            if (reached == null) {
                high = middle - 1
            } else {
                results[middle] = reached
                low = middle
            }
        }
        val reached = results[low] ?: checkNotNull(reach(scriptVerifier, library, model, roundDirectory.resolve("bare-$low"), className, steps, null, low)) {
            "the script's init does not run"
        }
        return Prefix(low, reached)
    }

    private suspend fun reach(
        scriptVerifier: SemantifyrVerifier,
        library: Path,
        model: Path,
        directory: Path,
        className: String,
        steps: List<List<String>>,
        states: List<WitnessStateStep>?,
        target: Int,
    ): Reached? {
        directory.createDirectories()
        val witnessFile = directory.resolve("witness.oxsts")
        witnessFile.writeText(scriptText(className, steps, states, target))
        val inlinedOxsts = loader.startContext()
            .loadLibraries(library)
            .loadModel(model)
            .loadModel(witnessFile)
            .buildAndResolve()
            .modelResources
            .flatMap {
                it.contents
            }
            .filterIsInstance<InlinedOxsts>()
            .single()
        val result = scriptVerifier.verify(inlinedOxsts)
        return when (result.verdict) {
            VerificationVerdict.Failed -> {
                val trace = checkNotNull(result.trace)
                Reached(trace.witnessState, trace.callTrace)
            }
            VerificationVerdict.Passed -> {
                null
            }
            else -> {
                error("${result.verdict} on $witnessFile: ${result.message}")
            }
        }
    }

    private fun scriptText(className: String, steps: List<List<String>>, states: List<WitnessStateStep>?, target: Int): String {
        return buildString {
            appendLine("witness of $className")
            appendLine()
            appendLine("var step: int := 0")
            appendLine()
            appendLine("contains root: $className")
            appendLine()
            appendLine("init {")
            appendLine("    assume(step == 0)")
            appendLine("    inline root.init()")
            appendAssumptions(states?.get(0))
            appendLine("    step := 1")
            appendLine("}")
            appendLine()
            val executed = steps.take(target - 1)
            if (executed.isEmpty()) {
                appendLine("tran {")
                appendLine("    assume(false)")
                appendLine("}")
            }
            for ((index, operations) in executed.withIndex()) {
                if (index == 0) {
                    appendLine("tran {")
                } else {
                    appendLine("} or {")
                }
                appendLine("    assume(step == ${index + 1})")
                for (operation in operations) {
                    appendLine("    $operation")
                }
                appendAssumptions(states?.get(index + 1))
                appendLine("    step := ${index + 2}")
                if (index == executed.lastIndex) {
                    appendLine("}")
                }
            }
            appendLine()
            appendLine("prop {")
            appendLine("    AG (step != $target)")
            appendLine("}")
        }
    }

    private fun StringBuilder.appendAssumptions(state: WitnessStateStep?) {
        if (state == null) {
            return
        }
        for (value in state.values) {
            appendLine("    assume(${value.variable} == ${value.value})")
        }
    }

    private fun patchedLibrary(source: Path, target: Path, patches: List<Patch>): Path {
        target.createDirectories()
        for (file in source.listDirectoryEntries("*.oxsts")) {
            file.copyTo(target.resolve(file.name), overwrite = true)
        }
        for (patch in patches) {
            val file = target.resolve(patch.file)
            val text = file.readText()
            check(text.contains(patch.before)) {
                "patch target not found in ${patch.file}: ${patch.before}"
            }
            file.writeText(text.replace(patch.before, patch.after))
        }
        return target
    }

    private fun verifier(portfolio: VerificationPortfolio, directory: Path, optimization: OptimizationConfig): SemantifyrVerifier {
        return SemantifyrVerifier.builder()
            .injector(injector)
            .portfolio(portfolio)
            .artifacts(ArtifactConfig.NONE)
            .optimization(optimization)
            .outputDirectory(directory)
            .build()
    }
}

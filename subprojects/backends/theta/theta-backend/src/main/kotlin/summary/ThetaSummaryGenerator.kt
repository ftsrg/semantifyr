/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.theta.summary

import com.google.inject.Inject
import hu.bme.mit.semantifyr.backends.theta.backannotation.CexReader
import hu.bme.mit.semantifyr.backends.theta.backannotation.witness.cex.CexAssumptionWitnessTransformer
import hu.bme.mit.semantifyr.backends.theta.backannotation.witness.oxsts.InlinedOxstsAssumptionWitnessTransformer
import hu.bme.mit.semantifyr.backends.theta.backannotation.witness.xsts.XstsAssumptionWitnessTransformer
import hu.bme.mit.semantifyr.backends.theta.transformation.xsts.OxstsTransformer
import hu.bme.mit.semantifyr.backends.theta.wrapper.execution.ThetaExecutionSpecification
import hu.bme.mit.semantifyr.backends.theta.wrapper.execution.ThetaXstsExecutor
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ClassDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import hu.bme.mit.semantifyr.semantics.compilation.OxstsClassInliner
import hu.bme.mit.semantifyr.semantics.progress.ProgressContext
import hu.bme.mit.semantifyr.semantics.witness.AssumptionWitnessBackAnnotator
import hu.bme.mit.semantifyr.semantics.witness.OxstsClassAssumptionWitnessTransformer
import hu.bme.mit.semantifyr.xsts.lang.xsts.XstsModel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.eclipse.emf.common.util.URI
import java.io.File
import kotlin.io.path.pathString
import kotlin.time.Duration.Companion.minutes

class ThetaSummaryGenerator {

    @Inject
    private lateinit var oxstsClassInliner: OxstsClassInliner

    @Inject
    private lateinit var assumptionWitnessBackAnnotator: AssumptionWitnessBackAnnotator

    @Inject
    private lateinit var oxstsClassAssumptionWitnessTransformer: OxstsClassAssumptionWitnessTransformer

    @Inject
    private lateinit var oxstsTransformer: OxstsTransformer

    @Inject
    private lateinit var cexReader: CexReader

    @Inject
    private lateinit var cexAssumptionWitnessTransformer: CexAssumptionWitnessTransformer

    @Inject
    private lateinit var xstsAssumptionWitnessTransformer: XstsAssumptionWitnessTransformer

    @Inject
    private lateinit var inlinedOxstsAssumptionWitnessTransformer: InlinedOxstsAssumptionWitnessTransformer


    fun inlineClass(classDeclaration: ClassDeclaration): InlinedOxsts {
        return oxstsClassInliner.inline(classDeclaration)
    }

    fun transformToXsts(inlinedOxsts: InlinedOxsts): XstsModel {
        return oxstsTransformer.transform(inlinedOxsts)
    }

    fun runThetaTracegen(inlinedOxsts: InlinedOxsts, xstsModel: XstsModel) {
        xstsModel.property = null
        xstsModel.eResource().save(emptyMap<Any, Any>())

        val path = xstsModel.eResource().uri.path().removeSuffix(".xsts")
        val workingDirectory = File(path.replaceAfterLast(File.separator, ""))

        val thetaExecutor = ThetaXstsExecutor.Companion.of()

        val command = listOf(
            "TRACEGEN",
            "--model", "inlined.xsts",
            "--flatten-depth", "0",
            "--stacktrace",
            "--trace-dir", "traces",
        )

        val thetaExecutionSpecification = ThetaExecutionSpecification(
            workingDirectory = workingDirectory,
            command = command,
            logFile = workingDirectory.resolve("tracing/log.out"),
            errorFile = workingDirectory.resolve("tracing/log.err"),
        )

        val result = runBlocking {
            withTimeout(10.minutes) { thetaExecutor.execute(thetaExecutionSpecification) }
        }

        if (result.exitCode == 0) {
            workingDirectory.resolve("traces").walkTopDown().filter {
                it.extension == "trace"
            }.forEach {
                backannotateTrace(it, xstsModel, inlinedOxsts)
            }
        }
    }

    fun backannotateTrace(file: File, xstsModel: XstsModel, inlinedOxsts: InlinedOxsts) {
        val cexPath = file.toPath()
        val cexModel = cexReader.loadCexModel(cexPath)
        val cexWitness = cexAssumptionWitnessTransformer.transform(cexModel)
        val xstsWitness = xstsAssumptionWitnessTransformer.transform(xstsModel, cexWitness)
        val inlinedOxstsWitness = inlinedOxstsAssumptionWitnessTransformer.transform(inlinedOxsts, xstsWitness)
        val classWitness = oxstsClassAssumptionWitnessTransformer.transform(inlinedOxstsWitness)
        val witnessInlinedOxsts = assumptionWitnessBackAnnotator.createWitnessInlinedOxsts(classWitness)

        val resourceSet = inlinedOxsts.eResource().resourceSet
        val path = cexPath.pathString.replace(".trace", ".oxsts")
        val uri = URI.createURI(path)
        resourceSet.getResource(uri, false)?.delete(mutableMapOf<Any, Any>())
        val resource = resourceSet.createResource(uri)
        resource.contents += witnessInlinedOxsts
        resource.save(emptyMap<Any, Any>())
    }

    fun createSummary(progressContext: ProgressContext, classDeclaration: ClassDeclaration) {
        progressContext.reportProgress("Inlining class")

        val inlinedOxsts = inlineClass(classDeclaration)

        progressContext.checkIsCancelled()
        progressContext.reportProgress("Transforming to Xsts")

        val xstsModel = transformToXsts(inlinedOxsts)

        progressContext.checkIsCancelled()
        progressContext.reportProgress("Running Theta Tracegen")

        runThetaTracegen(inlinedOxsts, xstsModel)
    }

}

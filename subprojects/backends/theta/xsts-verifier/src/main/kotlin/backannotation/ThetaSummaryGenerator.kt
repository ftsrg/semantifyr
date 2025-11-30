/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.theta.verification.backannotation

import com.google.inject.Inject
import hu.bme.mit.semantifyr.backends.theta.verification.backannotation.witness.cex.CexAssumptionWitnessTransformer
import hu.bme.mit.semantifyr.backends.theta.verification.backannotation.witness.oxsts.InlinedOxstsAssumptionWitnessTransformer
import hu.bme.mit.semantifyr.backends.theta.verification.backannotation.witness.xsts.XstsAssumptionWitnessTransformer
import hu.bme.mit.semantifyr.backends.theta.verification.transformation.xsts.OxstsTransformer
import hu.bme.mit.semantifyr.backends.theta.verification.utils.ensureExistsOutputStream
import hu.bme.mit.semantifyr.backends.theta.wrapper.execution.ThetaExecutionSpecification
import hu.bme.mit.semantifyr.backends.theta.wrapper.execution.ThetaXstsExecutorProvider
import hu.bme.mit.semantifyr.backends.theta.wrapper.utils.CexReader
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ClassDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import hu.bme.mit.semantifyr.semantics.transformation.OxstsClassInliner
import hu.bme.mit.semantifyr.semantics.transformation.ProgressContext
import hu.bme.mit.semantifyr.semantics.transformation.backannotation.AssumptionWitnessBackAnnotator
import hu.bme.mit.semantifyr.semantics.transformation.backannotation.OxstsClassAssumptionWitnessTransformer
import hu.bme.mit.semantifyr.semantics.verification.VerificationDispatcher
import hu.bme.mit.semantifyr.xsts.lang.xsts.XstsModel
import org.eclipse.emf.common.util.URI
import java.io.File
import kotlin.io.path.pathString

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
    private lateinit var thetaXstsExecutorProvider: ThetaXstsExecutorProvider

    @Inject
    private lateinit var verificationDispatcher: VerificationDispatcher

    @Inject
    private lateinit var cexReader: CexReader

    @Inject
    private lateinit var cexAssumptionWitnessTransformer: CexAssumptionWitnessTransformer

    @Inject
    private lateinit var xstsAssumptionWitnessTransformer: XstsAssumptionWitnessTransformer

    @Inject
    private lateinit var inlinedOxstsAssumptionWitnessTransformer: InlinedOxstsAssumptionWitnessTransformer


    fun inlineClass(progressContext: ProgressContext, classDeclaration: ClassDeclaration): InlinedOxsts {
        return oxstsClassInliner.inline(progressContext, classDeclaration)
    }

    fun transformToXsts(progressContext: ProgressContext, inlinedOxsts: InlinedOxsts): XstsModel {
        return oxstsTransformer.transform(inlinedOxsts, progressContext)
    }

    fun runThetaTracegen(progressContext: ProgressContext, inlinedOxsts: InlinedOxsts, xstsModel: XstsModel) {
        xstsModel.property = null
        xstsModel.eResource().save(emptyMap<Any, Any>())

        val path = xstsModel.eResource().uri.path().removeSuffix(".xsts")
        val workingDirectory = File(path.replaceAfterLast(File.separator, ""))

        val thetaExecutor = thetaXstsExecutorProvider.getExecutor()

        val command = listOf(
            "TRACEGEN",
            "--model", "inlined.xsts",
            "--flatten-depth", "0",
            "--stacktrace",
            "--trace-dir", "traces"
        )

        val logStream = workingDirectory.resolve("tracing/log.out").ensureExistsOutputStream()
        val errorStream = workingDirectory.resolve("tracing/log.err").ensureExistsOutputStream()

        val thetaExecutionSpecification = ThetaExecutionSpecification(
            workingDirectory,
            command,
            logStream,
            errorStream,
        )

        val result = verificationDispatcher.runBlocking {
            thetaExecutor.execute(thetaExecutionSpecification)
        }

        if (result.exitCode == 0) {
            progressContext.reportProgress("Creating witnesses")
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

        val inlinedOxsts = inlineClass(progressContext, classDeclaration)

        progressContext.checkIsCancelled()
        progressContext.reportProgress("Transforming to Xsts")

        val xstsModel = transformToXsts(progressContext, inlinedOxsts)

        progressContext.checkIsCancelled()
        progressContext.reportProgress("Running Theta Tracegen")

        runThetaTracegen(progressContext, inlinedOxsts, xstsModel)
    }

}

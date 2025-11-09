/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.theta.verification

import com.google.inject.Inject
import hu.bme.mit.semantifyr.backends.theta.verification.backannotation.CexReader
import hu.bme.mit.semantifyr.backends.theta.verification.backannotation.witness.cex.CexAssumptionWitnessTransformer
import hu.bme.mit.semantifyr.backends.theta.verification.backannotation.witness.oxsts.InlinedOxstsAssumptionWitnessTransformer
import hu.bme.mit.semantifyr.backends.theta.verification.backannotation.witness.xsts.XstsAssumptionWitnessTransformer
import hu.bme.mit.semantifyr.backends.theta.verification.execution.ThetaErrorVerificationResult
import hu.bme.mit.semantifyr.backends.theta.verification.execution.ThetaPortfolioExecutor
import hu.bme.mit.semantifyr.backends.theta.verification.execution.ThetaSafeVerificationResult
import hu.bme.mit.semantifyr.backends.theta.verification.execution.ThetaUnsafeVerificationResult
import hu.bme.mit.semantifyr.backends.theta.verification.execution.ThetaVerificationResult
import hu.bme.mit.semantifyr.backends.theta.verification.transformation.xsts.OxstsTransformer
import hu.bme.mit.semantifyr.oxsts.lang.library.builtin.BuiltinAnnotationHandler
import hu.bme.mit.semantifyr.oxsts.lang.library.builtin.VerificationCaseExpectedResult
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ClassDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import hu.bme.mit.semantifyr.semantics.transformation.ProgressContext
import hu.bme.mit.semantifyr.semantics.transformation.injection.scope.CompilationScoped
import hu.bme.mit.semantifyr.semantics.verification.AbstractOxstsVerifier
import hu.bme.mit.semantifyr.semantics.verification.VerificationCaseRunResult
import hu.bme.mit.semantifyr.semantics.verification.VerificationResult
import hu.bme.mit.semantifyr.xsts.lang.xsts.XstsModel
import java.io.File
import kotlin.io.path.Path

@CompilationScoped
open class ThetaVerifier : AbstractOxstsVerifier() {

    @Inject
    private lateinit var oxstsTransformer: OxstsTransformer

    @Inject
    private lateinit var builtinAnnotationHandler: BuiltinAnnotationHandler

    @Inject
    private lateinit var cexReader: CexReader

    @Inject
    private lateinit var cexAssumptionWitnessTransformer: CexAssumptionWitnessTransformer

    @Inject
    private lateinit var xstsAssumptionWitnessTransformer: XstsAssumptionWitnessTransformer

    @Inject
    private lateinit var inlinedOxstsAssumptionWitnessTransformer: InlinedOxstsAssumptionWitnessTransformer

    private val thetaExecutor = ThetaPortfolioExecutor(
        "6.21.4",
        listOf(
            "CEGAR --domain EXPL --refinement SEQ_ITP --maxenum 250 --initprec CTRL --stacktrace",
            "CEGAR --domain EXPL_PRED_COMBINED --autoexpl NEWOPERANDS --initprec CTRL --stacktrace",
            "CEGAR --domain PRED_CART --refinement SEQ_ITP --stacktrace",
            "BOUNDED --variant KINDUCTION --stacktrace",
        ),
        timeout = 5,
    )

    protected fun transformToXsts(progressContext: ProgressContext, inlinedOxsts: InlinedOxsts): XstsModel {
        return oxstsTransformer.transform(inlinedOxsts, progressContext)
    }

    protected fun verifyXsts(progressContext: ProgressContext, xstsModel: XstsModel): ThetaVerificationResult {
        xstsModel.eResource().save(emptyMap<Any, Any>())

        val path = xstsModel.eResource().uri.path().removeSuffix(".xsts")
        val name = path.split(File.separator).last()
        val workingDirectory = path.replaceAfterLast(File.separator, "")

        thetaExecutor.initialize()
        return thetaExecutor.run(workingDirectory, name, progressContext)
    }

    override fun verify(progressContext: ProgressContext, classDeclaration: ClassDeclaration): VerificationCaseRunResult {
        val expected = builtinAnnotationHandler.getExpectedResults(classDeclaration)

        progressContext.reportProgress("Inlining class")

        val inlinedOxsts = inlineClass(progressContext, classDeclaration)

        progressContext.checkIsCancelled()
        progressContext.reportProgress("Transforming to Xsts")

        val xstsModel = transformToXsts(progressContext, inlinedOxsts)

        progressContext.checkIsCancelled()
        progressContext.reportProgress("Running Theta Portfolio")

        val result = verifyXsts(progressContext, xstsModel)

        if (result is ThetaErrorVerificationResult) {
            return VerificationCaseRunResult(VerificationResult.Failed, result.failureMessage)
        }

        if (result.hasWitness) {
            progressContext.reportProgress("Creating witness")
            val cexPath = Path(result.runtimeDetails.workingDirectory, result.runtimeDetails.cexPath)
            val cexModel = cexReader.loadCexModel(cexPath)
            val cexWitness = cexAssumptionWitnessTransformer.transform(cexModel)
            val xstsWitness = xstsAssumptionWitnessTransformer.transform(xstsModel, cexWitness)
            val inlinedOxstsWitness = inlinedOxstsAssumptionWitnessTransformer.transform(inlinedOxsts, xstsWitness)
            backAnnotateWitness(inlinedOxstsWitness)
        }

        if (expected == VerificationCaseExpectedResult.SAFE && result is ThetaUnsafeVerificationResult) {
            return VerificationCaseRunResult(VerificationResult.Failed, "Expected Safe result, got Unsafe instead!")
        }

        if (expected == VerificationCaseExpectedResult.UNSAFE && result is ThetaSafeVerificationResult) {
            return VerificationCaseRunResult(VerificationResult.Failed, "Expected Unsafe result, got Safe instead!")
        }

        return VerificationCaseRunResult(VerificationResult.Passed)
    }

}

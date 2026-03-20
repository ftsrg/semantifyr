/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.theta.verification

import com.google.inject.Inject
import hu.bme.mit.semantifyr.backends.theta.verification.backannotation.witness.cex.CexAssumptionWitnessTransformer
import hu.bme.mit.semantifyr.backends.theta.verification.backannotation.witness.oxsts.InlinedOxstsAssumptionWitnessTransformer
import hu.bme.mit.semantifyr.backends.theta.verification.backannotation.witness.xsts.XstsAssumptionWitnessTransformer
import hu.bme.mit.semantifyr.backends.theta.verification.execution.ThetaErrorVerificationResult
import hu.bme.mit.semantifyr.backends.theta.verification.execution.ThetaPortfolioRunner
import hu.bme.mit.semantifyr.backends.theta.verification.execution.ThetaSafeVerificationResult
import hu.bme.mit.semantifyr.backends.theta.verification.execution.ThetaUnsafeVerificationResult
import hu.bme.mit.semantifyr.backends.theta.verification.execution.ThetaVerificationResult
import hu.bme.mit.semantifyr.backends.theta.verification.transformation.xsts.OxstsTransformer
import hu.bme.mit.semantifyr.backends.theta.wrapper.utils.CexReader
import hu.bme.mit.semantifyr.oxsts.model.oxsts.AG
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ClassDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.EF
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import hu.bme.mit.semantifyr.semantics.transformation.ProgressContext
import hu.bme.mit.semantifyr.semantics.transformation.injection.scope.CompilationScoped
import hu.bme.mit.semantifyr.semantics.verification.AbstractOxstsVerifier
import hu.bme.mit.semantifyr.semantics.verification.VerificationCaseRunResult
import hu.bme.mit.semantifyr.semantics.verification.VerificationResult
import hu.bme.mit.semantifyr.xsts.lang.xsts.XstsModel
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.io.path.Path

@CompilationScoped
open class ThetaVerifier : AbstractOxstsVerifier() {

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

    @Inject
    private lateinit var thetaPortfolioRunner: ThetaPortfolioRunner

    protected fun transformToXsts(progressContext: ProgressContext, inlinedOxsts: InlinedOxsts): XstsModel {
        return oxstsTransformer.transform(inlinedOxsts, progressContext)
    }

    protected fun verifyXsts(
        progressContext: ProgressContext,
        xstsModel: XstsModel,
        timeout: Long,
        timeUnit: TimeUnit,
    ): ThetaVerificationResult {
        xstsModel.eResource().save(emptyMap<Any, Any>())

        val path = xstsModel.eResource().uri.path().removeSuffix(".xsts")
        val name = path.split(File.separator).last()
        val workingDirectory = path.replaceAfterLast(File.separator, "")

        return thetaPortfolioRunner.run(workingDirectory, name, progressContext, timeout, timeUnit)
    }

    override fun verify(
        progressContext: ProgressContext,
        classDeclaration: ClassDeclaration,
        timeout: Long,
        timeUnit: TimeUnit,
    ): VerificationCaseRunResult {
        progressContext.reportProgress("Inlining class")

        val inlinedOxsts = inlineClass(progressContext, classDeclaration)

        progressContext.checkIsCancelled()
        progressContext.reportProgress("Transforming to Xsts")

        val xstsModel = transformToXsts(progressContext, inlinedOxsts)

        progressContext.checkIsCancelled()
        progressContext.reportProgress("Running Theta Portfolio")

        val result = verifyXsts(progressContext, xstsModel, timeout, timeUnit)

        if (result is ThetaErrorVerificationResult) {
            error(result.failureMessage)
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

        // Because of the Temporal bubbling optimizations these are the only options
        val property = inlinedOxsts.property.expression

        if (property is AG && result is ThetaUnsafeVerificationResult) {
            return VerificationCaseRunResult(VerificationResult.Failed, "Expected Safe result, got Unsafe instead!")
        }

        if (property is EF && result is ThetaSafeVerificationResult) {
            return VerificationCaseRunResult(VerificationResult.Failed, "Expected Unsafe result, got Safe instead!")
        }

        return VerificationCaseRunResult(VerificationResult.Passed)
    }

}

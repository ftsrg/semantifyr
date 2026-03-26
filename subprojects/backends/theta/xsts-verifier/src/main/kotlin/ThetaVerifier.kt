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
import hu.bme.mit.semantifyr.backends.theta.verification.execution.ThetaPortfolioRunner
import hu.bme.mit.semantifyr.backends.theta.verification.execution.ThetaSafeVerificationResult
import hu.bme.mit.semantifyr.backends.theta.verification.execution.ThetaUnsafeVerificationResult
import hu.bme.mit.semantifyr.backends.theta.verification.execution.ThetaVerificationResult
import hu.bme.mit.semantifyr.backends.theta.verification.transformation.xsts.OxstsTransformer
import hu.bme.mit.semantifyr.backends.theta.wrapper.utils.CexReader
import hu.bme.mit.semantifyr.oxsts.lang.naming.OxstsQualifiedNameProvider
import hu.bme.mit.semantifyr.oxsts.model.oxsts.AG
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ClassDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.EF
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts
import hu.bme.mit.semantifyr.semantics.transformation.ProgressContext
import hu.bme.mit.semantifyr.semantics.transformation.injection.scope.CompilationScoped
import hu.bme.mit.semantifyr.semantics.transformation.serializer.ArtifactManager
import hu.bme.mit.semantifyr.semantics.verification.AbstractOxstsVerifier
import hu.bme.mit.semantifyr.semantics.verification.VerificationCaseRunResult
import hu.bme.mit.semantifyr.semantics.verification.VerificationResult
import hu.bme.mit.semantifyr.xsts.lang.xsts.XstsModel
import java.io.File
import kotlin.io.path.Path
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.measureTime
import kotlin.time.measureTimedValue

@CompilationScoped
open class ThetaVerifier : AbstractOxstsVerifier() {

    @Inject
    private lateinit var oxstsTransformer: OxstsTransformer

    @Inject
    private lateinit var oxstsQualifiedNameProvider: OxstsQualifiedNameProvider

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

    @Inject
    private lateinit var artifactManager: ArtifactManager

    @Inject
    private lateinit var thetaArtifactManager: ThetaArtifactManager

    protected fun transformToXsts(progressContext: ProgressContext, inlinedOxsts: InlinedOxsts): XstsModel {
        return oxstsTransformer.transform(inlinedOxsts, progressContext)
    }

    protected fun verifyXsts(
        progressContext: ProgressContext,
        xstsModel: XstsModel,
        timeout: Duration
    ): ThetaVerificationResult {
        xstsModel.eResource().save(emptyMap<Any, Any>())

        val workingDirectory = thetaArtifactManager.xstsFile.parent
        val name = thetaArtifactManager.xstsFile.nameWithoutExtension

        return thetaPortfolioRunner.run(workingDirectory, name, progressContext, timeout)
    }

    override fun verify(
        progressContext: ProgressContext,
        classDeclaration: ClassDeclaration,
        timeout: Duration
    ): VerificationCaseRunResult {
        // TODO: the artifact manager should be created with this base path already initialized -> see scope helper
        val filePath = File(classDeclaration.eResource().uri.toFileString())
        val parentPath = filePath.parentFile
        val outPath = parentPath.resolve("out")
        val basePath = outPath.resolve(filePath.nameWithoutExtension + File.separator + classDeclaration.name)
        artifactManager.initialize(basePath)

        val startedAt = Clock.System.now()

        val (result, duration) = measureTimedValue {
            doRunVerification(progressContext, classDeclaration, timeout)
        }

        val verificationReport = VerificationReport(
            oxstsQualifiedNameProvider.getFullyQualifiedNameString(classDeclaration),
            classDeclaration.eResource().uri.toFileString(),
            startedAt,
            duration,
            result,
            timeout,
        )

        thetaArtifactManager.serialize(verificationReport)

        return result
    }

    private fun doRunVerification(
        progressContext: ProgressContext,
        classDeclaration: ClassDeclaration,
        timeout: Duration
    ): VerificationCaseRunResult {
        val verificationTimeMetrics = VerificationTimeMetrics()

        progressContext.reportProgress("Inlining class")
        val (inlinedOxsts, inliningDuration) = measureTimedValue {
            inlineClass(progressContext, classDeclaration)
        }
        verificationTimeMetrics.inliningMs = inliningDuration

        progressContext.checkIsCancelled()
        progressContext.reportProgress("Transforming to Xsts")
        val (xstsModel, xstsDuration) = measureTimedValue {
            transformToXsts(progressContext, inlinedOxsts)
        }
        verificationTimeMetrics.xstsTransformationMs = xstsDuration

        progressContext.checkIsCancelled()
        progressContext.reportProgress("Running Theta Portfolio")
        val (result, verifyDuration) = measureTimedValue {
            try {
                // Because of the Temporal bubbling optimizations these are the only options
                val property = inlinedOxsts.property.expression

                val result = verifyXsts(progressContext, xstsModel, timeout)

                if (result.hasWitness) {
                    progressContext.reportProgress("Creating witness")
                    val backAnnotationDuration = measureTime {
                        val cexPath = Path(result.runtimeDetails.workingDirectory, result.runtimeDetails.cexPath)
                        val cexModel = cexReader.loadCexModel(cexPath)
                        val cexWitness = cexAssumptionWitnessTransformer.transform(cexModel)
                        val xstsWitness = xstsAssumptionWitnessTransformer.transform(xstsModel, cexWitness)
                        val inlinedOxstsWitness = inlinedOxstsAssumptionWitnessTransformer.transform(inlinedOxsts, xstsWitness)
                        backAnnotateWitness(inlinedOxstsWitness)
                    }
                    verificationTimeMetrics.backAnnotationMs = backAnnotationDuration
                }

                when (property) {
                    is AG if result is ThetaUnsafeVerificationResult -> {
                        VerificationCaseRunResult(VerificationResult.Failed, "Expected Safe result, got Unsafe instead!")
                    }
                    is EF if result is ThetaSafeVerificationResult -> {
                        VerificationCaseRunResult(VerificationResult.Failed, "Expected Unsafe result, got Safe instead!")
                    }
                    else -> {
                        VerificationCaseRunResult(VerificationResult.Passed)
                    }
                }
            } catch (e: Exception) {
                VerificationCaseRunResult(VerificationResult.Errored, e.message)
            }
        }
        verificationTimeMetrics.verificationMs = verifyDuration

        thetaArtifactManager.serialize(verificationTimeMetrics)

        return result
    }

}

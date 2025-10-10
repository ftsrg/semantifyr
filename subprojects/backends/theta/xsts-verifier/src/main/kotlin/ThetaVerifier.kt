/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.backends.theta.verification

import com.google.inject.Inject
import hu.bme.mit.semantifyr.backends.theta.verification.execution.DockerBasedThetaExecutor
import hu.bme.mit.semantifyr.backends.theta.verification.transformation.xsts.OxstsTransformer
import hu.bme.mit.semantifyr.oxsts.lang.library.builtin.BuiltinLibraryUtils
import hu.bme.mit.semantifyr.oxsts.lang.library.builtin.VerificationCaseExpectedResult
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ClassDeclaration
import hu.bme.mit.semantifyr.semantics.transformation.InlinedOxstsModelManager
import hu.bme.mit.semantifyr.semantics.transformation.ProgressContext
import hu.bme.mit.semantifyr.semantics.transformation.injection.scope.CompilationScoped
import hu.bme.mit.semantifyr.semantics.transformation.inliner.OxstsInliner
import hu.bme.mit.semantifyr.semantics.transformation.instantiation.OxstsInflator
import hu.bme.mit.semantifyr.semantics.transformation.serializer.CompilationStateManager
import hu.bme.mit.semantifyr.semantics.verification.AbstractOxstsVerifier
import java.io.File

@CompilationScoped
open class ThetaVerifier : AbstractOxstsVerifier() {

    @Inject
    private lateinit var inlinedOxstsModelManager: InlinedOxstsModelManager

    @Inject
    private lateinit var oxstsInflator: OxstsInflator

    @Inject
    private lateinit var oxstsInliner: OxstsInliner

    @Inject
    private lateinit var oxstsTransformer: OxstsTransformer

    @Inject
    private lateinit var compilationStateManager: CompilationStateManager

    @Inject
    private lateinit var builtinLibraryUtils: BuiltinLibraryUtils

    private val dockerBasedThetaExecutor = DockerBasedThetaExecutor(
        "6.5.2",
        listOf(
            "--domain EXPL --refinement SEQ_ITP --maxenum 250 --initprec CTRL --stacktrace",
            "--domain EXPL_PRED_COMBINED --autoexpl NEWOPERANDS --initprec CTRL --stacktrace",
            "--domain PRED_CART --refinement SEQ_ITP --stacktrace",
            "--stacktrace",
        ),
        5,
    )

    override fun verify(progressContext: ProgressContext, classDeclaration: ClassDeclaration) {
        val expected = builtinLibraryUtils.getExpectedResults(classDeclaration)

        val inlinedOxsts = inlinedOxstsModelManager.createInlinedOxsts(classDeclaration)

        compilationStateManager.initArtifactManager(inlinedOxsts, progressContext)

        progressContext.reportProgress("Instantiating model", 1)

        oxstsInflator.inflateInstanceModel(inlinedOxsts)

        progressContext.reportProgress("Inlining calls", 2)

        oxstsInliner.inlineOxsts(inlinedOxsts)

        progressContext.reportProgress("Deflating instances and structure", 6)

        oxstsInflator.deflateInstanceModel(inlinedOxsts)

        progressContext.reportProgress("Transforming to XSTS", 8)

        val xsts = oxstsTransformer.transform(inlinedOxsts, true)

        xsts.eResource().save(emptyMap<Any, Any>())

        progressContext.reportProgress("Verifying XSTS", 10)

        compilationStateManager.finalizeArtifactManager(inlinedOxsts)

        val path = xsts.eResource().uri.path().removeSuffix(".xsts")
        val name = path.split(File.separator).last()
        val workingDirectory = path.replaceAfterLast(File.separator, "")

        dockerBasedThetaExecutor.initialize()
        val results = dockerBasedThetaExecutor.run(workingDirectory, name)

        if (expected == VerificationCaseExpectedResult.SAFE && !results.isSafe) {
            error("Expected Safe result, got Unsafe instead!")
        }
        if (expected == VerificationCaseExpectedResult.UNSAFE && !results.isUnsafe) {
            error("Expected Unsafe result, got Safe instead!")
        }
    }

}

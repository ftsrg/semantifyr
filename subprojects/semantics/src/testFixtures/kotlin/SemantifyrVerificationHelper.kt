/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.semantics

import com.google.inject.Inject
import com.google.inject.Provider
import hu.bme.mit.semantifyr.oxsts.lang.library.builtin.BuiltInLibraryUtils
import hu.bme.mit.semantifyr.oxsts.lang.library.builtin.BuiltinAnnotationHandler
import hu.bme.mit.semantifyr.oxsts.lang.naming.OxstsQualifiedNameProvider
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ClassDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.OxstsModelPackage
import hu.bme.mit.semantifyr.semantics.reader.SemantifyrLoader
import hu.bme.mit.semantifyr.semantics.reader.SemantifyrModelContext
import hu.bme.mit.semantifyr.semantics.transformation.ProgressContext
import hu.bme.mit.semantifyr.semantics.utils.info
import hu.bme.mit.semantifyr.semantics.utils.loggerFactory
import hu.bme.mit.semantifyr.semantics.verification.CompilationScopeHelper
import hu.bme.mit.semantifyr.semantics.verification.OxstsVerifier
import hu.bme.mit.semantifyr.semantics.verification.VerificationCaseRunResult
import hu.bme.mit.semantifyr.semantics.verification.VerificationResult
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Named
import org.junit.jupiter.params.provider.Arguments
import org.slf4j.Logger
import java.util.concurrent.TimeUnit
import kotlin.streams.asSequence

/**
 * Helper class to be instantiated from a Global injector such as StandaloneOxstsSemanticsRuntimeModule#injector
 */
class SemantifyrVerificationHelper {

    private val logger by loggerFactory()

    @Inject
    lateinit var oxstsQualifiedNameProvider: OxstsQualifiedNameProvider

    @Inject
    lateinit var builtInLibraryUtils: BuiltInLibraryUtils

    @Inject
    lateinit var builtinAnnotationHandler: BuiltinAnnotationHandler

    @Inject
    lateinit var semantifyrLoader: SemantifyrLoader

    private fun rawCollectVerificationCases(model: SemantifyrModelContext): Sequence<ClassDeclaration> {
        return model.modelResources.asSequence().map {
            it.contents.single()
        }.filterIsInstance<OxstsModelPackage>().flatMap {
            builtInLibraryUtils.streamTestCases(it).asSequence()
        }
    }

    private fun ClassDeclaration.named(): Arguments {
        return Arguments.of(Named.of(oxstsQualifiedNameProvider.getFullyQualifiedNameString(this), this))
    }

    private fun Sequence<ClassDeclaration>.named(): Sequence<Arguments> {
        return map {
            it.named()
        }
    }

    fun collectSlowVerificationCases(model: SemantifyrModelContext): Sequence<Arguments> {
        logger.info { "Collecting verification cases tagged with 'slow'" }

        return rawCollectVerificationCases(model).filter {
            builtinAnnotationHandler.isTaggedWith(it, "slow")
        }.named()
    }

    fun collectNotSlowVerificationCases(model: SemantifyrModelContext): Sequence<Arguments> {
        logger.info { "Collecting verification cases not tagged with 'slow'" }

        return rawCollectVerificationCases(model).filter {
            builtinAnnotationHandler.isNotTaggedWith(it, "slow")
        }.named()
    }

    fun collectVerificationCases(
        model: SemantifyrModelContext,
        including: List<String> = emptyList(),
        excluding: List<String> = emptyList(),
    ): Sequence<Arguments> {
        logger.info { "Collecting verification cases" }

        var verificationCases = rawCollectVerificationCases(model)

        if (including.any()) {
            logger.info { "Including tags ${including.joinToString(",")}" }
            verificationCases = verificationCases.filter {
                // allow all classes that are tagged by at least one in the including list
                including.map { tag ->
                    builtinAnnotationHandler.isTaggedWith(it, tag)
                }.reduce { a, b ->
                    a || b
                }
            }
        }

        if (excluding.any()) {
            logger.info { "Excluding tags ${including.joinToString(",")}" }
            verificationCases = verificationCases.filterNot {
                // disallow all classes that are tagged by at least one in the including list
                excluding.map { tag ->
                    builtinAnnotationHandler.isTaggedWith(it, tag)
                }.reduce { a, b ->
                    a || b
                }
            }
        }

        return verificationCases.named()
    }

}

abstract class BaseSemantifyrVerificationTest<T : OxstsVerifier> {

    abstract val logger: Logger

    val loggerContext = object : ProgressContext {
        override fun checkIsCancelled() {
            // never cancelled
        }

        override fun reportProgress(message: String, percentage: Int) {
            logger.info { "$message - $percentage%" }
        }

        override fun reportProgress(message: String) {
            logger.info { message }
        }

    }

    @Inject
    lateinit var oxstsQualifiedNameProvider: OxstsQualifiedNameProvider

    @Inject
    lateinit var compilationScopeHelper: CompilationScopeHelper

    abstract val oxstsVerifierProvider: Provider<T>

    fun executeVerificationCase(
        classDeclaration: ClassDeclaration,
        timeout: Long = 30L,
        timeUnit: TimeUnit = TimeUnit.MINUTES
    ): VerificationCaseRunResult {
        logger.info {
            "Verifying class: ${oxstsQualifiedNameProvider.getFullyQualifiedNameString(classDeclaration)}"
        }

        return oxstsVerifierProvider.get().verify(loggerContext, classDeclaration, timeout, timeUnit)
    }

    fun checkVerificationCase(
        verificationCase: ClassDeclaration,
        timeout: Long = 30L,
        timeUnit: TimeUnit = TimeUnit.MINUTES
    ) {
        compilationScopeHelper.runInCompilationScope(verificationCase) {
            val result = executeVerificationCase(it, timeout, timeUnit)

            Assertions.assertEquals(VerificationResult.Passed, result.result)
        }
    }

}

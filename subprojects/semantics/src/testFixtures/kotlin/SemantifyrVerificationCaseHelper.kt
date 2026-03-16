/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.semantics

import com.google.inject.Inject
import com.google.inject.Provider
import hu.bme.mit.semantifyr.oxsts.lang.library.builtin.BuiltInLibraryUtils
import hu.bme.mit.semantifyr.oxsts.lang.naming.OxstsQualifiedNameProvider
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ClassDeclaration
import hu.bme.mit.semantifyr.oxsts.model.oxsts.OxstsModelPackage
import hu.bme.mit.semantifyr.semantics.reader.SemantifyrModelContext
import hu.bme.mit.semantifyr.semantics.transformation.ProgressContext
import hu.bme.mit.semantifyr.semantics.utils.info
import hu.bme.mit.semantifyr.semantics.verification.CompilationScopeHelper
import hu.bme.mit.semantifyr.semantics.verification.OxstsVerifier
import hu.bme.mit.semantifyr.semantics.verification.VerificationCaseRunResult
import hu.bme.mit.semantifyr.semantics.verification.VerificationResult
import org.junit.jupiter.api.Assertions
import org.slf4j.Logger
import kotlin.streams.asSequence

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
    lateinit var builtInLibraryUtils: BuiltInLibraryUtils

    @Inject
    lateinit var compilationScopeHelper: CompilationScopeHelper

    abstract val oxstsVerifierProvider: Provider<T>

    fun runVerification(classDeclaration: ClassDeclaration): VerificationCaseRunResult {
        logger.info {
            "Verifying class: ${oxstsQualifiedNameProvider.getFullyQualifiedNameString(classDeclaration)}"
        }

        return oxstsVerifierProvider.get().verify(loggerContext, classDeclaration)
    }

    fun collectVerificationCases(model: SemantifyrModelContext): Sequence<ClassDeclaration> {
        return model.modelResources.asSequence().map {
            it.contents.single()
        }.filterIsInstance<OxstsModelPackage>().flatMap {
            builtInLibraryUtils.streamTestCases(it).asSequence()
        }
    }

    fun verifyVerificationCases(model: SemantifyrModelContext) {
        logger.info { "Collecting verification cases" }

        val verificationCases = collectVerificationCases(model).toList()

        for (verificationCase in verificationCases) {
            compilationScopeHelper.runInCompilationScope(verificationCase) {
                val result = runVerification(it)

                Assertions.assertEquals(result.result, VerificationResult.Passed)
            }
        }
    }

    fun verifyVerificationCase(model: SemantifyrModelContext, qualifiedName: String) {
        logger.info { "Looking for $qualifiedName" }

        val verificationCase = collectVerificationCases(model).firstOrNull {
            oxstsQualifiedNameProvider.getFullyQualifiedNameString(it) == qualifiedName
        }

        if (verificationCase == null) {
            error("$qualifiedName can not be found!")
        }

        compilationScopeHelper.runInCompilationScope(verificationCase) {
            val result = runVerification(it)

            Assertions.assertEquals(result.result, VerificationResult.Passed)
        }
    }

}

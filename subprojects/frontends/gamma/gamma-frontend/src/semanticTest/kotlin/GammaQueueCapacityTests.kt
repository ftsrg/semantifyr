/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import hu.bme.mit.semantifyr.compiler.reader.SemantifyrLoader
import hu.bme.mit.semantifyr.frontends.gamma.testing.GammaFrontendTestHelper
import hu.bme.mit.semantifyr.oxsts.lang.OxstsStandaloneSetup
import hu.bme.mit.semantifyr.oxsts.lang.library.ClasspathBasedOxstsLibrary
import hu.bme.mit.semantifyr.oxsts.lang.tests.RestoreGlobalRegistries
import hu.bme.mit.semantifyr.portfolios.Portfolios
import hu.bme.mit.semantifyr.verifier.SemantifyrVerifierTestHelper
import hu.bme.mit.semantifyr.verifier.discovery.VerificationCaseDiscoverer
import org.eclipse.xtext.resource.FileExtensionProvider
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.io.path.Path
import kotlin.io.path.createDirectories

@RestoreGlobalRegistries
class GammaQueueCapacityTests {
    companion object {
        private val injector = OxstsStandaloneSetup().createInjectorAndDoEMFRegistration()
        private val loader = injector.getInstance(SemantifyrLoader::class.java)
        private val discoverer = injector.getInstance(VerificationCaseDiscoverer::class.java)
        private val verifierTestHelper = injector.getInstance(SemantifyrVerifierTestHelper::class.java)
        private val artifactRoot = GammaFrontendTestHelper.testArtifactRoot(GammaQueueCapacityTests::class.java)
        private val fixture = Path("..", "models", "etc", "queue_capacity.semantifyr")
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "QueueOfThreeDeliversInOrderTestCase",
            "QueueOfThreeHoldsThreeTestCase",
            "QueueOfThreeHoldingFourUnreachableTestCase",
            "QueueOfOneDeliversInOrderTestCase",
            "QueueOfOneDeliversEveryMessageTestCase",
            "QueueOfOneHoldingTwoUnreachableTestCase",
            "DroppingQueueOfThreeKeepsTheOrderTestCase",
            "DroppingQueueOfThreeDropsTheOldestTestCase",
            "DroppingQueueOfOneDropsTheOldestTestCase",
            "DroppingQueueOfOneKeepingTheOldestUnreachableTestCase",
            "QueueOfNoneHoldsNothingTestCase",
        ],
    )
    suspend fun `The Library Queue Keeps Its Meaning At Its Own Capacity`(caseName: String) {
        val extractionDirectory = artifactRoot.resolve("library").createDirectories()
        val library = ClasspathBasedOxstsLibrary(injector.getInstance(FileExtensionProvider::class.java), javaClass.classLoader, "default", extractionDirectory)
        val context = loader.startContext()
            .loadLibraryPaths(listOf(library.extractedLibraryPath))
            .loadModelPaths(listOf(fixture))
            .buildAndResolve()
        val case = discoverer.discover(context).single {
            it.qualifiedName.substringAfterLast("::") == caseName
        }
        verifierTestHelper.checkTestModel(
            context = context,
            case = case,
            verificationPortfolio = Portfolios.SmartFull,
            outputDirectory = artifactRoot,
        )
    }
}

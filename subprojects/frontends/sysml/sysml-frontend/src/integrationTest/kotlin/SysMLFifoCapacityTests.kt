/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.frontends.sysml

import hu.bme.mit.semantifyr.compiler.reader.SemantifyrLoader
import hu.bme.mit.semantifyr.frontends.sysml.testing.SysMLv2FrontendTestHelper
import hu.bme.mit.semantifyr.oxsts.lang.OxstsStandaloneSetup
import hu.bme.mit.semantifyr.oxsts.lang.library.ClasspathBasedOxstsLibrary
import hu.bme.mit.semantifyr.portfolios.Portfolios
import hu.bme.mit.semantifyr.verifier.SemantifyrVerifierTestHelper
import hu.bme.mit.semantifyr.verifier.discovery.VerificationCaseDiscoverer
import org.eclipse.xtext.resource.FileExtensionProvider
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.io.path.Path
import kotlin.io.path.createDirectories

// the mapper sets no capacity, so the fixture redefines it per port; the queue of one has a port of none beside it, and the queue of none is accepted from
class SysMLFifoCapacityTests {
    companion object {
        private val injector = OxstsStandaloneSetup().createInjectorAndDoEMFRegistration()
        private val loader = injector.getInstance(SemantifyrLoader::class.java)
        private val discoverer = injector.getInstance(VerificationCaseDiscoverer::class.java)
        private val verifierTestHelper = injector.getInstance(SemantifyrVerifierTestHelper::class.java)
        private val artifactRoot = SysMLv2FrontendTestHelper.testArtifactRoot(SysMLFifoCapacityTests::class.java)
        private val fixture = Path("..", "models", "etc", "fifo_capacity.oxsts")
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "QueueOfThreeAcceptsNewestTestCase",
            "QueueOfThreeNeverAcceptsOutOfOrderTestCase",
            "QueueOfOneAcceptsOldestTestCase",
            "QueueOfOneDropsMiddleTestCase",
            "QueueOfNoneNeverAcceptsTestCase",
        ],
    )
    suspend fun `Fifo Capacity Cases Should Pass`(caseName: String) {
        val variant = SysMLv2Variant.Fifo.name.lowercase()
        val extractionDirectory = artifactRoot.resolve("library").createDirectories()
        val library = ClasspathBasedOxstsLibrary(injector.getInstance(FileExtensionProvider::class.java), javaClass.classLoader, variant, extractionDirectory)
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

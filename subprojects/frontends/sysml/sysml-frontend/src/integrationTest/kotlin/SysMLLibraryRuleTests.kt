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
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import kotlin.io.path.Path
import kotlin.io.path.createDirectories

// the fixture is written in the shape the mapper is to emit for constructs it does not map yet, so its cases are listed here
class SysMLLibraryRuleTests {
    companion object {
        private val injector = OxstsStandaloneSetup().createInjectorAndDoEMFRegistration()
        private val loader = injector.getInstance(SemantifyrLoader::class.java)
        private val discoverer = injector.getInstance(VerificationCaseDiscoverer::class.java)
        private val verifierTestHelper = injector.getInstance(SemantifyrVerifierTestHelper::class.java)
        private val artifactRoot = SysMLv2FrontendTestHelper.testArtifactRoot(SysMLLibraryRuleTests::class.java)
        private val fixture = Path("..", "models", "etc", "library_rules.oxsts")

        private val CASES = listOf(
            "ExhibitedStateActiveUntilDoneTestCase",
            "ExhibitedStateInactiveAfterDoneTestCase",
            "ExhibitedStateReachesDoneTestCase",
            "ExhibitedStateEntryPrecedesDescentTestCase",
            "RegionEntryReachesTargetTestCase",
            "RegionEntryEntersSiblingRegionTestCase",
            "RegionEntryRunsEntriesInOrderTestCase",
            "DerivedAttributeFollowsItsExpressionTestCase",
            "DerivedAttributeChangesTestCase",
        )

        @JvmStatic
        fun `Library Rule Cases Should Pass`(): List<Arguments> {
            return SysMLv2Variant.entries.flatMap { variant ->
                CASES.map {
                    Arguments.of(variant, it)
                }
            }
        }
    }

    @ParameterizedTest(name = "{0} - {1}")
    @MethodSource
    suspend fun `Library Rule Cases Should Pass`(
        variant: SysMLv2Variant,
        caseName: String,
    ) {
        val outputDirectory = artifactRoot.resolve(variant.name.lowercase())
        val extractionDirectory = outputDirectory.resolve("library").createDirectories()
        val library = ClasspathBasedOxstsLibrary(javaClass.classLoader, variant.name.lowercase(), extractionDirectory)
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
            outputDirectory = outputDirectory,
        )
    }
}

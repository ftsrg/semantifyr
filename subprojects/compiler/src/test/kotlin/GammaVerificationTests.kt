/*
 * SPDX-FileCopyrightText: 2023-2024 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.compiler

import hu.bme.mit.semantifyr.oxsts.compiler.reader.prepareOxsts
import hu.bme.mit.semantifyr.oxsts.lang.tests.OxstsInjectorProvider
import org.eclipse.xtext.testing.InjectWith
import org.eclipse.xtext.testing.extensions.InjectionExtension
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

private const val baseDirectory = "TestModels/Automated/Gamma"
private const val libraryDirectory = "TestModels/Automated/GammaSemanticLibrary"

@ExtendWith(InjectionExtension::class)
@InjectWith(OxstsInjectorProvider::class)
class GammaVerificationTests : VerificationTest() {

    companion object {
        @JvmStatic
        fun `Crossroads Verification cases should pass`() = streamTargetsFromFolder("$baseDirectory/Crossroads", libraryDirectory)

        @JvmStatic
        fun `Simple Mission Verification cases should pass`() = streamTargetsFromFolder("$baseDirectory/SimpleMission", libraryDirectory)

        @JvmStatic
        fun `Spacecraft Verification cases should pass`() = streamTargetsFromFolder("$baseDirectory/Spacecraft", libraryDirectory) {
            (it.name.contains("_Safe") || it.name.contains("_Unsafe")) && !it.name.contains("_Slow")
        }

        @JvmStatic
        fun `Slow Spacecraft Verification cases should pass`() = streamTargetsFromFolder("$baseDirectory/Spacecraft", libraryDirectory) {
            (it.name.contains("_Safe") || it.name.contains("_Unsafe")) && it.name.contains("_Slow")
        }

        @BeforeAll
        @JvmStatic
        fun prepare() {
            prepareOxsts()
            thetaExecutor.initTheta()
        }
    }

    @ParameterizedTest
    @MethodSource
    fun `Crossroads Verification cases should pass`(targetDefinition: TargetDefinition) {
        testVerification(targetDefinition)
    }

    @ParameterizedTest
    @MethodSource
    fun `Simple Mission Verification cases should pass`(targetDefinition: TargetDefinition) {
        testVerification(targetDefinition)
    }

    @ParameterizedTest
    @MethodSource
    fun `Spacecraft Verification cases should pass`(targetDefinition: TargetDefinition) {
        testVerification(targetDefinition)
    }

    @Tag("slow")
    @ParameterizedTest
    @MethodSource
    fun `Slow Spacecraft Verification cases should pass`(targetDefinition: TargetDefinition) {
        testVerification(targetDefinition)
    }

}

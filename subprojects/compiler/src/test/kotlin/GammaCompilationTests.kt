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
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.io.File
import java.util.stream.Stream
import kotlin.streams.asStream

@ExtendWith(InjectionExtension::class)
@InjectWith(OxstsInjectorProvider::class)
class GammaCompilationTests : CompilationTest() {

    companion object {
        @JvmStatic
        fun `Model transformations should not regress`(): Stream<String> {
            return File("TestModels/Automated/Gamma").walkTopDown().filter {
                it.isDirectory
            }.filter {
                it.list { _, name -> name == "model.oxsts" }?.any() ?: false
            }.map {
                it.path
            }.asStream()
        }

        @BeforeAll
        @JvmStatic
        fun prepare() {
            prepareOxsts()
        }

    }

    @ParameterizedTest
    @MethodSource
    fun `Model transformations should not regress`(directory: String) {
        simpleReadTransformWrite(directory, "TestModels/Automated/GammaSemanticLibrary")
        assertModelEqualsExpected(directory)
    }

}

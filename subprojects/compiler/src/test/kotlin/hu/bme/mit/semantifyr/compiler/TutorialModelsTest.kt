/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.compiler

import hu.bme.mit.semantifyr.compiler.reader.SemantifyrLoader
import hu.bme.mit.semantifyr.compiler.reader.SemantifyrModelContext
import hu.bme.mit.semantifyr.oxsts.lang.OxstsStandaloneSetup
import org.assertj.core.api.Assertions.assertThat
import org.eclipse.xtext.diagnostics.Severity
import org.eclipse.xtext.util.CancelIndicator
import org.eclipse.xtext.validation.CheckMode
import org.eclipse.xtext.validation.IResourceValidator
import org.junit.jupiter.api.Named
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.walk

class TutorialModelsTest {

    companion object {

        private val injector = OxstsStandaloneSetup().createInjectorAndDoEMFRegistration()
        private val loader = injector.getInstance(SemantifyrLoader::class.java)
        private val validator = injector.getInstance(IResourceValidator::class.java)
        private val tutorial = Path("../../oxsts-test-models/tutorial")

        // every step of a tutorial is a model of its own, loaded alone as the website's live editor loads it
        private val contexts: Map<Path, SemantifyrModelContext> by lazy {
            tutorial.walk().filter {
                it.isRegularFile() && it.extension == "semantifyr"
            }.sorted().associateWith {
                loader.loadStandaloneModel(it)
            }
        }

        @JvmStatic
        fun `Tutorial Models Validate Without Warnings`(): List<Arguments> {
            return contexts.keys.map {
                Arguments.of(Named.of(it.name, it))
            }
        }
    }

    @ParameterizedTest
    @MethodSource
    fun `Tutorial Models Validate Without Warnings`(model: Path) {
        val resource = contexts.getValue(model).modelResources.single()
        val issues = validator.validate(resource, CheckMode.ALL, CancelIndicator.NullImpl).filter {
            it.severity == Severity.ERROR || it.severity == Severity.WARNING
        }
        assertThat(
            issues.map {
                "${it.lineNumber}:${it.column} ${it.message}"
            },
        ).describedAs("issues a reader would see in ${model.name}")
            .isEmpty()
    }
}

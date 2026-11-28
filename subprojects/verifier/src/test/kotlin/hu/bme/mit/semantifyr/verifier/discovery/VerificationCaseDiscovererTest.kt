/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.verifier.discovery

import com.google.inject.Inject
import hu.bme.mit.semantifyr.backend.testModel
import hu.bme.mit.semantifyr.compiler.reader.SemantifyrLoader
import hu.bme.mit.semantifyr.compiler.reader.SemantifyrModelContext
import hu.bme.mit.semantifyr.oxsts.lang.tests.InjectWithOxsts
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.writeText

@InjectWithOxsts
class VerificationCaseDiscovererTest {

    @Inject
    private lateinit var loader: SemantifyrLoader

    @Inject
    private lateinit var discoverer: VerificationCaseDiscoverer

    @TempDir
    private lateinit var modelDirectory: Path

    @Test
    fun `discover returns every class annotated with VerificationCase`() {
        val context = loadContext()
        val cases = discoverer.discover(context)

        assertThat(cases).extracting<String> { it.qualifiedName }
            .containsExactlyInAnyOrder(
                "verifier::discovery::Passing",
                "verifier::discovery::Failing",
            )
    }

    @Test
    fun `discover extracts Tag annotations into the VerificationCase tags`() {
        val context = loadContext()
        val cases = discoverer.discover(context).associateBy { it.qualifiedName }

        assertThat(cases.getValue("verifier::discovery::Passing").tags)
            .containsExactly("expect-pass")
        assertThat(cases.getValue("verifier::discovery::Failing").tags)
            .containsExactly("expect-fail")
    }

    @Test
    fun `discover with including filter keeps only matching cases`() {
        val context = loadContext()
        val filtered = discoverer.discover(context, CaseFilter.tagged("expect-pass"))

        assertThat(filtered).extracting<String> { it.qualifiedName }
            .containsExactly(
                "verifier::discovery::Passing",
            )
    }

    @Test
    fun `discover with excluding filter drops matching cases`() {
        val context = loadContext()
        val filtered = discoverer.discover(context, CaseFilter.excluding("expect-fail"))

        assertThat(filtered).extracting<String> { it.qualifiedName }
            .containsExactly(
                "verifier::discovery::Passing",
            )
    }

    @Test
    fun `findByQualifiedNameOrNull returns the matching case`() {
        val context = loadContext()
        val case = discoverer.findByQualifiedNameOrNull(context, "verifier::discovery::Passing")

        assertThat(case).isNotNull
        assertThat(case!!.qualifiedName).isEqualTo("verifier::discovery::Passing")
    }

    @Test
    fun `findByQualifiedNameOrNull returns null for an unknown name`() {
        val context = loadContext()
        val case = discoverer.findByQualifiedNameOrNull(context, "verifier::discovery::Missing")

        assertThat(case).isNull()
    }

    @Test
    fun `findByQualifiedName throws on an unknown name`() {
        val context = loadContext()

        assertThatThrownBy {
            discoverer.findByQualifiedName(context, "verifier::discovery::Missing")
        }.hasMessageContaining("verifier::discovery::Missing")
    }

    private fun loadContext(): SemantifyrModelContext {
        val model = modelDirectory.resolve("discovery.semantifyr")
        model.writeText(testModel("discovery.semantifyr"))
        return loader.loadStandaloneModel(model)
    }
}

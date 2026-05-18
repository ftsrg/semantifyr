/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.verifier.discovery

import com.google.inject.Inject
import hu.bme.mit.semantifyr.compiler.reader.SemantifyrLoader
import hu.bme.mit.semantifyr.compiler.reader.SemantifyrModelContext
import hu.bme.mit.semantifyr.oxsts.lang.tests.InjectWithOxsts
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import kotlin.io.path.Path

@InjectWithOxsts
class VerificationCaseDiscovererTest {

    @Inject
    private lateinit var loader: SemantifyrLoader

    @Inject
    private lateinit var discoverer: VerificationCaseDiscoverer

    private val modelPath = Path("../../oxsts-test-models/simple/simple.oxsts")

    @Test
    fun `discover returns every class annotated with VerificationCase`() {
        val context = loadContext()
        val cases = discoverer.discover(context)

        assertThat(cases).extracting<String> { it.qualifiedName }
            .containsExactlyInAnyOrder(
                "semantifyr::verification::simple::Incrementing",
                "semantifyr::verification::simple::StaysAtOne",
                "semantifyr::verification::simple::FlipFlopInvariant",
                "semantifyr::verification::simple::CounterExceedsBound",
            )
    }

    @Test
    fun `discover extracts Tag annotations into the VerificationCase tags`() {
        val context = loadContext()
        val cases = discoverer.discover(context).associateBy { it.qualifiedName }

        assertThat(cases.getValue("semantifyr::verification::simple::Incrementing").tags)
            .containsExactly("expect-pass")
        assertThat(cases.getValue("semantifyr::verification::simple::CounterExceedsBound").tags)
            .containsExactly("expect-fail")
    }

    @Test
    fun `discover with including filter keeps only matching cases`() {
        val context = loadContext()
        val filtered = discoverer.discover(context, CaseFilter.tagged("expect-pass"))

        assertThat(filtered).extracting<String> { it.qualifiedName }
            .containsExactly(
                "semantifyr::verification::simple::Incrementing",
                "semantifyr::verification::simple::FlipFlopInvariant",
            )
    }

    @Test
    fun `discover with excluding filter drops matching cases`() {
        val context = loadContext()
        val filtered = discoverer.discover(context, CaseFilter.excluding("expect-fail"))

        assertThat(filtered).extracting<String> { it.qualifiedName }
            .containsExactly(
                "semantifyr::verification::simple::Incrementing",
                "semantifyr::verification::simple::FlipFlopInvariant",
            )
    }

    @Test
    fun `findByQualifiedNameOrNull returns the matching case`() {
        val context = loadContext()
        val case = discoverer.findByQualifiedNameOrNull(context, "semantifyr::verification::simple::Incrementing")

        assertThat(case).isNotNull
        assertThat(case!!.qualifiedName).isEqualTo("semantifyr::verification::simple::Incrementing")
    }

    @Test
    fun `findByQualifiedNameOrNull returns null for an unknown name`() {
        val context = loadContext()
        val case = discoverer.findByQualifiedNameOrNull(context, "semantifyr::verification::simple::Missing")

        assertThat(case).isNull()
    }

    @Test
    fun `findByQualifiedName throws on an unknown name`() {
        val context = loadContext()

        assertThatThrownBy {
            discoverer.findByQualifiedName(context, "semantifyr::verification::simple::Missing")
        }.hasMessageContaining("semantifyr::verification::simple::Missing")
    }

    private fun loadContext(): SemantifyrModelContext {
        return loader.loadStandaloneModel(modelPath)
    }
}

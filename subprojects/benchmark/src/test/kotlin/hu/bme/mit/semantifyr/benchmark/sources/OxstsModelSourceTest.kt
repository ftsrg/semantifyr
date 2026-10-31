/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.benchmark.sources

import com.google.inject.Inject
import com.google.inject.Injector
import hu.bme.mit.semantifyr.backend.VerificationVerdict
import hu.bme.mit.semantifyr.benchmark.verification.BenchmarkCase
import hu.bme.mit.semantifyr.oxsts.lang.tests.InjectWithOxsts
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.Path

@InjectWithOxsts
class OxstsModelSourceTest {

    @Inject
    private lateinit var injector: Injector

    private val source by lazy {
        OxstsModelSource(injector, Path("../../oxsts-test-models/semantic"))
    }

    @Test
    fun `every semantic model is a model on the axis, named by its file`() {
        assertThat(source.models.map { it.name }).contains("arithmetic.oxsts", "initial_values.oxsts").isSorted()
    }

    @Test
    fun `the expect tags give the expected verdicts`() {
        val initialValues = source.models.single { it.name == "initial_values.oxsts" }

        assertThat(initialValues.cases).contains(
            BenchmarkCase("UninitializedIntegerBound", VerificationVerdict.Failed),
            BenchmarkCase("UninitializedBooleanBound", VerificationVerdict.Failed),
        )
        assertThat(source.models.single { it.name == "arithmetic.oxsts" }.cases).contains(BenchmarkCase("AddReach", VerificationVerdict.Passed))
    }

    @Test
    fun `every semantic case carries an expectation`() {
        assertThat(source.models.flatMap { it.cases }).allSatisfy {
            assertThat(it.expected).isNotNull()
        }
    }

    @Test
    fun `a prepared model resolves every case it lists`(@TempDir directory: Path) {
        val arithmetic = source.models.single { it.name == "arithmetic.oxsts" }

        val prepared = arithmetic.prepare(directory)

        assertThat(prepared.cases.keys).containsExactlyInAnyOrderElementsOf(arithmetic.cases.map { it.name })
        assertThat(prepared.cases.getValue("AddReach").qualifiedName).isEqualTo("semantifyr::verification::lang::arithmetic::AddReach")
    }
}

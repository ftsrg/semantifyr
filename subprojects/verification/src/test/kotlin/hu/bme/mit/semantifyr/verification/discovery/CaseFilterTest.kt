/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.verification.discovery

import hu.bme.mit.semantifyr.backend.VerificationCase
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ClassDeclaration
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class CaseFilterTest {

    private fun case(qualifiedName: String, vararg tags: String): VerificationCase {
        return VerificationCase(
            classDeclaration = mock<ClassDeclaration>(),
            qualifiedName = qualifiedName,
            tags = tags.toSet(),
        )
    }

    @Test
    fun `All matches everything`() {
        Assertions.assertThat(CaseFilter.All.matches(case("a"))).isTrue
        Assertions.assertThat(CaseFilter.All.matches(case("b", "slow"))).isTrue
    }

    @Test
    fun `Tags includes match when including is empty`() {
        val filter = CaseFilter.Tags(excluding = setOf("slow"))
        Assertions.assertThat(filter.matches(case("a"))).isTrue
        Assertions.assertThat(filter.matches(case("a", "fast"))).isTrue
        Assertions.assertThat(filter.matches(case("a", "slow"))).isFalse
        Assertions.assertThat(filter.matches(case("a", "slow", "fast"))).isFalse
    }

    @Test
    fun `Tags including requires at least one matching tag`() {
        val filter = CaseFilter.Tags(including = setOf("integration"))
        Assertions.assertThat(filter.matches(case("a"))).isFalse
        Assertions.assertThat(filter.matches(case("a", "unit"))).isFalse
        Assertions.assertThat(filter.matches(case("a", "integration"))).isTrue
        Assertions.assertThat(filter.matches(case("a", "integration", "unit"))).isTrue
    }

    @Test
    fun `Tags including with multiple tags is OR semantics`() {
        val filter = CaseFilter.Tags(including = setOf("a", "b"))
        Assertions.assertThat(filter.matches(case("x", "a"))).isTrue
        Assertions.assertThat(filter.matches(case("x", "b"))).isTrue
        Assertions.assertThat(filter.matches(case("x", "c"))).isFalse
    }

    @Test
    fun `Tags excluding takes precedence over including`() {
        val filter = CaseFilter.Tags(including = setOf("integration"), excluding = setOf("flaky"))
        Assertions.assertThat(filter.matches(case("x", "integration"))).isTrue
        Assertions.assertThat(filter.matches(case("x", "integration", "flaky"))).isFalse
    }

    @Test
    fun `Matching uses the predicate`() {
        val filter = CaseFilter.Matching { it.qualifiedName.startsWith("pkg.") }
        Assertions.assertThat(filter.matches(case("pkg.Foo"))).isTrue
        Assertions.assertThat(filter.matches(case("other.Bar"))).isFalse
    }

    @Test
    fun `excluding factory is shorthand for excluding-only Tags`() {
        val filter = CaseFilter.excluding("slow", "flaky")
        Assertions.assertThat(filter.matches(case("x"))).isTrue
        Assertions.assertThat(filter.matches(case("x", "slow"))).isFalse
        Assertions.assertThat(filter.matches(case("x", "flaky"))).isFalse
    }

    @Test
    fun `tagged factory is shorthand for including-only Tags`() {
        val filter = CaseFilter.tagged("integration")
        Assertions.assertThat(filter.matches(case("x"))).isFalse
        Assertions.assertThat(filter.matches(case("x", "integration"))).isTrue
    }
}

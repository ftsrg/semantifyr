/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.verifier

import hu.bme.mit.semantifyr.oxsts.model.oxsts.OxstsFactory
import org.assertj.core.api.Assertions.assertThat
import org.eclipse.emf.common.util.URI
import org.eclipse.emf.ecore.resource.impl.ResourceImpl
import org.junit.jupiter.api.Test

class CaseDirectoryNameTest {

    private fun case(file: String?, qualifiedName: String): VerificationCase {
        val classDeclaration = OxstsFactory.eINSTANCE.createClassDeclaration()
        if (file != null) {
            ResourceImpl(URI.createFileURI("/models/$file")).contents += classDeclaration
        }
        return VerificationCase(qualifiedName, classDeclaration)
    }

    @Test
    fun `cases sharing a qualified name in different models get different directories`() {
        assertThat(caseDirectoryName(case("power_subsystems.oxsts", "semantifyr::sysml::model::OffReachable"))).isEqualTo("power_subsystems.OffReachable")
        assertThat(caseDirectoryName(case("aircraft_engine.oxsts", "semantifyr::sysml::model::OffReachable"))).isEqualTo("aircraft_engine.OffReachable")
    }

    @Test
    fun `a long name is bounded and stays distinct`() {
        val reachable = caseDirectoryName(case("spacecraft.oxsts", "semantifyr::sysml::model::SpacecraftBehaviorCommunicationTransmittingConsumePowerConsumingReachable"))
        val unreachable = caseDirectoryName(case("spacecraft.oxsts", "semantifyr::sysml::model::SpacecraftBehaviorCommunicationTransmittingConsumePowerConsumingUnreachable"))

        assertThat(reachable).hasSize(64).startsWith("spacecraft.SpacecraftBehavior")
        assertThat(unreachable).hasSize(64).isNotEqualTo(reachable)
    }

    @Test
    fun `a case outside a resource is named by its simple name`() {
        assertThat(caseDirectoryName(case(null, "pkg::Foo"))).isEqualTo("Foo")
    }
}

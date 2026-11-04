/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.tests.semantics;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Inject;
import hu.bme.mit.semantifyr.oxsts.lang.semantics.FeatureSubSettersFinder;
import hu.bme.mit.semantifyr.oxsts.lang.tests.InjectWithOxsts;
import hu.bme.mit.semantifyr.oxsts.lang.tests.utils.OxstsPackageParseHelper;
import hu.bme.mit.semantifyr.oxsts.lang.validation.OxstsValidator;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.NamedElement;
import org.eclipse.xtext.validation.CheckMode;
import org.junit.jupiter.api.Test;

@InjectWithOxsts
public class SubsettingTest {

    private static final String HOLDERS = """
            package test
            class Item { }
            class SpecialItem : Item { }
            class Holder {
                contains items: Item[0..*]
                contains first: Item[1] subsets items
                contains second: Item[1] subsets items
            }
            class Restated : Holder {
                redefine contains first: SpecialItem[1] subsets items
            }
            class Omitted : Holder {
                redefine contains first: SpecialItem[1]
            }
            """;

    @Inject
    private OxstsPackageParseHelper parseHelper;

    @Inject
    private FeatureSubSettersFinder featureSubSettersFinder;

    @Test
    void subsettersAreTheFeaturesThatNameTheSuperset() {
        var parsedPackage = parseHelper.parse(HOLDERS, CheckMode.ALL);
        parsedPackage.assertNoResourceErrors();
        parsedPackage.assertNoValidationErrors();
        var holder = parsedPackage.classByName("Holder");

        assertThat(featureSubSettersFinder.getSubSetters(
                        holder.eObject(), holder.featureByName("items").eObject()))
                .extracting(NamedElement::getName)
                .containsExactly("first", "second");
    }

    @Test
    void aRedefinitionThatRestatesTheSupersetStaysASubsetter() {
        var parsedPackage = parseHelper.parse(HOLDERS, CheckMode.ALL);
        var items = parsedPackage.classByName("Holder").featureByName("items").eObject();
        var restated = parsedPackage.classByName("Restated");

        assertThat(featureSubSettersFinder.getSubSetters(restated.eObject(), items))
                .containsExactlyInAnyOrder(
                        restated.featureByName("first").eObject(),
                        parsedPackage
                                .classByName("Holder")
                                .featureByName("second")
                                .eObject());
    }

    @Test
    void aRedefinitionWithoutSubsetsKeepsTheSupersetOfTheRedefinedFeature() {
        var parsedPackage = parseHelper.parse(HOLDERS, CheckMode.ALL);
        var items = parsedPackage.classByName("Holder").featureByName("items").eObject();
        var omitted = parsedPackage.classByName("Omitted");

        assertThat(featureSubSettersFinder.getSubSetters(omitted.eObject(), items))
                .containsExactlyInAnyOrder(
                        omitted.featureByName("first").eObject(),
                        parsedPackage
                                .classByName("Holder")
                                .featureByName("second")
                                .eObject());
    }

    @Test
    void aValueOfARedefinitionWithoutSubsetsIsAssignableToTheSuperset() {
        var parsedPackage = parseHelper.parse(HOLDERS + """
            class User : Omitted {
                var pick: items[0..1] := nothing
                redefine init { pick := first }
            }
            """, CheckMode.ALL);

        parsedPackage.assertNoResourceErrors();
        parsedPackage.assertNoValidationErrors();
    }

    @Test
    void aValueOfAnUnrelatedFeatureIsNotAssignableToTheSuperset() {
        var parsedPackage = parseHelper.parse(HOLDERS + """
            class User : Omitted {
                contains other: Item
                var pick: items[0..1] := nothing
                redefine init { pick := other }
            }
            """, CheckMode.ALL);

        parsedPackage.assertHasValidationIssue(OxstsValidator.TYPE_MISMATCH, "assign");
    }
}

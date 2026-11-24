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
import java.util.List;
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

    private static final String REDEFINED_SUPERSETS = """
            package test
            class Item { }
            class Holder {
                contains all: Item[0..*]
                contains items: Item[0..*] subsets all
                contains first: Item[1] subsets items
            }
            class Direct : Holder {
                redefine contains items: Item[0..*]
            }
            class Middle : Holder {
                redefine contains items: Item[0..*]
            }
            class Chain : Middle {
                contains slots: Item[0..*] redefines items
            }
            class Both : Holder {
                contains extra: Item[0..*]
                redefine contains items: Item[0..*] subsets extra
            }
            """;

    private List<String> subSetterNames(String className, String declaringClassName, String featureName) {
        var parsedPackage = parseHelper.parse(REDEFINED_SUPERSETS, CheckMode.ALL);
        parsedPackage.assertNoResourceErrors();
        parsedPackage.assertNoValidationErrors();
        var domain = parsedPackage.classByName(className).eObject();
        var feature = parsedPackage
                .classByName(declaringClassName)
                .featureByName(featureName)
                .eObject();

        return featureSubSettersFinder.getSubSetters(domain, feature).stream()
                .map(NamedElement::getName)
                .toList();
    }

    @Test
    void aSubsetterOfAFeatureIsASubsetterOfItsRedefinition() {
        assertThat(subSetterNames("Direct", "Direct", "items")).containsExactly("first");
    }

    @Test
    void aSubsetterOfAFeatureIsASubsetterOfEveryRedefinitionInAChain() {
        assertThat(subSetterNames("Chain", "Chain", "slots")).containsExactly("first");
    }

    @Test
    void aSubsetterIsFoundThroughTheRedefinitionWhateverFeatureTheQueryNames() {
        var parsedPackage = parseHelper.parse(REDEFINED_SUPERSETS, CheckMode.ALL);
        var chain = parsedPackage.classByName("Chain").eObject();
        var items = parsedPackage.classByName("Holder").featureByName("items").eObject();

        assertThat(featureSubSettersFinder.getSubSetters(chain, items))
                .extracting(NamedElement::getName)
                .containsExactly("first");
    }

    @Test
    void aFeatureThatRedefinesAndSubsetsIsUnderTheSupersetOfTheRedefinedFeatureToo() {
        assertThat(subSetterNames("Both", "Both", "extra")).containsExactly("items");
        assertThat(subSetterNames("Both", "Holder", "all")).containsExactly("items");
        assertThat(subSetterNames("Both", "Both", "items")).containsExactly("first");
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

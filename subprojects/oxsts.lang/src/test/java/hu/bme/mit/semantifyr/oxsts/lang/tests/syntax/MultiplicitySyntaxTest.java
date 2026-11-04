/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.tests.syntax;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Inject;
import hu.bme.mit.semantifyr.oxsts.lang.semantics.MultiplicityRangeEvaluator;
import hu.bme.mit.semantifyr.oxsts.lang.semantics.expression.RangeEvaluation;
import hu.bme.mit.semantifyr.oxsts.lang.tests.InjectWithOxsts;
import hu.bme.mit.semantifyr.oxsts.lang.tests.utils.OxstsPackageParseHelper;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.DefiniteMultiplicity;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ElementReference;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LiteralInfinity;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.RangeExpression;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.UnboundedMultiplicity;
import org.eclipse.xtext.validation.CheckMode;
import org.junit.jupiter.api.Test;

@InjectWithOxsts
public class MultiplicitySyntaxTest {

    @Inject
    private OxstsPackageParseHelper parseHelper;

    @Inject
    private MultiplicityRangeEvaluator multiplicityRangeEvaluator;

    @Test
    void implicitMultiplicityIsOne() {
        // No explicit multiplicity -> default ONE (i.e. [1..1]).
        var parsedPackage = parseHelper.parse("""
            package test
            class Leaf { }
            class Host { contains leaf: Leaf }
            """);
        parsedPackage.assertNoResourceErrors();
        var typeSpec = parsedPackage.classByName("Host").featureByName("leaf").typeSpecification();
        assertThat(typeSpec.getMultiplicity()).isNull();
        var range = multiplicityRangeEvaluator.evaluate(typeSpec);
        assertThat(range.getLowerBound()).isEqualTo(1);
        assertThat(range.getUpperBound()).isEqualTo(1);
    }

    @Test
    void singleIntegerMultiplicity() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Leaf { }
            class Host { contains leaves: Leaf[3] }
            """);
        parsedPackage.assertNoResourceErrors();
        var typeSpec = parsedPackage.classByName("Host").featureByName("leaves").typeSpecification();
        assertThat(typeSpec.getMultiplicity()).isInstanceOf(DefiniteMultiplicity.class);
        var range = multiplicityRangeEvaluator.evaluate(typeSpec);
        assertThat(range.getLowerBound()).isEqualTo(3);
        assertThat(range.getUpperBound()).isEqualTo(3);
    }

    @Test
    void rangeMultiplicity() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Leaf { }
            class Host { contains leaves: Leaf[2..4] }
            """);
        parsedPackage.assertNoResourceErrors();
        var typeSpec = parsedPackage.classByName("Host").featureByName("leaves").typeSpecification();
        var multiplicity = (DefiniteMultiplicity) typeSpec.getMultiplicity();
        assertThat(multiplicity.getExpression()).isInstanceOf(RangeExpression.class);
        var range = multiplicityRangeEvaluator.evaluate(typeSpec);
        assertThat(range.getLowerBound()).isEqualTo(2);
        assertThat(range.getUpperBound()).isEqualTo(4);
    }

    @Test
    void exclusiveRangeMultiplicityExcludesUpperBound() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Leaf { }
            class Host { contains leaves: Leaf[0..<3] }
            """);
        parsedPackage.assertNoResourceErrors();
        var typeSpec = parsedPackage.classByName("Host").featureByName("leaves").typeSpecification();
        var range = multiplicityRangeEvaluator.evaluate(typeSpec);
        assertThat(range.getLowerBound()).isEqualTo(0);
        assertThat(range.getUpperBound()).isEqualTo(2);
    }

    @Test
    void rangeMultiplicityWithInfinityUpper() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Leaf { }
            class Host { contains leaves: Leaf[0..*] }
            """);
        parsedPackage.assertNoResourceErrors();
        var typeSpec = parsedPackage.classByName("Host").featureByName("leaves").typeSpecification();
        var range = multiplicityRangeEvaluator.evaluate(typeSpec);
        assertThat(range.getLowerBound()).isEqualTo(0);
        assertThat(range.getUpperBound()).isEqualTo(RangeEvaluation.INFINITY);
    }

    @Test
    void bareInfinityMultiplicityIsRejected() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Leaf { }
            class Host { contains leaves: Leaf[*] }
            """, CheckMode.ALL);
        parsedPackage.assertHasValidationIssue("OXSTS.TYPE_MISMATCH", "`[*]` is not a valid multiplicity");
        var typeSpec = parsedPackage.classByName("Host").featureByName("leaves").typeSpecification();
        var multiplicity = (DefiniteMultiplicity) typeSpec.getMultiplicity();
        assertThat(multiplicity.getExpression()).isInstanceOf(LiteralInfinity.class);
    }

    @Test
    void unboundedMultiplicityBrackets() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Leaf { }
            class Host { contains leaves: Leaf[] }
            """);
        parsedPackage.assertNoResourceErrors();
        var typeSpec = parsedPackage.classByName("Host").featureByName("leaves").typeSpecification();
        assertThat(typeSpec.getMultiplicity()).isInstanceOf(UnboundedMultiplicity.class);
    }

    @Test
    void multiplicityFromElementReference() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Leaf { }
            class Host {
                refers size: int = 3
                contains leaves: Leaf[size]
            }
            """);
        parsedPackage.assertNoResourceErrors();
        var typeSpec = parsedPackage.classByName("Host").featureByName("leaves").typeSpecification();
        var multiplicity = (DefiniteMultiplicity) typeSpec.getMultiplicity();
        assertThat(multiplicity.getExpression()).isInstanceOf(ElementReference.class);
    }
}

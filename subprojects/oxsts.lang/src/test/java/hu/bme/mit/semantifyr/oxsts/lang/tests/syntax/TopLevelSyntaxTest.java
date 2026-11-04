/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.tests.syntax;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Inject;
import hu.bme.mit.semantifyr.oxsts.lang.tests.InjectWithOxsts;
import hu.bme.mit.semantifyr.oxsts.lang.tests.utils.OxstsPackageParseHelper;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.AnnotationDeclaration;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.FeatureKind;
import org.eclipse.xtext.EcoreUtil2;
import org.junit.jupiter.api.Test;

@InjectWithOxsts
public class TopLevelSyntaxTest {

    @Inject
    private OxstsPackageParseHelper parseHelper;

    @Test
    void packageDeclaration() {
        var parsedPackage = parseHelper.parse("""
            package test::coverage::example
            class Example { }
            """);
        parsedPackage.assertNoResourceErrors();
        assertThat(parsedPackage.getOxstsPackage().getName()).isEqualTo("test::coverage::example");
    }

    @Test
    void emptyPackage() {
        var parsedPackage = parseHelper.parse("""
            package test::empty
            """);
        parsedPackage.assertNoResourceErrors();
        assertThat(parsedPackage.getOxstsPackage().getDeclarations()).isEmpty();
    }

    @Test
    void importStatement() {
        var parsedPackage = parseHelper.parse("""
            package test::base
            import semantifyr
            class Shared { }
            """);
        parsedPackage.assertNoResourceErrors();
    }

    @Test
    void enumWithLiterals() {
        var parsedPackage = parseHelper.parse("""
            package test
            enum Color {
                Red,
                Green,
                Blue,
            }
            """);
        parsedPackage.assertNoResourceErrors();
        var colorEnum = parsedPackage.enumByName("Color");
        assertThat(colorEnum.literalNames()).containsExactly("Red", "Green", "Blue");
    }

    @Test
    void enumWithoutLiterals() {
        var parsedPackage = parseHelper.parse("""
            package test
            enum Empty { }
            """);
        parsedPackage.assertNoResourceErrors();
        assertThat(parsedPackage.enumByName("Empty").literals()).isEmpty();
    }

    @Test
    void concreteClass() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Concrete { }
            """);
        parsedPackage.assertNoResourceErrors();
        assertThat(parsedPackage.classByName("Concrete").isAbstract()).isFalse();
    }

    @Test
    void abstractClass() {
        var parsedPackage = parseHelper.parse("""
            package test
            abstract class Base { }
            """);
        parsedPackage.assertNoResourceErrors();
        assertThat(parsedPackage.classByName("Base").isAbstract()).isTrue();
    }

    @Test
    void classWithSingleInheritance() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Base { }
            class Child : Base { }
            """);
        parsedPackage.assertNoResourceErrors();
        var child = parsedPackage.classByName("Child");
        assertThat(child.superTypes()).hasSize(1);
        assertThat(child.superTypes().getFirst().name()).isEqualTo("Base");
    }

    @Test
    void classWithMultipleInheritance() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Left { }
            class Right { }
            class Child : Left, Right { }
            """);
        parsedPackage.assertNoResourceErrors();
        var child = parsedPackage.classByName("Child");
        assertThat(child.superTypes()).hasSize(2);
        assertThat(child.superTypes().stream().map(superType -> superType.name()))
                .containsExactly("Left", "Right");
    }

    @Test
    void classWithSemicolonOnly() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Marker;
            """);
        parsedPackage.assertNoResourceErrors();
        assertThat(parsedPackage.classByName("Marker").eObject().getMembers()).isEmpty();
    }

    @Test
    void classWithoutBody() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Sentinel
            """);
        parsedPackage.assertNoResourceErrors();
        assertThat(parsedPackage.classByName("Sentinel").eObject().getMembers()).isEmpty();
    }

    @Test
    void globalContainmentFeature() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Shared { }
            global containment shared: Shared[1]
            """);
        parsedPackage.assertNoResourceErrors();
        var sharedFeature = parsedPackage.globalFeatureByName("shared");
        assertThat(sharedFeature.kind()).isEqualTo(FeatureKind.CONTAINMENT);
    }

    @Test
    void globalReferenceFeature() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Thing { }
            global reference pointer: Thing[1]
            """);
        parsedPackage.assertNoResourceErrors();
        assertThat(parsedPackage.globalFeatureByName("pointer").kind()).isEqualTo(FeatureKind.REFERENCE);
    }

    @Test
    void globalFeatureWithSubsets() {
        var parsedPackage = parseHelper.parse("""
            package test
            class Event { }
            global containment events: Event[0..*]
            global containment sharedEvent: Event subsets events
            """);
        parsedPackage.assertNoResourceErrors();
        assertThat(parsedPackage.globalFeatureByName("sharedEvent").superset().eObject())
                .isSameAs(parsedPackage.globalFeatureByName("events").eObject());
    }

    @Test
    void annotationDeclarationNoParameters() {
        var parsedPackage = parseHelper.parse("""
            package test
            annotation Marker
            """);
        parsedPackage.assertNoResourceErrors();
        var annotations = EcoreUtil2.eAllOfType(parsedPackage.getOxstsPackage(), AnnotationDeclaration.class);
        assertThat(annotations).hasSize(1);
        assertThat(annotations.getFirst().getName()).isEqualTo("Marker");
        assertThat(annotations.getFirst().getParameters()).isEmpty();
    }

    @Test
    void annotationDeclarationWithParameters() {
        var parsedPackage = parseHelper.parse("""
            package test
            annotation Tag(category: string)
            """);
        parsedPackage.assertNoResourceErrors();
        var annotation = EcoreUtil2.eAllOfType(parsedPackage.getOxstsPackage(), AnnotationDeclaration.class)
                .getFirst();
        assertThat(annotation.getParameters()).hasSize(1);
        assertThat(annotation.getParameters().getFirst().getName()).isEqualTo("category");
    }

    @Test
    void multipleTopLevelDeclarations() {
        var parsedPackage = parseHelper.parse("""
            package test
            enum Color {
                Red,
                Green,
            }
            annotation Marker
            class Left { }
            class Right { }
            """);
        parsedPackage.assertNoResourceErrors();
        assertThat(parsedPackage.classes()).hasSize(2);
        assertThat(parsedPackage.enums()).hasSize(1);
    }
}

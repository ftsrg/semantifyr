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
        var pkg = parseHelper.parse("""
            package test::coverage::example
            class X { }
            """);
        pkg.assertNoResourceErrors();
        assertThat(pkg.getOxstsPackage().getName()).isEqualTo("test::coverage::example");
    }

    @Test
    void emptyPackage() {
        var pkg = parseHelper.parse("""
            package test::empty
            """);
        pkg.assertNoResourceErrors();
        assertThat(pkg.getOxstsPackage().getDeclarations()).isEmpty();
    }

    @Test
    void importStatement() {
        var pkg = parseHelper.parse("""
            package test::base
            import semantifyr
            class Shared { }
            """);
        pkg.assertNoResourceErrors();
    }

    @Test
    void enumWithLiterals() {
        var pkg = parseHelper.parse("""
            package test
            enum Color { Red, Green, Blue }
            """);
        pkg.assertNoResourceErrors();
        var colorEnum = pkg.enumByName("Color");
        assertThat(colorEnum.literalNames()).containsExactly("Red", "Green", "Blue");
    }

    @Test
    void enumWithoutLiterals() {
        var pkg = parseHelper.parse("""
            package test
            enum Empty { }
            """);
        pkg.assertNoResourceErrors();
        assertThat(pkg.enumByName("Empty").literals()).isEmpty();
    }

    @Test
    void concreteClass() {
        var pkg = parseHelper.parse("""
            package test
            class Concrete { }
            """);
        pkg.assertNoResourceErrors();
        assertThat(pkg.classByName("Concrete").isAbstract()).isFalse();
    }

    @Test
    void abstractClass() {
        var pkg = parseHelper.parse("""
            package test
            abstract class Base { }
            """);
        pkg.assertNoResourceErrors();
        assertThat(pkg.classByName("Base").isAbstract()).isTrue();
    }

    @Test
    void classWithSingleInheritance() {
        var pkg = parseHelper.parse("""
            package test
            class Base { }
            class Child : Base { }
            """);
        pkg.assertNoResourceErrors();
        var child = pkg.classByName("Child");
        assertThat(child.superTypes()).hasSize(1);
        assertThat(child.superTypes().getFirst().name()).isEqualTo("Base");
    }

    @Test
    void classWithMultipleInheritance() {
        var pkg = parseHelper.parse("""
            package test
            class A { }
            class B { }
            class Child : A, B { }
            """);
        pkg.assertNoResourceErrors();
        var child = pkg.classByName("Child");
        assertThat(child.superTypes()).hasSize(2);
        assertThat(child.superTypes().stream().map(s -> s.name())).containsExactly("A", "B");
    }

    @Test
    void classWithSemicolonOnly() {
        var pkg = parseHelper.parse("""
            package test
            class Marker;
            """);
        pkg.assertNoResourceErrors();
        assertThat(pkg.classByName("Marker").eObject().getMembers()).isEmpty();
    }

    @Test
    void globalContainmentFeature() {
        var pkg = parseHelper.parse("""
            package test
            class Shared { }
            global containment shared: Shared[1]
            """);
        pkg.assertNoResourceErrors();
        var sharedFeature = pkg.globalFeatureByName("shared");
        assertThat(sharedFeature.kind()).isEqualTo(FeatureKind.CONTAINMENT);
    }

    @Test
    void globalReferenceFeature() {
        var pkg = parseHelper.parse("""
            package test
            class Thing { }
            global reference pointer: Thing[1]
            """);
        pkg.assertNoResourceErrors();
        assertThat(pkg.globalFeatureByName("pointer").kind()).isEqualTo(FeatureKind.REFERENCE);
    }

    @Test
    void annotationDeclarationNoParameters() {
        var pkg = parseHelper.parse("""
            package test
            annotation Marker
            """);
        pkg.assertNoResourceErrors();
        var ann = EcoreUtil2.eAllOfType(pkg.getOxstsPackage(), AnnotationDeclaration.class);
        assertThat(ann).hasSize(1);
        assertThat(ann.getFirst().getName()).isEqualTo("Marker");
        assertThat(ann.getFirst().getParameters()).isEmpty();
    }

    @Test
    void annotationDeclarationWithParameters() {
        var pkg = parseHelper.parse("""
            package test
            annotation Tag(category: string)
            """);
        pkg.assertNoResourceErrors();
        var ann = EcoreUtil2.eAllOfType(pkg.getOxstsPackage(), AnnotationDeclaration.class)
                .getFirst();
        assertThat(ann.getParameters()).hasSize(1);
        assertThat(ann.getParameters().getFirst().getName()).isEqualTo("category");
    }

    @Test
    void multipleTopLevelDeclarations() {
        var pkg = parseHelper.parse("""
            package test
            enum Color { Red, Green }
            annotation Marker
            class A { }
            class B { }
            """);
        pkg.assertNoResourceErrors();
        assertThat(pkg.classes()).hasSize(2);
        assertThat(pkg.enums()).hasSize(1);
    }
}

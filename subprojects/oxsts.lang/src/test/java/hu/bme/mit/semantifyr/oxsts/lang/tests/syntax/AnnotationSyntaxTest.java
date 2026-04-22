/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.tests.syntax;

import com.google.inject.Inject;
import hu.bme.mit.semantifyr.oxsts.lang.tests.InjectWithOxsts;
import hu.bme.mit.semantifyr.oxsts.lang.tests.utils.OxstsPackageParseHelper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@InjectWithOxsts
public class AnnotationSyntaxTest {

    @Inject
    private OxstsPackageParseHelper parseHelper;

    @Test
    void singleAnnotationWithoutArguments() {
        var pkg = parseHelper.parse("""
            package test
            @VerificationCase
            class Case { }
            """);
        pkg.assertNoResourceErrors();
        var container = pkg.classByName("Case").eObject().getAnnotation();
        assertThat(container).isNotNull();
        assertThat(container.getAnnotations()).hasSize(1);
    }

    @Test
    void annotationWithPositionalArgument() {
        var pkg = parseHelper.parse("""
            package test
            @VerificationCase("reachability")
            class Case { }
            """);
        pkg.assertNoResourceErrors();
        var ann = pkg.classByName("Case").eObject().getAnnotation().getAnnotations().getFirst();
        assertThat(ann.getArguments()).hasSize(1);
        assertThat(ann.getArguments().getFirst().getExpression()).isNotNull();
    }

    @Test
    void annotationWithNamedArgument() {
        var pkg = parseHelper.parse("""
            package test
            @VerificationCase(summary = "reachability")
            class Case { }
            """);
        pkg.assertNoResourceErrors();
        var ann = pkg.classByName("Case").eObject().getAnnotation().getAnnotations().getFirst();
        assertThat(ann.getArguments()).hasSize(1);
        assertThat(ann.getArguments().getFirst().getParameter()).isNotNull();
        assertThat(ann.getArguments().getFirst().getParameter().getName()).isEqualTo("summary");
    }

    @Test
    void multipleAnnotationsOnSameDeclaration() {
        var pkg = parseHelper.parse("""
            package test
            annotation Tag(category: string)
            @VerificationCase
            @Tag(category = "demo")
            class Case { }
            """);
        pkg.assertNoResourceErrors();
        assertThat(pkg.classByName("Case").eObject().getAnnotation().getAnnotations()).hasSize(2);
    }

    @Test
    void annotationOnVariableDeclaration() {
        var pkg = parseHelper.parse("""
            package test
            annotation Tracked
            class C {
                @Tracked
                var x: int := 0
            }
            """);
        pkg.assertNoResourceErrors();
        var variable = pkg.classByName("C").variableByName("x").eObject();
        assertThat(variable.getAnnotation()).isNotNull();
        assertThat(variable.getAnnotation().getAnnotations()).hasSize(1);
    }
}

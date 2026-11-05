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
import org.junit.jupiter.api.Test;

@InjectWithOxsts
public class AnnotationSyntaxTest {

    @Inject
    private OxstsPackageParseHelper parseHelper;

    @Test
    void singleAnnotationWithoutArguments() {
        var parsedPackage = parseHelper.parse("""
            package test
            @VerificationCase
            class Case { }
            """);
        parsedPackage.assertNoResourceErrors();
        var container = parsedPackage.classByName("Case").eObject().getAnnotation();
        assertThat(container).isNotNull();
        assertThat(container.getAnnotations()).hasSize(1);
    }

    @Test
    void annotationWithPositionalArgument() {
        var parsedPackage = parseHelper.parse("""
            package test
            @VerificationCase("reachability")
            class Case { }
            """);
        parsedPackage.assertNoResourceErrors();
        var annotation = parsedPackage
                .classByName("Case")
                .eObject()
                .getAnnotation()
                .getAnnotations()
                .getFirst();
        assertThat(annotation.getArguments()).hasSize(1);
        assertThat(annotation.getArguments().getFirst().getExpression()).isNotNull();
    }

    @Test
    void annotationWithNamedArgument() {
        var parsedPackage = parseHelper.parse("""
            package test
            @VerificationCase(summary = "reachability")
            class Case { }
            """);
        parsedPackage.assertNoResourceErrors();
        var annotation = parsedPackage
                .classByName("Case")
                .eObject()
                .getAnnotation()
                .getAnnotations()
                .getFirst();
        assertThat(annotation.getArguments()).hasSize(1);
        assertThat(annotation.getArguments().getFirst().getParameter()).isNotNull();
        assertThat(annotation.getArguments().getFirst().getParameter().getName())
                .isEqualTo("summary");
    }

    @Test
    void multipleAnnotationsOnSameDeclaration() {
        var parsedPackage = parseHelper.parse("""
            package test
            annotation Tag(category: string)
            @VerificationCase
            @Tag(category = "demo")
            class Case { }
            """);
        parsedPackage.assertNoResourceErrors();
        assertThat(parsedPackage.classByName("Case").eObject().getAnnotation().getAnnotations())
                .hasSize(2);
    }

    @Test
    void annotationOnVariableDeclaration() {
        var parsedPackage = parseHelper.parse("""
            package test
            annotation Tracked
            class Sensor {
                @Tracked
                var reading: int := 0
            }
            """);
        parsedPackage.assertNoResourceErrors();
        var variable =
                parsedPackage.classByName("Sensor").variableByName("reading").eObject();
        assertThat(variable.getAnnotation()).isNotNull();
        assertThat(variable.getAnnotation().getAnnotations()).hasSize(1);
    }
}

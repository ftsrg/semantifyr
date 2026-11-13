/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.website;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Inject;
import com.google.inject.Provider;
import hu.bme.mit.semantifyr.oxsts.lang.tests.InjectWithOxsts;
import hu.bme.mit.semantifyr.website.DocumentationFences.Fence;
import java.io.IOException;
import java.util.Objects;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.xtext.diagnostics.Diagnostic;
import org.eclipse.xtext.diagnostics.Severity;
import org.eclipse.xtext.resource.XtextResourceSet;
import org.eclipse.xtext.testing.util.ParseHelper;
import org.eclipse.xtext.util.CancelIndicator;
import org.eclipse.xtext.validation.CheckMode;
import org.eclipse.xtext.validation.IResourceValidator;
import org.eclipse.xtext.validation.Issue;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@InjectWithOxsts
public class DocumentationFenceTest {

    @Inject
    private ParseHelper<EObject> parseHelper;

    @Inject
    private Provider<XtextResourceSet> resourceSetProvider;

    @Inject
    private IResourceValidator resourceValidator;

    static Stream<Arguments> fencesValidate() throws IOException {
        return DocumentationFences.allFences().stream().map(fence -> {
            return Arguments.of(Named.of(fence.location(), fence));
        });
    }

    private static String locationOf(Fence fence, Issue issue) {
        var index = issue.getLineNumber() - 1;
        var pageLine = index >= 0 && index < fence.model().size()
                ? fence.model().get(index).pageLine()
                : fence.line();
        return DocumentationFences.DOCUMENTATION.relativize(fence.page()) + ":" + pageLine;
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void fencesValidate(Fence fence) throws Exception {
        var resourceSet = resourceSetProvider.get();
        if (!fence.continued()) {
            for (var sibling : DocumentationFences.fences(fence.page())) {
                if (sibling.line() != fence.line()
                        && !sibling.continued()
                        && sibling.issue() == null
                        && sibling.declaresPackage()) {
                    parseHelper.parse(sibling.text(), resourceSet);
                }
            }
        }
        var model = parseHelper.parse(fence.text(), resourceSet);
        var issues = resourceValidator.validate(model.eResource(), CheckMode.ALL, CancelIndicator.NullImpl).stream()
                .filter(issue -> {
                    return issue.getSeverity() == Severity.ERROR || issue.getSeverity() == Severity.WARNING;
                })
                .toList();
        var syntaxErrors = issues.stream()
                .filter(issue -> {
                    return Diagnostic.SYNTAX_DIAGNOSTIC.equals(issue.getCode());
                })
                .toList();
        // what follows a syntax error says nothing of the fence
        if (!syntaxErrors.isEmpty()) {
            issues = syntaxErrors;
        }
        var described = issues.stream()
                .map(issue -> {
                    return locationOf(fence, issue) + " " + issue.getCode() + " " + issue.getMessage();
                })
                .toList();

        if (fence.issue() == null) {
            assertThat(described)
                    .as("issues of the oxsts fence at %s", fence.location())
                    .isEmpty();
        } else {
            var codes = issues.stream()
                    .map(issue -> {
                        return Objects.toString(issue.getCode(), "no code");
                    })
                    .collect(Collectors.toCollection(TreeSet::new));
            assertThat(codes)
                    .as(
                            "the issue codes of the oxsts fence at %s, which names %s: %s",
                            fence.location(), fence.issue(), described)
                    .containsExactly(expectedCode(fence.issue()));
        }
    }

    private static String expectedCode(String issue) {
        return issue.equals(DocumentationFences.SYNTAX) ? Diagnostic.SYNTAX_DIAGNOSTIC : issue;
    }
}

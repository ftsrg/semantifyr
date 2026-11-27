/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.website;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.Gson;
import com.google.inject.Inject;
import hu.bme.mit.semantifyr.compiler.reader.SemantifyrLoader;
import hu.bme.mit.semantifyr.oxsts.lang.tests.InjectWithOxsts;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.eclipse.xtext.EcoreUtil2;
import org.eclipse.xtext.diagnostics.Diagnostic;
import org.eclipse.xtext.diagnostics.Severity;
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

    private static final Path FENCES = Path.of("build/fences");

    private static final Pattern PACKAGE_DECLARATION = Pattern.compile("^\\s*package\\s", Pattern.MULTILINE);

    record Manifest(List<Page> pages) {}

    record Page(String page, List<Fence> fences) {}

    record Fence(String file, int line, List<Integer> pageLines, boolean continued, String issue) {}

    @Inject
    private SemantifyrLoader loader;

    @Inject
    private IResourceValidator resourceValidator;

    static Stream<Arguments> fencesValidate() throws IOException {
        try (var reader = Files.newBufferedReader(FENCES.resolve("manifest.json"))) {
            return new Gson()
                    .fromJson(reader, Manifest.class).pages().stream()
                            .flatMap(page -> page.fences().stream()
                                    .map(fence ->
                                            Arguments.of(Named.of(page.page() + ":" + fence.line(), fence), page)));
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void fencesValidate(Fence fence, Page page) throws IOException {
        var context = loader.startContext();
        if (!fence.continued()) {
            for (var sibling : page.fences()) {
                if (!sibling.equals(fence)
                        && !sibling.continued()
                        && sibling.issue() == null
                        && declaresPackage(sibling)) {
                    context.loadModel(FENCES.resolve(sibling.file()));
                }
            }
        }
        context.loadModel(FENCES.resolve(fence.file()));
        EcoreUtil2.resolveAll(context.getResourceSet());
        var issues =
                resourceValidator
                        .validate(context.getModelResources().getLast(), CheckMode.ALL, CancelIndicator.NullImpl)
                        .stream()
                        .filter(issue ->
                                issue.getSeverity() == Severity.ERROR || issue.getSeverity() == Severity.WARNING)
                        .toList();
        var codes = issues.stream().map(Issue::getCode).distinct().toList();
        var described = issues.stream()
                .map(issue -> locationOf(page, fence, issue) + " " + issue.getCode() + " " + issue.getMessage())
                .toList();

        var location = page.page() + ":" + fence.line();
        if (fence.issue() == null) {
            assertThat(described)
                    .as("issues of the oxsts fence at %s", location)
                    .isEmpty();
        } else if (fence.issue().equals("syntax")) {
            assertThat(codes)
                    .as("the issue codes of the oxsts fence at %s: %s", location, described)
                    .contains(Diagnostic.SYNTAX_DIAGNOSTIC);
        } else {
            assertThat(codes)
                    .as("the issue codes of the oxsts fence at %s: %s", location, described)
                    .containsExactly(fence.issue());
        }
    }

    private static boolean declaresPackage(Fence fence) throws IOException {
        return PACKAGE_DECLARATION
                .matcher(Files.readString(FENCES.resolve(fence.file())))
                .find();
    }

    private static String locationOf(Page page, Fence fence, Issue issue) {
        var index = issue.getLineNumber() - 1;
        var pageLine = index >= 0 && index < fence.pageLines().size()
                ? fence.pageLines().get(index)
                : fence.line();
        return page.page() + ":" + pageLine;
    }
}

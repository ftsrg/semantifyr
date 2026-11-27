/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.website;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.Gson;
import com.google.inject.Inject;
import com.google.inject.Provider;
import hu.bme.mit.semantifyr.oxsts.lang.library.builtin.BuiltinLibrary;
import hu.bme.mit.semantifyr.oxsts.lang.tests.InjectWithOxsts;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;
import java.util.regex.Pattern;
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

// Goal: every model the site shows validates: a fence raises the issue it names or none, an imported model no error
@InjectWithOxsts
public class DocumentationFenceTest {

    // extractFences writes each fence's model as remarkFences assembles it
    private static final Path FENCES = Path.of("build/fences");

    private static final String SYNTAX = "syntax";

    private static final Pattern PACKAGE_DECLARATION = Pattern.compile("^\\s*package\\s+([\\w:]+)", Pattern.MULTILINE);

    record Manifest(List<Page> pages) {}

    record Page(String page, List<Fence> fences, List<ImportedModel> importedModels) {}

    record Fence(String file, int line, List<Integer> pageLines, boolean continued, String issue) {}

    record ImportedModel(String source, String file) {}

    @Inject
    private ParseHelper<EObject> parseHelper;

    @Inject
    private Provider<XtextResourceSet> resourceSetProvider;

    @Inject
    private IResourceValidator resourceValidator;

    private static Manifest manifest() throws IOException {
        try (var reader = Files.newBufferedReader(FENCES.resolve("manifest.json"))) {
            return new Gson().fromJson(reader, Manifest.class);
        }
    }

    private static String textOf(Fence fence) throws IOException {
        return Files.readString(FENCES.resolve(fence.file()));
    }

    private static boolean declaresPackage(String text) {
        return PACKAGE_DECLARATION.matcher(text).find();
    }

    static Stream<Arguments> fencesValidate() throws IOException {
        return manifest().pages().stream().flatMap(page -> {
            return page.fences().stream().map(fence -> {
                return Arguments.of(Named.of(page.page() + ":" + fence.line(), fence), page);
            });
        });
    }

    private static String locationOf(Page page, Fence fence, Issue issue) {
        var index = issue.getLineNumber() - 1;
        var pageLine = index >= 0 && index < fence.pageLines().size()
                ? fence.pageLines().get(index)
                : fence.line();
        return page.page() + ":" + pageLine;
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void fencesValidate(Fence fence, Page page) throws Exception {
        var resourceSet = resourceSetProvider.get();
        if (!fence.continued()) {
            for (var sibling : page.fences()) {
                var text = textOf(sibling);
                if (sibling.line() != fence.line()
                        && !sibling.continued()
                        && sibling.issue() == null
                        && declaresPackage(text)) {
                    parseHelper.parse(text, resourceSet);
                }
            }
        }
        var model = parseHelper.parse(textOf(fence), resourceSet);
        var issues = errorsAndWarnings(model);
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
                    return locationOf(page, fence, issue) + " " + issue.getCode() + " " + issue.getMessage();
                })
                .toList();

        var location = page.page() + ":" + fence.line();
        if (fence.issue() == null) {
            assertThat(described)
                    .as("issues of the oxsts fence at %s", location)
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
                            location, fence.issue(), described)
                    .containsExactly(expectedCode(fence.issue()));
        }
    }

    private static String expectedCode(String issue) {
        return issue.equals(SYNTAX) ? Diagnostic.SYNTAX_DIAGNOSTIC : issue;
    }

    // the standard library is the language's own, which validates only as the library the language loads it as
    private static List<ImportedModel> validatedModelsOf(Page page) throws IOException {
        var models = new ArrayList<ImportedModel>();
        for (var model : page.importedModels()) {
            var declaration = PACKAGE_DECLARATION.matcher(Files.readString(Path.of(model.file())));
            var builtinLibrary = declaration.find()
                    && declaration.group(1).equals(BuiltinLibrary.BUILTIN_LIBRARY_NAME.toString("::"));
            if (!builtinLibrary) {
                models.add(model);
            }
        }
        return models;
    }

    static Stream<Arguments> importedModelsValidate() throws IOException {
        var arguments = new ArrayList<Arguments>();
        for (var page : manifest().pages()) {
            for (var model : validatedModelsOf(page)) {
                arguments.add(Arguments.of(Named.of(page.page() + " " + model.source(), model), page));
            }
        }
        return arguments.stream();
    }

    // a model is read with the packages its page's other models declare, as a witness with the model it witnesses
    @ParameterizedTest(name = "{0}")
    @MethodSource
    void importedModelsValidate(ImportedModel model, Page page) throws Exception {
        var resourceSet = resourceSetProvider.get();
        for (var sibling : validatedModelsOf(page)) {
            var text = Files.readString(Path.of(sibling.file()));
            if (!sibling.file().equals(model.file()) && declaresPackage(text)) {
                parseHelper.parse(text, resourceSet);
            }
        }
        var parsed = parseHelper.parse(Files.readString(Path.of(model.file())), resourceSet);
        var described = errorsAndWarnings(parsed).stream()
                .filter(issue -> {
                    return issue.getSeverity() == Severity.ERROR;
                })
                .map(issue -> {
                    return model.file() + ":" + issue.getLineNumber() + " " + issue.getCode() + " "
                            + issue.getMessage();
                })
                .toList();

        assertThat(described).as("errors of %s %s", page.page(), model.source()).isEmpty();
    }

    private List<Issue> errorsAndWarnings(EObject model) {
        return resourceValidator.validate(model.eResource(), CheckMode.ALL, CancelIndicator.NullImpl).stream()
                .filter(issue -> {
                    return issue.getSeverity() == Severity.ERROR || issue.getSeverity() == Severity.WARNING;
                })
                .toList();
    }
}

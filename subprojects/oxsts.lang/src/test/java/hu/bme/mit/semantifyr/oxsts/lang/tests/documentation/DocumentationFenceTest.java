/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.tests.documentation;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Inject;
import com.google.inject.Provider;
import hu.bme.mit.semantifyr.oxsts.lang.tests.InjectWithOxsts;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.OxstsModelPackage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.eclipse.xtext.diagnostics.Severity;
import org.eclipse.xtext.resource.XtextResourceSet;
import org.eclipse.xtext.testing.util.ParseHelper;
import org.eclipse.xtext.util.CancelIndicator;
import org.eclipse.xtext.validation.CheckMode;
import org.eclipse.xtext.validation.IResourceValidator;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

// A fence opened with ```oxsts fragment is a fragment and skipped; every other oxsts fence is a whole model.
@InjectWithOxsts
public class DocumentationFenceTest {

    private static final Path DOCUMENTATION = Path.of("../website/src/docs");

    private static final Pattern OPENING_FENCE = Pattern.compile("^(\\s*)```oxsts(\\s.*)?$");

    private static final Pattern PACKAGE_DECLARATION = Pattern.compile("^\\s*package\\s+([\\w:]+)");

    @Inject
    private ParseHelper<OxstsModelPackage> parseHelper;

    @Inject
    private Provider<XtextResourceSet> resourceSetProvider;

    @Inject
    private IResourceValidator resourceValidator;

    record Fence(Path page, int line, String text) {

        String location() {
            return DOCUMENTATION.relativize(page) + ":" + line;
        }

        String packageName() {
            var matcher = PACKAGE_DECLARATION.matcher(text);
            if (matcher.find()) {
                return matcher.group(1);
            }
            return null;
        }
    }

    static Stream<Arguments> completeFencesValidate() throws IOException {
        try (var paths = Files.walk(DOCUMENTATION)) {
            var pages = paths.filter(path -> {
                        var name = path.getFileName().toString();
                        return name.endsWith(".md") || name.endsWith(".mdx");
                    })
                    .sorted()
                    .toList();
            var arguments = new ArrayList<Arguments>();
            for (var page : pages) {
                for (var fence : completeFences(page)) {
                    arguments.add(Arguments.of(Named.of(fence.location(), fence)));
                }
            }
            return arguments.stream();
        }
    }

    private static List<Fence> completeFences(Path page) throws IOException {
        var lines = Files.readAllLines(page);
        var fences = new ArrayList<Fence>();
        var index = 0;
        while (index < lines.size()) {
            var opening = OPENING_FENCE.matcher(lines.get(index));
            if (!opening.matches()) {
                index++;
                continue;
            }
            var openingLine = index + 1;
            var indentation = opening.group(1).length();
            var infoWords = opening.group(2) == null ? "" : opening.group(2);
            var text = new StringBuilder();
            index++;
            while (index < lines.size() && !lines.get(index).trim().equals("```")) {
                var line = lines.get(index);
                text.append(line.length() >= indentation ? line.substring(indentation) : line.strip())
                        .append('\n');
                index++;
            }
            index++;
            if (!List.of(infoWords.trim().split("\\s+")).contains("fragment")) {
                fences.add(new Fence(page, openingLine, text.toString()));
            }
        }
        return fences;
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void completeFencesValidate(Fence fence) throws Exception {
        var resourceSet = resourceSetProvider.get();
        for (var sibling : completeFences(fence.page())) {
            if (sibling.line() != fence.line() && sibling.packageName() != null) {
                parseHelper.parse(sibling.text(), resourceSet);
            }
        }
        var model = parseHelper.parse(fence.text(), resourceSet);
        var issues = resourceValidator.validate(model.eResource(), CheckMode.ALL, CancelIndicator.NullImpl).stream()
                .filter(issue -> {
                    return issue.getSeverity() == Severity.ERROR || issue.getSeverity() == Severity.WARNING;
                })
                .map(issue -> {
                    return DOCUMENTATION.relativize(fence.page()) + ":" + (fence.line() + issue.getLineNumber()) + " "
                            + issue.getMessage();
                })
                .toList();

        assertThat(issues)
                .as("issues of the oxsts fence at %s", fence.location())
                .isEmpty();
    }
}

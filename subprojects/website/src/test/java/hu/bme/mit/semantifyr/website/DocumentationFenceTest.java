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
import hu.bme.mit.semantifyr.oxsts.model.oxsts.OxstsModelPackage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
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

// remarkFences reads the same words and markers and builds the same model of a fence
@InjectWithOxsts
public class DocumentationFenceTest {

    private static final Path DOCUMENTATION = Path.of("src/docs");

    private static final Pattern OPENING_FENCE = Pattern.compile("^(\\s*)```oxsts(\\s.*)?$");

    private static final Pattern PACKAGE_DECLARATION = Pattern.compile("^\\s*package\\s+([\\w:]+)", Pattern.MULTILINE);

    private static final String HIDE_START = "// hide-start";

    private static final String HIDE_END = "// hide-end";

    private static final Set<String> FLAGS = Set.of("run", "edit", "open", "hidden", "continue", "fragment");

    private static final Set<String> KEYS = Set.of("continue", "checkpoint", "issue");

    @Inject
    private ParseHelper<OxstsModelPackage> parseHelper;

    @Inject
    private Provider<XtextResourceSet> resourceSetProvider;

    @Inject
    private IResourceValidator resourceValidator;

    record ModelLine(String text, int pageLine) {}

    record Fence(Path page, int line, List<ModelLine> model, boolean continued, String issue, boolean fragment) {

        String location() {
            return DOCUMENTATION.relativize(page) + ":" + line;
        }

        String text() {
            return model.stream().map(ModelLine::text).collect(Collectors.joining("\n", "", "\n"));
        }

        boolean declaresPackage() {
            return PACKAGE_DECLARATION.matcher(text()).find();
        }

        String locationOf(Issue issue) {
            var index = issue.getLineNumber() - 1;
            var pageLine = index >= 0 && index < model.size() ? model.get(index).pageLine() : line;
            return DOCUMENTATION.relativize(page) + ":" + pageLine;
        }
    }

    static Stream<Arguments> fencesValidate() throws IOException {
        try (var paths = Files.walk(DOCUMENTATION)) {
            var pages = paths.filter(path -> {
                        var name = path.getFileName().toString();
                        return name.endsWith(".md") || name.endsWith(".mdx");
                    })
                    .sorted()
                    .toList();
            var arguments = new ArrayList<Arguments>();
            for (var page : pages) {
                for (var fence : fences(page)) {
                    if (!fence.fragment()) {
                        arguments.add(Arguments.of(Named.of(fence.location(), fence)));
                    }
                }
            }
            return arguments.stream();
        }
    }

    // a continued fence's model is the model it continues, a blank line and its own lines without the markers
    private static List<Fence> fences(Path page) throws IOException {
        var lines = Files.readAllLines(page);
        var fences = new ArrayList<Fence>();
        var checkpoints = new HashMap<String, List<ModelLine>>();
        List<ModelLine> previous = null;
        var index = 0;
        while (index < lines.size()) {
            var opening = OPENING_FENCE.matcher(lines.get(index));
            if (!opening.matches()) {
                index++;
                continue;
            }
            var openingLine = index + 1;
            var location = DOCUMENTATION.relativize(page) + ":" + openingLine;
            var indentation = opening.group(1).length();
            var infoWords = opening.group(2) == null ? "" : opening.group(2).strip();
            String continues = null;
            String checkpoint = null;
            String issue = null;
            var fragment = false;
            for (var word : infoWords.isEmpty() ? new String[0] : infoWords.split("\\s+")) {
                var separator = word.indexOf('=');
                var key = separator < 0 ? word : word.substring(0, separator);
                var value = separator < 0 ? null : word.substring(separator + 1);
                var known = value == null ? FLAGS.contains(key) : KEYS.contains(key) && !value.isEmpty();
                if (!known) {
                    throw new IllegalStateException("unknown word " + word + " of the oxsts fence at " + location);
                }
                switch (key) {
                    case "continue" -> continues = value == null ? "" : value;
                    case "checkpoint" -> checkpoint = value;
                    case "issue" -> issue = value.equals("syntax") ? Diagnostic.SYNTAX_DIAGNOSTIC : value;
                    case "fragment" -> fragment = true;
                    default -> {}
                }
            }
            var model = new ArrayList<ModelLine>();
            if (continues != null) {
                var continued = continues.isEmpty() ? previous : checkpoints.get(continues);
                if (continued == null) {
                    throw new IllegalStateException("the oxsts fence at " + location + " continues nothing");
                }
                model.addAll(continued);
                model.add(new ModelLine("", openingLine));
            }
            index++;
            while (index < lines.size() && !lines.get(index).trim().equals("```")) {
                var line = lines.get(index);
                var text = line.length() >= indentation ? line.substring(indentation) : line.strip();
                if (!text.strip().equals(HIDE_START) && !text.strip().equals(HIDE_END)) {
                    model.add(new ModelLine(text, index + 1));
                }
                index++;
            }
            index++;
            previous = List.copyOf(model);
            if (checkpoint != null) {
                checkpoints.put(checkpoint, previous);
            }
            fences.add(new Fence(page, openingLine, previous, continues != null, issue, fragment));
        }
        return fences;
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void fencesValidate(Fence fence) throws Exception {
        var resourceSet = resourceSetProvider.get();
        if (!fence.continued()) {
            for (var sibling : fences(fence.page())) {
                if (sibling.line() != fence.line()
                        && !sibling.continued()
                        && !sibling.fragment()
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
                    return fence.locationOf(issue) + " " + issue.getCode() + " " + issue.getMessage();
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
                    .containsExactly(fence.issue());
        }
    }
}

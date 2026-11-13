/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.website;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

// remarkFences reads the same words and markers and builds the same model of a fence
final class DocumentationFences {

    static final Path DOCUMENTATION = Path.of("src/docs");

    private static final Path CONFIGURATION = Path.of("docusaurus.config.ts");

    static final String SYNTAX = "syntax";

    private static final Pattern OPENING_FENCE = Pattern.compile("^(\\s*)```oxsts(\\s.*)?$");

    private static final Pattern PACKAGE_DECLARATION = Pattern.compile("^\\s*package\\s+([\\w:]+)", Pattern.MULTILINE);

    private static final Pattern ALIAS =
            Pattern.compile("^\\s*'(@[\\w-]+)':\\s*path\\.resolve\\(__dirname,\\s*'([^']+)'\\)");

    private static final Pattern IMPORT = Pattern.compile("^import\\s+\\w+\\s+from\\s+'(@[\\w-]+)/([^']+\\.oxsts)';?$");

    private static final String HIDE_START = "// hide-start";

    private static final String HIDE_END = "// hide-end";

    private static final Set<String> FLAGS = Set.of("run", "edit", "open", "hidden", "continue");

    private static final Set<String> KEYS = Set.of("continue", "checkpoint", "issue");

    record ModelLine(String text, int pageLine) {}

    record Fence(Path page, int line, List<ModelLine> model, boolean continued, String issue) {

        String location() {
            return DOCUMENTATION.relativize(page) + ":" + line;
        }

        String text() {
            return model.stream().map(ModelLine::text).collect(Collectors.joining("\n", "", "\n"));
        }

        boolean declaresPackage() {
            return PACKAGE_DECLARATION.matcher(text()).find();
        }
    }

    private DocumentationFences() {}

    static List<Path> pages() throws IOException {
        try (var paths = Files.walk(DOCUMENTATION)) {
            return paths.filter(path -> {
                        var name = path.getFileName().toString();
                        return name.endsWith(".md") || name.endsWith(".mdx");
                    })
                    .sorted()
                    .toList();
        }
    }

    static List<Fence> allFences() throws IOException {
        var fences = new ArrayList<Fence>();
        for (var page : pages()) {
            fences.addAll(fences(page));
        }
        return fences;
    }

    // the OXSTS files the pages import through the site's aliases, which their own tests check
    static List<Path> importedModels() throws IOException {
        var aliases = new HashMap<String, Path>();
        for (var line : Files.readAllLines(CONFIGURATION)) {
            var alias = ALIAS.matcher(line);
            if (alias.find()) {
                aliases.put(alias.group(1), Path.of(alias.group(2)));
            }
        }
        var models = new ArrayList<Path>();
        for (var page : pages()) {
            for (var line : Files.readAllLines(page)) {
                var imported = IMPORT.matcher(line.strip());
                if (!imported.matches()) {
                    continue;
                }
                var directory = aliases.get(imported.group(1));
                if (directory == null) {
                    throw new IllegalStateException(
                            "no alias " + imported.group(1) + " in " + CONFIGURATION + " for " + page);
                }
                var model = directory.resolve(imported.group(2)).normalize();
                if (!models.contains(model)) {
                    models.add(model);
                }
            }
        }
        return models;
    }

    // a continued fence's model is the model it continues, a blank line and its own lines without the markers
    static List<Fence> fences(Path page) throws IOException {
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
                    case "issue" -> issue = value;
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
            fences.add(new Fence(page, openingLine, previous, continues != null, issue));
        }
        return fences;
    }
}

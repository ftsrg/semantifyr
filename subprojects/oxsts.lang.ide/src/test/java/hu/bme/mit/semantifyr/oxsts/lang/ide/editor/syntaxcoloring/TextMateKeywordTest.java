/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.ide.editor.syntaxcoloring;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.Gson;
import hu.bme.mit.semantifyr.oxsts.lang.ide.OxstsIdeSetup;
import hu.bme.mit.semantifyr.oxsts.lang.services.OxstsGrammarAccess;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.eclipse.xtext.GrammarUtil;
import org.junit.jupiter.api.Test;

class TextMateKeywordTest {

    private static final Path TEXT_MATE_GRAMMAR =
            Path.of("..", "semantifyr-vscode", "syntaxes", "oxsts.tmLanguage.json");

    private static final Pattern WORD_ALTERNATION = Pattern.compile("\\\\b\\(([^()]*)\\)\\\\b");

    private static final Pattern WORD_KEYWORD = Pattern.compile("[A-Za-z]+( [A-Za-z]+)*");

    record TextMateGrammar(Map<String, RepositoryEntry> repository) {}

    record RepositoryEntry(List<TextMatePattern> patterns) {}

    record TextMatePattern(String match) {}

    @Test
    void textMatePatternsHighlightExactlyTheWordKeywordsOfTheGrammar() throws IOException {
        var grammarAccess =
                new OxstsIdeSetup().createInjectorAndDoEMFRegistration().getInstance(OxstsGrammarAccess.class);
        var grammarKeywords = GrammarUtil.getAllKeywords(grammarAccess.getGrammar()).stream()
                .filter(keyword -> {
                    return WORD_KEYWORD.matcher(keyword).matches();
                })
                .collect(Collectors.toSet());

        assertThat(textMateKeywords()).containsExactlyInAnyOrderElementsOf(grammarKeywords);
    }

    private static Set<String> textMateKeywords() throws IOException {
        var grammar = new Gson().fromJson(Files.readString(TEXT_MATE_GRAMMAR), TextMateGrammar.class);
        return grammar.repository().values().stream()
                .filter(entry -> {
                    return entry.patterns() != null;
                })
                .flatMap(entry -> {
                    return entry.patterns().stream();
                })
                .filter(pattern -> {
                    return pattern.match() != null;
                })
                .flatMap(pattern -> {
                    return WORD_ALTERNATION.matcher(pattern.match()).results();
                })
                .flatMap(alternation -> {
                    return Pattern.compile("\\|").splitAsStream(alternation.group(1));
                })
                .map(alternative -> {
                    return alternative.replace("\\s+", " ");
                })
                .filter(alternative -> {
                    return WORD_KEYWORD.matcher(alternative).matches();
                })
                .collect(Collectors.toSet());
    }
}

/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.ide.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import hu.bme.mit.semantifyr.oxsts.lang.ide.MarkedText;
import hu.bme.mit.semantifyr.oxsts.lang.ide.OxstsLanguageServerTest;
import org.eclipse.lsp4j.DocumentHighlight;
import org.eclipse.lsp4j.DocumentHighlightKind;
import org.eclipse.lsp4j.DocumentHighlightParams;
import org.junit.jupiter.api.Test;

class DocumentHighlightTest extends OxstsLanguageServerTest {

    @Test
    void assignedAndHavocedVariablesAreWriteOccurrences() throws Exception {
        var model = MarkedText.of("""
                package example::counter

                class Counter {
                    var <|>count: int := 0

                    tran increment() {
                        self.<|>count := <|>count + 1
                        havoc(<|>count)
                    }
                }
                """);
        var document = openModel(model.text());

        var highlights = languageServer
                .documentHighlight(
                        new DocumentHighlightParams(document, model.positions().getFirst()))
                .get();

        var positions = model.positions();
        assertThat(highlights)
                .extracting(
                        highlight -> {
                            return highlight.getRange().getStart();
                        },
                        DocumentHighlight::getKind)
                .containsExactly(
                        tuple(positions.get(0), DocumentHighlightKind.Write),
                        tuple(positions.get(1), DocumentHighlightKind.Write),
                        tuple(positions.get(2), DocumentHighlightKind.Read),
                        tuple(positions.get(3), DocumentHighlightKind.Write));
    }
}

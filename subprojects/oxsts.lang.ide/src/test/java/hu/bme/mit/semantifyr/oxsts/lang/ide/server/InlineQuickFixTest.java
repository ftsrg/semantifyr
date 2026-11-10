/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.ide.server;

import static hu.bme.mit.semantifyr.oxsts.lang.ide.InlineModels.TRAFFIC_LIGHT;
import static org.assertj.core.api.Assertions.assertThat;

import hu.bme.mit.semantifyr.oxsts.lang.ide.MarkedText;
import hu.bme.mit.semantifyr.oxsts.lang.ide.OxstsLanguageServerTest;
import hu.bme.mit.semantifyr.oxsts.lang.validation.OxstsValidator;
import java.util.Comparator;
import java.util.List;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionContext;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.junit.jupiter.api.Test;

class InlineQuickFixTest extends OxstsLanguageServerTest {

    private static final String COUNTER = """
            package example::counter

            class Counter {
                var count: int := 0

                redefine tran {
                    count := %s
                }
            }
            """;

    @Test
    void makeInlineTurnsAnIfWithACompileTimeGuardIntoAnInlineIf() throws Exception {
        var fixed = applyQuickFix(TRAFFIC_LIGHT + """

                class Factory {
                    contains first: TrafficLight[1]
                    contains second: TrafficLight[1]

                    redefine tran {
                        <|>if (first == second) {
                            inline first.setColor(LightColor::Red)
                        } else {
                            inline second.setColor(LightColor::Green)
                        }
                    }
                }
                """, OxstsValidator.COULD_BE_INLINE, "Make inline");

        assertThat(fixed).isEqualTo(TRAFFIC_LIGHT + """

                class Factory {
                    contains first: TrafficLight[1]
                    contains second: TrafficLight[1]

                    redefine tran {
                        inline if (first == second) {
                            inline first.setColor(LightColor::Red)
                        } else {
                            inline second.setColor(LightColor::Green)
                        }
                    }
                }
                """);
    }

    @Test
    void makeInlineTurnsAForOverAFeatureIntoAnInlineFor() throws Exception {
        var fixed = applyQuickFix(TRAFFIC_LIGHT + """

                class Factory {
                    contains lights: TrafficLight[2]

                    redefine tran {
                        <|>for (light in lights) {
                            inline light.setColor(LightColor::Red)
                        }
                    }
                }
                """, OxstsValidator.COULD_BE_INLINE, "Make inline");

        assertThat(fixed).isEqualTo(TRAFFIC_LIGHT + """

                class Factory {
                    contains lights: TrafficLight[2]

                    redefine tran {
                        inline for (light in lights) {
                            inline light.setColor(LightColor::Red)
                        }
                    }
                }
                """);
    }

    @Test
    void inlineAsConstantReplacesAnArithmeticConstantByItsValue() throws Exception {
        var fixed = applyQuickFix(
                COUNTER.formatted("<|>2 * 3 + 1"), OxstsValidator.CONSTANT_EXPRESSION, "Inline as constant");

        assertThat(fixed).isEqualTo(COUNTER.formatted("7"));
    }

    @Test
    void inlineAsConstantWritesANegativeValueAsANegatedLiteral() throws Exception {
        var fixed =
                applyQuickFix(COUNTER.formatted("<|>1 - 4"), OxstsValidator.CONSTANT_EXPRESSION, "Inline as constant");

        assertThat(fixed).isEqualTo(COUNTER.formatted("-3"));
    }

    private String applyQuickFix(String markedSource, String issueCode, String title) throws Exception {
        var model = MarkedText.of(markedSource);
        var document = openModel(model.text());
        var diagnostics = getDiagnostics().get(document.getUri()).stream()
                .filter(diagnostic -> {
                    return diagnostic.getRange().getStart().equals(model.position())
                            && issueCode.equals(diagnostic.getCode().getLeft());
                })
                .toList();

        assertThat(diagnostics).singleElement().satisfies(diagnostic -> {
            assertThat(diagnostic.getSeverity()).isEqualTo(DiagnosticSeverity.Hint);
        });
        var cursor = new Range(model.position(), model.position());
        var codeActions =
                languageServer
                        .codeAction(new CodeActionParams(document, cursor, new CodeActionContext(diagnostics)))
                        .get()
                        .stream()
                        .map(Either::getRight)
                        .toList();

        assertThat(codeActions).extracting(CodeAction::getTitle).containsExactly(title);
        var edits = codeActions.getFirst().getEdit().getChanges().get(document.getUri());
        return applyEdits(model.text(), edits);
    }

    private static String applyEdits(String text, List<TextEdit> edits) {
        var result = new StringBuilder(text);
        var lastFirst = edits.stream()
                .sorted(Comparator.comparing(TextEdit::getRange, (first, second) -> {
                    return compare(second.getStart(), first.getStart());
                }))
                .toList();
        for (var edit : lastFirst) {
            var start = offsetOf(text, edit.getRange().getStart());
            var end = offsetOf(text, edit.getRange().getEnd());
            result.replace(start, end, edit.getNewText());
        }
        return result.toString();
    }

    private static int compare(Position first, Position second) {
        return Comparator.comparingInt(Position::getLine)
                .thenComparingInt(Position::getCharacter)
                .compare(first, second);
    }

    private static int offsetOf(String text, Position position) {
        var offset = 0;
        for (int line = 0; line < position.getLine(); line++) {
            offset = text.indexOf('\n', offset) + 1;
        }
        return offset + position.getCharacter();
    }
}

/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.ide.server;

import static hu.bme.mit.semantifyr.oxsts.lang.ide.server.QuickFixModels.SHAPES;
import static hu.bme.mit.semantifyr.oxsts.lang.ide.server.QuickFixModels.TRAFFIC_LIGHT;
import static org.assertj.core.api.Assertions.assertThat;

import hu.bme.mit.semantifyr.oxsts.lang.ide.MarkedText;
import hu.bme.mit.semantifyr.oxsts.lang.ide.OxstsLanguageServerTest;
import java.util.Comparator;
import java.util.List;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionContext;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.junit.jupiter.api.Test;

class QuickFixTest extends OxstsLanguageServerTest {

    @Test
    void addRedefineToATransitionThatShadowsAnInheritedOne() throws Exception {
        var fixed = applyQuickFix(TRAFFIC_LIGHT + """

                class Special : TrafficLight {
                    tran <|>setColor(color: LightColor) {
                    }
                }
                """, "Add redefine");

        assertThat(fixed).isEqualTo(TRAFFIC_LIGHT + """

                class Special : TrafficLight {
                    redefine tran setColor(color: LightColor) {
                    }
                }
                """);
    }

    @Test
    void removeRedefineWithNothingToRedefine() throws Exception {
        var fixed = applyQuickFix(TRAFFIC_LIGHT + """

                class Special : TrafficLight {
                    redefine tran <|>nothingHere() {
                    }
                }
                """, "Remove redefine");

        assertThat(fixed).isEqualTo(TRAFFIC_LIGHT + """

                class Special : TrafficLight {
                    tran nothingHere() {
                    }
                }
                """);
    }

    @Test
    void changeARedefineToTheNearInheritedName() throws Exception {
        var fixed = applyQuickFix(TRAFFIC_LIGHT + """

                class Special : TrafficLight {
                    redefine tran <|>setColr(color: LightColor) {
                    }
                }
                """, "Change to setColor");

        assertThat(withoutWhitespace(fixed)).isEqualTo(withoutWhitespace(TRAFFIC_LIGHT + """

                class Special : TrafficLight {
                    redefine tran setColor(color: LightColor) {
                    }
                }
                """));
    }

    @Test
    void changeAnUnresolvedReferenceToTheNearMatchInScope() throws Exception {
        var fixed = applyQuickFix(TRAFFIC_LIGHT + """

                class Garage {
                    contains light: <|>TraficLight
                }
                """, "Change to TrafficLight");

        assertThat(fixed).isEqualTo(TRAFFIC_LIGHT + """

                class Garage {
                    contains light: TrafficLight
                }
                """);
    }

    @Test
    void castAnArgumentToTheNarrowerParameterType() throws Exception {
        var fixed = applyQuickFix("""
                package example::holders

                class Base {
                }

                class Derived : Base {
                }

                class Holder {
                    contains base: Base

                    prop needsDerived(value: Derived): bool {
                        return true
                    }

                    prop {
                        return needsDerived(<|>base)
                    }
                }
                """, "Cast to Derived");

        assertThat(fixed).isEqualTo("""
                package example::holders

                class Base {
                }

                class Derived : Base {
                }

                class Holder {
                    contains base: Base

                    prop needsDerived(value: Derived): bool {
                        return true
                    }

                    prop {
                        return needsDerived(base as Derived)
                    }
                }
                """);
    }

    @Test
    void redefineTheAbstractMembersWithStubs() throws Exception {
        var fixed = applyQuickFix(SHAPES + """

                class <|>Square : Shape {
                }
                """, "Redefine draw");

        assertThat(withoutWhitespace(fixed)).isEqualTo(withoutWhitespace(SHAPES + """

                class Square : Shape {
                    redefine tran draw() {
                    }
                }
                """));
    }

    @Test
    void makeAClassWithAbstractMembersAbstract() throws Exception {
        var fixed = applyQuickFix(SHAPES + """

                class <|>Square : Shape {
                }
                """, "Make Square abstract");

        assertThat(fixed).isEqualTo(SHAPES + """

                abstract class Square : Shape {
                }
                """);
    }

    @Test
    void initializeAVariableWithTheDefaultOfItsType() throws Exception {
        var fixed = applyQuickFix(TRAFFIC_LIGHT + """

                class Counter {
                    var <|>count: int
                }
                """, "Initialize with 0");

        assertThat(withoutWhitespace(fixed)).isEqualTo(withoutWhitespace(TRAFFIC_LIGHT + """

                class Counter {
                    var count: int := 0
                }
                """));
    }

    @Test
    void addTheMissingArgumentsAsNamedPlaceholders() throws Exception {
        var fixed = applyQuickFix(TRAFFIC_LIGHT + """

                class Garage {
                    contains light: TrafficLight

                    redefine tran {
                        inline light.setColor(<|>)
                    }
                }
                """, "Add missing arguments");

        assertThat(fixed).isEqualTo(TRAFFIC_LIGHT + """

                class Garage {
                    contains light: TrafficLight

                    redefine tran {
                        inline light.setColor(color = LightColor::Red)
                    }
                }
                """);
    }

    private String applyQuickFix(String markedSource, String title) throws Exception {
        var model = MarkedText.of(markedSource);
        var document = openModel(model.text());
        var diagnostics = getDiagnostics().get(document.getUri()).stream()
                .filter(diagnostic -> contains(diagnostic.getRange(), model.position()))
                .toList();
        var cursor = new Range(model.position(), model.position());
        var codeActions =
                languageServer
                        .codeAction(new CodeActionParams(document, cursor, new CodeActionContext(diagnostics)))
                        .get()
                        .stream()
                        .map(Either::getRight)
                        .toList();

        assertThat(codeActions).extracting(CodeAction::getTitle).contains(title);
        var codeAction = codeActions.stream()
                .filter(action -> action.getTitle().equals(title))
                .findFirst()
                .orElseThrow();
        return applyEdits(model.text(), codeAction.getEdit().getChanges().get(document.getUri()));
    }

    // The change serializer's whitespace is the formatter's defect, so these fixes are checked by their tokens.
    private static String withoutWhitespace(String text) {
        return text.replaceAll("\\s+", "");
    }

    private static boolean contains(Range range, Position position) {
        return compare(range.getStart(), position) <= 0 && compare(position, range.getEnd()) <= 0;
    }

    private static int compare(Position first, Position second) {
        return Comparator.comparingInt(Position::getLine)
                .thenComparingInt(Position::getCharacter)
                .compare(first, second);
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

    private static int offsetOf(String text, Position position) {
        var offset = 0;
        for (int line = 0; line < position.getLine(); line++) {
            offset = text.indexOf('\n', offset) + 1;
        }
        return offset + position.getCharacter();
    }
}

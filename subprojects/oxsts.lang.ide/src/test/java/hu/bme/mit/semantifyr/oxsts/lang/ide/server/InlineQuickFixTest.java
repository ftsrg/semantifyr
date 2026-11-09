/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.ide.server;

import static hu.bme.mit.semantifyr.oxsts.lang.ide.server.InlineHintModels.COUNTER;
import static hu.bme.mit.semantifyr.oxsts.lang.ide.server.InlineHintModels.WORKER;
import static org.assertj.core.api.Assertions.assertThat;

import hu.bme.mit.semantifyr.oxsts.lang.ide.MarkedText;
import hu.bme.mit.semantifyr.oxsts.lang.ide.OxstsLanguageServerTest;
import hu.bme.mit.semantifyr.oxsts.lang.validation.OxstsValidator;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.AssignmentOperation;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ClassDeclaration;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlineIfOperation;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlineSeqFor;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.LiteralInteger;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Operation;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.OxstsModelPackage;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.TransitionDeclaration;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionContext;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.xtext.nodemodel.util.NodeModelUtils;
import org.eclipse.xtext.resource.XtextResourceSet;
import org.junit.jupiter.api.Test;

class InlineQuickFixTest extends OxstsLanguageServerTest {

    @Test
    void makeInlineTurnsAnIfWithACompileTimeGuardIntoAnInlineIf() throws Exception {
        var fixed = applyQuickFix(WORKER + """

                class Factory {
                    contains first: Worker[1]
                    contains second: Worker[1]

                    redefine tran {
                        <|>if (first == second) {
                            inline first.step()
                        } else {
                            inline second.step()
                        }
                    }
                }
                """, OxstsValidator.COULD_BE_INLINE, "Make inline");

        assertThat(firstStepOfLastClass(fixed)).isInstanceOfSatisfying(InlineIfOperation.class, inlineIfOperation -> {
            assertThat(tokenText(inlineIfOperation.getGuard())).isEqualTo("first == second");
            assertThat(tokenText(inlineIfOperation.getBody())).isEqualTo("{ inline first.step() }");
            assertThat(tokenText(inlineIfOperation.getElse())).isEqualTo("{ inline second.step() }");
        });
    }

    @Test
    void makeInlineTurnsAForOverAFeatureIntoAnInlineFor() throws Exception {
        var fixed = applyQuickFix(WORKER + """

                class Factory {
                    contains workers: Worker[2]

                    redefine tran {
                        <|>for (worker in workers) {
                            inline worker.step()
                        }
                    }
                }
                """, OxstsValidator.COULD_BE_INLINE, "Make inline");

        assertThat(firstStepOfLastClass(fixed)).isInstanceOfSatisfying(InlineSeqFor.class, inlineSeqFor -> {
            assertThat(inlineSeqFor.getLoopVariable().getName()).isEqualTo("worker");
            assertThat(tokenText(inlineSeqFor.getRangeExpression())).isEqualTo("workers");
            assertThat(tokenText(inlineSeqFor.getBody())).isEqualTo("{ inline worker.step() }");
        });
    }

    @Test
    void inlineAsConstantReplacesAnArithmeticConstantByItsValue() throws Exception {
        var fixed = applyQuickFix(
                COUNTER.formatted("<|>2 * 3 + 1"), OxstsValidator.CONSTANT_EXPRESSION, "Inline as constant");

        assertThat(firstStepOfLastClass(fixed)).isInstanceOfSatisfying(AssignmentOperation.class, assignment -> {
            assertThat(assignment.getExpression()).isInstanceOfSatisfying(LiteralInteger.class, literal -> {
                assertThat(literal.getValue()).isEqualTo(7);
            });
        });
    }

    @Test
    void inlineAsConstantWritesANegativeValueAsANegatedLiteral() throws Exception {
        var fixed =
                applyQuickFix(COUNTER.formatted("<|>1 - 4"), OxstsValidator.CONSTANT_EXPRESSION, "Inline as constant");

        assertThat(firstStepOfLastClass(fixed)).isInstanceOfSatisfying(AssignmentOperation.class, assignment -> {
            assertThat(tokenText(assignment.getExpression())).isEqualTo("-3");
        });
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

    private Operation firstStepOfLastClass(String text) throws Exception {
        var uri = URI.createURI("fixed.oxsts");
        var resourceSet =
                resourceServerProviderRegistry.getResourceServiceProvider(uri).get(XtextResourceSet.class);
        var resource = resourceSet.createResource(uri);
        resource.load(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)), Map.of());
        assertThat(resource.getErrors()).isEmpty();
        var oxstsPackage = (OxstsModelPackage) resource.getContents().getFirst();
        var lastClass = (ClassDeclaration) oxstsPackage.getDeclarations().getLast();
        var transition = (TransitionDeclaration) lastClass.getMembers().getLast();
        return transition.getBranches().getFirst().getSteps().getFirst();
    }

    private static String tokenText(EObject element) {
        return NodeModelUtils.getTokenText(NodeModelUtils.findActualNodeFor(element));
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

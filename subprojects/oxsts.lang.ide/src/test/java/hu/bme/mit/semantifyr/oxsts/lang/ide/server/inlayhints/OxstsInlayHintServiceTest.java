/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.ide.server.inlayhints;

import static org.assertj.core.api.Assertions.assertThat;

import hu.bme.mit.semantifyr.oxsts.lang.ide.InlineModels;
import hu.bme.mit.semantifyr.oxsts.lang.ide.MarkedText;
import hu.bme.mit.semantifyr.oxsts.lang.ide.OxstsLanguageServerTest;
import java.util.List;
import org.eclipse.lsp4j.InlayHint;
import org.eclipse.lsp4j.InlayHintKind;
import org.eclipse.lsp4j.InlayHintLabelPart;
import org.eclipse.lsp4j.InlayHintParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.junit.jupiter.api.Test;

class OxstsInlayHintServiceTest extends OxstsLanguageServerTest {

    @Test
    void positionalArgumentsOfCallsAreNamedByTheirParameters() throws Exception {
        var model = withGarage("""
                class Garage {
                    contains lamp: Lamp

                    redefine tran {
                        inline lamp.set(<|>1, <|>2)
                    }

                    prop {
                        return lamp.isAt(<|>3)
                    }
                }
                """);

        var hints = hintsOfKind(model, InlayHintKind.Parameter);

        assertThat(hints).extracting(InlayHint::getPosition).containsExactlyElementsOf(model.positions());
        assertThat(hints).extracting(this::labelOf).containsExactly("level:", "step:", "level:");
        assertThat(hints).allSatisfy(hint -> {
            var location = hint.getLabel().getRight().getFirst().getLocation();
            assertThat(textAt(model.text(), location.getRange()))
                    .isEqualTo(labelOf(hint).replace(":", ""));
        });
    }

    @Test
    void mixedArgumentsNameOnlyThePositionalOnes() throws Exception {
        var model = withGarage("""
                @Brightness(<|>3, label = "dim")
                class Garage {
                    contains lamp: Lamp

                    redefine tran {
                        inline lamp.set(<|>1, step = 2)
                    }
                }
                """);

        var hints = hintsOfKind(model, InlayHintKind.Parameter);

        assertThat(hints).extracting(InlayHint::getPosition).containsExactlyElementsOf(model.positions());
        assertThat(hints).extracting(this::labelOf).containsExactly("level:", "level:");
    }

    @Test
    void namedArgumentsGetNoParameterHints() throws Exception {
        var model = withGarage("""
                class Garage {
                    contains lamp: Lamp

                    redefine tran {
                        inline lamp.set(level = 1, step = 2)
                    }
                }
                """);

        assertThat(hintsOfKind(model, InlayHintKind.Parameter)).isEmpty();
    }

    @Test
    void argumentReferencingANameEqualToTheParameterGetsNoHint() throws Exception {
        var model = withGarage("""
                class Garage {
                    contains lamp: Lamp[1]

                    tran dim(level: int) {
                        inline lamp.set(level, <|>1)
                    }
                }
                """);

        var hints = hintsOfKind(model, InlayHintKind.Parameter);

        assertThat(hints).singleElement().satisfies(hint -> {
            assertThat(hint.getPosition()).isEqualTo(model.position());
            assertThat(labelOf(hint)).isEqualTo("step:");
        });
    }

    @Test
    void loopVariableShowsTheElementTypeOfItsRange() throws Exception {
        var model = withGarage("""
                class Garage {
                    contains lamp: Lamp[1]

                    redefine tran {
                        inline for (index<|> in 0..2) {
                            inline lamp.set(level = index, step = 1)
                        }
                    }
                }
                """);

        var hints = hintsOfKind(model, InlayHintKind.Type);

        assertThat(hints).singleElement().satisfies(hint -> {
            assertThat(hint.getPosition()).isEqualTo(model.position());
            assertThat(hint.getLabel().getRight())
                    .extracting(InlayHintLabelPart::getValue)
                    .containsExactly(": ", "int");
            assertThat(hint.getLabel().getRight().getLast().getLocation()).isNotNull();
        });
    }

    @Test
    void featureWithoutMultiplicityShowsTheDefault() throws Exception {
        var model = withGarage("""
                class Garage {
                    contains lamp: Lamp<|>
                    contains spares: Lamp[0..*]
                }
                """);

        var hints = hintsOfKind(model, InlayHintKind.Type);

        assertThat(hints).singleElement().satisfies(hint -> {
            assertThat(hint.getPosition()).isEqualTo(model.position());
            assertThat(hint.getLabel().getLeft()).isEqualTo("[1]");
        });
    }

    @Test
    void bindingToAComputedConstantShowsItsValue() throws Exception {
        var model = withGarage("""
                class Garage {
                    refers base: int[1] = 4
                    refers limit: int[1] = 2 * 3<|>
                }
                """);

        var hints = hintsOfKind(model, null);

        assertThat(hints).singleElement().satisfies(hint -> {
            assertThat(hint.getPosition()).isEqualTo(model.position());
            assertThat(hint.getLabel().getLeft()).isEqualTo("= 6");
        });
    }

    private static MarkedText withGarage(String garage) {
        return MarkedText.of(InlineModels.LAMP_LIBRARY + "\n" + garage);
    }

    private List<InlayHint> hintsOfKind(MarkedText model, InlayHintKind kind) throws Exception {
        var document = openModel(model.text());
        var wholeDocument =
                new Range(new Position(0, 0), new Position(model.text().split("\n").length, 0));
        return languageServer.inlayHint(new InlayHintParams(document, wholeDocument)).get().stream()
                .filter(hint -> hint.getKind() == kind)
                .toList();
    }

    private String labelOf(InlayHint hint) {
        return hint.getLabel().getRight().getFirst().getValue();
    }

    private String textAt(String text, Range range) {
        var line = text.lines().toList().get(range.getStart().getLine());
        return line.substring(range.getStart().getCharacter(), range.getEnd().getCharacter());
    }
}

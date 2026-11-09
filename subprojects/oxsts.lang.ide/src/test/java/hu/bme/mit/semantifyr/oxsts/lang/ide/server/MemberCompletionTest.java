/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.ide.server;

import static org.assertj.core.api.Assertions.assertThat;

import hu.bme.mit.semantifyr.oxsts.lang.ide.MarkedText;
import hu.bme.mit.semantifyr.oxsts.lang.ide.OxstsLanguageServerTest;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.CompletionParams;
import org.junit.jupiter.api.Test;

class MemberCompletionTest extends OxstsLanguageServerTest {

    @Test
    void completionAfterADotInAnInlineCallOffersTheTransitionsOfTheFeatureType() throws Exception {
        var model = MarkedText.of("""
                package example::trafficlight::basics

                enum LightColor {
                    Red,
                    Yellow,
                    Green
                }

                class TrafficLight {
                    var color: LightColor := LightColor::Red

                    tran assumeColor(color: LightColor) {
                        assume (self.color == color)
                    }

                    tran setColor(color: LightColor) {
                        self.color := color
                    }

                    redefine tran {
                        choice {
                            inline assumeColor(LightColor::Red)
                            inline setColor(LightColor::Green)
                        } or {
                            inline assumeColor(LightColor::Green)
                            inline setColor(LightColor::Yellow)
                        } or {
                            inline assumeColor(LightColor::Yellow)
                            inline setColor(LightColor::Red)
                        }
                    }

                    prop isColor(color: LightColor): bool {
                        return self.color == color
                    }
                }

                @VerificationCase
                @Tag("expect-pass")
                class GreenColorIsReachable {
                    contains light: TrafficLight[1]

                    redefine tran {
                        inline light.<|>main()
                    }

                    prop {
                        return EF (light.color == LightColor::Green)
                    }
                }
                """);
        var document = openModel(model.text());

        var completion = languageServer
                .completion(new CompletionParams(document, model.position()))
                .get();

        assertThat(completion.getRight().getItems())
                .filteredOn(item -> item.getKind() == CompletionItemKind.Method)
                .extracting(CompletionItem::getLabel)
                .containsExactlyInAnyOrder("assumeColor", "init", "main", "setColor");
    }
}

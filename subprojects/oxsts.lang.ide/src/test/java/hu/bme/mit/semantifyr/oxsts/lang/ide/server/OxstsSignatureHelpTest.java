/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.ide.server;

import static org.assertj.core.api.Assertions.assertThat;

import hu.bme.mit.semantifyr.oxsts.lang.ide.MarkedText;
import hu.bme.mit.semantifyr.oxsts.lang.ide.OxstsLanguageServerTest;
import org.eclipse.lsp4j.ParameterInformation;
import org.eclipse.lsp4j.SignatureHelp;
import org.eclipse.lsp4j.SignatureHelpParams;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OxstsSignatureHelpTest extends OxstsLanguageServerTest {

    @BeforeEach
    void addLightsLibrary() {
        addLibraryFile(TrafficLightFixture.LIBRARY_FILE_NAME, TrafficLightFixture.LIBRARY);
    }

    private SignatureHelp signatureHelp(String members) throws Exception {
        var model = MarkedText.of(TrafficLightFixture.crossing(members));
        var document = openModel(model.text());
        return languageServer
                .signatureHelp(new SignatureHelpParams(document, model.position()))
                .get();
    }

    private static String parameterLabel(String signatureLabel, ParameterInformation parameter) {
        var range = parameter.getLabel().getRight();
        return signatureLabel.substring(range.getFirst(), range.getSecond());
    }

    @Test
    void signatureHelpOnPositionalArgumentsHighlightsTheParameterAtTheCursor() throws Exception {
        var signatureHelp = signatureHelp("""
                tran blinkRed() {
                    inline light.blink(LightColor::Red, <|>2)
                }
                """);

        assertThat(signatureHelp.getSignatures()).singleElement().satisfies(signature -> {
            assertThat(signature.getLabel()).isEqualTo("blink(color: LightColor, times: int)");
            assertThat(signature.getParameters())
                    .extracting(parameter -> {
                        return parameterLabel(signature.getLabel(), parameter);
                    })
                    .containsExactly("color: LightColor", "times: int");
        });
        assertThat(signatureHelp.getActiveParameter()).isEqualTo(1);
    }

    @Test
    void signatureHelpOnANamedArgumentHighlightsTheNamedParameter() throws Exception {
        var signatureHelp = signatureHelp("""
                tran blinkRed() {
                    inline light.blink(times = 2, color = <|>LightColor::Red)
                }
                """);

        assertThat(signatureHelp.getSignatures()).singleElement().satisfies(signature -> {
            assertThat(signature.getLabel()).isEqualTo("blink(color: LightColor, times: int)");
        });
        assertThat(signatureHelp.getActiveParameter()).isEqualTo(0);
    }
}

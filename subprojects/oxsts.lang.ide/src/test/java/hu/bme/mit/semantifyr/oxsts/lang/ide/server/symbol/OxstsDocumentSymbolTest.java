/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.ide.server.symbol;

import static org.assertj.core.api.Assertions.assertThat;

import hu.bme.mit.semantifyr.oxsts.lang.ide.InlineModels;
import hu.bme.mit.semantifyr.oxsts.lang.ide.OxstsLanguageServerTest;
import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.DocumentSymbolCapabilities;
import org.eclipse.lsp4j.DocumentSymbolParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.TextDocumentClientCapabilities;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.junit.jupiter.api.Test;

class OxstsDocumentSymbolTest extends OxstsLanguageServerTest {

    @Override
    protected InitializeResult initialize() {
        return initialize(params -> {
            var documentSymbol = new DocumentSymbolCapabilities();
            documentSymbol.setHierarchicalDocumentSymbolSupport(true);
            var textDocument = new TextDocumentClientCapabilities();
            textDocument.setDocumentSymbol(documentSymbol);
            params.getCapabilities().setTextDocument(textDocument);
        });
    }

    @Test
    void outlineOfTheTrafficLightsHasOneKindPerDeclarationSimpleNamesAndSignatures() throws Exception {
        var document = openModel(InlineModels.trafficLightWith("""
                @VerificationCase
                class GreenIsReachable {
                    contains light: TrafficLight

                    redefine prop {
                        return EF (light.color == LightColor::Green)
                    }
                }
                """));

        var symbols = languageServer
                .documentSymbol(new DocumentSymbolParams(document))
                .get();

        var outline = new StringBuilder();
        for (var symbol : symbols) {
            render(symbol.getRight(), "", outline);
        }
        assertThat(symbols).allMatch(Either::isRight);
        assertThat(outline.toString()).isEqualTo("""
                Package example::lights
                    Enum LightColor
                        EnumMember Red
                        EnumMember Yellow
                        EnumMember Green
                    Class TrafficLight
                        Variable color LightColor
                        Method setColor (color: LightColor)
                        Method blink (color: LightColor, times: int)
                        Method main ()
                        Function isColor (color: LightColor): bool
                    Class SpecialLight : TrafficLight
                        Method setColor (color: LightColor)
                    Class DimmedLight : TrafficLight
                        Method main ()
                    Class Intersection
                        Property light contains TrafficLight[1]
                        Property special contains SpecialLight[1]
                        Property dimmed contains DimmedLight[1]
                        Method cycle ()
                    Class GreenIsReachable
                        Property light contains TrafficLight
                        Function prop
                """);
    }

    private static void render(DocumentSymbol symbol, String indentation, StringBuilder outline) {
        outline.append(indentation).append(symbol.getKind()).append(' ').append(symbol.getName());
        if (!symbol.getDetail().isEmpty()) {
            outline.append(' ').append(symbol.getDetail());
        }
        outline.append('\n');
        for (var child : symbol.getChildren()) {
            render(child, indentation + "    ", outline);
        }
    }
}

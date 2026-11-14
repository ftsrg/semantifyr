/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.ide.server.symbol;

import static org.assertj.core.api.Assertions.assertThat;

import hu.bme.mit.semantifyr.oxsts.lang.ide.OxstsLanguageServerTest;
import hu.bme.mit.semantifyr.oxsts.lang.ide.TestModels;
import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.DocumentSymbolCapabilities;
import org.eclipse.lsp4j.DocumentSymbolParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.TextDocumentClientCapabilities;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.junit.jupiter.api.Test;

class OxstsDocumentSymbolTest extends OxstsLanguageServerTest {

    // VS Code and Monaco ask for the outline as a tree.
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
    void outlineOfTheBasicsTutorialHasOneKindPerDeclarationSimpleNamesAndSignatures() throws Exception {
        var document = openModel(TestModels.basics());

        var symbols = languageServer
                .documentSymbol(new DocumentSymbolParams(document))
                .get();

        var outline = new StringBuilder();
        for (var symbol : symbols) {
            render(symbol.getRight(), "", outline);
        }
        assertThat(symbols).allMatch(Either::isRight);
        assertThat(outline.toString()).isEqualTo("""
                Package example::trafficlight::basics
                    Enum LightColor
                        EnumMember Red
                        EnumMember Yellow
                        EnumMember Green
                    Class TrafficLight
                        Variable color LightColor
                        Method main ()
                        Function isColor (color: LightColor): bool
                    Class Intersection
                        Property north contains TrafficLight
                        Property east contains TrafficLight
                        Method main ()
                    Class NeverBothGreen
                        Property intersection contains Intersection
                        Method main ()
                        Function prop
                    Class EastCanTurnGreen
                        Property intersection contains Intersection
                        Method main ()
                        Function prop
                    Class NorthGreenOnlyWhileEastRed
                        Property intersection contains Intersection
                        Method main ()
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

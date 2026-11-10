/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.ide.server.symbol;

import static org.assertj.core.api.Assertions.assertThat;

import hu.bme.mit.semantifyr.oxsts.lang.ide.MarkedText;
import hu.bme.mit.semantifyr.oxsts.lang.ide.OxstsLanguageServerTest;
import hu.bme.mit.semantifyr.oxsts.lang.ide.TestModels;
import java.util.List;
import org.eclipse.lsp4j.SymbolKind;
import org.eclipse.lsp4j.WorkspaceSymbol;
import org.eclipse.lsp4j.WorkspaceSymbolParams;
import org.junit.jupiter.api.Test;

class OxstsWorkspaceSymbolTest extends OxstsLanguageServerTest {

    @Test
    void workspaceSymbolsFindAMemberOfALibraryClass() throws Exception {
        var library = MarkedText.of("""
                package example::library

                class Lamp {
                    var on: bool := false

                    tran <|>switchOn() {
                        on := true
                    }
                }
                """);
        var libraryUri = addLibraryFile("Lamps.oxsts", library.text());
        openModel(TestModels.basics());

        var symbols = symbols("switchOn");

        assertThat(symbols).singleElement().satisfies(symbol -> {
            assertThat(symbol.getKind()).isEqualTo(SymbolKind.Method);
            assertThat(symbol.getContainerName()).isEqualTo("example::library::Lamp");
            assertThat(symbol.getLocation().getLeft().getUri()).isEqualTo(libraryUri);
            assertThat(symbol.getLocation().getLeft().getRange().getStart()).isEqualTo(library.position());
        });
    }

    @Test
    void workspaceSymbolsFindTheBuiltinLibraryOutsideTheWorkspace() throws Exception {
        openModel(TestModels.basics());

        var symbols = symbols("Anything");

        assertThat(symbols)
                .filteredOn(symbol -> symbol.getName().equals("Anything"))
                .singleElement()
                .satisfies(symbol -> {
                    assertThat(symbol.getKind()).isEqualTo(SymbolKind.Class);
                    assertThat(symbol.getContainerName()).isEqualTo("semantifyr");
                    assertThat(symbol.getLocation().getLeft().getUri()).endsWith("/semantifyr.oxsts");
                });
    }

    private List<? extends WorkspaceSymbol> symbols(String query) throws Exception {
        return languageServer.symbol(new WorkspaceSymbolParams(query)).get().getRight();
    }
}

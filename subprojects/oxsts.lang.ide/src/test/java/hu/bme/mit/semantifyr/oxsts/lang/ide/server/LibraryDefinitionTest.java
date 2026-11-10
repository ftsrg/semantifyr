/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.ide.server;

import static org.assertj.core.api.Assertions.assertThat;

import hu.bme.mit.semantifyr.oxsts.lang.ide.MarkedText;
import hu.bme.mit.semantifyr.oxsts.lang.ide.OxstsLanguageServerTest;
import org.eclipse.lsp4j.DefinitionParams;
import org.junit.jupiter.api.Test;

class LibraryDefinitionTest extends OxstsLanguageServerTest {

    @Test
    void definitionOfALibraryClassGoesToItsDeclarationInTheLibraryFile() throws Exception {
        var library = MarkedText.of("""
                package example::library

                class <|>Lamp {
                    var on: bool := false
                }
                """);
        var libraryUri = addLibraryFile("Lamps.oxsts", library.text());
        var model = MarkedText.of("""
                package example::garage

                import example::library

                class Garage {
                    contains lamp: La<|>mp
                }
                """);
        var document = openModel(model.text());

        var definition = languageServer
                .definition(new DefinitionParams(document, model.position()))
                .get();

        assertThat(definition.getLeft()).singleElement().satisfies(location -> {
            assertThat(location.getUri()).isEqualTo(libraryUri);
            assertThat(location.getRange().getStart()).isEqualTo(library.position());
        });
    }
}

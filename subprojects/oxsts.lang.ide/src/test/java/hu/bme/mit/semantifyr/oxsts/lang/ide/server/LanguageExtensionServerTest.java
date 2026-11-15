/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.ide.server;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Guice;
import hu.bme.mit.semantifyr.oxsts.lang.OxstsRuntimeModule;
import hu.bme.mit.semantifyr.oxsts.lang.ide.MarkedText;
import hu.bme.mit.semantifyr.oxsts.lang.ide.OxstsLanguageServerTest;
import java.util.ArrayList;
import org.eclipse.lsp4j.DefinitionParams;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.xtext.resource.FileExtensionProvider;
import org.junit.jupiter.api.Test;

class LanguageExtensionServerTest extends OxstsLanguageServerTest {

    private final FileExtensionProvider fileExtensionProvider =
            Guice.createInjector(new OxstsRuntimeModule()).getInstance(FileExtensionProvider.class);

    @Test
    void aFileOfEveryExtensionIsIndexedAndServed() throws Exception {
        var extensions = new ArrayList<>(fileExtensionProvider.getFileExtensions());
        var libraryUris = new ArrayList<String>();
        var libraries = new ArrayList<MarkedText>();
        var models = new ArrayList<MarkedText>();
        var modelUris = new ArrayList<String>();
        for (var index = 0; index < extensions.size(); index++) {
            var library = MarkedText.of("""
                    package example::library%d

                    class <|>Lamp%d
                    """.formatted(index, index));
            libraries.add(library);
            libraryUris.add(addLibraryFile("Lamps%d.%s".formatted(index, extensions.get(index)), library.text()));
            var model = MarkedText.of("""
                    package example::garage%d

                    import example::library%d

                    class Garage {
                        contains lamp: La<|>mp%d
                    }
                    """.formatted(index, index, index));
            models.add(model);
            modelUris.add(writeFile("Garage%d.%s".formatted(index, extensions.get(index)), model.text()));
        }
        openModel("package example::snippet");
        for (var index = 0; index < extensions.size(); index++) {
            open(modelUris.get(index), models.get(index).text());

            var definition = languageServer
                    .definition(new DefinitionParams(
                            new TextDocumentIdentifier(modelUris.get(index)),
                            models.get(index).position()))
                    .get();

            var expectedUri = libraryUris.get(index);
            var expectedPosition = libraries.get(index).position();
            assertThat(definition.getLeft())
                    .as(extensions.get(index))
                    .singleElement()
                    .satisfies(location -> {
                        assertThat(location.getUri()).isEqualTo(expectedUri);
                        assertThat(location.getRange().getStart()).isEqualTo(expectedPosition);
                    });
        }
    }
}

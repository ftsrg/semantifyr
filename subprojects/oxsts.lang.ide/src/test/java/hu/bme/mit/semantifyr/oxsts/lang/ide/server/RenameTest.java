/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.ide.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import hu.bme.mit.semantifyr.oxsts.lang.ide.MarkedText;
import hu.bme.mit.semantifyr.oxsts.lang.ide.OxstsLanguageServerTest;
import java.util.List;
import java.util.concurrent.ExecutionException;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.RenameParams;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException;
import org.junit.jupiter.api.Test;

class RenameTest extends OxstsLanguageServerTest {

    @Test
    void renameOfARedefinedTransitionRenamesItsRedefinitionsAndEditsOnlyNames() throws Exception {
        var model = MarkedText.of(RedefinitionModels.TRAFFIC_LIGHTS);
        var document = openModel(model.text());

        var edit = rename(document, model.positions().getFirst(), "switchColor");

        assertThat(edit.getChanges()).containsOnlyKeys(document.getUri());
        assertThat(edit.getChanges().get(document.getUri()))
                .containsExactlyInAnyOrderElementsOf(nameEdits(model.positions(), "setColor", "switchColor"));
    }

    @Test
    void renameOfARedefinitionRenamesTheRedefinedTransition() throws Exception {
        var model = MarkedText.of(RedefinitionModels.TRAFFIC_LIGHTS);
        var document = openModel(model.text());

        var edit = rename(document, model.positions().get(2), "switchColor");

        assertThat(edit.getChanges().get(document.getUri()))
                .containsExactlyInAnyOrderElementsOf(nameEdits(model.positions(), "setColor", "switchColor"));
    }

    @Test
    void renameOfARedefinitionInTheModelRenamesTheRedefinedTransitionInALibrary() throws Exception {
        var library = MarkedText.of("""
                package example::library

                class Lamp {
                    var on: bool := false

                    tran <|>toggle() {
                        self.on := true
                    }
                }
                """);
        var libraryUri = addLibraryFile("Lamps.oxsts", library.text());
        var model = MarkedText.of("""
                package example::garage

                import example::library

                class GarageLamp : Lamp {
                    redefine tran <|>toggle() {
                        self.on := false
                    }
                }

                class Garage {
                    contains lamp: GarageLamp[1]

                    redefine tran {
                        inline lamp.<|>toggle()
                    }
                }
                """);
        var document = openModel(model.text());

        var edit = rename(document, model.positions().getFirst(), "flip");

        assertThat(edit.getChanges()).containsOnlyKeys(document.getUri(), libraryUri);
        assertThat(edit.getChanges().get(document.getUri()))
                .containsExactlyInAnyOrderElementsOf(nameEdits(model.positions(), "toggle", "flip"));
        assertThat(edit.getChanges().get(libraryUri))
                .containsExactlyInAnyOrderElementsOf(nameEdits(library.positions(), "toggle", "flip"));
    }

    @Test
    void renameToANameTheClassAlreadyDeclaresIsRefused() {
        var model = MarkedText.of(RedefinitionModels.TRAFFIC_LIGHTS);
        var document = openModel(model.text());

        assertThatThrownBy(() -> rename(document, model.positions().getFirst(), "color"))
                .cause()
                .isInstanceOf(ResponseErrorException.class)
                .hasMessage("example::trafficlight::TrafficLight already declares color");
    }

    @Test
    void renameThatAnEarlierImportWouldCaptureIsRefused() {
        addLibraryFile("A.oxsts", """
                package example::a

                class Widget {
                }
                """);
        addLibraryFile("B.oxsts", """
                package example::b

                class Gadget {
                }
                """);
        var model = MarkedText.of("""
                package example::main

                import example::b
                import example::a

                class Holder {
                    contains widget: <|>Widget
                }
                """);
        var document = openModel(model.text());

        assertThatThrownBy(() -> rename(document, model.position(), "Gadget"))
                .cause()
                .isInstanceOf(ResponseErrorException.class)
                .hasMessage("Gadget in snippet.oxsts already refers to example::b::Gadget");
    }

    private WorkspaceEdit rename(TextDocumentIdentifier document, Position position, String newName)
            throws InterruptedException, ExecutionException {
        return languageServer
                .rename(new RenameParams(document, position, newName))
                .get();
    }

    private static List<TextEdit> nameEdits(List<Position> starts, String oldName, String newName) {
        return starts.stream()
                .map(start -> {
                    var end = new Position(start.getLine(), start.getCharacter() + oldName.length());
                    return new TextEdit(new Range(start, end), newName);
                })
                .toList();
    }
}

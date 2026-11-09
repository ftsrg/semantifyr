/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.ide.server;

import static hu.bme.mit.semantifyr.oxsts.lang.ide.server.TypeHierarchyModels.GARAGE;
import static hu.bme.mit.semantifyr.oxsts.lang.ide.server.TypeHierarchyModels.LAMPS_LIBRARY;
import static org.assertj.core.api.Assertions.assertThat;

import hu.bme.mit.semantifyr.oxsts.lang.ide.OxstsLanguageServerTest;
import java.util.List;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.SymbolKind;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TypeHierarchyItem;
import org.eclipse.lsp4j.TypeHierarchyPrepareParams;
import org.eclipse.lsp4j.TypeHierarchySubtypesParams;
import org.eclipse.lsp4j.TypeHierarchySupertypesParams;
import org.junit.jupiter.api.Test;

class TypeHierarchyTest extends OxstsLanguageServerTest {

    private String libraryUri;

    private TextDocumentIdentifier garage;

    @Test
    void supertypesOfAClassClimbThroughTheLibraryToAnything() throws Exception {
        openGarage();

        var dimmableLamp = prepare(GARAGE.positions().get(0));
        assertThat(dimmableLamp.getName()).isEqualTo("DimmableLamp");
        assertThat(dimmableLamp.getKind()).isEqualTo(SymbolKind.Class);
        assertThat(dimmableLamp.getDetail()).isEqualTo("example::garage");
        assertThat(dimmableLamp.getUri()).isEqualTo(garage.getUri());
        assertThat(dimmableLamp.getSelectionRange().getStart())
                .isEqualTo(GARAGE.positions().get(0));

        var lamp = supertypes(dimmableLamp);
        assertThat(lamp).singleElement().satisfies(item -> {
            assertThat(item.getName()).isEqualTo("Lamp");
            assertThat(item.getUri()).isEqualTo(libraryUri);
            assertThat(item.getSelectionRange().getStart())
                    .isEqualTo(LAMPS_LIBRARY.positions().get(0));
        });

        var anything = supertypes(lamp.getFirst());
        assertThat(anything).singleElement().satisfies(item -> {
            assertThat(item.getName()).isEqualTo("Anything");
            assertThat(item.getDetail()).isEqualTo("semantifyr");
        });

        assertThat(supertypes(anything.getFirst())).isEmpty();
    }

    @Test
    void subtypesOfALibraryClassAreFoundInTheWorkspace() throws Exception {
        openGarage();

        var lamp = prepare(GARAGE.positions().get(1));
        assertThat(lamp.getName()).isEqualTo("Lamp");
        assertThat(lamp.getUri()).isEqualTo(libraryUri);

        var dimmableLamp = subtypes(lamp);
        assertThat(dimmableLamp).extracting(TypeHierarchyItem::getName).containsExactly("DimmableLamp");
        assertThat(subtypes(dimmableLamp.getFirst()))
                .extracting(TypeHierarchyItem::getName)
                .containsExactly("SmartLamp");
    }

    @Test
    void aRedefinedTransitionShowsItsRedefinitionChain() throws Exception {
        openGarage();

        var toggle = prepare(GARAGE.positions().get(2));
        assertThat(toggle.getName()).isEqualTo("toggle");
        assertThat(toggle.getKind()).isEqualTo(SymbolKind.Method);
        assertThat(toggle.getDetail()).isEqualTo("example::garage::DimmableLamp");

        assertThat(supertypes(toggle)).singleElement().satisfies(item -> {
            assertThat(item.getUri()).isEqualTo(libraryUri);
            assertThat(item.getSelectionRange().getStart())
                    .isEqualTo(LAMPS_LIBRARY.positions().get(1));
            assertThat(item.getDetail()).isEqualTo("example::library::Lamp");
        });
        assertThat(subtypes(toggle)).singleElement().satisfies(item -> {
            assertThat(item.getUri()).isEqualTo(garage.getUri());
            assertThat(item.getSelectionRange().getStart())
                    .isEqualTo(GARAGE.positions().get(4));
        });
    }

    private void openGarage() {
        libraryUri = addLibraryFile("Lamps.oxsts", LAMPS_LIBRARY.text());
        garage = openModel(GARAGE.text());
    }

    private TypeHierarchyItem prepare(Position position) throws Exception {
        var items = languageServer
                .prepareTypeHierarchy(new TypeHierarchyPrepareParams(garage, position))
                .get();
        assertThat(items).hasSize(1);
        return items.getFirst();
    }

    private List<TypeHierarchyItem> supertypes(TypeHierarchyItem item) throws Exception {
        return languageServer
                .typeHierarchySupertypes(new TypeHierarchySupertypesParams(item))
                .get();
    }

    private List<TypeHierarchyItem> subtypes(TypeHierarchyItem item) throws Exception {
        return languageServer
                .typeHierarchySubtypes(new TypeHierarchySubtypesParams(item))
                .get();
    }
}

/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.ide.server;

import static org.assertj.core.api.Assertions.assertThat;

import hu.bme.mit.semantifyr.oxsts.lang.ide.MarkedText;
import hu.bme.mit.semantifyr.oxsts.lang.ide.OxstsLanguageServerTest;
import org.eclipse.lsp4j.CallHierarchyIncomingCallsParams;
import org.eclipse.lsp4j.CallHierarchyItem;
import org.eclipse.lsp4j.CallHierarchyOutgoingCallsParams;
import org.eclipse.lsp4j.CallHierarchyPrepareParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SymbolKind;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.junit.jupiter.api.Test;

class CallHierarchyTest extends OxstsLanguageServerTest {

    private final MarkedText lamps = MarkedText.of(CallHierarchyModels.LAMPS);

    private final MarkedText garage = MarkedText.of(CallHierarchyModels.GARAGE);

    @Test
    void incomingCallsOfALibraryTransitionIncludeTheCallsThroughItsRedefiner() throws Exception {
        var lampsUri = addLibraryFile(CallHierarchyModels.LAMPS_FILE_NAME, lamps.text());
        var document = openModel(garage.text());

        var toggle = prepare(document, garage.positions().getFirst());

        assertThat(toggle.getName()).isEqualTo("toggle");
        assertThat(toggle.getUri()).isEqualTo(lampsUri);
        assertThat(toggle.getSelectionRange().getStart()).isEqualTo(lamps.position());

        var incoming = languageServer
                .callHierarchyIncomingCalls(new CallHierarchyIncomingCallsParams(toggle))
                .get();

        assertThat(incoming).hasSize(2);
        assertThat(incoming).anySatisfy(call -> {
            assertThat(call.getFrom().getName()).isEqualTo("reset");
            assertThat(call.getFrom().getUri()).isEqualTo(document.getUri());
            assertThat(call.getFromRanges())
                    .containsExactly(callRange(garage.positions().getFirst(), "toggle"));
        });
        assertThat(incoming).anySatisfy(call -> {
            assertThat(call.getFrom().getName()).isEqualTo("main");
            assertThat(call.getFrom().getDetail()).isEqualTo("example::garage::Garage");
            assertThat(call.getFromRanges())
                    .containsExactlyInAnyOrder(
                            callRange(garage.positions().get(2), "toggle"),
                            callRange(garage.positions().get(3), "toggle"));
        });
    }

    @Test
    void outgoingCallsOfACaseMainAreEveryCallInItsBody() throws Exception {
        var lampsUri = addLibraryFile(CallHierarchyModels.LAMPS_FILE_NAME, lamps.text());
        var document = openModel(garage.text());

        var main = prepare(document, garage.positions().get(1));

        assertThat(main.getName()).isEqualTo("main");

        var outgoing = languageServer
                .callHierarchyOutgoingCalls(new CallHierarchyOutgoingCallsParams(main))
                .get();

        assertThat(outgoing).hasSize(3);
        assertThat(outgoing).anySatisfy(call -> {
            assertThat(call.getTo().getUri()).isEqualTo(lampsUri);
            assertThat(call.getTo().getDetail()).isEqualTo("example::library::Lamp");
            assertThat(call.getTo().getKind()).isEqualTo(SymbolKind.Method);
            assertThat(call.getFromRanges())
                    .containsExactly(callRange(garage.positions().get(2), "toggle"));
        });
        assertThat(outgoing).anySatisfy(call -> {
            assertThat(call.getTo().getUri()).isEqualTo(document.getUri());
            assertThat(call.getTo().getDetail()).isEqualTo("example::garage::GarageLamp");
            assertThat(call.getFromRanges())
                    .containsExactly(callRange(garage.positions().get(3), "toggle"));
        });
        assertThat(outgoing).anySatisfy(call -> {
            assertThat(call.getTo().getName()).isEqualTo("reset");
            assertThat(call.getFromRanges())
                    .containsExactly(callRange(garage.positions().get(4), "reset"));
        });
    }

    private CallHierarchyItem prepare(TextDocumentIdentifier document, Position position) throws Exception {
        var items = languageServer
                .prepareCallHierarchy(new CallHierarchyPrepareParams(document, position))
                .get();
        assertThat(items).hasSize(1);
        return items.getFirst();
    }

    private static Range callRange(Position start, String name) {
        return new Range(start, new Position(start.getLine(), start.getCharacter() + name.length()));
    }
}

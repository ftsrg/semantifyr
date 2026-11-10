/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.ide.server.folding;

import static org.assertj.core.api.Assertions.assertThat;

import hu.bme.mit.semantifyr.oxsts.lang.ide.OxstsLanguageServerTest;
import hu.bme.mit.semantifyr.oxsts.lang.ide.TestModels;
import java.util.List;
import org.eclipse.lsp4j.FoldingRange;
import org.eclipse.lsp4j.FoldingRangeKind;
import org.eclipse.lsp4j.FoldingRangeRequestParams;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.junit.jupiter.api.Test;

class OxstsFoldingRangeTest extends OxstsLanguageServerTest {

    private record Lines(int start, int end) {}

    @Test
    void everyBlockFoldsOnceAndKeepsItsClosingBraceVisible() throws Exception {
        var document = openModel(TestModels.basics());

        var lines = foldingRanges(document).stream()
                .map(range -> new Lines(range.getStartLine(), range.getEndLine()))
                .toList();

        assertThat(lines).doesNotHaveDuplicates();
        assertThat(lines)
                .contains(
                        new Lines(14, 40),
                        new Lines(21, 22),
                        new Lines(25, 35),
                        new Lines(26, 34),
                        new Lines(26, 28),
                        new Lines(29, 31),
                        new Lines(32, 34));
    }

    @Test
    void importsFoldAsOneRange() throws Exception {
        addLibraryFile("Lamps.oxsts", """
                package example::lamps

                class Lamp {
                }
                """);
        addLibraryFile("Doors.oxsts", """
                package example::doors

                class Door {
                }
                """);
        var document = openModel("""
                package example::garage

                import example::lamps
                import example::doors

                class Garage {
                    contains lamp: Lamp
                    contains door: Door
                }
                """);

        assertThat(foldingRanges(document))
                .filteredOn(range -> FoldingRangeKind.Imports.equals(range.getKind()))
                .singleElement()
                .satisfies(range -> {
                    assertThat(range.getStartLine()).isEqualTo(2);
                    assertThat(range.getEndLine()).isEqualTo(3);
                });
    }

    private List<FoldingRange> foldingRanges(TextDocumentIdentifier document) throws Exception {
        return languageServer
                .foldingRange(new FoldingRangeRequestParams(document))
                .get();
    }
}

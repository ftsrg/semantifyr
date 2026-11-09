/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.ide.server.formatting;

import static org.assertj.core.api.Assertions.assertThat;

import hu.bme.mit.semantifyr.oxsts.lang.ide.MarkedText;
import hu.bme.mit.semantifyr.oxsts.lang.ide.OxstsLanguageServerTest;
import java.util.Comparator;
import java.util.List;
import org.eclipse.lsp4j.DocumentFormattingParams;
import org.eclipse.lsp4j.DocumentRangeFormattingParams;
import org.eclipse.lsp4j.FormattingOptions;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;
import org.junit.jupiter.api.Test;

class OxstsFormattingTest extends OxstsLanguageServerTest {

    private static final FormattingOptions OPTIONS = new FormattingOptions(4, true);

    @Test
    void formattingTheDocumentSpacesOperatorsDeclarationsAndKeywords() throws Exception {
        var model = MarkedText.of(FormattingModels.UNFORMATTED);
        var document = openModel(model.text());

        var edits = languageServer.formatting(new DocumentFormattingParams(document, OPTIONS)).get();

        assertThat(applyEdits(model.text(), edits)).isEqualTo(FormattingModels.FORMATTED);
    }

    @Test
    void rangeFormattingFormatsTheFirstLineOfTheRange() throws Exception {
        var model = MarkedText.of(FormattingModels.UNFORMATTED);
        var document = openModel(model.text());
        var range = new Range(model.positions().getFirst(), model.positions().getLast());

        var edits = languageServer.rangeFormatting(new DocumentRangeFormattingParams(document, OPTIONS, range)).get();

        assertThat(applyEdits(model.text(), edits)).isEqualTo(FormattingModels.TRANSITION_FORMATTED);
    }

    private static String applyEdits(String text, List<? extends TextEdit> edits) {
        var result = new StringBuilder(text);
        var lastFirst = edits.stream()
                .sorted(Comparator.comparing((TextEdit edit) -> offset(text, edit.getRange().getStart())).reversed())
                .toList();
        for (var edit : lastFirst) {
            result.replace(offset(text, edit.getRange().getStart()), offset(text, edit.getRange().getEnd()), edit.getNewText());
        }
        return result.toString();
    }

    private static int offset(String text, Position position) {
        var offset = 0;
        for (var line = 0; line < position.getLine(); line++) {
            offset = text.indexOf('\n', offset) + 1;
        }
        return offset + position.getCharacter();
    }
}

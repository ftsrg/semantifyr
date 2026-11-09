/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.lang.ide.server.formatting;

import java.util.List;
import org.eclipse.lsp4j.DocumentRangeFormattingParams;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.xtext.ide.server.Document;
import org.eclipse.xtext.ide.server.formatting.FormattingService;
import org.eclipse.xtext.resource.XtextResource;
import org.eclipse.xtext.util.CancelIndicator;

public class SemantifyrFormattingService extends FormattingService {

    // Xtext formats only the whitespace wholly inside the range, and the whitespace before the first line starts on the line above.
    @Override
    public List<? extends TextEdit> format(Document document, XtextResource resource, DocumentRangeFormattingParams params, CancelIndicator cancelIndicator) {
        var region = document.getTextRegion(params.getRange());
        var text = document.getContents();
        var start = region.getOffset();
        while (start > 0 && Character.isWhitespace(text.charAt(start - 1))) {
            start--;
        }
        return format(resource, document, start, region.getOffset() + region.getLength() - start, params.getOptions());
    }
}

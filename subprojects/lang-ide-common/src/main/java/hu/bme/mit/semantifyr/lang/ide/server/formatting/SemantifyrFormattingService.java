/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.lang.ide.server.formatting;

import java.util.List;
import org.eclipse.lsp4j.DocumentRangeFormattingParams;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.xtext.formatting2.FormatterPreferenceKeys;
import org.eclipse.xtext.formatting2.regionaccess.ITextReplacement;
import org.eclipse.xtext.ide.server.Document;
import org.eclipse.xtext.ide.server.formatting.FormattingService;
import org.eclipse.xtext.preferences.ITypedPreferenceValues;
import org.eclipse.xtext.preferences.MapBasedPreferenceValues;
import org.eclipse.xtext.resource.XtextResource;
import org.eclipse.xtext.util.CancelIndicator;
import org.eclipse.xtext.util.ITextRegion;

public class SemantifyrFormattingService extends FormattingService {

    // Xtext skips whitespace not wholly inside the range, such as the indentation of its first line.
    @Override
    public List<? extends TextEdit> format(
            Document document,
            XtextResource resource,
            DocumentRangeFormattingParams params,
            CancelIndicator cancelIndicator) {
        var region = document.getTextRegion(params.getRange());
        var text = document.getContents();
        var start = region.getOffset();
        while (start > 0 && Character.isWhitespace(text.charAt(start - 1))) {
            start--;
        }
        return format(resource, document, start, region.getOffset() + region.getLength() - start, params.getOptions());
    }

    // The base class takes the platform's line separator, which would put CRLF into LF documents on Windows.
    @Override
    protected List<ITextReplacement> format2(
            XtextResource resource, ITextRegion selection, ITypedPreferenceValues preferences) {
        var text = resource.getParseResult().getRootNode().getText();
        var values = (MapBasedPreferenceValues) preferences;
        values.put(FormatterPreferenceKeys.lineSeparator, text.contains("\r\n") ? "\r\n" : "\n");
        return super.format2(resource, selection, values);
    }
}

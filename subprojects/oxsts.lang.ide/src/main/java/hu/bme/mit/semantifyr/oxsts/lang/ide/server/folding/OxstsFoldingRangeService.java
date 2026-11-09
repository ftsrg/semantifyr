/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.ide.server.folding;

import com.google.inject.Singleton;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.OxstsModelPackage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.eclipse.lsp4j.FoldingRange;
import org.eclipse.lsp4j.FoldingRangeKind;
import org.eclipse.xtext.ide.server.Document;
import org.eclipse.xtext.ide.server.folding.FoldingRangeService;
import org.eclipse.xtext.nodemodel.util.NodeModelUtils;
import org.eclipse.xtext.resource.XtextResource;
import org.eclipse.xtext.util.CancelIndicator;

@Singleton
public class OxstsFoldingRangeService extends FoldingRangeService {

    @Override
    public List<FoldingRange> createFoldingRanges(
            Document document, XtextResource resource, CancelIndicator cancelIndicator) {
        var ranges = new ArrayList<>(super.createFoldingRanges(document, resource, cancelIndicator));
        if (resource != null && document != null) {
            addImportsRange(document, resource, ranges);
        }
        return ranges.stream()
                .distinct()
                .sorted(Comparator.comparing(FoldingRange::getStartLine))
                .toList();
    }

    // The closing brace stays visible, so a block ending on the line where its sibling starts (`} or {`) folds too.
    @Override
    protected FoldingRange toFoldingRange(Document document, org.eclipse.xtext.ide.editor.folding.FoldingRange range) {
        var result = super.toFoldingRange(document, range);
        var endOffset = range.getOffset() + range.getLength();
        if (range.getKind() == null && endOffset > 0 && document.getContents().charAt(endOffset - 1) == '}') {
            result.setEndLine(result.getEndLine() - 1);
        }
        return result;
    }

    private void addImportsRange(Document document, XtextResource resource, List<FoldingRange> ranges) {
        if (resource.getContents().isEmpty()
                || !(resource.getContents().getFirst() instanceof OxstsModelPackage modelPackage)) {
            return;
        }
        var imports = modelPackage.getImports();
        if (imports.size() < 2) {
            return;
        }
        var firstNode = NodeModelUtils.findActualNodeFor(imports.getFirst());
        var lastNode = NodeModelUtils.findActualNodeFor(imports.getLast());
        if (firstNode == null || lastNode == null) {
            return;
        }
        var startLine = document.getPosition(firstNode.getOffset()).getLine();
        var endLine = document.getPosition(lastNode.getEndOffset()).getLine();
        if (startLine < endLine) {
            var importsRange = new FoldingRange(startLine, endLine);
            importsRange.setKind(FoldingRangeKind.Imports);
            ranges.add(importsRange);
        }
    }
}

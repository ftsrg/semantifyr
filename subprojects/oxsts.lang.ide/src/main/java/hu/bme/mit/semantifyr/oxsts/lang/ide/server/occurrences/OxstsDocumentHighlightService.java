/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.ide.server.occurrences;

import hu.bme.mit.semantifyr.oxsts.model.oxsts.AssignmentOperation;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.ElementReference;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.Expression;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.HavocOperation;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.IndexingSuffixExpression;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.NavigationSuffixExpression;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.OxstsPackage;
import java.util.HashSet;
import java.util.List;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.lsp4j.DocumentHighlight;
import org.eclipse.lsp4j.DocumentHighlightKind;
import org.eclipse.lsp4j.Range;
import org.eclipse.xtext.ide.server.Document;
import org.eclipse.xtext.ide.server.occurrences.DefaultDocumentHighlightService;
import org.eclipse.xtext.resource.XtextResource;
import org.eclipse.xtext.util.ITextRegion;

public class OxstsDocumentHighlightService extends DefaultDocumentHighlightService {

    @Override
    public List<DocumentHighlight> getDocumentHighlights(XtextResource resource, int offset) {
        var highlights = super.getDocumentHighlights(resource, offset);
        if (highlights.isEmpty()) {
            return highlights;
        }

        var document = new Document(-1, resource.getParseResult().getRootNode().getText());
        var writtenRanges = new HashSet<Range>();
        EcoreUtil.getAllProperContents(resource, false).forEachRemaining(element -> {
            var target = switch (element) {
                case AssignmentOperation assignment -> assignment.getReference();
                case HavocOperation havoc -> havoc.getReference();
                default -> null;
            };
            if (target != null) {
                var region = getWrittenRegion(target);
                if (!isNullOrEmpty(region)) {
                    var highlight = textRegionTransformer.apply(document, region, DocumentHighlightKind.Write);
                    writtenRanges.add(highlight.getRange());
                }
            }
        });

        return highlights.stream()
                .map(highlight -> {
                    if (highlight.getKind() == DocumentHighlightKind.Read
                            && writtenRanges.contains(highlight.getRange())) {
                        return new DocumentHighlight(highlight.getRange(), DocumentHighlightKind.Write);
                    }
                    return highlight;
                })
                .toList();
    }

    private ITextRegion getWrittenRegion(Expression target) {
        var written = target;
        while (written instanceof IndexingSuffixExpression indexing) {
            written = indexing.getPrimary();
        }
        return switch (written) {
            case NavigationSuffixExpression navigation ->
                locationInFileProvider.getSignificantTextRegion(
                        navigation, OxstsPackage.Literals.NAVIGATION_SUFFIX_EXPRESSION__MEMBER, -1);
            case ElementReference reference ->
                locationInFileProvider.getSignificantTextRegion(
                        reference, OxstsPackage.Literals.ELEMENT_REFERENCE__ELEMENT, -1);
            default -> null;
        };
    }
}

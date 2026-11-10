/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.ide.server.contentassist;

import hu.bme.mit.semantifyr.oxsts.lang.ide.editor.contentassist.OxstsIdeCrossrefProposalProvider;
import java.util.Comparator;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.InsertTextFormat;
import org.eclipse.lsp4j.Position;
import org.eclipse.xtext.ide.editor.contentassist.ContentAssistEntry;
import org.eclipse.xtext.ide.server.Document;
import org.eclipse.xtext.ide.server.contentassist.ContentAssistService;
import org.eclipse.xtext.util.TextRegion;

public class OxstsContentAssistService extends ContentAssistService {

    @Override
    protected CompletionItem toCompletionItem(
            ContentAssistEntry entry, int caretOffset, Position caretPosition, Document document) {
        var item = super.toCompletionItem(entry, caretOffset, caretPosition, document);
        if (entry.getEditPositions().isEmpty() && entry.getEscapePosition() == null) {
            return item;
        }
        var prefix = entry.getPrefix() == null ? "" : entry.getPrefix();
        item.getTextEdit().getLeft().setNewText(toSnippet(entry, caretOffset - prefix.length()));
        item.setInsertTextFormat(InsertTextFormat.Snippet);
        return item;
    }

    // Xtext keeps placeholders as document offsets beside the plain proposal; LSP carries them in snippet syntax.
    private String toSnippet(ContentAssistEntry entry, int start) {
        var proposal = entry.getProposal();
        var snippet = new StringBuilder();
        var position = 0;
        var placeholder = 1;
        var editPositions = entry.getEditPositions().stream()
                .sorted(Comparator.comparingInt(TextRegion::getOffset))
                .toList();
        for (var editPosition : editPositions) {
            var offset = editPosition.getOffset() - start;
            var end = offset + editPosition.getLength();
            snippet.append(escape(proposal.substring(position, offset)));
            snippet.append("${").append(placeholder).append(":");
            snippet.append(escapePlaceholder(proposal.substring(offset, end))).append("}");
            position = end;
            placeholder++;
        }
        if (entry.getEscapePosition() == null) {
            return snippet.append(escape(proposal.substring(position))).toString();
        }
        var escapeOffset = entry.getEscapePosition() - start;
        snippet.append(escape(proposal.substring(position, escapeOffset)));
        snippet.append("$0");
        return snippet.append(escape(proposal.substring(escapeOffset))).toString();
    }

    private String escape(String text) {
        return text.replace("\\", "\\\\").replace("$", "\\$");
    }

    private String escapePlaceholder(String text) {
        return escape(text).replace("}", "\\}");
    }

    @Override
    protected CompletionItemKind translateKind(ContentAssistEntry entry) {
        if (OxstsIdeCrossrefProposalProvider.KIND_ENUM_MEMBER.equals(entry.getKind())) {
            return CompletionItemKind.EnumMember;
        }
        if (OxstsIdeCrossrefProposalProvider.KIND_STRUCT.equals(entry.getKind())) {
            return CompletionItemKind.Struct;
        }
        return super.translateKind(entry);
    }
}

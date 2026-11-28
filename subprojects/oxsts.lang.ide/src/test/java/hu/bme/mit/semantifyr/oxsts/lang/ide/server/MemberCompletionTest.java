/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.ide.server;

import static hu.bme.mit.semantifyr.oxsts.lang.tests.utils.TestModels.testModel;
import static org.assertj.core.api.Assertions.assertThat;

import hu.bme.mit.semantifyr.oxsts.lang.ide.MarkedText;
import hu.bme.mit.semantifyr.oxsts.lang.ide.OxstsLanguageServerTest;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.CompletionParams;
import org.junit.jupiter.api.Test;

class MemberCompletionTest extends OxstsLanguageServerTest {

    @Test
    void completionAfterADotInAnInlineCallOffersTheTransitionsOfTheFeatureType() throws Exception {
        var model = MarkedText.of(MarkedText.markEvery(testModel(getClass(), "traffic_lights.semantifyr"), "main()"));
        var document = openModel(model.text());

        var completion = languageServer
                .completion(new CompletionParams(document, model.position()))
                .get();

        assertThat(completion.getRight().getItems())
                .filteredOn(item -> item.getKind() == CompletionItemKind.Method)
                .extracting(CompletionItem::getLabel)
                .containsExactlyInAnyOrder("assumeColor", "init", "main", "setColor");
    }
}

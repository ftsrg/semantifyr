/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.ide.server;

import static org.assertj.core.api.Assertions.assertThat;

import hu.bme.mit.semantifyr.oxsts.lang.ide.InlineModels;
import hu.bme.mit.semantifyr.oxsts.lang.ide.MarkedText;
import hu.bme.mit.semantifyr.oxsts.lang.ide.OxstsLanguageServerTest;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.ReferenceContext;
import org.eclipse.lsp4j.ReferenceParams;
import org.junit.jupiter.api.Test;

class ReferencesTest extends OxstsLanguageServerTest {

    @Test
    void referencesOnARedefinedTransitionIncludeItsRedefinitionsAndTheCallsThroughThem() throws Exception {
        var model = MarkedText.of(MarkedText.markEvery(InlineModels.TRAFFIC_LIGHT, "setColor"));
        var document = openModel(model.text());

        var references = languageServer
                .references(new ReferenceParams(document, model.positions().getFirst(), new ReferenceContext(true)))
                .get();

        assertThat(references)
                .allSatisfy(location -> {
                    assertThat(location.getUri()).isEqualTo(document.getUri());
                })
                .extracting(Location::getRange)
                .extracting(range -> {
                    return range.getStart();
                })
                .containsExactlyInAnyOrderElementsOf(model.positions());
    }

    @Test
    void referencesOnAnUnnamedTransitionStopBelowTheBuiltinAnything() throws Exception {
        var model = MarkedText.of(MarkedText.markEvery(InlineModels.TRAFFIC_LIGHT, "redefine tran {", "main()"));
        var document = openModel(model.text());

        var references = languageServer
                .references(new ReferenceParams(document, model.positions().get(2), new ReferenceContext(true)))
                .get();

        assertThat(references)
                .allSatisfy(location -> {
                    assertThat(location.getUri()).isEqualTo(document.getUri());
                })
                .extracting(Location::getRange)
                .extracting(range -> {
                    return range.getStart();
                })
                .containsExactlyInAnyOrderElementsOf(model.positions());
    }
}

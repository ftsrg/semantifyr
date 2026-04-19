/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.tests.utils;

import com.google.inject.Inject;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.xtext.testing.util.ParseHelper;

/**
 * Parses a text fixture as an {@link InlinedOxsts} model. The Oxsts grammar's
 * top-level {@code Model} rule accepts both {@code OxstsModelPackage} and
 * {@code InlinedOxsts}; this helper disambiguates to the inlined form for
 * optimization-pass tests.
 */
public class InlinedOxstsParseHelper {

    @Inject
    private ParseHelper<InlinedOxsts> parseHelper;

    public InlinedOxsts parse(String text) {
        try {
            var inlined = parseHelper.parse(text);
            EcoreUtil.resolveAll(inlined);
            return inlined;
        } catch (Exception e) {
            throw new AssertionError("Unexpected exception while parsing InlinedOxsts", e);
        }
    }

}

/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.tests.utils;

import com.google.inject.Inject;
import com.google.inject.Provider;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.InlinedOxsts;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.xtext.resource.XtextResourceSet;
import org.eclipse.xtext.testing.util.ParseHelper;

public class InlinedOxstsParseHelper {

    @Inject
    private ParseHelper<InlinedOxsts> parseHelper;

    @Inject
    private Provider<XtextResourceSet> resourceSetProvider;

    public InlinedOxsts parse(String text) {
        return parse(text, resourceSetProvider.get());
    }

    public InlinedOxsts parse(String text, ResourceSet resourceSet) {
        try {
            var inlined = parseHelper.parse(text, resourceSet);
            EcoreUtil.resolveAll(inlined);
            return inlined;
        } catch (Exception exception) {
            throw new AssertionError("Unexpected exception while parsing InlinedOxsts", exception);
        }
    }
}

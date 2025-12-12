/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.tests.utils;

import com.google.inject.Inject;
import hu.bme.mit.semantifyr.oxsts.model.oxsts.OxstsModelPackage;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.xtext.testing.util.ParseHelper;
import org.eclipse.xtext.util.CancelIndicator;
import org.eclipse.xtext.validation.CheckMode;
import org.eclipse.xtext.validation.IResourceValidator;

public class OxstsPackageParseHelper {

    @Inject
    private IResourceValidator resourceValidator;

    @Inject
    private ParseHelper<OxstsModelPackage> parseHelper;

    public OxstsPackageWrapper parse(String text) {
        try {
            var oxstsPackage = parseHelper.parse(text);
            EcoreUtil.resolveAll(oxstsPackage);
            return new OxstsPackageWrapper(oxstsPackage, null);
        } catch (Exception e) {
            throw new AssertionError("Unexpected exception while parsing Oxsts Package", e);
        }
    }

    public OxstsPackageWrapper parse(String text, CheckMode checkMode) {
        try {
            var oxstsPackage = parseHelper.parse(text);
            EcoreUtil.resolveAll(oxstsPackage);
            var issues = resourceValidator.validate(oxstsPackage.eResource(), checkMode, CancelIndicator.NullImpl);
            return new OxstsPackageWrapper(oxstsPackage, issues);
        } catch (Exception e) {
            throw new AssertionError("Unexpected exception while parsing Oxsts Package", e);
        }
    }

}

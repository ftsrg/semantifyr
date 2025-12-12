/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.tests.utils;

import hu.bme.mit.semantifyr.oxsts.model.oxsts.OxstsModelPackage;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.xtext.validation.Issue;

import java.util.List;

public record OxstsPackageWrapper(OxstsModelPackage oxstsModelPackage, List<Issue> issues) {

    public OxstsModelPackage getOxstsPackage() {
        return oxstsModelPackage;
    }

    public List<Resource.Diagnostic> getResourceErrors() {
        return oxstsModelPackage.eResource().getErrors();
    }

    public List<Issue> getIssues() {
        return issues;
    }

}

/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.library;

import hu.bme.mit.semantifyr.oxsts.lang.utils.OxstsUtils;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.ResourceSet;

public interface OxstsLibrary {
    String FILE_NAME_SUFFIX = "." + OxstsUtils.LIBRARY_EXTENSION;

    Iterable<URI> getImplicitImports();

    void loadLibrary(ResourceSet resourceSet);

}

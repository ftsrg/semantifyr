/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.library;

import hu.bme.mit.semantifyr.oxsts.lang.utils.OxstsUtils;
import org.eclipse.emf.common.util.URI;

import java.util.List;

public interface OxstsLibrary {
    String FILE_NAME_SUFFIX = "." + OxstsUtils.LIBRARY_EXTENSION;

    default Iterable<URI> getImplicitImports() {
        return List.of();
    }

    default Iterable<URI> getIncludedResourceUris() {
        return List.of();
    }

    default void prepareLoading() {
        // NO-OP
    }

}

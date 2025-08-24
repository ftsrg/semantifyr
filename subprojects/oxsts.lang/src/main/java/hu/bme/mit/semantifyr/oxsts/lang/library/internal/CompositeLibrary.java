/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.library.internal;

import com.google.common.collect.FluentIterable;
import hu.bme.mit.semantifyr.oxsts.lang.library.OxstsLibrary;
import org.eclipse.emf.common.util.URI;

import java.util.List;

public class CompositeLibrary implements OxstsLibrary {
    private final List<OxstsLibrary> libraries;

    public CompositeLibrary(List<OxstsLibrary> libraries) {
        this.libraries = libraries;
    }

    @Override
    public Iterable<URI> getImplicitImports() {
        return FluentIterable.from(libraries).transformAndConcat(OxstsLibrary::getImplicitImports);
    }

    @Override
    public Iterable<URI> getIncludedResourceUris() {
        return FluentIterable.from(libraries).transformAndConcat(OxstsLibrary::getIncludedResourceUris);
    }

    @Override
    public void prepareLoading() {
        for (var library : libraries) {
            library.prepareLoading();
        }
    }
}

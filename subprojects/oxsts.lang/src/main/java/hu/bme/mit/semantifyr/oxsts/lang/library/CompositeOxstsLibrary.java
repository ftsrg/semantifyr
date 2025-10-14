/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.library;

import com.google.common.collect.FluentIterable;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.ResourceSet;

public abstract class CompositeOxstsLibrary implements OxstsLibrary {

    protected abstract Iterable<OxstsLibrary> getLibraries();

    @Override
    public Iterable<URI> getImplicitImports() {
        return FluentIterable.from(getLibraries()).transformAndConcat(OxstsLibrary::getImplicitImports);
    }

    @Override
    public void loadLibrary(ResourceSet resourceSet) {
        for (var library : getLibraries()) {
            library.loadLibrary(resourceSet);
        }
    }

}

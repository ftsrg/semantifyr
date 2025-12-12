/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.library.builtin;

import com.google.inject.Inject;
import hu.bme.mit.semantifyr.oxsts.lang.library.OxstsLibrary;
import hu.bme.mit.semantifyr.oxsts.lang.library.OxstsLibraryProvider;

public class BuiltinOxstsLibraryProvider implements OxstsLibraryProvider {

    @Inject
    protected BuiltinLibrary builtinLibrary;

    @Override
    public OxstsLibrary getLibrary() {
        return builtinLibrary;
    }

}

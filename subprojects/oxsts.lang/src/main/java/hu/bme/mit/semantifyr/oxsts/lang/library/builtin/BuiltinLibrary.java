/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.library.builtin;

import com.google.inject.Singleton;
import hu.bme.mit.semantifyr.oxsts.lang.library.ClasspathBasedOxstsLibrary;
import java.nio.file.Path;
import java.util.List;
import org.eclipse.emf.common.util.URI;
import org.eclipse.xtext.naming.QualifiedName;

@Singleton
public class BuiltinLibrary extends ClasspathBasedOxstsLibrary {

    public static final QualifiedName BUILTIN_LIBRARY_NAME = QualifiedName.create("semantifyr");

    private static final String RESOURCE_PREFIX = "hu/bme/mit/semantifyr/oxsts/lang/library";

    private URI builtinLibraryUri;

    public BuiltinLibrary() {
        super(
                BuiltinLibrary.class.getClassLoader(),
                RESOURCE_PREFIX,
                Path.of(System.getProperty("user.home")).resolve(".semantifyr").resolve("builtin"));
    }

    public URI getBuiltinResourceUri() {
        if (builtinLibraryUri == null) {
            builtinLibraryUri = resolveUri(BUILTIN_LIBRARY_NAME);
        }

        return builtinLibraryUri;
    }

    @Override
    public Iterable<URI> getImplicitImports() {
        return List.of(getBuiltinResourceUri());
    }
}

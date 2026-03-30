/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.library.builtin;

import com.google.inject.Singleton;
import hu.bme.mit.semantifyr.oxsts.lang.library.ResourceBasedOxstsLibrary;
import org.eclipse.emf.common.util.URI;
import org.eclipse.xtext.naming.QualifiedName;

import java.nio.file.Path;
import java.util.List;

@Singleton
public class BuiltinLibrary extends ResourceBasedOxstsLibrary {

    public static final QualifiedName BUILTIN_LIBRARY_NAME = QualifiedName.create("semantifyr");

    private URI builtinLibraryUri;

    public BuiltinLibrary() {
        super(getHomePath().resolve(".semantifyr").resolve("builtin"));
    }

    public URI getBuiltinResourceUri() {
        if (builtinLibraryUri == null) {
            builtinLibraryUri = resolveUri(BUILTIN_LIBRARY_NAME);
        }

        return builtinLibraryUri;
    }

    @Override
    public Iterable<URI> getImplicitImports() {
        return List.of(
                getBuiltinResourceUri()
        );
    }

    protected static Path getHomePath() {
        return Path.of(System.getProperty("user.home"));
    }

    @Override
    protected void saveResources() {
        saveResource("hu/bme/mit/semantifyr/oxsts/lang/library/semantifyr.oxsts", Path.of("semantifyr.oxsts"));
    }

    @Override
    protected ClassLoader getClassLoader() {
        return BuiltinLibrary.class.getClassLoader();
    }

}

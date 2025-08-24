/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.library.builtin;

import com.google.inject.Singleton;
import hu.bme.mit.semantifyr.oxsts.lang.library.LibraryResolutionUtil;
import hu.bme.mit.semantifyr.oxsts.lang.library.PathLibrary;
import org.eclipse.emf.common.util.URI;
import org.eclipse.xtext.naming.QualifiedName;

import java.nio.file.Path;
import java.util.List;

@Singleton
public class BuiltinLibrary extends PathLibrary {

    private static final Path basePath = Path.of("/home/armin/work/ftsrg/semantifyr/subprojects/oxsts.lang/src/main/resources/hu/bme/mit/semantifyr/oxsts/lang/library/");

    public static final QualifiedName BUILTIN_LIBRARY_NAME = QualifiedName.create("semantifyr");
    public static final QualifiedName BUILTIN_TEST_LIBRARY_NAME = BUILTIN_LIBRARY_NAME.append("test");
    private URI builtinLibraryUri;
    private URI builtinTestLibraryUri;

    public BuiltinLibrary() {
        super(List.of(basePath));
    }

    protected URI resolveUri(QualifiedName name) {
        return URI.createFileURI(basePath.resolve(LibraryResolutionUtil.qualifiedNameToPath(name)).toString());
    }

    public URI getBuiltinResourceUri() {
        if (builtinLibraryUri == null) {
            builtinLibraryUri = resolveUri(BUILTIN_LIBRARY_NAME);
        }

        return builtinLibraryUri;
    }

    public URI getBuiltinTestResourceUri() {
        if (builtinTestLibraryUri == null) {
            builtinTestLibraryUri = resolveUri(BUILTIN_TEST_LIBRARY_NAME);
        }

        return builtinTestLibraryUri;
    }

    @Override
    public Iterable<URI> getImplicitImports() {
        return List.of(
                getBuiltinResourceUri()
        );
    }

}

/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.library.builtin;

import com.google.inject.Singleton;
import hu.bme.mit.semantifyr.oxsts.lang.library.LibraryResolutionUtil;
import hu.bme.mit.semantifyr.oxsts.lang.library.ResourceSourcePathLibrary;
import org.eclipse.emf.common.util.URI;
import org.eclipse.xtext.naming.QualifiedName;

import java.nio.file.Path;
import java.util.List;

@Singleton
public class BuiltinLibrary extends ResourceSourcePathLibrary {

    public static final QualifiedName BUILTIN_LIBRARY_NAME = QualifiedName.create("semantifyr");
    public static final QualifiedName BUILTIN_VERIFICATION_LIBRARY_NAME = BUILTIN_LIBRARY_NAME.append("verification");
    private URI builtinLibraryUri;
    private URI builtinVerificationLibraryUri;

    public BuiltinLibrary() {
        super(getHomePath().resolve(".semantifyr").resolve("builtin"));
    }

    protected URI resolveUri(QualifiedName name) {
        return URI.createFileURI(libraryPath.resolve(LibraryResolutionUtil.qualifiedNameToPath(name)).toString());
    }

    public URI getBuiltinResourceUri() {
        if (builtinLibraryUri == null) {
            builtinLibraryUri = resolveUri(BUILTIN_LIBRARY_NAME);
        }

        return builtinLibraryUri;
    }

    public URI getBuiltinVerificationResourceUri() {
        if (builtinVerificationLibraryUri == null) {
            builtinVerificationLibraryUri = resolveUri(BUILTIN_VERIFICATION_LIBRARY_NAME);
        }

        return builtinVerificationLibraryUri;
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
        saveResource("hu/bme/mit/semantifyr/oxsts/lang/library/semantifyr/verification.oxsts", Path.of("semantifyr/verification.oxsts"));
    }

    @Override
    protected ClassLoader getClassLoader() {
        return BuiltinLibrary.class.getClassLoader();
    }

}

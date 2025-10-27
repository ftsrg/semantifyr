/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.library;

import com.google.inject.Inject;
import hu.bme.mit.semantifyr.oxsts.lang.utils.ResourceUriProvider;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.xtext.naming.QualifiedName;

import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;

public abstract class PathBasedOxstsLibrary implements OxstsLibrary {

    @Inject
    protected ResourceUriProvider resourceUriProvider;

    protected final Path libraryPath;

    public PathBasedOxstsLibrary(Path libraryPath) {
        this.libraryPath = libraryPath;
    }

    protected URI resolveUri(QualifiedName name) {
        var path = LibraryResolutionUtil.qualifiedNameToPath(name);
        var absolutePath = libraryPath.resolve(path);
        return resourceUriProvider.createFileUri(absolutePath);
    }

    protected Iterable<URI> getIncludedResourceUris() {
        try (var fileStream = Files.walk(libraryPath, FileVisitOption.FOLLOW_LINKS)) {
            return fileStream.map(Path::toFile)
                    .filter(file -> file.getName().endsWith(FILE_NAME_SUFFIX))
                    .map(file -> resourceUriProvider.createFileUri(file.toPath()))
                    .toList();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void loadLibrary(ResourceSet resourceSet) {
        for (var uri : getIncludedResourceUris()) {
            resourceSet.getResource(uri, true);
        }
    }

}

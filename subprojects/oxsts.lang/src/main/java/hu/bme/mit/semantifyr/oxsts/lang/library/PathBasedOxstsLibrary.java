/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.library;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.ResourceSet;

import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;

public abstract class PathBasedOxstsLibrary implements OxstsLibrary {

    protected final Path libraryPath;

    public PathBasedOxstsLibrary(Path libraryPath) {
        this.libraryPath = libraryPath;
    }

    protected Iterable<URI> getIncludedResourceUris() {
        try (var fileStream = Files.walk(libraryPath, FileVisitOption.FOLLOW_LINKS)) {
            return fileStream.map(Path::toFile)
                    .filter(file -> file.getName().endsWith(FILE_NAME_SUFFIX))
                    .map(file -> URI.createFileURI(file.toString()))
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

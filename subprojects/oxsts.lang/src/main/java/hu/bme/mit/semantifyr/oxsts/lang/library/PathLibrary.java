/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.library;

import org.eclipse.emf.common.util.URI;

import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;

public abstract class PathLibrary implements OxstsLibrary {

    protected final Path libraryPath;

    public PathLibrary(Path libraryPath) {
        this.libraryPath = libraryPath;
    }

    @Override
    public Iterable<URI> getIncludedResourceUris() {
        try (var fileStream = Files.walk(libraryPath, FileVisitOption.FOLLOW_LINKS)) {
            return fileStream.map(Path::toFile)
                    .filter(file -> file.getName().endsWith(FILE_NAME_SUFFIX))
                    .map(file -> URI.createFileURI(file.toString()))
                    .toList();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}

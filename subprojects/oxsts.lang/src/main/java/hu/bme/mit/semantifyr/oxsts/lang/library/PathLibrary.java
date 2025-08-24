/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.library;

import com.google.common.collect.FluentIterable;
import org.eclipse.emf.common.util.URI;

import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public abstract class PathLibrary implements OxstsLibrary {

    protected List<Path> libraryPaths;

    public PathLibrary(List<Path> libraryPaths) {
        this.libraryPaths = libraryPaths;
    }

    @Override
    public Iterable<URI> getIncludedResourceUris() {
        return FluentIterable.from(libraryPaths).transformAndConcat(path -> {
            try (var fileStream = Files.walk(path, FileVisitOption.FOLLOW_LINKS)) {
                return fileStream.map(Path::toFile)
                        .filter(file -> file.getName().endsWith(FILE_NAME_SUFFIX))
                        .map(file -> URI.createFileURI(file.toString()))
                        .toList();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

}

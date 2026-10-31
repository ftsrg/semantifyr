/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.library;

import hu.bme.mit.semantifyr.utils.files.ClasspathBundle;
import java.nio.file.Path;
import java.util.List;
import org.eclipse.emf.common.util.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClasspathBasedOxstsLibrary extends PathBasedOxstsLibrary {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClasspathBasedOxstsLibrary.class);

    private final ClasspathBundle bundle;
    private final Path baseDirectory;
    private Path extractedLibraryPath;

    public ClasspathBasedOxstsLibrary(ClassLoader classLoader, String resourcePrefix, Path baseDirectory) {
        this.bundle =
                new ClasspathBundle(classLoader, stripSlashes(resourcePrefix), name -> name.endsWith(FILE_NAME_SUFFIX));
        this.baseDirectory = baseDirectory;
    }

    public synchronized Path getExtractedLibraryPath() {
        if (extractedLibraryPath == null) {
            extractedLibraryPath = bundle.extractTo(baseDirectory);
            LOGGER.debug("Classpath OXSTS library under '{}' resolved to '{}'", baseDirectory, extractedLibraryPath);
        }
        return extractedLibraryPath;
    }

    @Override
    protected Path getLibraryPath() {
        return getExtractedLibraryPath();
    }

    @Override
    public Iterable<URI> getImplicitImports() {
        return List.of();
    }

    private static String stripSlashes(String prefix) {
        return prefix.replaceAll("(?:^/+)|(?:/+$)", "");
    }
}

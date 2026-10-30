/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.library;

import java.io.IOException;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.eclipse.emf.common.util.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClasspathBasedOxstsLibrary extends ResourceBasedOxstsLibrary {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClasspathBasedOxstsLibrary.class);

    private final ClassLoader classLoader;
    private final String resourcePrefix;

    public ClasspathBasedOxstsLibrary(ClassLoader classLoader, String resourcePrefix, Path libraryPath) {
        super(libraryPath);
        this.classLoader = classLoader;
        this.resourcePrefix = stripSlashes(resourcePrefix);
    }

    public Path getExtractedLibraryPath() {
        return libraryPath;
    }

    @Override
    public Iterable<URI> getImplicitImports() {
        return List.of();
    }

    @Override
    protected ClassLoader getClassLoader() {
        return classLoader;
    }

    @Override
    protected void saveResources() {
        LOGGER.info("Extracting classpath OXSTS library '{}' to '{}'", resourcePrefix, libraryPath);
        int extractedCount = 0;
        try {
            var bases = classLoader.getResources(resourcePrefix);
            if (!bases.hasMoreElements()) {
                LOGGER.warn("No classpath resources found for prefix '{}'", resourcePrefix);
            }
            while (bases.hasMoreElements()) {
                var base = bases.nextElement();
                LOGGER.debug("Extracting from classpath base '{}'", base);
                switch (base.getProtocol()) {
                    case "file" -> extractedCount += extractFromDir(Path.of(base.toURI()));
                    case "jar" -> extractedCount += extractFromJar((JarURLConnection) base.openConnection());
                    default ->
                        throw new IllegalStateException(
                                "Unsupported resource URL protocol for classpath library: " + base);
                }
            }
        } catch (IOException | URISyntaxException e) {
            throw new RuntimeException("Failed to extract classpath library at '" + resourcePrefix + "'", e);
        }
        LOGGER.info(
                "Extracted {} OXSTS library file(s) from prefix '{}' to '{}'",
                extractedCount,
                resourcePrefix,
                libraryPath);
    }

    private int extractFromDir(Path baseDir) throws IOException {
        int count = 0;
        try (var walk = Files.walk(baseDir)) {
            for (var path : (Iterable<Path>) walk::iterator) {
                if (!Files.isRegularFile(path)) {
                    continue;
                }
                if (!path.getFileName().toString().endsWith(FILE_NAME_SUFFIX)) {
                    continue;
                }
                var relative = baseDir.relativize(path);
                var resourceKey = qualifiedResourceKey(relative);
                saveResource(resourceKey, relative);
                count++;
            }
        }
        return count;
    }

    private int extractFromJar(JarURLConnection connection) throws IOException {
        var normalizedPrefix = resourcePrefix.isEmpty() ? "" : resourcePrefix + "/";
        var jar = connection.getJarFile();
        var entries = jar.entries();
        int count = 0;
        while (entries.hasMoreElements()) {
            var entry = entries.nextElement();
            if (entry.isDirectory()) {
                continue;
            }
            var name = entry.getName();
            if (!name.startsWith(normalizedPrefix)) {
                continue;
            }
            if (!name.endsWith(FILE_NAME_SUFFIX)) {
                continue;
            }
            var relativeText = name.substring(normalizedPrefix.length());
            saveResource(name, Path.of(relativeText));
            count++;
        }
        return count;
    }

    private String qualifiedResourceKey(Path relative) {
        var relativeText = relative.toString().replace('\\', '/');
        return resourcePrefix.isEmpty() ? relativeText : resourcePrefix + "/" + relativeText;
    }

    private static String stripSlashes(String prefix) {
        return prefix.replaceAll("^/+|/+$", "");
    }
}

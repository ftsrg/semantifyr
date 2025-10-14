/*
 * SPDX-FileCopyrightText: 2025 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.library;

import org.eclipse.emf.ecore.resource.ResourceSet;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

public abstract class ResourceBasedOxstsLibrary extends PathBasedOxstsLibrary {

    public ResourceBasedOxstsLibrary(Path libraryPath) {
        super(libraryPath);
    }

    public void prepareLoading() {
        // TODO: should be smarter in the future, should use some kind of versioning
        try (var files = Files.walk(libraryPath)) {
            files.sorted(Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(File::delete);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        saveResources();
    }

    @Override
    public void loadLibrary(ResourceSet resourceSet) {
        prepareLoading();

        super.loadLibrary(resourceSet);
    }

    protected abstract void saveResources();
    protected abstract ClassLoader getClassLoader();

    protected void saveResource(String resourceKey, Path subPath) {
        try (var stream = getClassLoader().getResourceAsStream(resourceKey)) {
            if (stream == null) {
                throw new IllegalArgumentException("Resource does not exist!");
            }
            var filePath = libraryPath.resolve(subPath);
            filePath.toFile().getParentFile().mkdirs();
            Files.copy(stream, filePath);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}

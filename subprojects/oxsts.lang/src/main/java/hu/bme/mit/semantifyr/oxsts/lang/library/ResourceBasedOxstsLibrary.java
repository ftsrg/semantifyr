/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.library;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import org.eclipse.emf.ecore.resource.ResourceSet;

public abstract class ResourceBasedOxstsLibrary extends PathBasedOxstsLibrary {

    public ResourceBasedOxstsLibrary(Path libraryPath) {
        super(libraryPath);
    }

    public void prepareLoading() {
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
        byte[] desired;
        try (var stream = getClassLoader().getResourceAsStream(resourceKey)) {
            if (stream == null) {
                throw new IllegalArgumentException("Resource does not exist: " + resourceKey);
            }
            desired = stream.readAllBytes();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        var filePath = libraryPath.resolve(subPath);

        // If the file is already correct, skip the write entirely. This makes the common case of
        // repeated initialisation free and avoids any chance of fighting with a parallel writer.
        try {
            if (Files.exists(filePath) && Arrays.equals(Files.readAllBytes(filePath), desired)) {
                return;
            }
        } catch (IOException ignored) {
            // Fall through to the write; a transient read failure shouldn't block refreshing.
        }

        try {
            var parent = filePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.write(
                    filePath,
                    desired,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);
        } catch (IOException ignored) {
            // A concurrent writer may be mid-flight. Their copy of the file will be correct by the
            // time loading reads it, so swallowing here is safe and better than throwing.
        }
    }
}

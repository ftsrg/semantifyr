/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.frontend

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class FrontendLibraryExtractorTest {
    @Test
    fun `extractClasspath memoizes per classloader plus prefix`(@TempDir tempDir: Path) {
        val resourceDir = tempDir.resolve("root/lib-prefix")
        Files.createDirectories(resourceDir)
        Files.writeString(resourceDir.resolve("One.oxsts"), "package one\n")
        Files.writeString(resourceDir.resolve("Two.oxsts"), "package two\n")

        val isolatedLoader = java.net.URLClassLoader(
            arrayOf(tempDir.resolve("root").toUri().toURL()),
            null,
        )

        val first = FrontendLibraryExtractor.extractClasspath(isolatedLoader, "lib-prefix")
        val second = FrontendLibraryExtractor.extractClasspath(isolatedLoader, "lib-prefix")

        assertSame(first, second, "Repeated extraction should return the same list instance")
        assertEquals(first, second)
        assertFalse(first.isEmpty(), "Expected the seeded classpath entries to be extracted")
        val firstParent = first.first().parent
        val secondParent = second.first().parent
        assertEquals(firstParent, secondParent, "Cache hit should reuse the original temp directory")
    }
}

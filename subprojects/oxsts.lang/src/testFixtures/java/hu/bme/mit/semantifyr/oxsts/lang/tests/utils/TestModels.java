/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.tests.utils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

public final class TestModels {

    private TestModels() {}

    public static String testModel(Class<?> testClass, String fileName) {
        var path = testClass.getSimpleName() + "/" + fileName;
        try (var stream = testClass.getResourceAsStream(path)) {
            if (stream == null) {
                throw new IllegalArgumentException("No test model " + path + " next to " + testClass.getName());
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}

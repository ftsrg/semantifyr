/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.ide;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class OxstsTestModels {

    private static final Path TUTORIAL_DIRECTORY = Path.of("../../oxsts-test-models/tutorial");

    private OxstsTestModels() {}

    public static String basics() {
        return readTutorial("basics.oxsts");
    }

    private static String readTutorial(String fileName) {
        try {
            return Files.readString(TUTORIAL_DIRECTORY.resolve(fileName));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}

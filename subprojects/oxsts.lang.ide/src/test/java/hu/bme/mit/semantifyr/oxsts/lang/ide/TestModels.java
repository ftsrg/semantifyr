/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.oxsts.lang.ide;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;

public final class TestModels {

    private static final Path BASICS_TUTORIAL = Path.of("../../oxsts-test-models/tutorial/basics.oxsts");

    private static final Path GAMMA_LIBRARY = Path.of("../frontends/gamma/models/libraries/default");

    private TestModels() {}

    public static String basicsWith(String addition) throws IOException {
        return Files.readString(BASICS_TUTORIAL) + "\n" + addition;
    }

    public static Map<String, String> gammaLibrary() throws IOException {
        var files = new TreeMap<String, String>();
        try (var paths = Files.list(GAMMA_LIBRARY)) {
            for (var path : paths.toList()) {
                files.put(path.getFileName().toString(), Files.readString(path));
            }
        }
        return files;
    }
}

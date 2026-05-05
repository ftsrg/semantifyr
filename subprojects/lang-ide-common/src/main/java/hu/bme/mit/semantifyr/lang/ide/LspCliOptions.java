/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.lang.ide;

import com.google.inject.AbstractModule;
import com.google.inject.Module;
import java.util.ArrayList;

public record LspCliOptions(CodeLensMode codeLensMode) {

    public enum CodeLensMode {
        FULL,
        NONE;

        public static CodeLensMode parse(String raw) {
            return switch (raw.toLowerCase().trim()) {
                case "full" -> FULL;
                case "none" -> NONE;
                default ->
                    throw new IllegalArgumentException("Unknown --code-lens-mode '" + raw + "' (expected: full, none)");
            };
        }
    }

    public static ParsedArgs parse(String[] argv) {
        var codeLensMode = CodeLensMode.FULL;
        var remaining = new ArrayList<String>(argv.length);
        for (int i = 0; i < argv.length; i++) {
            var arg = argv[i];
            if (arg.equals("--code-lens-mode") && i + 1 < argv.length) {
                codeLensMode = CodeLensMode.parse(argv[++i]);
                continue;
            }
            if (arg.startsWith("--code-lens-mode=")) {
                codeLensMode = CodeLensMode.parse(arg.substring("--code-lens-mode=".length()));
                continue;
            }
            remaining.add(arg);
        }
        return new ParsedArgs(new LspCliOptions(codeLensMode), remaining.toArray(new String[0]));
    }

    public record ParsedArgs(LspCliOptions options, String[] remaining) {}

    public Module asModule() {
        return new AbstractModule() {
            @Override
            protected void configure() {
                bind(LspCliOptions.class).toInstance(LspCliOptions.this);
            }
        };
    }
}

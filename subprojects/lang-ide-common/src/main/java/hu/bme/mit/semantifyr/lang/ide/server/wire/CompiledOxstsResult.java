/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.lang.ide.server.wire;

public record CompiledOxstsResult(String status, String oxsts, String message) {

    public static final String COMPILED = "compiled";
    public static final String ERRORED = "errored";

    public static CompiledOxstsResult compiled(String oxsts) {
        return new CompiledOxstsResult(COMPILED, oxsts, null);
    }

    public static CompiledOxstsResult errored(String message) {
        return new CompiledOxstsResult(ERRORED, null, message);
    }
}

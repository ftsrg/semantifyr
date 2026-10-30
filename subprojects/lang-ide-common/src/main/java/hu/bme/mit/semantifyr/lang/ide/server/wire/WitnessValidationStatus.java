/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.lang.ide.server.wire;

public final class WitnessValidationStatus {

    public static final String VALID = "valid";
    public static final String INVALID = "invalid";
    public static final String INCONCLUSIVE = "inconclusive";
    public static final String ERRORED = "errored";

    private WitnessValidationStatus() {}
}

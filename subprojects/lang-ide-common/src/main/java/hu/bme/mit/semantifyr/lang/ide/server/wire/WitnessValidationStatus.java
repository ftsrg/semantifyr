/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.lang.ide.server.wire;

import hu.bme.mit.semantifyr.backend.VerificationVerdict;

public final class WitnessValidationStatus {

    public static final String VALID = "valid";
    public static final String INVALID = "invalid";
    public static final String INCONCLUSIVE = "inconclusive";
    public static final String ERRORED = "errored";
    public static final String TIMEOUT = "timeout";
    public static final String CANCELLED = "cancelled";

    private WitnessValidationStatus() {}

    public static String of(VerificationVerdict verdict) {
        return switch (verdict) {
            case Passed -> VALID;
            case Failed -> INVALID;
            case Inconclusive, Timeout, NotSupported -> INCONCLUSIVE;
            case Errored -> ERRORED;
        };
    }
}

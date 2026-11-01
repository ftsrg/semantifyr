/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.lang.ide.server.wire;

import hu.bme.mit.semantifyr.backend.VerificationVerdict;

public final class VerificationStatus {

    public static final String PASSED = "passed";
    public static final String FAILED = "failed";
    public static final String INCONCLUSIVE = "inconclusive";
    public static final String TIMEOUT = "timeout";
    public static final String NOT_SUPPORTED = "not_supported";
    public static final String ERRORED = "errored";

    private VerificationStatus() {}

    public static String of(VerificationVerdict verdict) {
        return switch (verdict) {
            case Passed -> PASSED;
            case Failed -> FAILED;
            case Inconclusive -> INCONCLUSIVE;
            case Timeout -> TIMEOUT;
            case NotSupported -> NOT_SUPPORTED;
            case Errored -> ERRORED;
        };
    }
}

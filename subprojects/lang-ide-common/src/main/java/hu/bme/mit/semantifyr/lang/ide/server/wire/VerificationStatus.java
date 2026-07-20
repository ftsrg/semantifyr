/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.lang.ide.server.wire;

public final class VerificationStatus {

    public static final String PASSED = "passed";
    public static final String FAILED = "failed";
    public static final String INCONCLUSIVE = "inconclusive";
    public static final String NOT_SUPPORTED = "not_supported";
    public static final String ERRORED = "errored";

    private VerificationStatus() {}
}

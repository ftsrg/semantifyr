/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.lang.ide.server.wire;

import hu.bme.mit.semantifyr.verifier.VerificationOutcome;
import hu.bme.mit.semantifyr.verifier.VerificationReport;
import java.nio.file.Path;

public record VerificationCaseResult(
        String status,
        String message,
        String portfolioId,
        VerificationReport report,
        String witnessUri,
        String traceUri) {

    public static VerificationCaseResult fromOutcome(VerificationOutcome outcome, Path tracePath) {
        return new VerificationCaseResult(
                null,
                null,
                null,
                outcome.getReport(),
                outcome.getWitness() == null ? null : uriOf(outcome.getWitness().getPath()),
                tracePath == null ? null : uriOf(tracePath));
    }

    public String outcomeStatus() {
        if (report == null) {
            return status;
        }
        return VerificationStatus.of(report.getResult().getVerdict());
    }

    private static String uriOf(Path path) {
        return path.toAbsolutePath().toUri().toString();
    }

    public static VerificationCaseResult errored(String message, String portfolioId) {
        return new VerificationCaseResult(VerificationStatus.ERRORED, message, portfolioId, null, null, null);
    }

    public static VerificationCaseResult timedOut(String message, String portfolioId) {
        return new VerificationCaseResult(VerificationStatus.TIMEOUT, message, portfolioId, null, null, null);
    }

    public static VerificationCaseResult cancelled(String message, String portfolioId) {
        return new VerificationCaseResult(VerificationStatus.CANCELLED, message, portfolioId, null, null, null);
    }
}

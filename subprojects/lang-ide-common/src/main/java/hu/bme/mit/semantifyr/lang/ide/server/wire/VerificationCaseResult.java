/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.lang.ide.server.wire;

import hu.bme.mit.semantifyr.verifier.VerificationOutcome;
import hu.bme.mit.semantifyr.verifier.VerificationReport;
import hu.bme.mit.semantifyr.verifier.VerificationWitness;
import org.eclipse.emf.common.util.URI;

public record VerificationCaseResult(
        String status,
        String message,
        String portfolioId,
        VerificationReport report,
        String witnessUri,
        String traceUri) {

    public static VerificationCaseResult fromOutcome(VerificationOutcome outcome, String portfolioId, String traceUri) {
        var report = outcome.getReport();
        var result = report.getResult();
        return new VerificationCaseResult(
                VerificationStatus.of(result.getVerdict()),
                result.getMessage(),
                portfolioId,
                report,
                uriOf(outcome.getWitness()),
                traceUri);
    }

    static String uriOf(VerificationWitness witness) {
        return witness == null
                ? null
                : URI.createFileURI(witness.getPath().toAbsolutePath().toString())
                        .toString();
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

/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.lang.ide.server.wire;

import hu.bme.mit.semantifyr.verifier.VerificationReport;
import hu.bme.mit.semantifyr.verifier.witness.WitnessValidationResult.Errored;
import hu.bme.mit.semantifyr.verifier.witness.WitnessValidationResult.Inconclusive;
import hu.bme.mit.semantifyr.verifier.witness.WitnessValidationResult.Invalid;
import hu.bme.mit.semantifyr.verifier.witness.WitnessValidationResult.Valid;

public record WitnessValidationResult(
        String status, String verdict, String message, String portfolioId, VerificationReport report) {

    public static WitnessValidationResult fromValidation(
            hu.bme.mit.semantifyr.verifier.witness.WitnessValidationResult validation, String portfolioId) {
        var report = validation.getOutcome().getReport();
        var verification = report.getResult();
        return new WitnessValidationResult(
                statusOf(validation),
                VerificationStatus.of(verification.getVerdict()),
                verification.getMessage(),
                portfolioId,
                report);
    }

    public static WitnessValidationResult errored(String message, String portfolioId) {
        return new WitnessValidationResult(WitnessValidationStatus.ERRORED, null, message, portfolioId, null);
    }

    public static WitnessValidationResult timedOut(String message, String portfolioId) {
        return new WitnessValidationResult(
                WitnessValidationStatus.TIMEOUT, VerificationStatus.TIMEOUT, message, portfolioId, null);
    }

    public static WitnessValidationResult cancelled(String message, String portfolioId) {
        return new WitnessValidationResult(
                WitnessValidationStatus.CANCELLED, VerificationStatus.CANCELLED, message, portfolioId, null);
    }

    private static String statusOf(hu.bme.mit.semantifyr.verifier.witness.WitnessValidationResult validation) {
        return switch (validation) {
            case Valid ignored -> WitnessValidationStatus.VALID;
            case Invalid ignored -> WitnessValidationStatus.INVALID;
            case Inconclusive ignored -> WitnessValidationStatus.INCONCLUSIVE;
            case Errored ignored -> WitnessValidationStatus.ERRORED;
            default -> throw new IllegalStateException("unknown validation result " + validation);
        };
    }
}

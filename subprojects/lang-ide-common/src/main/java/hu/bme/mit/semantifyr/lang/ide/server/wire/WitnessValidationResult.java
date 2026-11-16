/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.lang.ide.server.wire;

import hu.bme.mit.semantifyr.verifier.VerificationMetricsDto;
import hu.bme.mit.semantifyr.verifier.VerificationReport;
import hu.bme.mit.semantifyr.verifier.witness.WitnessValidationResult.Errored;
import hu.bme.mit.semantifyr.verifier.witness.WitnessValidationResult.Inconclusive;
import hu.bme.mit.semantifyr.verifier.witness.WitnessValidationResult.Invalid;
import hu.bme.mit.semantifyr.verifier.witness.WitnessValidationResult.Valid;

public record WitnessValidationResult(
        String status,
        String verdict,
        String message,
        String backendId,
        String portfolioId,
        VerificationMetrics metrics,
        VerificationReport report) {

    public static WitnessValidationResult fromValidation(
            hu.bme.mit.semantifyr.verifier.witness.WitnessValidationResult validation,
            VerificationReport report,
            String portfolioId) {
        var verification = validation.getVerification();
        return new WitnessValidationResult(
                statusOf(validation),
                VerificationStatus.of(verification.getVerdict()),
                verification.getMessage(),
                verification.getMetadata().getBackendId(),
                portfolioId,
                VerificationMetrics.fromDto(VerificationMetricsDto.Companion.fromMetrics(verification.getMetrics())),
                report);
    }

    public static WitnessValidationResult errored(String message, String portfolioId) {
        return new WitnessValidationResult(
                WitnessValidationStatus.ERRORED, null, message, null, portfolioId, null, null);
    }

    public static WitnessValidationResult timedOut(String message, String portfolioId) {
        return new WitnessValidationResult(
                WitnessValidationStatus.TIMEOUT, VerificationStatus.TIMEOUT, message, null, portfolioId, null, null);
    }

    public static WitnessValidationResult cancelled(String message, String portfolioId) {
        return new WitnessValidationResult(
                WitnessValidationStatus.CANCELLED,
                VerificationStatus.CANCELLED,
                message,
                null,
                portfolioId,
                null,
                null);
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

/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.lang.ide.server.wire;

import hu.bme.mit.semantifyr.verifier.VerificationOutcome;
import hu.bme.mit.semantifyr.verifier.VerificationReport;

public record WitnessValidationResult(
        String status, String message, String portfolioId, VerificationReport report, String witnessUri) {

    public static WitnessValidationResult fromOutcome(VerificationOutcome outcome, String portfolioId) {
        var report = outcome.getReport();
        return new WitnessValidationResult(
                WitnessValidationStatus.of(report.getResult().getVerdict()),
                report.getResult().getMessage(),
                portfolioId,
                report,
                VerificationCaseResult.uriOf(outcome.getWitness()));
    }

    public static WitnessValidationResult errored(String message, String portfolioId) {
        return new WitnessValidationResult(WitnessValidationStatus.ERRORED, message, portfolioId, null, null);
    }

    public static WitnessValidationResult timedOut(String message, String portfolioId) {
        return new WitnessValidationResult(WitnessValidationStatus.TIMEOUT, message, portfolioId, null, null);
    }

    public static WitnessValidationResult cancelled(String message, String portfolioId) {
        return new WitnessValidationResult(WitnessValidationStatus.CANCELLED, message, portfolioId, null, null);
    }
}

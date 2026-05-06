/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.lang.ide.server.wire;

import hu.bme.mit.semantifyr.verifier.WitnessValidationResultDto;

public record WitnessValidationResult(
        String status, String message, String backendId, String portfolioId, VerificationMetrics metrics) {

    public static WitnessValidationResult fromDto(WitnessValidationResultDto dto, String portfolioId) {
        var verification = dto.getVerification();
        return new WitnessValidationResult(
                statusOf(dto),
                verification.getMessage(),
                verification.getMetadata().getBackendId(),
                portfolioId,
                VerificationMetrics.fromDto(verification.getMetrics()));
    }

    private static String statusOf(WitnessValidationResultDto dto) {
        return switch (dto.getKind()) {
            case VALID -> WitnessValidationStatus.VALID;
            case INVALID -> WitnessValidationStatus.INVALID;
            case INCONCLUSIVE -> WitnessValidationStatus.INCONCLUSIVE;
            case ERRORED -> WitnessValidationStatus.ERRORED;
        };
    }
}

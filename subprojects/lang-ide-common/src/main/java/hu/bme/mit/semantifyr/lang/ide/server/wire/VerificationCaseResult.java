/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.lang.ide.server.wire;

import hu.bme.mit.semantifyr.backend.VerificationVerdict;
import hu.bme.mit.semantifyr.verifier.VerificationResultDto;

public record VerificationCaseResult(
        String status,
        String message,
        String backendId,
        String portfolioId,
        VerificationMetrics metrics,
        VerificationTrace trace) {

    public static VerificationCaseResult fromDto(VerificationResultDto dto, String portfolioId) {
        var trace = dto.getTrace() != null ? VerificationTrace.fromDto(dto.getTrace()) : null;
        return build(dto, portfolioId, trace);
    }

    public static VerificationCaseResult fromDtoWithoutTrace(VerificationResultDto dto, String portfolioId) {
        return build(dto, portfolioId, null);
    }

    public static VerificationCaseResult errored(String message, String portfolioId) {
        return new VerificationCaseResult(VerificationStatus.ERRORED, message, null, portfolioId, null, null);
    }

    private static VerificationCaseResult build(
            VerificationResultDto dto, String portfolioId, VerificationTrace trace) {
        return new VerificationCaseResult(
                statusOf(dto.getVerdict()),
                dto.getMessage(),
                dto.getMetadata().getBackendId(),
                portfolioId,
                VerificationMetrics.fromDto(dto.getMetrics()),
                trace);
    }

    private static String statusOf(VerificationVerdict verdict) {
        return switch (verdict) {
            case Passed -> VerificationStatus.PASSED;
            case Failed -> VerificationStatus.FAILED;
            case Inconclusive -> VerificationStatus.INCONCLUSIVE;
            case NotSupported -> VerificationStatus.NOT_SUPPORTED;
            case Errored -> VerificationStatus.ERRORED;
        };
    }
}

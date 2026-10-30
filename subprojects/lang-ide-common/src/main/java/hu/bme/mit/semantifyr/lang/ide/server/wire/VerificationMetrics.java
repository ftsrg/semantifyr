/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.lang.ide.server.wire;

import hu.bme.mit.semantifyr.verifier.VerificationMetricsDto;
import java.time.Duration;

public record VerificationMetrics(
        Duration totalDuration,
        Duration preparationDuration,
        Duration verificationDuration,
        Duration backAnnotationDuration) {

    public static VerificationMetrics fromDto(VerificationMetricsDto dto) {
        var backend = dto.getBackend();
        var verifier = dto.getVerifier();

        var preparation = verifier.getCompilationDuration();
        if (backend != null) {
            preparation = preparation.plus(backend.getPreparationDuration());
        }

        var verification = backend != null ? backend.getVerificationDuration() : Duration.ZERO;

        var backAnnotation = verifier.getBackAnnotationDuration();
        if (backend != null) {
            backAnnotation = backAnnotation.plus(backend.getBackAnnotationDuration());
        }

        return new VerificationMetrics(dto.getTotalDuration(), preparation, verification, backAnnotation);
    }
}

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
        return new VerificationMetrics(
                dto.getTotalDuration(),
                backend != null ? backend.getPreparationDuration() : Duration.ZERO,
                backend != null ? backend.getVerificationDuration() : Duration.ZERO,
                backend != null ? backend.getBackAnnotationDuration() : Duration.ZERO);
    }
}

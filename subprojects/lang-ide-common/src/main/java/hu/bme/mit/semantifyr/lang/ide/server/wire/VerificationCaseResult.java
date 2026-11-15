/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package hu.bme.mit.semantifyr.lang.ide.server.wire;

import hu.bme.mit.semantifyr.lang.ide.wire.VerificationReportAdapter;
import hu.bme.mit.semantifyr.verifier.VerificationMetricsDto;
import hu.bme.mit.semantifyr.verifier.VerificationReport;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.eclipse.emf.common.util.URI;

public record VerificationCaseResult(
        String status,
        String message,
        String backendId,
        String portfolioId,
        VerificationMetrics metrics,
        VerificationReport report,
        String witnessUri) {

    // the verifier writes the report and the witness model of a case beside each other
    public static VerificationCaseResult fromCaseDirectory(Path caseDirectory, String portfolioId) throws IOException {
        var report = VerificationReportAdapter.read(caseDirectory.resolve("report.json"));
        var witness = caseDirectory.resolve("witness.oxsts");
        String witnessUri = null;
        if (report.getTrace() != null && Files.isRegularFile(witness)) {
            witnessUri = URI.createFileURI(witness.toAbsolutePath().toString()).toString();
        }
        return fromReport(report, portfolioId, witnessUri);
    }

    public static VerificationCaseResult fromReport(VerificationReport report, String portfolioId, String witnessUri) {
        var result = report.getResult();
        return new VerificationCaseResult(
                VerificationStatus.of(result.getVerdict()),
                result.getMessage(),
                result.getMetadata().getBackendId(),
                portfolioId,
                VerificationMetrics.fromDto(VerificationMetricsDto.Companion.fromMetrics(result.getMetrics())),
                report,
                witnessUri);
    }

    public static VerificationCaseResult errored(String message, String portfolioId) {
        return new VerificationCaseResult(VerificationStatus.ERRORED, message, null, portfolioId, null, null, null);
    }

    public static VerificationCaseResult timedOut(String message, String portfolioId) {
        return new VerificationCaseResult(VerificationStatus.TIMEOUT, message, null, portfolioId, null, null, null);
    }

    public static VerificationCaseResult cancelled(String message, String portfolioId) {
        return new VerificationCaseResult(VerificationStatus.CANCELLED, message, null, portfolioId, null, null, null);
    }
}

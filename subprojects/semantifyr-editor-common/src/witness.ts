/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { formatReportValue, lastNameSegment } from "./trace.ts";
import {
    type ReportCall,
    type ReportVerdict,
    type SoundnessDirection,
    type VerificationCaseResult,
    VerificationStatus,
    type WitnessValidationResult,
    WitnessValidationStatus,
} from "./wire.ts";

const STATUS_OF_VERDICT: Record<ReportVerdict, VerificationStatus> = {
    Passed: VerificationStatus.Passed,
    Failed: VerificationStatus.Failed,
    Inconclusive: VerificationStatus.Inconclusive,
    Timeout: VerificationStatus.Timeout,
    Errored: VerificationStatus.Errored,
    NotSupported: VerificationStatus.NotSupported,
};

export interface CaseOutcome {
    status: VerificationStatus;
    message: string | null;
    portfolioId: string | null;
}

export function caseOutcomeOf(result: VerificationCaseResult): CaseOutcome {
    if (result.report !== null) {
        return {
            status: STATUS_OF_VERDICT[result.report.result.verdict],
            message: result.message ?? result.report.result.message,
            portfolioId: result.report.portfolioId,
        };
    }
    return {
        status: result.status ?? VerificationStatus.Errored,
        message: result.message,
        portfolioId: result.portfolioId,
    };
}

export function traceStepLabel(index: number): string {
    if (index === 0) {
        return "Init";
    }
    return `Step ${String(index)}`;
}

export function formatTraceCall(call: ReportCall): string {
    const argumentList = Object.entries(call.arguments).map(([parameter, value]) => {
        return `${parameter}=${formatReportValue(value)}`;
    }).join(", ");
    return `${call.self}.${lastNameSegment(call.transition)}(${argumentList})`;
}

export function soundnessLabel(soundness: SoundnessDirection): string {
    switch (soundness) {
        case "Exact":
            return "exact";
        case "OverApproximating":
            return "over-approximating";
        case "UnderApproximating":
            return "under-approximating";
    }
}

export function noWitnessMessage(caseLabel: string): string {
    return `No witness for ${caseLabel}.`;
}

export function witnessOpenFailureMessage(witnessUri: string, error: unknown): string {
    return `Cannot open the witness ${witnessUri}: ${error instanceof Error ? error.message : String(error)}`;
}

export function witnessValidationStatusOf(result: WitnessValidationResult | null): WitnessValidationStatus {
    switch (result?.status) {
        case WitnessValidationStatus.Valid:
        case WitnessValidationStatus.Invalid:
        case WitnessValidationStatus.Inconclusive:
        case WitnessValidationStatus.Timeout:
        case WitnessValidationStatus.Cancelled:
            return result.status;
        default:
            return WitnessValidationStatus.Errored;
    }
}

export function witnessValidationLabel(status: WitnessValidationStatus): string {
    switch (status) {
        case WitnessValidationStatus.Valid:
            return "Witness valid";
        case WitnessValidationStatus.Invalid:
            return "Witness invalid";
        case WitnessValidationStatus.Inconclusive:
            return "Validation inconclusive";
        case WitnessValidationStatus.Timeout:
            return "Validation timed out";
        case WitnessValidationStatus.Cancelled:
            return "Validation cancelled";
        case WitnessValidationStatus.Errored:
            return "Validation errored";
    }
}

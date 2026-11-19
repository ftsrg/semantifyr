/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { formatReportValue, lastNameSegment } from "./trace.ts";
import { type ReportCall, type SoundnessDirection, type WitnessValidationResult, WitnessValidationStatus } from "./wire.ts";

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

export function missingCallsMessage(reason: string): string {
    return `The calls of this trace could not be obtained: ${reason}`;
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

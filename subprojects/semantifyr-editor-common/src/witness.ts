/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { type TraceEntry, type WitnessState, type WitnessValidationResult, WitnessValidationStatus } from "./wire.ts";

export interface WitnessStateChange {
    variable: string;
    value: string;
    previous: string | undefined;
}

export function witnessStateChanges(state: WitnessState): WitnessStateChange[][] {
    const current = new Map<string, string>();
    return [state.initializedStep, ...state.steps].map((step) => {
        const changes = step.values.map((value) => {
            return { variable: value.variable, value: value.value, previous: current.get(value.variable) };
        }).filter((change) => change.value !== change.previous);
        for (const change of changes) {
            current.set(change.variable, change.value);
        }
        return changes;
    });
}

export function traceStepLabel(index: number): string {
    if (index === 0) {
        return "Init";
    }
    return `Step ${String(index)}`;
}

export function formatTraceCall(entry: TraceEntry): string {
    const argumentList = entry.arguments.map((argument) => {
        return `${argument.parameter}=${argument.value}`;
    }).join(", ");
    return `${entry.self}.${entry.calledTransition}(${argumentList})`;
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

/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

export interface Position {
    line: number;
    character: number;
}

export interface Range {
    start: Position;
    end: Position;
}

export interface Location {
    uri: string;
    range: Range;
}

export interface VerificationCaseSpecification {
    id: string;
    label: string;
    location: Location;
}

export interface VerificationCaseRequest {
    uri: string;
    range: Range;
    portfolio?: string;
}

export const VerificationStatus = {
    Passed: "passed",
    Failed: "failed",
    Inconclusive: "inconclusive",
    NotSupported: "not_supported",
    Errored: "errored",
} as const;

export type VerificationStatus = (typeof VerificationStatus)[keyof typeof VerificationStatus];

export interface VerificationMetrics {
    totalDuration: string;
    preparationDuration: string;
    verificationDuration: string;
    backAnnotationDuration: string;
}

export interface TraceArgument {
    parameter: string;
    value: string;
}

export interface TraceEntry {
    self: string;
    calledTransition: string;
    arguments: TraceArgument[];
    innerTraces: TraceEntry[] | null;
}

export interface CallTraceStep {
    traces: TraceEntry[];
}

export interface CallTrace {
    initialStep: CallTraceStep;
    steps: CallTraceStep[];
}

export interface WitnessStateValue {
    variable: string;
    value: string;
}

export interface WitnessStateStep {
    values: WitnessStateValue[];
}

export interface WitnessState {
    initialStep: WitnessStateStep;
    steps: WitnessStateStep[];
}

export interface VerificationTrace {
    callTrace: CallTrace;
    witnessState: WitnessState;
    witnessUri: string | null;
}

export interface VerificationCaseResult {
    status: VerificationStatus;
    message: string | null;
    backendId: string | null;
    portfolioId: string | null;
    metrics: VerificationMetrics | null;
    trace: VerificationTrace | null;
}

export type ThetaExecutor = "auto" | "shell" | "docker";

export type ArtifactsLocation = "temporary" | "workspace";

export type ArtifactsPreset = "none" | "all" | "debug";

export type OptimizationLevel = "none" | "all";

export interface ServerSettings {
    portfolio?: string;
    timeoutSeconds?: number;
    maxConcurrency?: number;
    theta?: {
        executor?: ThetaExecutor;
        dockerImage?: string;
    };
    artifacts?: {
        location?: ArtifactsLocation;
        preset?: ArtifactsPreset;
    };
    optimization?: {
        level?: OptimizationLevel;
    };
}

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

export interface NavigateToParams {
    locations: Location[];
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
    Timeout: "timeout",
    NotSupported: "not_supported",
    Errored: "errored",
    Cancelled: "cancelled",
} as const;

export type VerificationStatus = (typeof VerificationStatus)[keyof typeof VerificationStatus];

export interface PassStats {
    name: string;
    duration: string;
    runs: number;
    firings: number;
}

export interface AnalysisStats {
    name: string;
    duration: string;
    computations: number;
}

export interface OptimizationStats {
    duration: string;
    passes: PassStats[];
    analyses: AnalysisStats[];
}

export interface ModelSize {
    variables: number;
    operations: number;
    expressions: number;
}

export interface CompilationMetrics {
    createDuration: string;
    instantiationDuration: string;
    inliningDuration: string;
    flatteningDuration: string;
    inliningOptimization: OptimizationStats;
    flatteningOptimization: OptimizationStats;
    inputSize: ModelSize;
    inlinedSize: ModelSize;
    flattenedSize: ModelSize;
}

export interface VerificationMetrics {
    totalDuration: string;
    preparationDuration: string;
    verificationDuration: string;
    backAnnotationDuration: string;
    compilation?: CompilationMetrics;
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
    initializedStep: CallTraceStep;
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
    initializedStep: WitnessStateStep;
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

export const WitnessValidationStatus = {
    Valid: "valid",
    Invalid: "invalid",
    Inconclusive: "inconclusive",
    Errored: "errored",
    Timeout: "timeout",
    Cancelled: "cancelled",
} as const;

export type WitnessValidationStatus = (typeof WitnessValidationStatus)[keyof typeof WitnessValidationStatus];

export interface WitnessValidationResult {
    status: WitnessValidationStatus;
    verdict: VerificationStatus | null;
    message: string | null;
    backendId: string | null;
    portfolioId: string | null;
    metrics: VerificationMetrics | null;
}

export type ThetaExecutor = "auto" | "shell" | "docker";

export type ArtifactsLocation = "temporary" | "workspace";

export type ArtifactsPreset = "none" | "all" | "debug";

export type OptimizationLevel = "none" | "all";

export interface ServerSettings {
    portfolio?: string;
    timeoutSeconds?: number;
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

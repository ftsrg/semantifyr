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

export interface WitnessValidationRequest extends VerificationCaseRequest {
    witnessUri?: string;
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

export type ReportKind =
    | "run"
    | "re-check";

export type ReportVerdict =
    | "Passed"
    | "Failed"
    | "Inconclusive"
    | "Timeout"
    | "Errored"
    | "NotSupported";

export type SoundnessDirection =
    | "Exact"
    | "OverApproximating"
    | "UnderApproximating";

export type ReportValue =
    | boolean
    | number
    | string
    | null
    | ReportValue[]
    | { [field: string]: ReportValue };

export type Fraction = number | string;

export type ReportTypeKind =
    | "datatype"
    | "enum"
    | "class"
    | "record"
    | "feature";

export interface ReportType {
    kind: ReportTypeKind;
    name: string;
}

export interface ObservedParameter {
    parameter: string;
    type: ReportType;
}

export interface ObservedTransition {
    transition: string;
    parameters: ObservedParameter[];
}

export interface ObservedSlot {
    array: string;
    index: number;
    size: number;
}

export interface ObservedVariable {
    variable: string;
    type: ReportType;
    slot: ObservedSlot | null;
}

export interface ReportCall {
    self: string;
    transition: string;
    arguments: Record<string, ReportValue>;
    calls: ReportCall[];
}

export interface ReportInitialEntry {
    state: number;
    calls: ReportCall[];
}

export interface ReportState {
    values: Record<string, ReportValue>;
}

export interface ReportStep {
    source: number;
    target: number;
    elapsed: Fraction;
    calls: ReportCall[];
}

export interface ReportTrace {
    root: string;
    transitions: ObservedTransition[];
    variables: ObservedVariable[];
    initial: ReportInitialEntry;
    states: ReportState[];
    steps: ReportStep[];
    missingCalls: string | null;
}

export interface ReportBackendMetrics {
    preparationDuration: string;
    verificationDuration: string;
    backAnnotationDuration: string;
}

export interface ReportVerifierMetrics {
    compilationDuration: string;
    compilation: CompilationMetrics;
    backAnnotationDuration: string;
    portfolioDuration: string;
}

export interface ReportMetrics {
    totalDuration: string;
    backend: ReportBackendMetrics | null;
    verifier: ReportVerifierMetrics;
}

export interface ReportResult {
    metadata: {
        backendId: string | null;
        startedAt: string;
    };
    verdict: ReportVerdict;
    soundness: SoundnessDirection;
    isTrusted: boolean;
    metrics: ReportMetrics;
    message: string | null;
}

export interface VerificationReport {
    version: number;
    kind: ReportKind;
    verificationCase: string;
    portfolioId: string;
    optimization: {
        enabled: string[];
    };
    timeout: string;
    result: ReportResult;
    trace: ReportTrace | null;
}

export interface VerificationCaseResult {
    status: VerificationStatus;
    message: string | null;
    portfolioId: string | null;
    report: VerificationReport | null;
    witnessUri: string | null;
    traceUri: string | null;
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
    message: string | null;
    portfolioId: string | null;
    report: VerificationReport | null;
    witnessUri: string | null;
}

export const CompiledOxstsStatus = {
    Compiled: "compiled",
    Errored: "errored",
} as const;

export type CompiledOxstsStatus = (typeof CompiledOxstsStatus)[keyof typeof CompiledOxstsStatus];

export interface CompiledOxstsResult {
    status: CompiledOxstsStatus;
    oxsts: string | null;
    message: string | null;
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

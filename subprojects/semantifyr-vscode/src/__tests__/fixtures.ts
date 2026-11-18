/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type { ReportTrace, VerificationCaseResult, VerificationCaseSpecification, VerificationReport } from '@semantifyr/editor-common';

export const modelUri = 'file:///workspace/lamp.semantifyr';

export const witnessUri = 'file:/tmp/semantifyr/lamp.Safe/witness.semantifyr';

export const resolutionNote = "Portfolio 'all-agree' resolved to Passed from theta:cegar-expl-pred-combined over spin:safe-dfs=Failed (not trusted, UnderApproximating)";

export const safeCase: VerificationCaseSpecification = {
    id: 'lamp::Safe',
    label: 'Safe',
    location: { uri: modelUri, range: { start: { line: 3, character: 0 }, end: { line: 7, character: 1 } } },
};

export const reachableCase: VerificationCaseSpecification = {
    id: 'lamp::Reachable',
    label: 'Reachable',
    location: { uri: modelUri, range: { start: { line: 9, character: 0 }, end: { line: 12, character: 1 } } },
};

export const counterexample: ReportTrace = {
    root: 'root',
    transitions: [
        {
            transition: 'lamp::Lamp::toggle',
            parameters: [{ parameter: 'brightness', type: { kind: 'datatype', name: 'semantifyr::int' } }],
        },
    ],
    variables: [
        { variable: 'root.lamp.on', type: { kind: 'datatype', name: 'semantifyr::bool' }, slot: null },
        { variable: 'root.lamp.brightness', type: { kind: 'datatype', name: 'semantifyr::int' }, slot: null },
    ],
    initial: { state: 0, calls: [] },
    states: [
        { values: { 'root.lamp.on': false, 'root.lamp.brightness': 0 } },
        { values: { 'root.lamp.on': true, 'root.lamp.brightness': 2 } },
    ],
    steps: [
        {
            source: 0,
            target: 1,
            elapsed: 0,
            calls: [{ self: 'root.lamp', transition: 'lamp::Lamp::toggle', arguments: { brightness: 2 }, calls: [] }],
        },
    ],
    missingCalls: null,
};

function reportOf(status: VerificationCaseResult['status'], trace: ReportTrace | null): VerificationReport {
    return {
        version: 2,
        kind: 'run',
        verificationCase: 'lamp::Safe',
        portfolioId: 'theta',
        optimization: { enabled: [] },
        timeout: 'PT5M',
        result: {
            metadata: { backendId: 'theta-cegar-expl', startedAt: '2026-11-15T10:00:00Z' },
            verdict: status === 'failed' ? 'Failed' : 'Passed',
            soundness: 'Exact',
            isTrusted: true,
            metrics: {
                totalDuration: 'PT1S',
                backend: null,
                verifier: {
                    compilationDuration: 'PT0S',
                    compilation: {
                        createDuration: 'PT0S',
                        instantiationDuration: 'PT0S',
                        inliningDuration: 'PT0S',
                        flatteningDuration: 'PT0S',
                        inliningOptimization: { duration: 'PT0S', passes: [], analyses: [] },
                        flatteningOptimization: { duration: 'PT0S', passes: [], analyses: [] },
                        inputSize: { variables: 0, operations: 0, expressions: 0 },
                        inlinedSize: { variables: 0, operations: 0, expressions: 0 },
                        flattenedSize: { variables: 0, operations: 0, expressions: 0 },
                    },
                    backAnnotationDuration: 'PT0S',
                    portfolioDuration: 'PT0S',
                },
            },
            message: null,
        },
        trace,
    };
}

export function result(status: VerificationCaseResult['status'], trace: ReportTrace | null = null, message: string | null = null): VerificationCaseResult {
    return { status, message, portfolioId: 'theta', report: reportOf(status, trace), witnessUri: trace === null ? null : witnessUri, traceUri: null };
}

export function validationReport(): VerificationReport {
    const report = reportOf('passed', null);
    return {
        ...report,
        kind: 're-check',
        result: {
            ...report.result,
            metadata: { ...report.result.metadata, backendId: 'spin-safe-dfs' },
            soundness: 'UnderApproximating',
            metrics: { ...report.result.metrics, totalDuration: 'PT1.503S' },
        },
    };
}

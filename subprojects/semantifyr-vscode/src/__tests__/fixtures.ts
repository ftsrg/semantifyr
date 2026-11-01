/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type { VerificationCaseResult, VerificationCaseSpecification, VerificationTrace } from '@semantifyr/editor-common';

export const modelUri = 'file:///workspace/lamp.oxsts';

export const witnessUri = 'file:/tmp/semantifyr/lamp.Safe/witness.oxsts';

export const untrustedNote = "spin's Passed on an AG property is not trusted: the leg is under-approximating";

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

export const counterexample: VerificationTrace = {
    callTrace: {
        initialStep: { traces: [] },
        initializedStep: { traces: [{ self: 'root', calledTransition: 'init', arguments: [], innerTraces: null }] },
        steps: [
            {
                traces: [{
                    self: 'root',
                    calledTransition: 'main',
                    arguments: [],
                    innerTraces: [{ self: 'root.lamp', calledTransition: 'toggle', arguments: [{ parameter: 'brightness', value: '2' }], innerTraces: null }],
                }],
            },
        ],
    },
    witnessState: {
        initialStep: { values: [{ variable: 'root.lamp.on', value: 'false' }, { variable: 'root.lamp.brightness', value: '0' }] },
        initializedStep: { values: [{ variable: 'root.lamp.on', value: 'false' }, { variable: 'root.lamp.brightness', value: '0' }] },
        steps: [{ values: [{ variable: 'root.lamp.on', value: 'true' }, { variable: 'root.lamp.brightness', value: '2' }] }],
    },
    witnessUri,
};

export function result(status: VerificationCaseResult['status'], trace: VerificationTrace | null = null, message: string | null = null): VerificationCaseResult {
    return { status, message, backendId: 'theta-cegar-expl', portfolioId: 'theta-full', metrics: null, trace };
}

/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type {
    Range,
    VerificationCaseRequest,
    VerificationCaseResult,
    VerificationCaseSpecification,
    WitnessValidationResult,
} from "./wire.ts";

export interface CancellationToken {
    readonly isCancellationRequested: boolean;
    onCancellationRequested(listener: () => void): { dispose(): void };
}

export interface LspClient {
    sendRequest(method: string, params?: unknown, token?: CancellationToken): Promise<unknown>;
    sendNotification(method: string, params?: unknown): void;
}

export async function discoverVerificationCases(
    client: LspClient,
    discoveryCommand: string,
    fileUri: string,
): Promise<VerificationCaseSpecification[]> {
    const result = await client.sendRequest("workspace/executeCommand", {
        command: discoveryCommand,
        arguments: [fileUri],
    }) as VerificationCaseSpecification[] | null | undefined;
    return result ?? [];
}

export interface VerificationCaseLocation {
    uri: string;
    range: Range;
}

export interface VerifyCaseOptions {
    portfolio?: string;
    token?: CancellationToken;
}

export async function verifyCase(
    client: LspClient,
    verificationCommand: string,
    location: VerificationCaseLocation,
    options: VerifyCaseOptions = {},
): Promise<VerificationCaseResult | null> {
    return await executeLocationCommand(client, verificationCommand, location, options) as VerificationCaseResult | null;
}

export async function validateWitness(
    client: LspClient,
    validationCommand: string,
    location: VerificationCaseLocation,
    options: VerifyCaseOptions = {},
): Promise<WitnessValidationResult | null> {
    return await executeLocationCommand(client, validationCommand, location, options) as WitnessValidationResult | null;
}

// gamma.witness.validate takes the case, oxsts.case.validateWitness the back-annotated witness model
export function witnessValidationLocation(
    caseLocation: VerificationCaseLocation,
    witnessUri: string,
    byCase: boolean,
): VerificationCaseLocation {
    if (byCase) {
        return caseLocation;
    }
    return {
        uri: witnessUri,
        range: { start: { line: 0, character: 0 }, end: { line: 0, character: 0 } },
    };
}

async function executeLocationCommand(
    client: LspClient,
    command: string,
    location: VerificationCaseLocation,
    options: VerifyCaseOptions,
): Promise<unknown> {
    const args: VerificationCaseRequest = {
        uri: location.uri,
        range: location.range,
        ...(options.portfolio ? { portfolio: options.portfolio } : {}),
    };
    const params = { command, arguments: [args] };
    // vscode-jsonrpc sends a trailing undefined as a second positional parameter, which lsp4j rejects
    if (options.token) {
        return await client.sendRequest("workspace/executeCommand", params, options.token);
    }
    return await client.sendRequest("workspace/executeCommand", params);
}

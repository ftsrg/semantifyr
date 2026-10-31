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
} from "./wire.js";

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
    const args: VerificationCaseRequest = {
        uri: location.uri,
        range: location.range,
        ...(options.portfolio ? { portfolio: options.portfolio } : {}),
    };
    const params = { command: verificationCommand, arguments: [args] };
    // vscode-jsonrpc sends a trailing undefined as a second positional parameter, which lsp4j rejects
    const response = options.token
        ? await client.sendRequest("workspace/executeCommand", params, options.token)
        : await client.sendRequest("workspace/executeCommand", params);
    return response as VerificationCaseResult | null;
}

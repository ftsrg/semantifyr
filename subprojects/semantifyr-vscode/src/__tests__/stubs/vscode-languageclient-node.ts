/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { PassThrough } from 'node:stream';
import { vi } from 'vitest';
import { createMessageConnection, type MessageConnection, type MessageSignature, StreamMessageReader, StreamMessageWriter } from 'vscode-jsonrpc/node';

export * from './vscode-languageclient.js';

type Responder = (client: LanguageClient, method: string, params: unknown) => unknown;

export class LanguageClient {
    static readonly instances: LanguageClient[] = [];
    static respond: Responder = () => null;

    readonly id: string;
    readonly name: string;
    readonly serverOptions: unknown;
    readonly clientOptions: Record<string, unknown>;
    readonly connection: MessageConnection;
    readonly server: MessageConnection;
    readonly notificationHandlers = new Map<string, (params: never) => unknown>();
    readonly start = vi.fn(async () => {});
    readonly stop = vi.fn(async () => {});

    constructor(id: string, name: string, serverOptions: unknown, clientOptions: Record<string, unknown>) {
        this.id = id;
        this.name = name;
        this.serverOptions = serverOptions;
        this.clientOptions = clientOptions;
        const toServer = new PassThrough();
        const toClient = new PassThrough();
        this.connection = createMessageConnection(new StreamMessageReader(toClient), new StreamMessageWriter(toServer));
        this.server = createMessageConnection(new StreamMessageReader(toServer), new StreamMessageWriter(toClient));
        this.server.onRequest((method, params) => LanguageClient.respond(this, method, params));
        this.connection.listen();
        this.server.listen();
        LanguageClient.instances.push(this);
    }

    onNotification(method: string, handler: (params: never) => unknown) {
        this.notificationHandlers.set(method, handler);
        return { dispose: () => this.notificationHandlers.delete(method) };
    }

    sendRequest(type: string | MessageSignature, ...params: unknown[]): Promise<unknown> {
        return this.connection.sendRequest(type as string, ...params);
    }

    sendNotification(type: string | MessageSignature, params?: unknown): Promise<void> {
        return this.connection.sendNotification(type as string, params);
    }

    static disposeAll() {
        for (const client of LanguageClient.instances) {
            client.connection.dispose();
            client.server.dispose();
        }
        LanguageClient.instances.length = 0;
        LanguageClient.respond = () => null;
    }
}

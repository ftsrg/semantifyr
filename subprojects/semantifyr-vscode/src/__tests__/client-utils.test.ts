/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { EventEmitter } from 'node:events';
import net from 'node:net';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { spawn } from 'child_process';
import { createLspClient } from '../clients/client-utils.js';
import { resetVscode } from './stubs/vscode.js';
import { LanguageClient } from './stubs/vscode-languageclient-node.js';

vi.mock('child_process', () => ({ spawn: vi.fn() }));

const spawnMock = vi.mocked(spawn);
const platform = process.platform;

type Child = EventEmitter;

function serverOptions(executable: string) {
    const client = createLspClient(executable, 'oxsts') as unknown as LanguageClient;
    return (client.serverOptions as () => Promise<{ reader: net.Socket; writer: net.Socket }>)();
}

function portOf(args: readonly string[]): number {
    return Number(/^--socket=(\d+)$/.exec(args[0]!)![1]);
}

function startChild(behave: (child: Child, port: number) => void) {
    spawnMock.mockImplementation(((_command: string, args: readonly string[]) => {
        const child = new EventEmitter();
        behave(child, portOf(args));
        return child;
    }) as never);
}

beforeEach(() => {
    resetVscode();
});

afterEach(() => {
    LanguageClient.disposeAll();
    Object.defineProperty(process, 'platform', { value: platform });
});

describe('createLspClient', () => {
    it('starts the server on a loopback port and hands over the connection it opens', async () => {
        let childSocket: net.Socket | undefined;
        startChild((_child, port) => {
            childSocket = net.connect(port, '127.0.0.1');
        });

        const streams = await serverOptions('/extension path/bin/oxsts.lang.ide/bin/oxsts.lang.ide');

        expect(streams.reader).toBe(streams.writer);
        expect(streams.reader.remotePort).toBe(childSocket!.localPort);
        const [command, args] = spawnMock.mock.calls[0]!;
        expect(args).toEqual([`--socket=${String(streams.reader.localPort)}`]);
        if (platform === 'win32') {
            expect(command).toBe('"/extension path/bin/oxsts.lang.ide/bin/oxsts.lang.ide"');
        } else {
            expect(command).toBe('/extension path/bin/oxsts.lang.ide/bin/oxsts.lang.ide');
            expect(spawnMock.mock.calls[0]![2]).toEqual({ shell: false, stdio: ['ignore', 'inherit', 'inherit'] });
        }
        childSocket!.destroy();
        streams.reader.destroy();
    });

    it('quotes the launcher for the Windows shell', async () => {
        Object.defineProperty(process, 'platform', { value: 'win32' });
        let childSocket: net.Socket | undefined;
        startChild((_child, port) => {
            childSocket = net.connect(port, '127.0.0.1');
        });

        const streams = await serverOptions('C:\\Users\\Jane Doe\\.vscode\\extensions\\semantifyr\\bin\\oxsts.lang.ide.bat');

        expect(spawnMock.mock.calls[0]![0]).toBe('"C:\\Users\\Jane Doe\\.vscode\\extensions\\semantifyr\\bin\\oxsts.lang.ide.bat"');
        expect(spawnMock.mock.calls[0]![2]).toEqual({ shell: true, stdio: ['ignore', 'inherit', 'inherit'] });
        childSocket!.destroy();
        streams.reader.destroy();
    });

    it('fails when the server exits before it connects', async () => {
        startChild((child) => {
            setTimeout(() => child.emit('exit', 1, null), 0);
        });

        await expect(serverOptions('/extension/bin/oxsts.lang.ide')).rejects.toThrow('The oxsts language server exited with code 1 before connecting');
    });

    it('fails when the server cannot be started', async () => {
        startChild((child) => {
            setTimeout(() => child.emit('error', new Error('spawn EACCES')), 0);
        });

        await expect(serverOptions('/extension/bin/oxsts.lang.ide')).rejects.toThrow('spawn EACCES');
    });
});

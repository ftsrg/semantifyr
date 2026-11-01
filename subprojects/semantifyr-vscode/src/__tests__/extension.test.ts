/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import net from 'node:net';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { activate, deactivate } from '../extension.js';
import { commands, createExtensionContext, Position, resetVscode, state, Uri, window } from './stubs/vscode.js';
import { LanguageClient } from './stubs/vscode-languageclient-node.js';

interface ExecuteCommandParams {
    command: string;
    arguments: unknown[];
}

let requests: { client: string; params: ExecuteCommandParams }[];

function client(id: string): LanguageClient {
    return LanguageClient.instances.find((instance) => instance.id === id)!;
}

beforeEach(() => {
    resetVscode();
    requests = [];
    LanguageClient.respond = (instance, _method, params) => {
        requests.push({ client: instance.id, params: params as ExecuteCommandParams });
        return (params as ExecuteCommandParams).command.endsWith('.case.discover') ? [] : null;
    };
});

afterEach(() => {
    LanguageClient.disposeAll();
    vi.unstubAllEnvs();
});

describe('activate', () => {
    it('starts an OXSTS and a Gamma language client', async () => {
        await activate(createExtensionContext('/extension') as never);

        expect(LanguageClient.instances.map((instance) => [instance.id, instance.name])).toEqual([
            ['oxstsLSP', 'OXSTS Language Server'],
            ['gammaLSP', 'GAMMA Language Server'],
        ]);
        for (const instance of LanguageClient.instances) {
            expect(instance.start).toHaveBeenCalledOnce();
            expect(typeof instance.serverOptions).toBe('function');
        }
        expect(client('oxstsLSP').clientOptions.documentSelector).toEqual([{ scheme: 'file', language: 'oxsts' }]);
        expect(client('gammaLSP').clientOptions.documentSelector).toEqual([{ scheme: 'file', language: 'gamma' }]);
    });

    it('registers the output channel, the test controllers and the commands', async () => {
        const context = createExtensionContext('/extension');

        await activate(context as never);

        expect(window.createOutputChannel).toHaveBeenCalledWith('Semantifyr');
        expect(state.controllers.map((controller) => [controller.id, controller.label])).toEqual([
            ['oxsts.verification', 'Semantifyr Verification Cases'],
            ['gamma.verification', 'Gamma Verification Cases'],
        ]);
        expect(state.controllers.map((controller) => controller.profiles.map((profile) => [profile.label, profile.isDefault]))).toEqual([[['Run', true]], [['Run', true]]]);
        expect([...state.commands.keys()]).toEqual(['semantifyr.gamma.compile']);
        expect(context.subscriptions.length).toBeGreaterThan(0);
    });

    it('discovers the cases of each workspace file on the server of its language', async () => {
        state.files = [Uri.parse('file:///workspace/lamp.oxsts'), Uri.parse('file:///workspace/crossroads.gamma')];

        await activate(createExtensionContext('/extension') as never);

        expect(requests).toEqual([
            { client: 'oxstsLSP', params: { command: 'oxsts.case.discover', arguments: ['file:///workspace/lamp.oxsts'] } },
            { client: 'gammaLSP', params: { command: 'gamma.case.discover', arguments: ['file:///workspace/crossroads.gamma'] } },
        ]);
    });

    it('compiles a Gamma file through the Gamma server', async () => {
        await activate(createExtensionContext('/extension') as never);
        const compile = state.commands.get('semantifyr.gamma.compile')!;

        await compile(Uri.parse('file:///workspace/crossroads.gamma'));
        window.activeTextEditor = { document: { uri: Uri.parse('file:///workspace/open.gamma'), languageId: 'gamma', getText: () => '' }, viewColumn: 1 };
        await compile();

        expect(requests).toEqual([
            { client: 'gammaLSP', params: { command: 'gamma.compile', arguments: ['file:///workspace/crossroads.gamma'] } },
            { client: 'gammaLSP', params: { command: 'gamma.compile', arguments: ['file:///workspace/open.gamma'] } },
        ]);
    });

    it('asks for a Gamma file when there is none to compile', async () => {
        await activate(createExtensionContext('/extension') as never);

        await state.commands.get('semantifyr.gamma.compile')!();

        expect(window.showErrorMessage).toHaveBeenCalledWith('Compile Gamma File: no Gamma file selected.');
        expect(requests).toEqual([]);
    });

    it('peeks the locations the OXSTS server navigates to', async () => {
        await activate(createExtensionContext('/extension') as never);
        const current = Uri.parse('file:///workspace/lamp.oxsts');
        Object.assign(window, { activeTextEditor: { document: { uri: current }, selection: { start: new Position(2, 4) } } });

        client('oxstsLSP').notificationHandlers.get('workspace/navigateTo')!({
            locations: [{ uri: 'file:///workspace/library.oxsts', range: { start: { line: 1, character: 2 }, end: { line: 1, character: 8 } } }],
        } as never);

        await expect.poll(() => commands.executeCommand.mock.calls.find(([id]) => id === 'editor.action.goToLocations')).toBeDefined();
        const [, uri, position, locations, mode] = commands.executeCommand.mock.calls.find(([id]) => id === 'editor.action.goToLocations')!;
        expect(uri).toBe(current);
        expect(position).toEqual(new Position(2, 4));
        expect(mode).toBe('peek');
        expect(locations).toMatchObject([{ range: { start: { line: 1, character: 2 }, end: { line: 1, character: 8 } } }]);
        expect(String((locations as { uri: Uri }[])[0]!.uri)).toBe('file:///workspace/library.oxsts');
    });

    it('connects to a running server when its debug port is set', async () => {
        const server = net.createServer();
        await new Promise<void>((resolve) => server.listen(0, '127.0.0.1', resolve));
        const accepted = new Promise<void>((resolve) => server.once('connection', () => { resolve(); }));
        vi.stubEnv('DEBUG_OXSTS_LSP', String((server.address() as net.AddressInfo).port));

        await activate(createExtensionContext('/extension') as never);
        const streams = await (client('oxstsLSP').serverOptions as () => Promise<{ reader: net.Socket; writer: net.Socket }>)();

        await accepted;
        expect(streams.reader).toBe(streams.writer);
        streams.reader.destroy();
        server.close();
    });
});

describe('deactivate', () => {
    it('stops both language clients', async () => {
        await activate(createExtensionContext('/extension') as never);

        await deactivate();

        expect(client('oxstsLSP').stop).toHaveBeenCalledOnce();
        expect(client('gammaLSP').stop).toHaveBeenCalledOnce();
    });
});

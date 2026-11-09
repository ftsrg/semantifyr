/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import net from 'node:net';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { activate, deactivate } from '../extension.js';
import { commands, createExtensionContext, Position, resetVscode, state, Uri, ViewColumn, window } from './stubs/vscode.js';
import { LanguageClient } from './stubs/vscode-languageclient-node.js';

interface ExecuteCommandParams {
    command: string;
    arguments: unknown[];
}

interface ExecuteCommandMiddleware {
    executeCommand(command: string, commandArguments: unknown[], next: (command: string, commandArguments: unknown[]) => Promise<unknown>): Promise<unknown>;
}

let requests: { client: string; parameters: ExecuteCommandParams }[];

function client(id: string): LanguageClient {
    return LanguageClient.instances.find((instance) => instance.id === id)!;
}

beforeEach(() => {
    resetVscode();
    requests = [];
    LanguageClient.respond = (instance, _method, parameters) => {
        requests.push({ client: instance.id, parameters: parameters as ExecuteCommandParams });
        return (parameters as ExecuteCommandParams).command.endsWith('.case.discover') ? [] : null;
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

    it('registers the output channel, the test controllers, the commands and the trace documents', async () => {
        const context = createExtensionContext('/extension');

        await activate(context as never);

        expect(window.createOutputChannel).toHaveBeenCalledWith('Semantifyr');
        expect(state.controllers.map((controller) => [controller.id, controller.label])).toEqual([
            ['oxsts.verification', 'Semantifyr Verification Cases'],
            ['gamma.verification', 'Gamma Verification Cases'],
        ]);
        expect(state.controllers.map((controller) => controller.profiles.map((profile) => [profile.label, profile.isDefault]))).toEqual([[['Run', true]], [['Run', true]]]);
        expect([...state.commands.keys()].sort()).toEqual([
            'semantifyr.gamma.compile',
            'semantifyr.witness.show',
            'semantifyr.witness.validate',
        ]);
        expect(state.contentProviders.has('semantifyr-trace')).toBe(true);
        expect(context.subscriptions.length).toBeGreaterThan(0);
    });

    it('discovers the cases of each workspace file on the server of its language', async () => {
        state.files = [Uri.parse('file:///workspace/lamp.oxsts'), Uri.parse('file:///workspace/crossroads.gamma')];

        await activate(createExtensionContext('/extension') as never);

        expect(requests).toEqual([
            { client: 'oxstsLSP', parameters: { command: 'oxsts.case.discover', arguments: ['file:///workspace/lamp.oxsts'] } },
            { client: 'gammaLSP', parameters: { command: 'gamma.case.discover', arguments: ['file:///workspace/crossroads.gamma'] } },
        ]);
    });

    it('compiles a Gamma file through the Gamma server', async () => {
        await activate(createExtensionContext('/extension') as never);
        const compile = state.commands.get('semantifyr.gamma.compile')!;

        await compile(Uri.parse('file:///workspace/crossroads.gamma'));
        window.activeTextEditor = { document: { uri: Uri.parse('file:///workspace/open.gamma'), languageId: 'gamma', getText: () => '' }, viewColumn: 1 };
        await compile();

        expect(requests).toEqual([
            { client: 'gammaLSP', parameters: { command: 'gamma.compile', arguments: ['file:///workspace/crossroads.gamma'] } },
            { client: 'gammaLSP', parameters: { command: 'gamma.compile', arguments: ['file:///workspace/open.gamma'] } },
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

    it('opens the inlined model of an Inline lens beside the source, read-only', async () => {
        await activate(createExtensionContext('/extension') as never);
        const middleware = client('oxstsLSP').clientOptions.middleware as ExecuteCommandMiddleware;
        const request = { location: { uri: 'file:///workspace/lamp.oxsts' }, serializeSteps: true };
        const next = vi.fn(async () => 'file:///tmp/semantifyr/lamp.Safe/inlined.oxsts');

        await middleware.executeCommand('oxsts.class.inline', [request], next);

        expect(next).toHaveBeenCalledWith('oxsts.class.inline', [request]);
        const [target, options] = window.showTextDocument.mock.calls[0]!;
        expect((target as Uri).toString()).toBe('file:///tmp/semantifyr/lamp.Safe/inlined.oxsts');
        expect(options).toEqual({ viewColumn: ViewColumn.Beside, preview: false });
        expect(commands.executeCommand).toHaveBeenCalledWith('workbench.action.files.setActiveEditorReadonlyInSession');
    });

    it('opens the compiled model of a Compile lens beside the source, read-only', async () => {
        await activate(createExtensionContext('/extension') as never);
        const middleware = client('oxstsLSP').clientOptions.middleware as ExecuteCommandMiddleware;
        const location = { uri: 'file:///tmp/semantifyr/lamp.Safe/inlined.oxsts' };
        const next = vi.fn(async () => 'file:///tmp/semantifyr/lamp.Safe/compiled.oxsts');

        await middleware.executeCommand('oxsts.inlined.compile', [location], next);

        expect(next).toHaveBeenCalledWith('oxsts.inlined.compile', [location]);
        const [target, options] = window.showTextDocument.mock.calls[0]!;
        expect((target as Uri).toString()).toBe('file:///tmp/semantifyr/lamp.Safe/compiled.oxsts');
        expect(options).toEqual({ viewColumn: ViewColumn.Beside, preview: false });
        expect(commands.executeCommand).toHaveBeenCalledWith('workbench.action.files.setActiveEditorReadonlyInSession');
    });

    it('passes the other OXSTS commands through without opening anything', async () => {
        await activate(createExtensionContext('/extension') as never);
        const middleware = client('oxstsLSP').clientOptions.middleware as ExecuteCommandMiddleware;
        const next = vi.fn(async () => 'file:///workspace/other.oxsts');

        expect(await middleware.executeCommand('oxsts.inlined.verify', [], next)).toBe('file:///workspace/other.oxsts');

        expect(window.showTextDocument).not.toHaveBeenCalled();
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

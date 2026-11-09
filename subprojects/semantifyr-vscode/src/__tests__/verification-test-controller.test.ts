/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { type CancellationToken, ResponseError } from 'vscode-jsonrpc';
import { LSPErrorCodes } from 'vscode-languageserver-protocol';
import type { VerificationCaseResult, VerificationCaseSpecification } from '@semantifyr/editor-common';
import { VerificationTestController } from '../clients/verification-test-controller.js';
import { WitnessView } from '../witness.js';
import { counterexample, modelUri, reachableCase, result, safeCase, untrustedNote } from './fixtures.js';
import { CancellationTokenSource, createExtensionContext, resetVscode, state, type TestController, type TestItem, Uri, window } from './stubs/vscode.js';
import { LanguageClient } from './stubs/vscode-languageclient-node.js';

interface ExecuteCommandParams {
    command: string;
    arguments: unknown[];
}

type VerifyHandler = (parameters: unknown, token: CancellationToken) => Promise<VerificationCaseResult | ResponseError | null> | VerificationCaseResult | ResponseError | null;

let cases: VerificationCaseSpecification[] | null;
let verify: VerifyHandler;
let requests: { method: string; parameters: unknown }[];
let controller: TestController;
let witnesses: WitnessView;

async function setUp() {
    const client = new LanguageClient('oxstsLSP', 'OXSTS Language Server', undefined, {});
    client.server.onRequest((method, parameters, token) => {
        requests.push({ method, parameters });
        const command = (parameters as ExecuteCommandParams).command;
        if (command === 'oxsts.case.discover') {
            return cases;
        }
        return verify((parameters as ExecuteCommandParams).arguments[0], token);
    });
    const context = createExtensionContext('/extension');
    witnesses = new WitnessView();
    witnesses.register(context as never);
    const testController = new VerificationTestController(client as never, {
        languageId: 'oxsts',
        fileGlob: '**/*.oxsts',
        controllerId: 'oxsts.verification',
        controllerLabel: 'Semantifyr Verification Cases',
        discoverCommand: 'oxsts.case.discover',
        verifyCommand: 'oxsts.case.verify',
    }, witnesses);
    testController.registerTestProvider(context as never);
    await testController.refreshWorkspaceTests();
    controller = state.controllers[0]!;
}

function caseItem(id: string): TestItem {
    return controller.items.get(modelUri)!.children.get(id)!;
}

async function run(items: TestItem[], token = new CancellationTokenSource().token) {
    await controller.profiles[0]!.runHandler({ include: items }, token);
    return controller.runs.at(-1)!;
}

beforeEach(() => {
    resetVscode();
    state.files = [Uri.parse(modelUri)];
    cases = [safeCase, reachableCase];
    verify = () => result('passed');
    requests = [];
});

afterEach(() => {
    LanguageClient.disposeAll();
});

describe('discovery', () => {
    it('asks the server for the cases of each workspace file', async () => {
        await setUp();

        expect(requests).toEqual([{ method: 'workspace/executeCommand', parameters: { command: 'oxsts.case.discover', arguments: [modelUri] } }]);
        const fileItem = controller.items.get(modelUri)!;
        expect(fileItem.label).toBe('lamp.oxsts');
        expect([...fileItem.children].map(([id]) => id)).toEqual(['lamp::Safe', 'lamp::Reachable']);
        expect(caseItem('lamp::Reachable').range).toEqual({ start: { line: 9, character: 0 }, end: { line: 12, character: 1 } });
    });

    it('drops cases the server no longer reports, and the file once it has none', async () => {
        await setUp();

        cases = [reachableCase];
        await controller.refreshHandler!();
        expect([...controller.items.get(modelUri)!.children].map(([id]) => id)).toEqual(['lamp::Reachable']);

        cases = null;
        await controller.refreshHandler!();
        expect(controller.items.get(modelUri)).toBeUndefined();
    });

    it('rediscovers a file when it changes on disk', async () => {
        await setUp();

        cases = [safeCase];
        state.watchers.find((watcher) => watcher.glob === '**/*.oxsts')!.changed.fire(Uri.parse(modelUri));
        await expect.poll(() => controller.items.get(modelUri)?.children.size).toBe(1);
    });
});

describe('verification', () => {
    it('sends the case location as a single named parameter', async () => {
        await setUp();

        await run([caseItem('lamp::Safe')]);

        expect(requests.at(-1)).toEqual({
            method: 'workspace/executeCommand',
            parameters: { command: 'oxsts.case.verify', arguments: [{ uri: modelUri, range: safeCase.location.range }] },
        });
    });

    it('runs every case of a file when the file is selected', async () => {
        await setUp();

        const testRun = await run([controller.items.get(modelUri)!]);

        expect(testRun.outcome(caseItem('lamp::Safe'))).toBe('passed');
        expect(testRun.outcome(caseItem('lamp::Reachable'))).toBe('passed');
        expect(testRun.ended).toBe(true);
    });

    it.each([
        ['passed', 'passed', undefined],
        ['inconclusive', 'errored', 'Inconclusive: bound reached'],
        ['timeout', 'errored', 'Timed out: bound reached'],
        ['not_supported', 'errored', 'Not supported by the chosen portfolio: bound reached'],
        ['errored', 'errored', 'bound reached'],
    ] as const)('reports a %s verdict as %s', async (status, outcome, message) => {
        await setUp();
        verify = () => result(status, null, 'bound reached');

        const testRun = await run([caseItem('lamp::Safe')]);

        const event = testRun.events.at(-1)!;
        expect(event.kind).toBe(outcome);
        expect('message' in event ? event.message.message : undefined).toBe(message);
    });

    it('shows the message of a passed verdict at the case and still passes it', async () => {
        await setUp();
        verify = () => result('passed', null, untrustedNote);

        const testRun = await run([caseItem('lamp::Safe')]);

        expect(testRun.outcome(caseItem('lamp::Safe'))).toBe('passed');
        expect(testRun.output).toHaveLength(1);
        const [output] = testRun.output;
        expect(output!.text).toBe(`${untrustedNote}\r\n`);
        expect(output!.item).toBe(caseItem('lamp::Safe'));
        expect(output!.location?.uri.toString()).toBe(modelUri);
        expect(output!.location?.range).toEqual(safeCase.location.range);
    });

    it.each([null, ''])('shows nothing extra for a passed verdict with the message %j', async (message) => {
        await setUp();
        verify = () => result('passed', null, message);

        const testRun = await run([caseItem('lamp::Safe')]);

        expect(testRun.outcome(caseItem('lamp::Safe'))).toBe('passed');
        expect(testRun.output).toEqual([]);
    });

    it('reports a failed verdict at the case with a witness action', async () => {
        await setUp();
        verify = () => result('failed', counterexample, 'property violated');

        const testRun = await run([caseItem('lamp::Safe')]);

        const event = testRun.events.at(-1)!;
        expect(event.kind).toBe('failed');
        const message = (event as { message: { message: string; contextValue?: string; location?: { uri: Uri; range: unknown } } }).message;
        expect(message.message).toBe('property violated');
        expect(message.contextValue).toBe('semantifyr.witness');
        expect(message.location?.uri.toString()).toBe(modelUri);
        expect(message.location?.range).toEqual(safeCase.location.range);
    });

    it('offers no witness action on a failed verdict without a trace', async () => {
        await setUp();
        verify = () => result('failed');

        const testRun = await run([caseItem('lamp::Safe')]);

        const event = testRun.events.at(-1) as { message: { message: string; contextValue?: string } };
        expect(event.message.message).toBe('Failed');
        expect(event.message.contextValue).toBeUndefined();
    });

    it('reports an error when the server returns no result', async () => {
        await setUp();
        verify = () => null;

        const testRun = await run([caseItem('lamp::Safe')]);

        expect(testRun.events.at(-1)).toMatchObject({ kind: 'errored', message: { message: 'The language server returned no result' } });
    });

    it('reports the server error of a failed request', async () => {
        await setUp();
        verify = () => new ResponseError(-32603, 'compilation failed');

        const testRun = await run([caseItem('lamp::Safe')]);

        expect(testRun.events.at(-1)).toMatchObject({ kind: 'errored', message: { message: 'compilation failed' } });
    });

    it('records the witness of each result and forgets it when a rerun has none', async () => {
        await setUp();
        const showWitness = state.commands.get('semantifyr.witness.show')!;

        verify = () => result('passed', counterexample);
        await run([caseItem('lamp::Reachable')]);
        await showWitness(caseItem('lamp::Reachable'));
        expect((window.showTextDocument.mock.calls[0]![0] as Uri).toString()).toBe('file:///tmp/semantifyr/lamp.Safe/witness.oxsts');

        verify = () => result('passed');
        await run([caseItem('lamp::Reachable')]);
        await showWitness(caseItem('lamp::Reachable'));
        expect(window.showInformationMessage).toHaveBeenLastCalledWith('No witness recorded for Reachable.');
    });
});

describe('cancellation', () => {
    it('skips every case when the run is cancelled before it starts', async () => {
        await setUp();
        const source = new CancellationTokenSource();
        source.cancel();

        const testRun = await run([caseItem('lamp::Safe'), caseItem('lamp::Reachable')], source.token);

        expect(testRun.outcome(caseItem('lamp::Safe'))).toBe('skipped');
        expect(testRun.outcome(caseItem('lamp::Reachable'))).toBe('skipped');
        expect(requests.filter((request) => (request.parameters as ExecuteCommandParams).command === 'oxsts.case.verify')).toEqual([]);
        expect(testRun.ended).toBe(true);
    });

    it('cancels the running request on the server and skips the rest', async () => {
        await setUp();
        const source = new CancellationTokenSource();
        let serverCancelled = false;
        verify = (_parameters, token) => new Promise((resolve) => {
            token.onCancellationRequested(() => {
                serverCancelled = true;
                resolve(new ResponseError(LSPErrorCodes.RequestCancelled, 'cancelled'));
            });
            source.cancel();
        });

        const testRun = await run([caseItem('lamp::Safe'), caseItem('lamp::Reachable')], source.token);

        expect(serverCancelled).toBe(true);
        expect(testRun.outcome(caseItem('lamp::Safe'))).toBe('skipped');
        expect(testRun.outcome(caseItem('lamp::Reachable'))).toBe('skipped');
        expect(requests.filter((request) => (request.parameters as ExecuteCommandParams).command === 'oxsts.case.verify')).toHaveLength(1);
        expect(testRun.ended).toBe(true);
    });
});

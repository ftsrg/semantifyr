/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { type CancellationToken, ResponseError } from 'vscode-jsonrpc';
import { LSPErrorCodes } from 'vscode-languageserver-protocol';
import type { VerificationCaseResult, VerificationCaseSpecification, WitnessValidationResult } from '@semantifyr/editor-common';
import { VerificationTestController, type VerificationTestControllerConfig } from '../clients/verification-test-controller.js';
import { WitnessView } from '../witness.js';
import { registerOutputChannel } from '../outputChannel.js';
import { counterexample, modelUri, reachableCase, resolutionNote, result, safeCase } from './fixtures.js';
import { CancellationTokenSource, createExtensionContext, openDocument, resetVscode, state, type TestController, type TestItem, Uri, window } from './stubs/vscode.js';
import { LanguageClient } from './stubs/vscode-languageclient-node.js';

interface ExecuteCommandParams {
    command: string;
    arguments: unknown[];
}

type VerifyHandler = (parameters: unknown, token: CancellationToken) => Promise<VerificationCaseResult | ResponseError | null> | VerificationCaseResult | ResponseError | null;

let cases: VerificationCaseSpecification[] | null;
let verify: VerifyHandler;
let validate: (parameters: unknown, token: CancellationToken) => Promise<WitnessValidationResult | null> | WitnessValidationResult | null;
let requests: { method: string; parameters: unknown }[];
let controller: TestController;
let witnesses: WitnessView;

async function setUp(validation: Partial<VerificationTestControllerConfig> = {}) {
    const client = new LanguageClient('oxstsLSP', 'OXSTS Language Server', undefined, {});
    client.server.onRequest((method, parameters, token) => {
        requests.push({ method, parameters });
        const command = (parameters as ExecuteCommandParams).command;
        if (command === 'oxsts.case.discover') {
            return cases;
        }
        if (command === validation.validateWitnessCommand) {
            return validate((parameters as ExecuteCommandParams).arguments[0], token);
        }
        return verify((parameters as ExecuteCommandParams).arguments[0], token);
    });
    const context = createExtensionContext('/extension');
    registerOutputChannel(context as never);
    witnesses = new WitnessView();
    witnesses.register(context as never);
    const testController = new VerificationTestController(client as never, {
        languageId: 'oxsts',
        fileGlob: '**/*.oxsts',
        controllerId: 'oxsts.verification',
        controllerLabel: 'Semantifyr Verification Cases',
        discoverCommand: 'oxsts.case.discover',
        verifyCommand: 'oxsts.case.verify',
        ...validation,
    }, witnesses);
    testController.registerTestProvider(context as never);
    await testController.refreshWorkspaceTests();
    controller = state.controllers[0]!;
}

function caseItem(id: string): TestItem {
    return controller.items.get(modelUri)!.children.get(id)!;
}

function discoverRequests() {
    return requests.filter((request) => (request.parameters as ExecuteCommandParams).command === 'oxsts.case.discover');
}

function edit(uri: string) {
    state.documentChanged.fire({ document: openDocument(Uri.parse(uri)) });
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
    validate = () => null;
    requests = [];
});

afterEach(() => {
    vi.useRealTimers();
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

describe('rediscovery on edits', () => {
    const movedSafeCase = { ...safeCase, location: { uri: modelUri, range: { start: { line: 5, character: 0 }, end: { line: 9, character: 1 } } } };

    it('rediscovers an edited document once the edits settle', async () => {
        await setUp();
        vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] });
        cases = [movedSafeCase];

        edit(modelUri);
        edit(modelUri);
        expect(discoverRequests()).toHaveLength(1);
        await vi.advanceTimersByTimeAsync(1000);
        vi.useRealTimers();

        await expect.poll(() => caseItem('lamp::Safe').range).toEqual(movedSafeCase.location.range);
        expect(discoverRequests()).toHaveLength(2);
    });

    it('ignores edits of documents in another language', async () => {
        await setUp();
        vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] });

        edit('file:///workspace/crossroads.gamma');
        await vi.advanceTimersByTimeAsync(1000);
        await run([caseItem('lamp::Safe')]);

        expect(discoverRequests()).toHaveLength(1);
    });

    it('rediscovers an edited document before a run verifies its cases', async () => {
        await setUp();
        cases = [movedSafeCase, reachableCase];

        edit(modelUri);
        await run([caseItem('lamp::Safe')]);

        expect(requests.at(-1)).toEqual({
            method: 'workspace/executeCommand',
            parameters: { command: 'oxsts.case.verify', arguments: [{ uri: modelUri, range: movedSafeCase.location.range }] },
        });
    });

    it('skips a case the edit removed instead of verifying another one', async () => {
        await setUp();
        const removed = caseItem('lamp::Safe');
        cases = [reachableCase];

        edit(modelUri);
        const testRun = await run([removed]);

        expect(testRun.outcome(removed)).toBe('skipped');
        expect(requests.filter((request) => (request.parameters as ExecuteCommandParams).command === 'oxsts.case.verify')).toEqual([]);
    });
});

describe('generated artifacts', () => {
    const artifactUri = 'file:///workspace/.artifacts/lamp.Safe/pipeline/flattened.oxsts';

    it('are not discovered in the workspace', async () => {
        state.files = [Uri.parse(modelUri), Uri.parse(artifactUri)];

        await setUp();

        expect(discoverRequests().map((request) => (request.parameters as ExecuteCommandParams).arguments)).toEqual([[modelUri]]);
        expect(controller.items.get(artifactUri)).toBeUndefined();
    });

    it('are not discovered when they are written, opened or edited', async () => {
        await setUp();

        const watcher = state.watchers.find((it) => it.glob === '**/*.oxsts')!;
        watcher.created.fire(Uri.parse(artifactUri));
        watcher.changed.fire(Uri.parse(artifactUri));
        state.documentOpened.fire(openDocument(Uri.parse(artifactUri)));
        edit(artifactUri);
        await run([caseItem('lamp::Safe')]);

        expect(discoverRequests()).toHaveLength(1);
        expect(controller.items.get(artifactUri)).toBeUndefined();
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
        ['cancelled', 'skipped', undefined],
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
        verify = () => result('passed', null, resolutionNote);

        const testRun = await run([caseItem('lamp::Safe')]);

        expect(testRun.outcome(caseItem('lamp::Safe'))).toBe('passed');
        expect(testRun.output).toHaveLength(1);
        const [output] = testRun.output;
        expect(output!.text).toBe(`${resolutionNote}\r\n`);
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
        expect(window.showInformationMessage).toHaveBeenLastCalledWith('No witness for Reachable.');
    });
});

describe('witness validation', () => {
    const byCase = { validateWitnessCommand: 'gamma.witness.validate', validateWitnessByCase: true };
    const valid: WitnessValidationResult = { status: 'valid', verdict: 'failed', message: null, backendId: 'theta-bmc', portfolioId: 'smart', metrics: null };

    function validateWitnessOf(item: TestItem) {
        return state.commands.get('semantifyr.witness.validate')!(item);
    }

    function validations() {
        return requests.filter((request) => {
            return (request.parameters as ExecuteCommandParams).command.endsWith('alidateWitness') || (request.parameters as ExecuteCommandParams).command === 'gamma.witness.validate';
        });
    }

    beforeEach(() => {
        state.configuration.set('semantifyr.verification.autoValidate', false);
    });

    it('offers a validation on a failed verdict when the language validates witnesses', async () => {
        await setUp(byCase);
        verify = () => result('failed', counterexample, 'property violated');

        const testRun = await run([caseItem('lamp::Safe')]);

        const event = testRun.events.at(-1) as { message: { contextValue?: string } };
        expect(event.message.contextValue).toBe('semantifyr.witness.validatable');
    });

    it('sends the case location for a validation by case', async () => {
        await setUp(byCase);
        verify = () => result('failed', counterexample);
        validate = () => valid;
        await run([caseItem('lamp::Safe')]);

        await validateWitnessOf(caseItem('lamp::Safe'));

        expect(requests.at(-1)).toEqual({
            method: 'workspace/executeCommand',
            parameters: { command: 'gamma.witness.validate', arguments: [{ uri: modelUri, range: safeCase.location.range }] },
        });
        expect(window.showInformationMessage).toHaveBeenLastCalledWith('Safe: Witness valid (backend theta-bmc, portfolio smart)');
    });

    it('sends the witness location for a validation by witness', async () => {
        await setUp({ validateWitnessCommand: 'oxsts.case.validateWitness' });
        verify = () => result('failed', counterexample);
        validate = () => valid;
        await run([caseItem('lamp::Safe')]);

        await validateWitnessOf(caseItem('lamp::Safe'));

        expect((requests.at(-1)!.parameters as ExecuteCommandParams).arguments).toEqual([
            { uri: counterexample.witnessUri, range: { start: { line: 0, character: 0 }, end: { line: 0, character: 0 } } },
        ]);
    });

    it('cancels the validation on the server when the progress is cancelled and reports nothing', async () => {
        await setUp(byCase);
        verify = () => result('failed', counterexample);
        let serverCancelled = false;
        validate = (_parameters, token) => new Promise((resolve) => {
            token.onCancellationRequested(() => {
                serverCancelled = true;
                resolve(new ResponseError(LSPErrorCodes.RequestCancelled, 'cancelled') as never);
            });
            window.progressTokens.at(-1)!.cancel();
        });
        await run([caseItem('lamp::Safe')]);

        await validateWitnessOf(caseItem('lamp::Safe'));

        expect(serverCancelled).toBe(true);
        expect(window.showErrorMessage).not.toHaveBeenCalled();
        expect(window.showInformationMessage).not.toHaveBeenCalled();
    });

    it('validates the witness after the verdict and adds the result to the failure message', async () => {
        state.configuration.set('semantifyr.verification.autoValidate', true);
        await setUp(byCase);
        verify = () => result('failed', counterexample, 'property violated');
        validate = () => valid;

        const testRun = await run([caseItem('lamp::Safe')]);

        expect(validations()).toHaveLength(1);
        const event = testRun.events.at(-1) as { kind: string; message: { message: string } };
        expect(event.kind).toBe('failed');
        expect(event.message.message).toBe('property violated\nValidation: Witness valid (backend theta-bmc, portfolio smart)');
    });

    it('validates the witness of an OXSTS case at the witness model, which may lie outside the workspace', async () => {
        state.configuration.set('semantifyr.verification.autoValidate', true);
        await setUp({ validateWitnessCommand: 'oxsts.case.validateWitness' });
        verify = () => result('failed', counterexample);
        validate = () => valid;

        await run([caseItem('lamp::Safe')]);

        expect(validations().map((request) => request.parameters)).toEqual([{
            command: 'oxsts.case.validateWitness',
            arguments: [{ uri: 'file:/tmp/semantifyr/lamp.Safe/witness.oxsts', range: { start: { line: 0, character: 0 }, end: { line: 0, character: 0 } } }],
        }]);
    });

    it('validates on the validation portfolio when one is set', async () => {
        state.configuration.set('semantifyr.verification.autoValidate', true);
        state.configuration.set('semantifyr.verification.validationPortfolio', 'all-agree');
        await setUp(byCase);
        verify = () => result('failed', counterexample);
        validate = () => valid;

        await run([caseItem('lamp::Safe')]);

        expect((validations()[0]!.parameters as ExecuteCommandParams).arguments).toEqual([{ uri: modelUri, range: safeCase.location.range, portfolio: 'all-agree' }]);
    });

    it('adds the validation of a passed verdict with a witness to the output at the case', async () => {
        state.configuration.set('semantifyr.verification.autoValidate', true);
        await setUp(byCase);
        verify = () => result('passed', counterexample);
        validate = () => ({ ...valid, status: 'invalid', message: 'spurious' });

        const testRun = await run([caseItem('lamp::Reachable')]);

        expect(testRun.outcome(caseItem('lamp::Reachable'))).toBe('passed');
        expect(testRun.output.map((output) => output.text)).toEqual(['Validation: Witness invalid (backend theta-bmc, portfolio smart) - spurious\r\n']);
    });

    it('validates nothing when auto-validation is off or the result has no witness', async () => {
        await setUp(byCase);
        verify = () => result('failed', counterexample);
        await run([caseItem('lamp::Safe')]);
        state.configuration.set('semantifyr.verification.autoValidate', true);
        verify = () => result('failed');
        await run([caseItem('lamp::Safe')]);

        expect(validations()).toEqual([]);
    });

    it('offers no validation without a validation command', async () => {
        await setUp();
        verify = () => result('failed', counterexample);
        await run([caseItem('lamp::Safe')]);

        await validateWitnessOf(caseItem('lamp::Safe'));

        expect(requests.map((request) => (request.parameters as ExecuteCommandParams).command)).not.toContain('gamma.witness.validate');
        expect(window.showInformationMessage).toHaveBeenLastCalledWith('No witness for Safe.');
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

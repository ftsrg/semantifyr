/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { beforeEach, describe, expect, it } from 'vitest';
import { renderTrace, WitnessView } from '../witness.js';
import { registerOutputChannel } from '../outputChannel.js';
import { counterexample, resolutionNote, result, witnessUri } from './fixtures.js';
import { createExtensionContext, resetVscode, state, TestItem, TestMessage, Uri, ViewColumn, commands, window, workspace } from './stubs/vscode.js';
import type { WitnessValidationResult } from '@semantifyr/editor-common';

describe('renderTrace', () => {
    it('lists the calls and the state changes of each step', () => {
        expect(renderTrace('Safe', result('failed', counterexample), counterexample)).toBe([
            '# Safe',
            '',
            'Verdict: failed (backend theta-cegar-expl, portfolio theta-full)',
            '',
            `Witness: ${witnessUri}`,
            '',
            '## Init',
            '',
            'Calls:',
            '',
            '- `root.init()`',
            '',
            'State:',
            '',
            '| Variable | Value |',
            '| --- | --- |',
            '| `root.lamp.on` | `false` |',
            '| `root.lamp.brightness` | `0` |',
            '',
            '## Step 1',
            '',
            'Calls:',
            '',
            '- `root.main()`',
            '  - `root.lamp.toggle(brightness=2)`',
            '',
            'Changes:',
            '',
            '| Variable | Value |',
            '| --- | --- |',
            '| `root.lamp.on` | `true` |',
            '| `root.lamp.brightness` | `2` |',
            '',
        ].join('\n'));
    });

    it.each([
        ['file:/tmp/semantifyr/lamp.Safe/witness.oxsts', 'Witness'],
        ['file:/tmp/semantifyr/crossroads.Green/trace.get', 'Gamma trace'],
    ])('labels the opened file %s as %s', (uri, label) => {
        const trace = { ...counterexample, witnessUri: uri };

        expect(renderTrace('Safe', result('failed', trace), trace).split('\n')[4]).toBe(`${label}: ${uri}`);
    });

    it('appends the message of a passed verdict to the verdict line', () => {
        const document = renderTrace('Safe', result('passed', counterexample, resolutionNote), counterexample);

        expect(document.split('\n')[2]).toBe(`Verdict: passed (backend theta-cegar-expl, portfolio theta-full) - ${resolutionNote}`);
    });

    it.each([null, ''])('keeps the verdict line of a passed verdict with the message %j bare', (message) => {
        const document = renderTrace('Safe', result('passed', counterexample, message), counterexample);

        expect(document.split('\n')[2]).toBe('Verdict: passed (backend theta-cegar-expl, portfolio theta-full)');
    });

    it('adds the witness validation below the verdict', () => {
        const validation: WitnessValidationResult = { status: 'invalid', verdict: 'passed', message: 'spurious', backendId: 'theta-bmc', portfolioId: 'smart-full', metrics: null };

        const document = renderTrace('Safe', result('failed', counterexample), counterexample, validation);

        expect(document.split('\n').slice(2, 5)).toEqual([
            'Verdict: failed (backend theta-cegar-expl, portfolio theta-full)',
            '',
            'Validation: Witness invalid (backend theta-bmc, portfolio smart-full) - spurious',
        ]);
    });

    it('leaves out a missing witness and origin', () => {
        const trace = {
            callTrace: { initializedStep: { traces: [] }, steps: [{ traces: [] }] },
            witnessState: { initializedStep: { values: [] }, steps: [{ values: [{ variable: 'root.mode', value: 'A | B' }] }] },
            witnessUri: null,
        };

        const document = renderTrace('Reachable', { ...result('passed', trace), backendId: null, portfolioId: null }, trace);

        expect(document).toBe([
            '# Reachable',
            '',
            'Verdict: passed',
            '',
            '## Init',
            '',
            'No calls.',
            '',
            'No state.',
            '',
            '## Step 1',
            '',
            'No calls.',
            '',
            'Changes:',
            '',
            '| Variable | Value |',
            '| --- | --- |',
            '| `root.mode` | `A \\| B` |',
            '',
        ].join('\n'));
    });
});

describe('WitnessView', () => {
    const modelUri = Uri.parse('file:///workspace/lamp.oxsts');
    let view: WitnessView;
    let item: TestItem;

    beforeEach(() => {
        resetVscode();
        const context = createExtensionContext('/extension');
        registerOutputChannel(context as never);
        view = new WitnessView();
        view.register(context as never);
        item = new TestItem('lamp::Safe', 'Safe', modelUri);
    });

    function showWitness(argument: unknown) {
        return state.commands.get('semantifyr.witness.show')!(argument);
    }

    it('opens the witness read-only beside the model and the trace next to it', async () => {
        view.record(item as never, result('failed', counterexample));

        await showWitness({ test: item, message: new TestMessage('property violated') });

        const [witnessTarget, witnessOptions] = window.showTextDocument.mock.calls[0]!;
        expect((witnessTarget as Uri).toString()).toBe('file:///tmp/semantifyr/lamp.Safe/witness.oxsts');
        expect(witnessOptions).toEqual({ viewColumn: ViewColumn.Beside, preview: false });
        expect(commands.executeCommand).toHaveBeenCalledWith('workbench.action.files.setActiveEditorReadonlyInSession');

        const [traceDocument, traceOptions] = window.showTextDocument.mock.calls[1]!;
        expect(traceOptions).toEqual({ viewColumn: ViewColumn.Two, preview: false });
        const document = traceDocument as { uri: Uri; languageId: string; getText(): string };
        expect(document.uri.scheme).toBe('semantifyr-trace');
        expect(document.uri.path).toBe('/Safe.md');
        expect(document.languageId).toBe('markdown');
        expect(document.getText()).toBe(renderTrace('Safe', result('failed', counterexample), counterexample));
    });

    it('accepts the test item itself from the test explorer', async () => {
        view.record(item as never, result('failed', counterexample));

        await showWitness(item);

        expect(window.showTextDocument).toHaveBeenCalledTimes(2);
    });

    it('opens only the trace when the result carries no witness file', async () => {
        view.record(item as never, result('passed', { ...counterexample, witnessUri: null }));

        await showWitness(item);

        expect(window.showTextDocument).toHaveBeenCalledTimes(1);
        expect(window.showTextDocument.mock.calls[0]![1]).toEqual({ viewColumn: ViewColumn.Beside, preview: false });
        expect(commands.executeCommand).not.toHaveBeenCalledWith('workbench.action.files.setActiveEditorReadonlyInSession');
    });

    it('reports a witness file it cannot open and still opens the trace', async () => {
        view.record(item as never, result('failed', counterexample));
        window.showTextDocument.mockRejectedValueOnce(new Error('file not found'));

        await showWitness(item);

        expect(window.showErrorMessage).toHaveBeenCalledWith(`Cannot open the witness ${witnessUri}: file not found\n`);
        expect(String((window.showTextDocument.mock.calls[1]![0] as { uri: Uri }).uri)).toMatch(/^semantifyr-trace:/);
    });

    it('says so when the case has no witness', async () => {
        await showWitness(item);
        await showWitness(undefined);

        expect(window.showInformationMessage.mock.calls).toEqual([['No witness for Safe.'], ['No witness for this test.']]);
        expect(window.showTextDocument).not.toHaveBeenCalled();
    });

    it('keeps one trace document per case and refreshes it on a rerun', async () => {
        const changes: string[] = [];
        view.onDidChange((uri) => changes.push(String(uri)));
        const other = new TestItem('lamp::Reachable', 'Reachable', modelUri);

        view.record(item as never, result('failed', counterexample));
        view.record(other as never, result('passed', counterexample));
        view.record(item as never, result('failed', counterexample, 'again'));

        expect(changes).toHaveLength(3);
        expect(changes[2]).toBe(changes[0]);
        expect(changes[1]).not.toBe(changes[0]);
        expect(view.provideTextDocumentContent(Uri.parse(changes[0]!) as never)).toContain('# Safe');
        expect(view.provideTextDocumentContent(Uri.parse(changes[1]!) as never)).toContain('# Reachable');
        expect(workspace.registerTextDocumentContentProvider).toHaveBeenCalledWith('semantifyr-trace', view);
    });
});

describe('WitnessView validation', () => {
    const modelUri = Uri.parse('file:///workspace/crossroads.gamma');
    let view: WitnessView;
    let item: TestItem;

    beforeEach(() => {
        resetVscode();
        const context = createExtensionContext('/extension');
        registerOutputChannel(context as never);
        view = new WitnessView();
        view.register(context as never);
        item = new TestItem('crossroads::ACanBeGreen', 'ACanBeGreen', modelUri);
    });

    function validateWitness(argument: unknown) {
        return state.commands.get('semantifyr.witness.validate')!(argument);
    }

    function validation(status: WitnessValidationResult['status'], message: string | null = null): WitnessValidationResult {
        return { status, verdict: null, message, backendId: null, portfolioId: 'smart-full', metrics: null };
    }

    function traceDocument(): string {
        return [...state.contentProviders.values()][0]!.provideTextDocumentContent(Uri.parse('semantifyr-trace:/ACanBeGreen.md?1'));
    }

    it('validates under a cancellable progress and adds the result to the trace', async () => {
        view.record(item as never, result('failed', counterexample), async () => validation('valid'));

        await validateWitness({ test: item });

        expect(window.withProgress.mock.calls[0]![0]).toEqual({ location: 15, title: 'Validating the witness of ACanBeGreen', cancellable: true });
        expect(window.showInformationMessage).toHaveBeenCalledWith('ACanBeGreen: Witness valid (portfolio smart-full)');
        expect(traceDocument()).toContain('Validation: Witness valid (portfolio smart-full)');
    });

    it.each([
        ['invalid', 'showErrorMessage', 'ACanBeGreen: Witness invalid (portfolio smart-full) - spurious\n'],
        ['errored', 'showErrorMessage', 'ACanBeGreen: Validation errored (portfolio smart-full) - spurious\n'],
        ['inconclusive', 'showWarningMessage', 'ACanBeGreen: Validation inconclusive (portfolio smart-full) - spurious'],
        ['timeout', 'showWarningMessage', 'ACanBeGreen: Validation timed out (portfolio smart-full) - spurious'],
    ] as const)('reports a %s witness through %s', async (status, method, text) => {
        view.record(item as never, result('failed', counterexample), async () => validation(status, 'spurious'));

        await validateWitness(item);

        expect(window[method]).toHaveBeenCalledWith(text);
    });

    it('reports a validator that throws as errored', async () => {
        view.record(item as never, result('failed', counterexample), async () => {
            throw new Error('connection closed');
        });

        await validateWitness(item);

        expect(window.showErrorMessage).toHaveBeenCalledWith('ACanBeGreen: Validation errored - connection closed\n');
    });

    it('drops the validation of a witness a rerun replaced', async () => {
        view.record(item as never, result('failed', counterexample), async () => {
            view.record(item as never, result('failed', counterexample, 'again'));
            return validation('valid');
        });

        await validateWitness(item);

        expect(window.showInformationMessage).not.toHaveBeenCalled();
        expect(traceDocument()).not.toContain('Validation:');
    });

    it('says so when the case has no witness to validate', async () => {
        view.record(item as never, result('failed', counterexample));

        await validateWitness(item);

        expect(window.showInformationMessage).toHaveBeenCalledWith('No witness for ACanBeGreen.');
        expect(window.withProgress).not.toHaveBeenCalled();
    });
});

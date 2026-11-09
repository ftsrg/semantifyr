/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { type CancellationToken, commands, EventEmitter, type ExtensionContext, ProgressLocation, type TestItem, type TextDocumentContentProvider, Uri, ViewColumn, window, workspace } from "vscode";
import {
    type CallTraceStep,
    formatTraceCall,
    noWitnessMessage,
    type TraceEntry,
    traceStepLabel,
    type VerificationCaseResult,
    type VerificationTrace,
    type WitnessStateChange,
    witnessOpenFailureMessage,
    type WitnessValidationResult,
    WitnessValidationStatus,
    witnessStateChanges,
    witnessValidationLabel,
    witnessValidationStatusOf,
} from "@semantifyr/editor-common";
import { writeErrorMessage, writeToOutputChannel } from "./outputChannel.js";

export const witnessMessageContext = "semantifyr.witness";

export const validatableWitnessMessageContext = "semantifyr.witness.validatable";

const traceScheme = "semantifyr-trace";

export type WitnessValidator = (token: CancellationToken) => Promise<WitnessValidationResult | null>;

interface RecordedWitness {
    result: VerificationCaseResult;
    trace: VerificationTrace;
    traceUri: Uri;
    validator: WitnessValidator | undefined;
    validation: WitnessValidationResult | null | undefined;
}

type ShowWitnessArgument = TestItem | { test: TestItem | undefined } | undefined;

export class WitnessView implements TextDocumentContentProvider {

    private readonly witnesses = new WeakMap<TestItem, RecordedWitness>();
    private readonly documents = new Map<string, string>();
    private readonly changeEmitter = new EventEmitter<Uri>();
    private traceCount = 0;

    readonly onDidChange = this.changeEmitter.event;

    register(context: ExtensionContext) {
        context.subscriptions.push(
            this.changeEmitter,
            workspace.registerTextDocumentContentProvider(traceScheme, this),
            commands.registerCommand("semantifyr.witness.show", async (argument: ShowWitnessArgument) => {
                await this.show(testItemOf(argument));
            }),
            commands.registerCommand("semantifyr.witness.validate", async (argument: ShowWitnessArgument) => {
                await this.validate(testItemOf(argument));
            }),
        );
    }

    record(testItem: TestItem, result: VerificationCaseResult, validator?: WitnessValidator) {
        if (!result.trace) {
            this.witnesses.delete(testItem);
            return;
        }
        const traceUri = this.witnesses.get(testItem)?.traceUri ?? this.createTraceUri(testItem.label);
        const witness = { result, trace: result.trace, traceUri, validator, validation: undefined };
        this.witnesses.set(testItem, witness);
        this.render(testItem.label, witness);
    }

    private render(label: string, witness: RecordedWitness) {
        this.documents.set(witness.traceUri.toString(), renderTrace(label, witness.result, witness.trace, witness.validation));
        this.changeEmitter.fire(witness.traceUri);
    }

    private createTraceUri(label: string): Uri {
        this.traceCount += 1;
        return Uri.from({ scheme: traceScheme, path: `/${label}.md`, query: String(this.traceCount) });
    }

    provideTextDocumentContent(uri: Uri): string {
        return this.documents.get(uri.toString()) ?? "";
    }

    async show(testItem: TestItem | undefined) {
        const witness = testItem === undefined ? undefined : this.witnesses.get(testItem);
        if (testItem === undefined || witness === undefined) {
            void window.showInformationMessage(noWitnessMessage(testItem?.label ?? "this test"));
            return;
        }
        let column = ViewColumn.Beside;
        const witnessUri = witness.trace.witnessUri;
        if (witnessUri !== null) {
            try {
                const editor = await window.showTextDocument(Uri.parse(witnessUri), { viewColumn: ViewColumn.Beside, preview: false });
                await commands.executeCommand("workbench.action.files.setActiveEditorReadonlyInSession");
                column = editor.viewColumn ?? column;
            } catch (error: unknown) {
                void writeErrorMessage(`${witnessOpenFailureMessage(witnessUri, error)}\n`);
            }
        }
        const traceDocument = await workspace.openTextDocument(witness.traceUri);
        await window.showTextDocument(traceDocument, { viewColumn: column, preview: false });
    }

    async validate(testItem: TestItem | undefined) {
        const witness = testItem === undefined ? undefined : this.witnesses.get(testItem);
        const validator = witness?.validator;
        if (testItem === undefined || witness === undefined || validator === undefined) {
            void window.showInformationMessage(noWitnessMessage(testItem?.label ?? "this test"));
            return;
        }
        const validation = await window.withProgress({
            location: ProgressLocation.Notification,
            title: `Validating the witness of ${testItem.label}`,
            cancellable: true,
        }, async (_progress, token) => {
            return await this.runValidation(testItem, witness, validator, token);
        });
        if (validation === undefined) {
            return;
        }
        const status = witnessValidationStatusOf(validation);
        const text = `${testItem.label}: ${renderValidation(validation)}`;
        if (status === WitnessValidationStatus.Valid) {
            void window.showInformationMessage(text);
            writeToOutputChannel(`${text}\n`, false);
        } else if (status === WitnessValidationStatus.Invalid || status === WitnessValidationStatus.Errored) {
            void writeErrorMessage(`${text}\n`);
        } else {
            void window.showWarningMessage(text);
            writeToOutputChannel(`${text}\n`, false);
        }
    }

    async autoValidate(testItem: TestItem, token: CancellationToken): Promise<WitnessValidationResult | null | undefined> {
        const witness = this.witnesses.get(testItem);
        const validator = witness?.validator;
        if (witness === undefined || validator === undefined) {
            return undefined;
        }
        return await this.runValidation(testItem, witness, validator, token);
    }

    private async runValidation(testItem: TestItem, witness: RecordedWitness, validator: WitnessValidator, token: CancellationToken): Promise<WitnessValidationResult | null | undefined> {
        let validation: WitnessValidationResult | null;
        try {
            validation = await validator(token);
        } catch (error: unknown) {
            if (token.isCancellationRequested) {
                return undefined;
            }
            validation = erroredValidation(error);
        }
        if (this.witnesses.get(testItem) !== witness) {
            return undefined;
        }
        witness.validation = validation;
        this.render(testItem.label, witness);
        return validation;
    }
}

function testItemOf(argument: ShowWitnessArgument): TestItem | undefined {
    if (argument !== undefined && "test" in argument) {
        return argument.test;
    }
    return argument;
}

function erroredValidation(error: unknown): WitnessValidationResult {
    return {
        status: WitnessValidationStatus.Errored,
        verdict: null,
        message: error instanceof Error ? error.message : String(error),
        backendId: null,
        portfolioId: null,
        metrics: null,
    };
}

export function renderValidation(validation: WitnessValidationResult | null): string {
    const label = witnessValidationLabel(witnessValidationStatusOf(validation));
    if (validation === null) {
        return label;
    }
    const message = validation.message ? ` - ${validation.message}` : "";
    return `${label}${renderOrigin(validation)}${message}`;
}

export function renderTrace(label: string, result: VerificationCaseResult, trace: VerificationTrace, validation?: WitnessValidationResult | null): string {
    const message = result.message ? ` - ${result.message}` : "";
    const lines = [`# ${label}`, "", `Verdict: ${result.status}${renderOrigin(result)}${message}`];
    if (validation !== undefined) {
        lines.push("", `Validation: ${renderValidation(validation)}`);
    }
    if (trace.witnessUri !== null) {
        lines.push("", `${witnessLabel(trace.witnessUri)}: ${trace.witnessUri}`);
    }

    const callSteps = [trace.callTrace.initializedStep, ...trace.callTrace.steps];
    const stateChanges = witnessStateChanges(trace.witnessState);
    for (let index = 0; index < Math.max(callSteps.length, stateChanges.length); index++) {
        const calls = callSteps[index];
        lines.push("", `## ${traceStepLabel(index)}`, "", ...renderCalls(calls), "", ...renderChanges(index === 0, stateChanges[index] ?? []));
    }
    return `${lines.join("\n")}\n`;
}

function witnessLabel(witnessUri: string): string {
    return witnessUri.endsWith(".gamma") ? "Gamma trace" : "Witness";
}

function renderOrigin(result: { backendId: string | null; portfolioId: string | null }): string {
    const origin = [
        result.backendId === null ? undefined : `backend ${result.backendId}`,
        result.portfolioId === null ? undefined : `portfolio ${result.portfolioId}`,
    ].filter((part) => part !== undefined);
    return origin.length === 0 ? "" : ` (${origin.join(", ")})`;
}

function renderCalls(step: CallTraceStep | null | undefined): string[] {
    const traces = step?.traces ?? [];
    if (traces.length === 0) {
        return ["No calls."];
    }
    return ["Calls:", "", ...traces.flatMap((entry) => renderCall(entry, ""))];
}

function renderCall(entry: TraceEntry, indent: string): string[] {
    const inner = (entry.innerTraces ?? []).flatMap((child) => renderCall(child, `${indent}  `));
    return [`${indent}- \`${formatTraceCall(entry)}\``, ...inner];
}

function renderChanges(initial: boolean, changes: WitnessStateChange[]): string[] {
    if (changes.length === 0) {
        return [initial ? "No state." : "No changes."];
    }
    return [
        initial ? "State:" : "Changes:",
        "",
        "| Variable | Value |",
        "| --- | --- |",
        ...changes.map((change) => `| \`${escapeCell(change.variable)}\` | \`${escapeCell(change.value)}\` |`),
    ];
}

function escapeCell(text: string): string {
    return text.replaceAll("|", "\\|");
}

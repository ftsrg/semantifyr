/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { type CancellationToken, commands, EventEmitter, type ExtensionContext, ProgressLocation, type TestItem, type TextDocumentContentProvider, Uri, ViewColumn, window, workspace } from "vscode";
import {
    caseOutcomeOf,
    formatSeconds,
    formatTraceCall,
    isoDurationToMs,
    noWitnessMessage,
    type ReportCall,
    soundnessLabel,
    traceStates,
    traceStepLabel,
    type TraceValue,
    type VerificationCaseResult,
    witnessOpenFailureMessage,
    type WitnessValidationResult,
    WitnessValidationStatus,
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
        if (!result.report?.trace) {
            this.witnesses.delete(testItem);
            return;
        }
        const traceUri = this.witnesses.get(testItem)?.traceUri ?? this.createTraceUri(testItem.label);
        const witness = { result, traceUri, validator, validation: undefined };
        this.witnesses.set(testItem, witness);
        this.render(testItem.label, witness);
    }

    private render(label: string, witness: RecordedWitness) {
        this.documents.set(witness.traceUri.toString(), renderTrace(label, witness.result, witness.validation));
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
        const openedUri = witness.result.traceUri ?? witness.result.witnessUri;
        if (openedUri !== null) {
            try {
                const editor = await window.showTextDocument(Uri.parse(openedUri), { viewColumn: ViewColumn.Beside, preview: false });
                await commands.executeCommand("workbench.action.files.setActiveEditorReadonlyInSession");
                column = editor.viewColumn ?? column;
            } catch (error: unknown) {
                void writeErrorMessage(`${witnessOpenFailureMessage(openedUri, error)}\n`);
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
        message: error instanceof Error ? error.message : String(error),
        portfolioId: null,
        report: null,
    };
}

export function renderValidation(validation: WitnessValidationResult | null): string {
    const label = witnessValidationLabel(witnessValidationStatusOf(validation));
    if (validation === null) {
        return label;
    }
    const message = validation.message ? ` - ${validation.message}` : "";
    return `${label}${renderValidationOrigin(validation)}${message}`;
}

function renderValidationOrigin(validation: WitnessValidationResult): string {
    const result = validation.report?.result;
    if (result === undefined) {
        return renderOrigin({ backendId: null, portfolioId: validation.portfolioId });
    }
    return renderOrigin(
        { backendId: result.metadata.backendId, portfolioId: validation.portfolioId },
        [
            formatSeconds(isoDurationToMs(result.metrics.totalDuration)),
            soundnessLabel(result.soundness),
        ],
    );
}

const TRACE_LABELS: Readonly<Record<string, string>> = {
    ".get": "Gamma trace",
    ".sysml": "SysML trace",
};

function traceLabelOf(traceUri: string): string {
    const extension = traceUri.slice(traceUri.lastIndexOf("."));
    return TRACE_LABELS[extension] ?? "Trace";
}

export function renderTrace(label: string, result: VerificationCaseResult, validation?: WitnessValidationResult | null): string {
    const outcome = caseOutcomeOf(result);
    const message = outcome.message ? ` - ${outcome.message}` : "";
    const report = result.report;
    const soundness = report === null ? [] : [soundnessLabel(report.result.soundness)];
    const origin = renderOrigin({ backendId: report?.result.metadata.backendId ?? null, portfolioId: outcome.portfolioId }, soundness);
    const lines = [`# ${label}`, "", `Verdict: ${outcome.status}${origin}${message}`];
    if (validation !== undefined) {
        lines.push("", `Validation: ${renderValidation(validation)}`);
    }
    if (result.traceUri !== null) {
        lines.push("", `${traceLabelOf(result.traceUri)}: ${result.traceUri}`);
    }
    if (result.witnessUri !== null) {
        lines.push("", `Witness: ${result.witnessUri}`);
    }
    const trace = report?.trace;
    if (!trace) {
        return `${lines.join("\n")}\n`;
    }

    const states = traceStates(trace);
    lines.push("", `## ${traceStepLabel(0)}`, "", ...renderCalls(trace.initial.calls), "", ...renderStart(states.start));
    trace.steps.forEach((step, index) => {
        const state = states.steps[index];
        const elapsed = state?.elapsed ?? null;
        const time = elapsed === null ? [] : ["", `Elapsed: ${elapsed}`];
        const changes = (state?.variables ?? []).filter((variable) => {
            return variable.changed;
        });
        lines.push("", `## ${traceStepLabel(index + 1)}`, ...time, "", ...renderCalls(step.calls), "", ...renderChanges(changes));
    });
    return `${lines.join("\n")}\n`;
}

function renderOrigin(result: { backendId: string | null; portfolioId: string | null }, facts: readonly string[] = []): string {
    const origin = [
        result.backendId === null ? undefined : `backend ${result.backendId}`,
        result.portfolioId === null ? undefined : `portfolio ${result.portfolioId}`,
        ...facts,
    ].filter((part) => part !== undefined);
    return origin.length === 0 ? "" : ` (${origin.join(", ")})`;
}

function renderCalls(calls: readonly ReportCall[]): string[] {
    if (calls.length === 0) {
        return ["No calls."];
    }
    return ["Calls:", "", ...calls.flatMap((call) => renderCall(call, ""))];
}

function renderCall(call: ReportCall, indent: string): string[] {
    const inner = call.calls.flatMap((child) => renderCall(child, `${indent}  `));
    return [`${indent}- \`${formatTraceCall(call)}\``, ...inner];
}

function renderStart(values: readonly TraceValue[]): string[] {
    if (values.length === 0) {
        return ["No state."];
    }
    return ["State:", "", ...renderTable(values)];
}

function renderChanges(changes: readonly TraceValue[]): string[] {
    if (changes.length === 0) {
        return ["No changes."];
    }
    return ["Changes:", "", ...renderTable(changes)];
}

function renderTable(values: readonly TraceValue[]): string[] {
    return [
        "| Variable | Value |",
        "| --- | --- |",
        ...values.map((value) => `| \`${escapeCell(value.variable)}\` | \`${escapeCell(value.value)}\` |`),
    ];
}

function escapeCell(text: string): string {
    return text.replaceAll("|", "\\|");
}

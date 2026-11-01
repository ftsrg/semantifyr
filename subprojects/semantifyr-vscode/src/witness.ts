/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { commands, EventEmitter, type ExtensionContext, type TestItem, type TextDocumentContentProvider, Uri, ViewColumn, window, workspace } from "vscode";
import type { CallTraceStep, TraceEntry, VerificationCaseResult, VerificationTrace, WitnessStateStep } from "@semantifyr/editor-common";
import { writeErrorMessage } from "./outputChannel.js";

export const witnessMessageContext = "semantifyr.witness";

const traceScheme = "semantifyr-trace";

interface RecordedWitness {
    trace: VerificationTrace;
    traceUri: Uri;
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
                await this.show(argument !== undefined && "test" in argument ? argument.test : argument);
            }),
        );
    }

    record(testItem: TestItem, result: VerificationCaseResult) {
        if (!result.trace) {
            this.witnesses.delete(testItem);
            return;
        }
        const traceUri = this.witnesses.get(testItem)?.traceUri ?? this.createTraceUri(testItem.label);
        this.witnesses.set(testItem, { trace: result.trace, traceUri });
        this.documents.set(traceUri.toString(), renderTrace(testItem.label, result, result.trace));
        this.changeEmitter.fire(traceUri);
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
            void window.showInformationMessage(`No witness recorded for ${testItem?.label ?? "this test"}.`);
            return;
        }
        let column = ViewColumn.Beside;
        const witnessUri = witness.trace.witnessUri;
        if (witnessUri !== null) {
            try {
                const editor = await window.showTextDocument(Uri.parse(witnessUri), { viewColumn: ViewColumn.Beside, preview: false });
                await commands.executeCommand("workbench.action.files.setActiveEditorReadonlyInSession");
                column = editor.viewColumn ?? column;
            } catch (e: unknown) {
                void writeErrorMessage(`Cannot open the witness ${witnessUri}: ${e instanceof Error ? e.message : String(e)}\n`);
            }
        }
        const traceDocument = await workspace.openTextDocument(witness.traceUri);
        await window.showTextDocument(traceDocument, { viewColumn: column, preview: false });
    }
}

export function renderTrace(label: string, result: VerificationCaseResult, trace: VerificationTrace): string {
    const message = result.message ? ` - ${result.message}` : "";
    const lines = [`# ${label}`, "", `Verdict: ${result.status}${renderOrigin(result)}${message}`];
    if (trace.witnessUri !== null) {
        lines.push("", `${witnessLabel(trace.witnessUri)}: ${trace.witnessUri}`);
    }

    const callSteps = [trace.callTrace.initialStep, trace.callTrace.initializedStep, ...trace.callTrace.steps];
    const stateSteps = [trace.witnessState.initialStep, trace.witnessState.initializedStep, ...trace.witnessState.steps];
    const state = new Map<string, string>();
    for (let index = 0; index < Math.max(callSteps.length, stateSteps.length); index++) {
        const calls = callSteps[index];
        const values = stateSteps[index];
        if (index === 1 && !calls && !values) {
            continue;
        }
        lines.push("", `## ${stepLabel(index)}`, "", ...renderCalls(calls), "", ...renderChanges(index === 0, values, state));
    }
    return `${lines.join("\n")}\n`;
}

function witnessLabel(witnessUri: string): string {
    return witnessUri.endsWith(".gamma") ? "Gamma trace" : "Witness";
}

function renderOrigin(result: VerificationCaseResult): string {
    const origin = [
        result.backendId === null ? undefined : `backend ${result.backendId}`,
        result.portfolioId === null ? undefined : `portfolio ${result.portfolioId}`,
    ].filter((part) => part !== undefined);
    return origin.length === 0 ? "" : ` (${origin.join(", ")})`;
}

function stepLabel(index: number): string {
    if (index === 0) {
        return "Initial";
    }
    if (index === 1) {
        return "After init";
    }
    return `Step ${index - 1}`;
}

function renderCalls(step: CallTraceStep | null | undefined): string[] {
    const traces = step?.traces ?? [];
    if (traces.length === 0) {
        return ["No calls."];
    }
    return ["Calls:", "", ...traces.flatMap((entry) => renderCall(entry, ""))];
}

function renderCall(entry: TraceEntry, indent: string): string[] {
    const argumentList = entry.arguments.map((argument) => `${argument.parameter}=${argument.value}`).join(", ");
    const inner = (entry.innerTraces ?? []).flatMap((child) => renderCall(child, `${indent}  `));
    return [`${indent}- \`${entry.self}.${entry.calledTransition}(${argumentList})\``, ...inner];
}

function renderChanges(initial: boolean, step: WitnessStateStep | null | undefined, state: Map<string, string>): string[] {
    const changes = (step?.values ?? []).filter((value) => state.get(value.variable) !== value.value);
    for (const change of changes) {
        state.set(change.variable, change.value);
    }
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

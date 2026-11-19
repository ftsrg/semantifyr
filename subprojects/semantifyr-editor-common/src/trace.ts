/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type { ReportCall, ReportState, ReportStep, ReportTrace, ReportValue } from "./wire.ts";

const CLOCK_TYPE = "semantifyr::clock";

export interface TraceValue {
    variable: string;
    value: string;
}

export interface TraceVariable {
    variable: string;
    value: string;
    previous: string | undefined;
    changed: boolean;
}

export interface TraceStep {
    elapsed: string | null;
    variables: TraceVariable[];
}

export interface TraceStates {
    start: TraceValue[];
    steps: TraceStep[];
}

export interface TraceArgument {
    parameter: string;
    value: string;
}

export interface TraceCall {
    self: string;
    transition: string;
    arguments: TraceArgument[];
    // the index of the call that encloses this one in its step, null for the call the environment makes
    enclosing: number | null;
    depth: number;
}

// the calls of one step, each before the calls it encloses, the first one the environment's call on the root instance
export interface TraceCallStep {
    calls: TraceCall[];
    elapsed: string | null;
}

export interface TraceCalls {
    initialized: TraceCallStep;
    steps: TraceCallStep[];
    // the root instance, then every other instance a call is made on, in the order of its first call
    lifelines: string[];
}

export function formatReportValue(value: ReportValue): string {
    if (value === null) {
        return "null";
    }
    if (Array.isArray(value)) {
        return `[${value.map((element) => {
            return formatReportValue(element);
        }).join(", ")}]`;
    }
    if (typeof value === "object") {
        return `{${Object.entries(value).map(([field, fieldValue]) => {
            return `${field}: ${formatReportValue(fieldValue)}`;
        }).join(", ")}}`;
    }
    return String(value);
}

function valuesOf(state: ReportState | undefined): TraceValue[] {
    return Object.entries(state?.values ?? {}).map(([variable, value]) => {
        return { variable, value: formatReportValue(value) };
    });
}

function isTimed(trace: ReportTrace): boolean {
    return trace.steps.some((step) => {
        return step.elapsed !== 0;
    }) || trace.variables.some((variable) => {
        return variable.type.name === CLOCK_TYPE;
    });
}

function elapsedOf(trace: ReportTrace): (step: ReportStep) => string | null {
    const timed = isTimed(trace);
    return (step) => {
        return timed ? String(step.elapsed) : null;
    };
}

export function traceStates(trace: ReportTrace): TraceStates {
    const elapsed = elapsedOf(trace);
    const steps = trace.steps.map((step) => {
        const previous = new Map(valuesOf(trace.states[step.source]).map((entry) => {
            return [entry.variable, entry.value];
        }));
        const variables = valuesOf(trace.states[step.target]).map((entry) => {
            const before = previous.get(entry.variable);
            return { ...entry, previous: before, changed: before !== undefined && before !== entry.value };
        });
        return { elapsed: elapsed(step), variables };
    });
    return { start: valuesOf(trace.states[trace.initial.state]), steps };
}

function argumentsOf(call: ReportCall): TraceArgument[] {
    return Object.entries(call.arguments).map(([parameter, value]) => {
        return { parameter, value: formatReportValue(value) };
    });
}

function flatten(entries: readonly ReportCall[], enclosing: number | null, depth: number, calls: TraceCall[]): void {
    for (const entry of entries) {
        const index = calls.length;
        calls.push({ self: entry.self, transition: entry.transition, arguments: argumentsOf(entry), enclosing, depth });
        flatten(entry.calls, index, depth + 1, calls);
    }
}

function callStepOf(root: string, transition: string, entries: readonly ReportCall[], elapsed: string | null): TraceCallStep {
    const calls: TraceCall[] = [{ self: root, transition, arguments: [], enclosing: null, depth: 0 }];
    flatten(entries, 0, 1, calls);
    return { calls, elapsed };
}

// the environment runs init once and main for every step after it, on the root instance; nothing recorded leaves nothing to show
export function traceCalls(trace: ReportTrace): TraceCalls {
    const recorded = [trace.initial.calls, ...trace.steps.map((step) => {
        return step.calls;
    })].some((calls) => {
        return calls.length > 0;
    });
    if (!recorded) {
        return { initialized: { calls: [], elapsed: null }, steps: [], lifelines: [] };
    }
    const elapsed = elapsedOf(trace);
    const initialized = callStepOf(trace.root, "init", trace.initial.calls, null);
    const steps = trace.steps.map((step) => {
        return callStepOf(trace.root, "main", step.calls, elapsed(step));
    });
    const lifelines = new Set<string>([trace.root]);
    for (const step of [initialized, ...steps]) {
        for (const call of step.calls) {
            lifelines.add(call.self);
        }
    }
    return { initialized, steps, lifelines: [...lifelines] };
}

export function lastNameSegment(qualifiedName: string): string {
    const separator = qualifiedName.lastIndexOf("::");
    return separator < 0 ? qualifiedName : qualifiedName.slice(separator + 2);
}

// keeps the last segments of an instance path that fit the length, at least the last one
export function shortenPath(path: string, maximumLength: number): string {
    const segments = path.split(".");
    let kept = segments.pop() ?? path;
    while (segments.length > 0 && kept.length + (segments.at(-1)?.length ?? 0) + 2 <= maximumLength) {
        kept = `${segments.pop() ?? ""}.${kept}`;
    }
    return segments.length > 0 ? `…${kept}` : kept;
}

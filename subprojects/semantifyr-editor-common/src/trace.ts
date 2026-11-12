/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type { CallTrace, CallTraceStep, TraceArgument, TraceEntry, WitnessState, WitnessStateValue } from "./wire.ts";
import { witnessStateChanges, type WitnessStateChange } from "./witness.ts";

export interface TraceVariable {
    variable: string;
    value: string;
    changed: boolean;
}

export interface TraceStateStep {
    changes: WitnessStateChange[];
    // every variable known at this step, a value the step leaves out kept from before
    variables: TraceVariable[];
}

export interface TraceStates {
    start: WitnessStateValue[];
    steps: TraceStateStep[];
}

export interface TraceCall {
    self: string;
    calledTransition: string;
    arguments: TraceArgument[];
    // the index of the call that encloses this one in its step, null for the call the environment makes
    enclosing: number | null;
    depth: number;
}

// the calls of one step, each before the calls it encloses, the first one the environment's call on the root instance
export interface TraceCallStep {
    calls: TraceCall[];
}

export interface TraceCalls {
    initialized: TraceCallStep;
    steps: TraceCallStep[];
    // the root instance, then every other instance a call is made on, in the order of its first call
    lifelines: string[];
}

export function traceStates(state: WitnessState): TraceStates {
    const [, ...stepChanges] = witnessStateChanges(state);
    const current = new Map<string, string>();
    for (const value of state.initializedStep.values) {
        current.set(value.variable, value.value);
    }
    const steps = stepChanges.map((changes) => {
        for (const change of changes) {
            current.set(change.variable, change.value);
        }
        const changed = new Set(changes.map((change) => {
            return change.variable;
        }));
        const variables = [...current].map(([variable, value]) => {
            return { variable, value, changed: changed.has(variable) };
        });
        return { changes, variables };
    });
    return { start: state.initializedStep.values, steps };
}

function flatten(entries: readonly TraceEntry[], enclosing: number | null, depth: number, calls: TraceCall[]): void {
    for (const entry of entries) {
        const index = calls.length;
        calls.push({ self: entry.self, calledTransition: entry.calledTransition, arguments: entry.arguments, enclosing, depth });
        flatten(entry.innerTraces ?? [], index, depth + 1, calls);
    }
}

function rootOf(steps: readonly CallTraceStep[]): string | null {
    for (const step of steps) {
        const first = step.traces[0];
        if (first) {
            return first.self.split(".")[0] ?? first.self;
        }
    }
    return null;
}

function callStepOf(root: string, transition: string, entries: readonly TraceEntry[]): TraceCallStep {
    const calls: TraceCall[] = [{ self: root, calledTransition: transition, arguments: [], enclosing: null, depth: 0 }];
    flatten(entries, 0, 1, calls);
    return { calls };
}

// the environment runs init once and main for every step after it, on the root instance; nothing recorded leaves nothing to show
export function traceCalls(trace: CallTrace): TraceCalls {
    const root = rootOf([trace.initializedStep, ...trace.steps]);
    if (root === null) {
        return { initialized: { calls: [] }, steps: [], lifelines: [] };
    }
    const initialized = callStepOf(root, "init", trace.initializedStep.traces);
    const steps = trace.steps.map((step) => {
        return callStepOf(root, "main", step.traces);
    });
    const lifelines = new Set<string>([root]);
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

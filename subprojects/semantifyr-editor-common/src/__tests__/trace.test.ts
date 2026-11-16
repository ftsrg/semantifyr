/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { describe, expect, it } from "vitest";
import { formatReportValue, lastNameSegment, resultTraceOf, shortenPath, traceCalls, traceStates } from "../trace.ts";
import type { ReportCall, ReportTrace, VerificationReport } from "../wire.ts";

function call(self: string, transition: string, calls: ReportCall[] = []): ReportCall {
    return { self, transition, arguments: {}, calls };
}

function trace(overrides: Partial<ReportTrace>): ReportTrace {
    return {
        root: "root",
        transitions: [],
        variables: [],
        initial: { state: 0, calls: [] },
        states: [{ values: {} }],
        steps: [],
        missingCalls: null,
        ...overrides,
    };
}

describe("traceStates", () => {
    const lamp = trace({
        states: [
            { values: { "root.lamp.on": false, "root.lamp.level": 0 } },
            { values: { "root.lamp.on": true, "root.lamp.level": 0 } },
            { values: { "root.lamp.on": true } },
        ],
        steps: [
            { source: 0, target: 1, elapsed: 0, calls: [] },
            { source: 1, target: 2, elapsed: 0, calls: [] },
            { source: 2, target: 0, elapsed: 0, calls: [] },
        ],
    });

    it("starts at the initial state and gives each step the values its target holds, those its source held otherwise marked as changes", () => {
        const states = traceStates(lamp);

        expect(states.start).toEqual([{ variable: "root.lamp.on", value: "false" }, { variable: "root.lamp.level", value: "0" }]);
        expect(states.steps[0]).toEqual({
            elapsed: "0",
            changes: [{ variable: "root.lamp.on", value: "true", previous: "false" }],
            variables: [{ variable: "root.lamp.on", value: "true", changed: true }, { variable: "root.lamp.level", value: "0", changed: false }],
        });
    });

    it("claims nothing for a value the target state does not hold", () => {
        expect(traceStates(lamp).steps[1]).toEqual({
            elapsed: "0",
            changes: [],
            variables: [{ variable: "root.lamp.on", value: "true", changed: false }],
        });
    });

    it("compares a step back to an earlier state with its own source", () => {
        expect(traceStates(lamp).steps[2]?.changes).toEqual([
            { variable: "root.lamp.on", value: "false", previous: "true" },
            { variable: "root.lamp.level", value: "0", previous: undefined },
        ]);
    });

    it("shows elapsed time for a model that keeps a clock or lets time pass, and none for one that does neither", () => {
        const waited = trace({ states: [{ values: {} }, { values: {} }], steps: [{ source: 0, target: 1, elapsed: "3/2", calls: [] }] });
        const clocked = trace({ variables: [{ variable: "root.timer", type: { kind: "datatype", name: "semantifyr::clock", element: null, size: null }, slot: null }] });

        expect(traceStates(waited)).toMatchObject({ timed: true, steps: [{ elapsed: "3/2" }] });
        expect(traceStates(clocked).timed).toBe(true);
        expect(traceStates(lamp).timed).toBe(false);
    });
});

describe("formatReportValue", () => {
    it("writes every JSON value of a report", () => {
        expect([true, 3, "Red", null, "9007199254740993", [1, 2], { x: 1, inner: { on: false } }].map((value) => {
            return formatReportValue(value);
        })).toEqual(["true", "3", "Red", "null", "9007199254740993", "[1, 2]", "{x: 1, inner: {on: false}}"]);
    });
});

describe("traceCalls", () => {
    const calls = trace({
        root: "system",
        states: [{ values: {} }, { values: {} }, { values: {} }],
        steps: [
            {
                source: 0,
                target: 1,
                elapsed: 0,
                calls: [
                    { self: "system.track.transition", transition: "Transition::fire", arguments: { item: { speed: 2 } }, calls: [call("system.track.port", "Port::send")] },
                ],
            },
            { source: 1, target: 2, elapsed: 0, calls: [] },
        ],
    });

    it("starts every step with the environment's call on the report's root, init first and main after it, which encloses the recorded calls", () => {
        const result = traceCalls(calls);

        expect(result.initialized.calls.map((entry) => {
            return [entry.self, entry.transition, entry.enclosing, entry.depth];
        })).toEqual([
            ["system", "init", null, 0],
        ]);
        expect(result.steps.map((step) => {
            return step.calls.map((entry) => {
                return [entry.self, entry.transition, entry.enclosing, entry.depth];
            });
        })).toEqual([
            [
                ["system", "main", null, 0],
                ["system.track.transition", "Transition::fire", 0, 1],
                ["system.track.port", "Port::send", 1, 2],
            ],
            [
                ["system", "main", null, 0],
            ],
        ]);
    });

    it("gives a call its argument values", () => {
        expect(traceCalls(calls).steps[0]?.calls[1]?.arguments).toEqual([{ parameter: "item", value: "{speed: 2}" }]);
    });

    it("has a lifeline for the root instance, then one per instance in the order of its first call", () => {
        expect(traceCalls(calls).lifelines).toEqual(["system", "system.track.transition", "system.track.port"]);
    });

    it("has no lifeline and no step when no call was recorded", () => {
        const result = traceCalls(trace({ states: [{ values: {} }, { values: {} }], steps: [{ source: 0, target: 1, elapsed: 0, calls: [] }] }));

        expect(result.lifelines).toEqual([]);
        expect(result.steps).toEqual([]);
    });
});

describe("resultTraceOf", () => {
    const report = { kind: "run", trace: trace({}) } as VerificationReport;

    it("pairs the report's trace with the file its text was written to", () => {
        expect(resultTraceOf({ status: "failed", message: null, backendId: null, portfolioId: null, metrics: null, report, witnessUri: "file:///workspace/witness.semantifyr" }))
            .toMatchObject({ root: "root", witnessUri: "file:///workspace/witness.semantifyr" });
    });

    it("has no trace for a result without a report or a report without a trace", () => {
        expect(resultTraceOf({ status: "errored", message: null, backendId: null, portfolioId: null, metrics: null, report: null, witnessUri: null })).toBeNull();
        expect(resultTraceOf({ status: "passed", message: null, backendId: null, portfolioId: null, metrics: null, report: { ...report, trace: null }, witnessUri: null })).toBeNull();
    });
});

describe("shortenPath", () => {
    it("keeps the last segments that fit and marks the cut", () => {
        expect(shortenPath("root.system.trackA.Behaviour.activeState", 30)).toBe("…trackA.Behaviour.activeState");
        expect(shortenPath("root.system", 30)).toBe("root.system");
    });

    it("keeps the last segment however long it is", () => {
        expect(shortenPath("root.UnnamedTransitionUsage12", 10)).toBe("…UnnamedTransitionUsage12");
    });
});

describe("lastNameSegment", () => {
    it("names a qualified name by its last segment", () => {
        expect(lastNameSegment("semantifyr::sysml::states::Transition::fire")).toBe("fire");
        expect(lastNameSegment("fire")).toBe("fire");
    });
});

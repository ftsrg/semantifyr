/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { describe, expect, it } from "vitest";
import { lastNameSegment, shortenPath, traceCalls, traceStates } from "../trace.ts";
import type { TraceEntry } from "../wire.ts";

function entry(self: string, calledTransition: string, innerTraces: TraceEntry[] | null = null): TraceEntry {
    return { self, calledTransition, arguments: [], innerTraces };
}

describe("traceStates", () => {
    it("gives each step its changes with the previous values and every variable known at it", () => {
        const states = traceStates({
            initializedStep: { values: [{ variable: "root.lamp.on", value: "false" }, { variable: "root.lamp.level", value: "0" }] },
            steps: [
                { values: [{ variable: "root.lamp.on", value: "true" }] },
                { values: [{ variable: "root.lamp.on", value: "true" }, { variable: "root.lamp.level", value: "2" }] },
            ],
        });

        expect(states.start).toEqual([{ variable: "root.lamp.on", value: "false" }, { variable: "root.lamp.level", value: "0" }]);
        expect(states.steps).toEqual([
            {
                changes: [{ variable: "root.lamp.on", value: "true", previous: "false" }],
                variables: [{ variable: "root.lamp.on", value: "true", changed: true }, { variable: "root.lamp.level", value: "0", changed: false }],
            },
            {
                changes: [{ variable: "root.lamp.level", value: "2", previous: "0" }],
                variables: [{ variable: "root.lamp.on", value: "true", changed: false }, { variable: "root.lamp.level", value: "2", changed: true }],
            },
        ]);
    });
});

describe("traceCalls", () => {
    const trace = {
        initializedStep: { traces: [] },
        steps: [
            {
                traces: [
                    entry("root.track.transition", "Transition::fire", [entry("root.track.port", "Port::send")]),
                    entry("root", "Case::passTime"),
                ],
            },
            { traces: [] },
        ],
    };

    it("starts every step with the environment's call on the root instance, init first and main after it, which encloses the recorded calls", () => {
        const calls = traceCalls(trace);

        expect(calls.initialized.calls.map((call) => {
            return [call.self, call.calledTransition, call.enclosing, call.depth];
        })).toEqual([
            ["root", "init", null, 0],
        ]);
        expect(calls.steps.map((step) => {
            return step.calls.map((call) => {
                return [call.self, call.calledTransition, call.enclosing, call.depth];
            });
        })).toEqual([
            [
                ["root", "main", null, 0],
                ["root.track.transition", "Transition::fire", 0, 1],
                ["root.track.port", "Port::send", 1, 2],
                ["root", "Case::passTime", 0, 1],
            ],
            [
                ["root", "main", null, 0],
            ],
        ]);
    });

    it("has a lifeline for the root instance, then one per instance in the order of its first call", () => {
        expect(traceCalls(trace).lifelines).toEqual(["root", "root.track.transition", "root.track.port"]);
    });

    it("has no lifeline and no step when no call was recorded", () => {
        const calls = traceCalls({ initializedStep: { traces: [] }, steps: [{ traces: [] }] });

        expect(calls.lifelines).toEqual([]);
        expect(calls.steps).toEqual([]);
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

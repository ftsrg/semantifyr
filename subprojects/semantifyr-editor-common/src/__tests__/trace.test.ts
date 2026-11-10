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
    it("lists the calls of a step each before the calls it encloses, and a lifeline per instance in the order of its first call", () => {
        const calls = traceCalls({
            initializedStep: { traces: [] },
            steps: [
                {
                    traces: [
                        entry("root.track.transition", "Transition::fire", [entry("root.track.port", "Port::send")]),
                        entry("root", "Case::passTime"),
                    ],
                },
                { traces: [entry("root.track.port", "Port::send")] },
            ],
        });

        expect(calls.steps[0]?.calls.map((call) => {
            return [call.self, call.enclosing, call.depth];
        })).toEqual([
            ["root.track.transition", null, 0],
            ["root.track.port", 0, 1],
            ["root", null, 0],
        ]);
        expect(calls.lifelines).toEqual(["root.track.transition", "root.track.port", "root"]);
    });

    it("has no lifeline when no call was recorded", () => {
        expect(traceCalls({ initializedStep: { traces: [] }, steps: [{ traces: [] }] }).lifelines).toEqual([]);
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

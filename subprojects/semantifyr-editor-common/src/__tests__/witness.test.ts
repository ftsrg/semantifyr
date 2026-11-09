/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { describe, expect, it } from "vitest";
import {
    formatTraceCall,
    noWitnessMessage,
    traceStepLabel,
    witnessOpenFailureMessage,
    witnessStateChanges,
    witnessValidationLabel,
    witnessValidationStatusOf,
} from "../witness.ts";
import { WitnessValidationStatus } from "../wire.ts";

describe("witnessStateChanges", () => {
    it("lists every initialized value and then the values each step changes", () => {
        const changes = witnessStateChanges({
            initializedStep: { values: [{ variable: "on", value: "false" }, { variable: "brightness", value: "1" }] },
            steps: [
                { values: [{ variable: "on", value: "true" }, { variable: "brightness", value: "1" }] },
                { values: [{ variable: "on", value: "true" }, { variable: "brightness", value: "1" }] },
            ],
        });

        expect(changes).toEqual([
            [{ variable: "on", value: "false", previous: undefined }, { variable: "brightness", value: "1", previous: undefined }],
            [{ variable: "on", value: "true", previous: "false" }],
            [],
        ]);
    });

    it("keeps the value of a variable a step leaves out", () => {
        const changes = witnessStateChanges({
            initializedStep: { values: [{ variable: "counter", value: "0" }, { variable: "limit", value: "0" }] },
            steps: [
                { values: [{ variable: "counter", value: "1" }] },
                { values: [{ variable: "counter", value: "1" }, { variable: "limit", value: "0" }] },
            ],
        });

        expect(changes[2]).toEqual([]);
    });
});

describe("formatTraceCall", () => {
    it("names the instance, the transition and the arguments", () => {
        expect(formatTraceCall({
            self: "root.lamp",
            calledTransition: "toggle",
            arguments: [{ parameter: "brightness", value: "2" }, { parameter: "on", value: "true" }],
            innerTraces: null,
        })).toBe("root.lamp.toggle(brightness=2, on=true)");
    });
});

describe("traceStepLabel", () => {
    it("labels the first step Init and counts the rest from one", () => {
        expect([0, 1, 2].map((index) => {
            return traceStepLabel(index);
        })).toEqual(["Init", "Step 1", "Step 2"]);
    });
});

describe("witness messages", () => {
    it("names the case without a witness", () => {
        expect(noWitnessMessage("Safe")).toBe("No witness for Safe.");
    });

    it("names the witness that does not open and why", () => {
        expect(witnessOpenFailureMessage("file:///tmp/witness.oxsts", new Error("file not found"))).toBe("Cannot open the witness file:///tmp/witness.oxsts: file not found");
        expect(witnessOpenFailureMessage("file:///tmp/witness.oxsts", "closed")).toBe("Cannot open the witness file:///tmp/witness.oxsts: closed");
    });
});

describe("witnessValidationStatusOf", () => {
    it.each([
        WitnessValidationStatus.Valid,
        WitnessValidationStatus.Invalid,
        WitnessValidationStatus.Inconclusive,
        WitnessValidationStatus.Timeout,
        WitnessValidationStatus.Cancelled,
        WitnessValidationStatus.Errored,
    ])("keeps the status %s", (status) => {
        expect(witnessValidationStatusOf({ status, verdict: null, message: null, backendId: null, portfolioId: null, metrics: null })).toBe(status);
    });

    it("treats no result and an unknown status as errored", () => {
        expect(witnessValidationStatusOf(null)).toBe(WitnessValidationStatus.Errored);
        expect(witnessValidationStatusOf({ status: "mystery", verdict: null, message: null, backendId: null, portfolioId: null, metrics: null } as never)).toBe(WitnessValidationStatus.Errored);
    });
});

describe("witnessValidationLabel", () => {
    it("labels every status", () => {
        expect(Object.values(WitnessValidationStatus).map((status) => {
            return witnessValidationLabel(status);
        })).toEqual([
            "Witness valid",
            "Witness invalid",
            "Validation inconclusive",
            "Validation errored",
            "Validation timed out",
            "Validation cancelled",
        ]);
    });
});

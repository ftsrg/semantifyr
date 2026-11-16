/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { describe, expect, it } from "vitest";
import {
    formatTraceCall,
    missingCallsMessage,
    noWitnessMessage,
    reportKindLabel,
    traceStepLabel,
    witnessOpenFailureMessage,
    witnessValidationLabel,
    witnessValidationStatusOf,
} from "../witness.ts";
import { ReportKind, WitnessValidationStatus } from "../wire.ts";

describe("formatTraceCall", () => {
    it("names the instance, the transition and the argument values", () => {
        expect(formatTraceCall({
            self: "root.lamp",
            transition: "example::Lamp::toggle",
            arguments: { brightness: 2, on: true, partner: null },
            calls: [],
        })).toBe("root.lamp.example::Lamp::toggle(brightness=2, on=true, partner=null)");
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

    it("says why the calls are missing", () => {
        expect(missingCallsMessage("the backend gave no trace variables")).toBe("The calls of this trace could not be obtained: the backend gave no trace variables");
    });

    it("tells a validation from a run", () => {
        expect(reportKindLabel(ReportKind.Run)).toBe("Run");
        expect(reportKindLabel(ReportKind.Recheck)).toBe("Validation");
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
        expect(witnessValidationStatusOf({ status, verdict: null, message: null, portfolioId: null, report: null })).toBe(status);
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

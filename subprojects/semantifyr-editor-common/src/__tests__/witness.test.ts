/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { describe, expect, it } from "vitest";
import {
    caseOutcomeOf,
    formatTraceCall,
    noWitnessMessage,
    traceStepLabel,
    witnessOpenFailureMessage,
    witnessValidationLabel,
    witnessValidationStatusOf,
} from "../witness.ts";
import {
    type ReportVerdict,
    type VerificationReport,
    VerificationStatus,
    WitnessValidationStatus,
} from "../wire.ts";

const NO_OPTIMIZATION = { duration: "PT0S", passes: [], analyses: [] };

const NO_SIZE = { variables: 0, operations: 0, expressions: 0 };

function reportOf(verdict: ReportVerdict, message: string | null): VerificationReport {
    return {
        kind: "run",
        verificationCase: "example::Case",
        portfolioId: "theta",
        optimization: { enabled: [] },
        timeout: "PT5M",
        result: {
            metadata: { backendId: "theta", startedAt: "2026-11-16T10:00:00Z" },
            verdict,
            soundness: "Exact",
            isTrusted: true,
            metrics: {
                totalDuration: "PT1S",
                backend: null,
                verifier: {
                    compilationDuration: "PT0S",
                    compilation: {
                        createDuration: "PT0S",
                        instantiationDuration: "PT0S",
                        inliningDuration: "PT0S",
                        flatteningDuration: "PT0S",
                        inliningOptimization: NO_OPTIMIZATION,
                        flatteningOptimization: NO_OPTIMIZATION,
                        inputSize: NO_SIZE,
                        inlinedSize: NO_SIZE,
                        flattenedSize: NO_SIZE,
                    },
                    backAnnotationDuration: "PT0S",
                    portfolioDuration: "PT1S",
                },
            },
            message,
        },
        trace: null,
    };
}

describe("caseOutcomeOf", () => {
    it("reads the verdict, the message and the portfolio from the report, whatever the fields beside it say", () => {
        expect(caseOutcomeOf({
            status: VerificationStatus.Passed,
            message: null,
            portfolioId: "smart",
            report: reportOf("Errored", "the backend crashed"),
            witnessUri: null,
            traceUri: null,
        })).toEqual({
            status: VerificationStatus.Errored,
            message: "the backend crashed",
            portfolioId: "theta",
        });
    });

    it("prefers a message the client put beside the report to the report's", () => {
        expect(caseOutcomeOf({
            status: null,
            message: "the trace could not be lifted",
            portfolioId: null,
            report: reportOf("Failed", "a counterexample"),
            witnessUri: null,
            traceUri: null,
        }).message).toBe("the trace could not be lifted");
    });

    it("reads the fields of a result without a report", () => {
        expect(caseOutcomeOf({
            status: VerificationStatus.Cancelled,
            message: "Verification cancelled",
            portfolioId: "smart",
            report: null,
            witnessUri: null,
            traceUri: null,
        })).toEqual({
            status: VerificationStatus.Cancelled,
            message: "Verification cancelled",
            portfolioId: "smart",
        });
    });

    it("reads a result with neither a report nor a status as errored", () => {
        expect(caseOutcomeOf({
            status: null,
            message: null,
            portfolioId: null,
            report: null,
            witnessUri: null,
            traceUri: null,
        }).status).toBe(VerificationStatus.Errored);
    });
});

describe("formatTraceCall", () => {
    it("names the instance, the transition by its own name and the argument values", () => {
        expect(formatTraceCall({
            self: "root.lamp",
            transition: "example::Lamp::toggle",
            arguments: { brightness: 2, on: true, partner: null },
            calls: [],
        })).toBe("root.lamp.toggle(brightness=2, on=true, partner=null)");
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
        expect(witnessOpenFailureMessage("file:///tmp/witness.semantifyr", new Error("file not found"))).toBe("Cannot open the witness file:///tmp/witness.semantifyr: file not found");
        expect(witnessOpenFailureMessage("file:///tmp/witness.semantifyr", "closed")).toBe("Cannot open the witness file:///tmp/witness.semantifyr: closed");
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
        expect(witnessValidationStatusOf({ status, message: null, portfolioId: null, report: null })).toBe(status);
    });

    it("treats no result and an unknown status as errored", () => {
        expect(witnessValidationStatusOf(null)).toBe(WitnessValidationStatus.Errored);
        expect(witnessValidationStatusOf({ status: "mystery", message: null, portfolioId: null, report: null } as never)).toBe(WitnessValidationStatus.Errored);
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

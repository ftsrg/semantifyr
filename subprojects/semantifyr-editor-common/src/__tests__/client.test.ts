/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { describe, expect, it, vi } from "vitest";
import { type CancellationToken, type LspClient, validateWitness, verifyCase, witnessValidationLocation } from "../client.ts";

const caseLocation = {
    uri: "file:///workspace/crossroads.gamma",
    range: { start: { line: 4, character: 0 }, end: { line: 6, character: 1 } },
};

const witnessUri = "file:///workspace/.artifacts/crossroads.ACanBeGreen/verification/theta/witness.oxsts";

function recordingClient(response: unknown) {
    const sendRequest = vi.fn<LspClient["sendRequest"]>(async () => {
        return response;
    });
    const client: LspClient = {
        sendRequest,
        sendNotification: () => {},
    };
    return { client, sendRequest };
}

const token: CancellationToken = {
    isCancellationRequested: false,
    onCancellationRequested: () => {
        return { dispose: () => {} };
    },
};

describe("validateWitness", () => {
    it("sends the location and the portfolio as the command's one argument", async () => {
        const validation = { status: "valid", verdict: "failed", message: null, backendId: "theta-bmc", portfolioId: "smart", metrics: null };
        const { client, sendRequest } = recordingClient(validation);

        const result = await validateWitness(client, "gamma.witness.validate", caseLocation, { portfolio: "smart" });

        expect(result).toEqual(validation);
        expect(sendRequest.mock.calls).toEqual([[
            "workspace/executeCommand",
            { command: "gamma.witness.validate", arguments: [{ ...caseLocation, portfolio: "smart" }] },
        ]]);
    });

    it("passes a token on and sends no trailing parameter without one", async () => {
        const { client, sendRequest } = recordingClient(null);

        await validateWitness(client, "oxsts.case.validateWitness", caseLocation);
        await validateWitness(client, "oxsts.case.validateWitness", caseLocation, { token });

        expect(sendRequest.mock.calls[0]).toHaveLength(2);
        expect(sendRequest.mock.calls[1]?.[2]).toBe(token);
    });
});

describe("verifyCase", () => {
    it("sends the location without a portfolio when none is given", async () => {
        const { client, sendRequest } = recordingClient(null);

        await verifyCase(client, "gamma.case.verify", caseLocation);

        expect(sendRequest.mock.calls).toEqual([[
            "workspace/executeCommand",
            { command: "gamma.case.verify", arguments: [caseLocation] },
        ]]);
    });
});

describe("witnessValidationLocation", () => {
    it("names the case for a validation by case", () => {
        expect(witnessValidationLocation(caseLocation, witnessUri, true)).toBe(caseLocation);
    });

    it("names the start of the witness model otherwise", () => {
        expect(witnessValidationLocation(caseLocation, witnessUri, false)).toEqual({
            uri: witnessUri,
            range: { start: { line: 0, character: 0 }, end: { line: 0, character: 0 } },
        });
    });
});

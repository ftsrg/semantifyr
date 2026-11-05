/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { describe, expect, it } from "vitest";
import { witnessStateChanges } from "../witness.ts";

describe("witnessStateChanges", () => {
    it("lists every initial value and then the values each step changes", () => {
        const changes = witnessStateChanges({
            initialStep: { values: [{ variable: "on", value: "false" }, { variable: "brightness", value: "0" }] },
            initializedStep: { values: [{ variable: "on", value: "false" }, { variable: "brightness", value: "1" }] },
            steps: [
                { values: [{ variable: "on", value: "true" }, { variable: "brightness", value: "1" }] },
                { values: [{ variable: "on", value: "true" }, { variable: "brightness", value: "1" }] },
            ],
        });

        expect(changes).toEqual([
            [{ variable: "on", value: "false", previous: undefined }, { variable: "brightness", value: "0", previous: undefined }],
            [{ variable: "brightness", value: "1", previous: "0" }],
            [{ variable: "on", value: "true", previous: "false" }],
            [],
        ]);
    });

    it("compares a step after a missing init step with the initial step", () => {
        const changes = witnessStateChanges({
            initialStep: { values: [{ variable: "mode", value: "A" }] },
            initializedStep: null,
            steps: [{ values: [{ variable: "mode", value: "B" }] }],
        });

        expect(changes).toEqual([
            [{ variable: "mode", value: "A", previous: undefined }],
            [],
            [{ variable: "mode", value: "B", previous: "A" }],
        ]);
    });

    it("keeps the value of a variable a step leaves out", () => {
        const changes = witnessStateChanges({
            initialStep: { values: [{ variable: "counter", value: "0" }, { variable: "limit", value: "0" }] },
            initializedStep: null,
            steps: [
                { values: [{ variable: "counter", value: "1" }] },
                { values: [{ variable: "counter", value: "1" }, { variable: "limit", value: "0" }] },
            ],
        });

        expect(changes[3]).toEqual([]);
    });
});

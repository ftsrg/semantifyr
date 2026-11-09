/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { describe, expect, it } from "vitest";
import { witnessStateChanges } from "../witness.ts";

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

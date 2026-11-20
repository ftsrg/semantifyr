/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { describe, expect, it } from "vitest";
import { formatSeconds, isoDurationToMs } from "../duration.ts";

describe("isoDurationToMs", () => {
    it.each([
        ["PT1.503S", 1503],
        ["PT0S", 0],
        ["PT2M3.5S", 123_500],
        ["PT1H", 3_600_000],
        ["P1DT1S", 86_401_000],
        ["P1W", 604_800_000],
        ["PT-0.5S", -500],
        ["-PT1M", -60_000],
    ])("reads %s as %d milliseconds", (iso, milliseconds) => {
        expect(isoDurationToMs(iso)).toBeCloseTo(milliseconds);
    });

    it("refuses a text that is no duration", () => {
        expect(() => {
            return isoDurationToMs("soon");
        }).toThrow("Not an ISO-8601 duration: soon");
    });
});

describe("formatSeconds", () => {
    it.each([
        [600, "0.6 s"],
        [4_000, "4.0 s"],
        [9_949, "9.9 s"],
        [30_000, "30 s"],
        [123_500, "124 s"],
    ])("shows %d milliseconds as %s", (milliseconds, text) => {
        expect(formatSeconds(milliseconds)).toBe(text);
    });
});

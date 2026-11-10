/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { describe, expect, it } from "vitest";
import {
    decodeCompressedBase64Url,
    decodeLiveVerdicts,
    encodeCompressedBase64Url,
    liveEditorUrl,
    normalizeBaseUrl,
    parseLiveEditorUrl,
} from "../liveUrls.ts";

describe("normalizeBaseUrl", () => {
    it.each([
        ["http://localhost:18080", "ws://localhost:18080", "http://localhost:18080"],
        ["https://live.semantifyr.org", "wss://live.semantifyr.org", "https://live.semantifyr.org"],
        ["https://live.semantifyr.org/", "wss://live.semantifyr.org", "https://live.semantifyr.org"],
        ["ws://localhost:18080", "ws://localhost:18080", "http://localhost:18080"],
        ["wss://live.semantifyr.org", "wss://live.semantifyr.org", "https://live.semantifyr.org"],
        ["live.semantifyr.org", "wss://live.semantifyr.org", "https://live.semantifyr.org"],
    ])("derives both schemes from %s", (url, ws, http) => {
        expect(normalizeBaseUrl(url)).toEqual({ ws, http });
    });
});

describe("encodeCompressedBase64Url and decodeCompressedBase64Url", () => {
    it.each([
        ["ASCII", "package demo\nclass Main { var x: int := 0 }"],
        ["multi-byte UTF-8", "/* émoji 🎉 mix */ var ç := \"λ\""],
        ["an empty string", ""],
    ])("round-trip %s", async (_name, text) => {
        expect(await decodeCompressedBase64Url(await encodeCompressedBase64Url(text))).toBe(text);
    });

    it("use the URL-safe alphabet", async () => {
        expect(await encodeCompressedBase64Url("?>&%/+ test ".repeat(20))).not.toMatch(/[+/=]/);
    });

    it.each([
        ["malformed base64url", "!!!not-valid!!!"],
        ["base64url that is not gzip", "cGFja2FnZSBkZW1v"],
    ])("decode %s to null", async (_name, encoded) => {
        expect(await decodeCompressedBase64Url(encoded)).toBeNull();
    });
});

function parsed(url: string) {
    return parseLiveEditorUrl(new URL(url));
}

describe("liveEditorUrl and parseLiveEditorUrl", () => {
    it("round-trip a link with every part", async () => {
        const url = await liveEditorUrl("https://live.semantifyr.org/", {
            language: "oxsts",
            example: "crossroads",
            library: "gamma",
            view: "embed",
            model: "package demo\nclass Foo {}",
            case: "crossroads.GreenCase",
            verdicts: [
                ["crossroads.GreenCase", "failed"],
                ["crossroads.SafeCase", "passed"],
            ],
        });

        const address = parsed(url);

        expect(new URL(url).pathname).toBe("/oxsts/crossroads");
        expect(address).toMatchObject({
            language: "oxsts",
            example: "crossroads",
            library: "gamma",
            view: "embed",
            case: "crossroads.GreenCase",
        });
        expect(await decodeCompressedBase64Url(address.compressedModel ?? "")).toBe("package demo\nclass Foo {}");
        expect(await decodeLiveVerdicts(address.compressedVerdicts ?? "")).toEqual([
            ["crossroads.GreenCase", "failed"],
            ["crossroads.SafeCase", "passed"],
        ]);
    });

    it("carry what the visitor made in the fragment and nothing of it in the path or the query", async () => {
        const url = new URL(await liveEditorUrl("https://live.semantifyr.org/", {
            language: "sysml",
            model: "part def Train;",
            case: "Safe",
        }));

        expect(url.pathname).toBe("/sysml");
        expect(url.search).toBe("");
        expect([...new URLSearchParams(url.hash.slice(1)).keys()]).toEqual([
            "model",
            "case",
        ]);
    });

    it("leave out the parts the link does not set", async () => {
        const url = await liveEditorUrl("https://live.semantifyr.org/", {
            language: "gamma",
        });

        expect(url).toBe("https://live.semantifyr.org/gamma");
        expect(parsed(url)).toEqual({
            language: "gamma",
            example: null,
            library: null,
            view: null,
            compressedModel: null,
            case: null,
            compressedVerdicts: null,
        });
    });

    it("replace the parts of the base the link names and keep its other query parameters", async () => {
        const url = await liveEditorUrl("https://live.semantifyr.org/gamma/crossroads?backend=http://localhost:18080&view=embed&library=gamma#model=abc", {
            language: "oxsts",
            example: "basics",
        });

        expect(new URL(url).searchParams.get("backend")).toBe("http://localhost:18080");
        expect(parsed(url)).toMatchObject({
            language: "oxsts",
            example: "basics",
            library: null,
            view: null,
            compressedModel: null,
        });
    });

    it("read the bare address as naming nothing, and an unknown view as the full editor", () => {
        expect(parsed("https://live.semantifyr.org/")).toMatchObject({ language: null, example: null });
        expect(parsed("https://live.semantifyr.org/sysml/level-crossing/?view=dark")).toMatchObject({ language: "sysml", example: "level-crossing", view: null });
        expect(parsed("https://live.semantifyr.org/oxsts?view=edit-only").view).toBe("edit-only");
    });

    it("do not read today's mode, example, code and embed parameters", () => {
        expect(parsed("https://live.semantifyr.org/?mode=gamma&example=Crossroads&code=abc&embed=1")).toEqual({
            language: null,
            example: null,
            library: null,
            view: null,
            compressedModel: null,
            case: null,
            compressedVerdicts: null,
        });
    });

    it("decode garbled or misshapen verdicts to null", async () => {
        expect(await decodeLiveVerdicts("garbled")).toBeNull();
        expect(await decodeLiveVerdicts(await encodeCompressedBase64Url(JSON.stringify({ a: "passed" })))).toBeNull();
        expect(await decodeLiveVerdicts(await encodeCompressedBase64Url(JSON.stringify([["a", 1]])))).toBeNull();
    });
});

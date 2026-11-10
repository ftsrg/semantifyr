/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { describe, expect, it } from "vitest";
import {
    decodeCompressedBase64Url,
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

describe("liveEditorUrl and parseLiveEditorUrl", () => {
    it("round-trip a link with every parameter", async () => {
        const url = new URL(await liveEditorUrl("https://live.semantifyr.org/", {
            flavor: "gamma",
            example: "crossroads",
            code: "package demo\nclass Foo {}",
            embed: true,
        }));

        const parameters = parseLiveEditorUrl(url.search);

        expect(url.origin + url.pathname).toBe("https://live.semantifyr.org/");
        expect(parameters).toEqual({
            flavor: "gamma",
            example: "crossroads",
            compressedCode: url.searchParams.get("code"),
            embed: true,
        });
        expect(await decodeCompressedBase64Url(parameters.compressedCode ?? "")).toBe("package demo\nclass Foo {}");
    });

    it("leave out the parameters the link does not set", async () => {
        const url = new URL(await liveEditorUrl("https://live.semantifyr.org/", {
            flavor: "oxsts",
        }));

        expect([...url.searchParams.keys()]).toEqual([
            "mode",
        ]);
        expect(parseLiveEditorUrl(url.search)).toEqual({
            flavor: "oxsts",
            example: null,
            compressedCode: null,
            embed: false,
        });
    });

    it("replace the link parameters of the base and keep the others", async () => {
        const url = new URL(await liveEditorUrl("https://live.semantifyr.org/?backend=http://localhost:18080&mode=gamma&code=abc&embed=1", {
            flavor: "oxsts",
            example: "basics",
        }));

        expect(url.searchParams.get("backend")).toBe("http://localhost:18080");
        expect(parseLiveEditorUrl(url.search)).toEqual({
            flavor: "oxsts",
            example: "basics",
            compressedCode: null,
            embed: false,
        });
    });

    it("read only embed=1 as embedded", () => {
        expect(parseLiveEditorUrl("?embed=true").embed).toBe(false);
        expect(parseLiveEditorUrl("embed=1").embed).toBe(true);
    });
});

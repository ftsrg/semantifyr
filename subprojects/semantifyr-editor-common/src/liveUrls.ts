/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

export interface LiveServerUrls {
    ws: string;
    http: string;
}

export function normalizeBaseUrl(url: string): LiveServerUrls {
    const base = url.trim().replace(/\/$/, "");
    if (base.startsWith("http://")) {
        return { ws: `ws://${base.slice("http://".length)}`, http: base };
    }
    if (base.startsWith("https://")) {
        return { ws: `wss://${base.slice("https://".length)}`, http: base };
    }
    if (base.startsWith("ws://")) {
        return { ws: base, http: `http://${base.slice("ws://".length)}` };
    }
    if (base.startsWith("wss://")) {
        return { ws: base, http: `https://${base.slice("wss://".length)}` };
    }
    return { ws: `wss://${base}`, http: `https://${base}` };
}

export interface LiveEditorLink {
    flavor: string;
    example?: string | undefined;
    code?: string | undefined;
    embed?: boolean | undefined;
}

export interface LiveEditorParameters {
    flavor: string | null;
    example: string | null;
    compressedCode: string | null;
    embed: boolean;
}

// Keeps the parameters of the base URL that are not part of the link, such as live's ?backend=.
export async function liveEditorUrl(base: string, link: LiveEditorLink): Promise<string> {
    const url = new URL(base);
    url.searchParams.set("mode", link.flavor);
    if (link.example === undefined) {
        url.searchParams.delete("example");
    } else {
        url.searchParams.set("example", link.example);
    }
    if (link.code === undefined) {
        url.searchParams.delete("code");
    } else {
        url.searchParams.set("code", await encodeCompressedBase64Url(link.code));
    }
    if (link.embed === true) {
        url.searchParams.set("embed", "1");
    } else {
        url.searchParams.delete("embed");
    }
    return url.toString();
}

export function parseLiveEditorUrl(search: string): LiveEditorParameters {
    const parameters = new URLSearchParams(search);
    return {
        flavor: parameters.get("mode"),
        example: parameters.get("example"),
        compressedCode: parameters.get("code"),
        embed: parameters.get("embed") === "1",
    };
}

function bytesToBase64Url(bytes: Uint8Array): string {
    let binary = "";
    for (const byte of bytes) {
        binary += String.fromCharCode(byte);
    }
    return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

function base64UrlToBytes(encoded: string): Uint8Array {
    const base64 = encoded.replace(/-/g, "+").replace(/_/g, "/");
    const padding = base64.length % 4 === 0 ? "" : "=".repeat(4 - (base64.length % 4));
    const binary = atob(base64 + padding);
    const bytes = new Uint8Array(binary.length);
    for (let index = 0; index < binary.length; index++) {
        bytes[index] = binary.charCodeAt(index);
    }
    return bytes;
}

// jsdom's Blob has no stream()
function singleChunkStream(bytes: Uint8Array): ReadableStream<Uint8Array> {
    return new ReadableStream<Uint8Array>({
        start(controller) {
            controller.enqueue(bytes);
            controller.close();
        },
    });
}

async function transform(bytes: Uint8Array, transformer: TransformStream<Uint8Array, Uint8Array>): Promise<Uint8Array> {
    const stream = singleChunkStream(bytes).pipeThrough(transformer);
    const chunks: Uint8Array[] = [];
    let total = 0;
    const reader = stream.getReader();
    for (;;) {
        const { value, done } = await reader.read();
        if (done) {
            break;
        }
        chunks.push(value);
        total += value.length;
    }
    const result = new Uint8Array(total);
    let offset = 0;
    for (const chunk of chunks) {
        result.set(chunk, offset);
        offset += chunk.length;
    }
    return result;
}

export async function encodeCompressedBase64Url(text: string): Promise<string> {
    return bytesToBase64Url(await transform(new TextEncoder().encode(text), new CompressionStream("gzip") as TransformStream<Uint8Array, Uint8Array>));
}

export async function decodeCompressedBase64Url(encoded: string): Promise<string | null> {
    try {
        return new TextDecoder().decode(await transform(base64UrlToBytes(encoded), new DecompressionStream("gzip") as TransformStream<Uint8Array, Uint8Array>));
    } catch {
        return null;
    }
}

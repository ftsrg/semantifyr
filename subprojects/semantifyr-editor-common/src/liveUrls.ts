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

export type LiveEditorView =
    | "embed"
    | "edit-only";

const VIEWS: readonly LiveEditorView[] = [
    "embed",
    "edit-only",
];

export type LiveEditorTheme =
    | "light"
    | "dark";

const THEMES: readonly LiveEditorTheme[] = [
    "light",
    "dark",
];

// a fixed frame keeps the size its page gives it; without one the embed grows with its content
export type LiveEditorFrame =
    | "fixed";

const FRAMES: readonly LiveEditorFrame[] = [
    "fixed",
];

// the lines an embed opens scrolled to, counted from one, the last included
export interface LiveEditorLines {
    first: number;
    last: number;
}

function linesOf(value: string | null): LiveEditorLines | null {
    const match = /^(\d+)-(\d+)$/.exec(value ?? "");
    if (!match) {
        return null;
    }
    const first = Number(match[1]);
    const last = Number(match[2]);
    if (first < 1 || last < first) {
        return null;
    }
    return { first, last };
}

// The path names the model, the query how it is shown, the fragment what the visitor made, which a browser never sends to the server
export interface LiveEditorLink {
    language: string;
    example?: string | undefined;
    library?: string | undefined;
    view?: LiveEditorView | undefined;
    theme?: LiveEditorTheme | undefined;
    frame?: LiveEditorFrame | undefined;
    lines?: LiveEditorLines | undefined;
    model?: string | undefined;
    case?: string | undefined;
    verdicts?: readonly (readonly [string, string])[] | undefined;
}

export interface LiveEditorAddress {
    language: string | null;
    example: string | null;
    library: string | null;
    view: LiveEditorView | null;
    theme: LiveEditorTheme | null;
    frame: LiveEditorFrame | null;
    lines: LiveEditorLines | null;
    compressedModel: string | null;
    case: string | null;
    compressedVerdicts: string | null;
}

export interface LiveEditorLocation {
    pathname: string;
    search: string;
    hash: string;
}

// Keeps the query parameters of the base URL that are not part of the link
function pathOf(segments: (string | undefined)[]): string {
    return segments.filter((segment) => {
        return segment !== undefined;
    }).map((segment) => {
        return `/${encodeURIComponent(segment)}`;
    }).join("");
}

// an example's address, /<language>/<example>, which names it everywhere
export function liveExampleUrl(base: string, language: string, example: string): string {
    const url = new URL(base);
    url.pathname = pathOf([language, example]);
    url.search = "";
    url.hash = "";
    return url.toString();
}

export async function liveEditorUrl(base: string, link: LiveEditorLink): Promise<string> {
    const url = new URL(base);
    url.pathname = pathOf([link.language, link.example]);
    setOrDelete(url.searchParams, "library", link.library);
    setOrDelete(url.searchParams, "view", link.view);
    setOrDelete(url.searchParams, "theme", link.theme);
    setOrDelete(url.searchParams, "frame", link.frame);
    setOrDelete(url.searchParams, "lines", link.lines === undefined ? undefined : `${link.lines.first}-${link.lines.last}`);
    const fragment = new URLSearchParams();
    if (link.model !== undefined) {
        fragment.set("model", await encodeCompressedBase64Url(link.model));
    }
    if (link.case !== undefined) {
        fragment.set("case", link.case);
    }
    if (link.verdicts !== undefined && link.verdicts.length > 0) {
        fragment.set("verdicts", await encodeCompressedBase64Url(JSON.stringify(link.verdicts)));
    }
    url.hash = fragment.toString();
    return url.toString();
}

function setOrDelete(parameters: URLSearchParams, name: string, value: string | undefined): void {
    if (value === undefined) {
        parameters.delete(name);
    } else {
        parameters.set(name, value);
    }
}

function decodedSegment(segment: string | undefined): string | null {
    if (segment === undefined) {
        return null;
    }
    try {
        return decodeURIComponent(segment);
    } catch {
        return segment;
    }
}

function viewOf(value: string | null): LiveEditorView | null {
    return VIEWS.find((view) => {
        return view === value;
    }) ?? null;
}

export function parseLiveEditorUrl(location: LiveEditorLocation): LiveEditorAddress {
    const [language, ...rest] = location.pathname.split("/").filter((segment) => {
        return segment !== "";
    });
    // a path longer than a language and an example names an example that does not exist
    const example = rest.length === 0 ? undefined : rest.join("/");
    const query = new URLSearchParams(location.search);
    const fragment = new URLSearchParams(location.hash.replace(/^#/, ""));
    return {
        language: decodedSegment(language),
        example: decodedSegment(example),
        library: query.get("library"),
        view: viewOf(query.get("view")),
        theme: THEMES.find((theme) => {
            return theme === query.get("theme");
        }) ?? null,
        frame: FRAMES.find((frame) => {
            return frame === query.get("frame");
        }) ?? null,
        lines: linesOf(query.get("lines")),
        compressedModel: fragment.get("model"),
        case: fragment.get("case"),
        compressedVerdicts: fragment.get("verdicts"),
    };
}

// null when the verdicts cannot be read; the names of the verdicts are the reader's to check
export async function decodeLiveVerdicts(compressed: string): Promise<[string, string][] | null> {
    const text = await decodeCompressedBase64Url(compressed);
    if (text === null) {
        return null;
    }
    try {
        const entries: unknown = JSON.parse(text);
        if (!Array.isArray(entries)) {
            return null;
        }
        const verdicts: [string, string][] = [];
        for (const entry of entries as unknown[]) {
            if (!Array.isArray(entry) || typeof entry[0] !== "string" || typeof entry[1] !== "string") {
                return null;
            }
            verdicts.push([entry[0], entry[1]]);
        }
        return verdicts;
    } catch {
        return null;
    }
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

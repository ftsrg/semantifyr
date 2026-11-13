/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

// vscode-textmate and vscode-oniguruma as live's editor bundles them, which ship no types; the highlighter's test compares against them

declare module '@codingame/monaco-vscode-api/external/vscode-textmate/release/main' {
  interface TextMateToken {
    startIndex: number;
    endIndex: number;
    scopes: string[];
  }
  interface TextMateGrammar {
    tokenizeLine: (line: string, state: unknown) => { tokens: TextMateToken[]; ruleStack: unknown };
  }
  interface TextMate {
    INITIAL: unknown;
    Registry: new (options: { onigLib: Promise<unknown>; loadGrammar: (scopeName: string) => Promise<unknown> }) => { loadGrammar: (scopeName: string) => Promise<TextMateGrammar | null> };
  }
  export function __require(): TextMate;
}

declare module '@codingame/monaco-vscode-textmate-service-override/external/vscode-oniguruma/release/main' {
  interface Oniguruma {
    loadWASM: (data: ArrayBuffer) => Promise<void>;
    createOnigScanner: (patterns: string[]) => unknown;
    createOnigString: (text: string) => unknown;
  }
  export function __require(): Oniguruma;
}

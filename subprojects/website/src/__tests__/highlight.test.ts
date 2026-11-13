/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { readdirSync, readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { __require as textMate } from '@codingame/monaco-vscode-api/external/vscode-textmate/release/main';
import { __require as oniguruma } from '@codingame/monaco-vscode-textmate-service-override/external/vscode-oniguruma/release/main';
import { beforeAll, describe, expect, it } from 'vitest';

import gammaGrammar from '@grammars/gamma.tmLanguage.json';
import oxstsGrammar from '@grammars/oxsts.tmLanguage.json';
import sysmlGrammar from '@grammars/sysml.tmLanguage.json';
import { styleOf, tokenizerOf, type RawGrammar } from '../lib/highlight';

// Goal: a code block of the site is tokenized as live's editor tokenizes the same text, with the same grammar, by vscode-textmate over Oniguruma

const REPOSITORY = fileURLToPath(new URL('../../../../', import.meta.url));

function modelsIn(directory: string, extension: string): string[] {
  return readdirSync(join(REPOSITORY, directory)).filter((name) => {
    return name.endsWith(extension);
  }).map((name) => {
    return readFileSync(join(REPOSITORY, directory, name), 'utf8');
  });
}

const CORPORA: readonly { grammar: RawGrammar; models: () => string[] }[] = [
  {
    grammar: oxstsGrammar,
    models: () => {
      return [
        ...modelsIn('oxsts-test-models/tutorial', '.oxsts'),
        ...modelsIn('subprojects/oxsts.lang/src/main/resources/hu/bme/mit/semantifyr/oxsts/lang/library', '.oxsts'),
        'x := "a \\" b" // c\n/* d\n e */ 1.5',
      ];
    },
  },
  {
    grammar: gammaGrammar,
    models: () => {
      return modelsIn('subprojects/frontends/gamma/models/examples', '.gamma');
    },
  },
  {
    grammar: sysmlGrammar,
    models: () => {
      return [
        ...modelsIn('subprojects/frontends/sysml/models/examples', '.sysml'),
        'part p; //* a block\n comment */ // a line',
      ];
    },
  },
];

type Scopes = string[][];

let liveTokenizers: Map<string, (code: string) => Scopes>;

beforeAll(async () => {
  const onig = oniguruma();
  const main = fileURLToPath(import.meta.resolve('@codingame/monaco-vscode-textmate-service-override/external/vscode-oniguruma/release/main'));
  const wasm = readFileSync(join(dirname(main), 'onig.wasm'));
  await onig.loadWASM(wasm.buffer.slice(wasm.byteOffset, wasm.byteOffset + wasm.byteLength));
  const textmate = textMate();
  const registry = new textmate.Registry({
    onigLib: Promise.resolve(onig),
    loadGrammar: (scopeName) => {
      return Promise.resolve(CORPORA.find((corpus) => {
        return corpus.grammar.scopeName === scopeName;
      })?.grammar ?? null);
    },
  });
  liveTokenizers = new Map();
  for (const { grammar } of CORPORA) {
    const loaded = await registry.loadGrammar(grammar.scopeName);
    if (loaded === null) {
      throw new Error(`vscode-textmate loaded no ${grammar.scopeName}`);
    }
    liveTokenizers.set(grammar.scopeName, (code) => {
      let state = textmate.INITIAL;
      return code.split('\n').flatMap((line) => {
        const result = loaded.tokenizeLine(line, state);
        state = result.ruleStack;
        return Array.from({ length: line.length }, (_, index) => {
          return result.tokens.find((token) => {
            return token.startIndex <= index && index < token.endIndex;
          })?.scopes ?? [];
        });
      });
    });
  }
});

function siteScopes(grammar: RawGrammar, code: string): Scopes {
  return tokenizerOf(grammar)(code).flatMap((line) => {
    return line.flatMap((token) => {
      return Array.from({ length: token.text.length }, () => {
        return [...token.scopes];
      });
    });
  });
}

describe('the highlighter', () => {
  for (const { grammar, models } of CORPORA) {
    it(`gives every character the scopes live's editor gives it, in ${grammar.scopeName}`, () => {
      const texts = models();
      expect(texts.length).toBeGreaterThan(1);
      for (const text of texts) {
        expect(siteScopes(grammar, text)).toEqual(liveTokenizers.get(grammar.scopeName)!(text));
      }
    });
  }

  it('colours a scope by the innermost scope a syntax rule matches, the longest selector first', () => {
    expect(styleOf(['source.oxsts', 'comment.line.oxsts'])).toEqual({ color: 'var(--syntax-comment)', fontStyle: 'italic' });
    expect(styleOf(['source.oxsts', 'keyword.operator.assignment.oxsts'])).toEqual({ color: 'var(--syntax-operator)' });
    expect(styleOf(['source.oxsts', 'string.quoted.double.languageId', 'constant.character.escape.languageId'])).toEqual({ color: 'var(--syntax-string)' });
    expect(styleOf(['source.oxsts'])).toEqual({});
  });
});

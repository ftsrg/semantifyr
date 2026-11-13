/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { describe, expect, it } from 'vitest';
import { highlight, type TokenStyle } from '../lib/highlight';

// Goal: a block of each language live has a grammar for comes out in the tokens' syntax colours by the grammar's scopes, and any other language stays plain

function styleOf(code: string, language: string, text: string): TokenStyle | undefined {
  return highlight(code, language)?.flat().find((token) => {
    return token.text === text;
  })?.style;
}

const KEYWORD = { color: 'var(--syntax-keyword)' };
const COMMENT = { color: 'var(--syntax-comment)', fontStyle: 'italic' };

const BLOCKS = [
  {
    language: 'oxsts',
    code: 'class Light {\n    var level: Integer := 1 // dimmed\n}',
    keyword: 'class',
    comment: '// dimmed',
  },
  {
    language: 'gamma',
    code: 'statechart Light [\n] {\n    var level : integer := 1 // dimmed\n}',
    keyword: 'statechart',
    comment: '// dimmed',
  },
  {
    language: 'sysmlv2',
    code: 'part def Light {\n    attribute level = 1; // dimmed\n}',
    keyword: 'attribute',
    comment: '// dimmed',
  },
];

describe('the highlighter', () => {
  for (const { language, code, keyword, comment } of BLOCKS) {
    it(`colours a ${language} block by its grammar's scopes`, () => {
      expect(styleOf(code, language, keyword)).toEqual(KEYWORD);
      expect(styleOf(code, language, comment)).toEqual(COMMENT);
      expect(styleOf(code, language, '}')).toEqual({ color: 'inherit' });
    });
  }

  it('leaves a language live has no grammar for plain', () => {
    expect(highlight('echo 1', 'bash')).toBeNull();
  });
});

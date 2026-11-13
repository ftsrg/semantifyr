/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { describe, expect, it } from 'vitest';

import { highlight, type TokenStyle } from '../lib/highlight';

// Goal: a block of each language live has a grammar for comes out in the tokens' syntax colours, read from the grammar's scopes

function styleOf(code: string, language: string, text: string): TokenStyle | undefined {
  return highlight(code, language)?.flat().find((token) => {
    return token.text === text;
  })?.style;
}

describe('the highlighter', () => {
  it('colours an OXSTS block', () => {
    const code = 'class Lamp {\n    var on: Boolean := false // off\n    var count: Integer := 1\n}';
    expect(styleOf(code, 'oxsts', 'class')).toEqual({ color: 'var(--syntax-keyword)' });
    expect(styleOf(code, 'oxsts', ':=')).toEqual({ color: 'var(--syntax-operator)' });
    expect(styleOf(code, 'oxsts', '1')).toEqual({ color: 'var(--syntax-number)' });
    expect(styleOf(code, 'oxsts', '// off')).toEqual({ color: 'var(--syntax-comment)', fontStyle: 'italic' });
  });

  it('colours a Gamma block', () => {
    const code = 'interface Control {\n    out event toggle // a toggle\n}';
    expect(styleOf(code, 'gamma', 'interface')).toEqual({ color: 'var(--syntax-keyword)' });
    expect(styleOf(code, 'gamma', '// a toggle')).toEqual({ color: 'var(--syntax-comment)', fontStyle: 'italic' });
  });

  it('colours a SysML v2 block', () => {
    const code = 'part def Lamp; // a lamp';
    expect(styleOf(code, 'sysmlv2', 'part')).toEqual({ color: 'var(--syntax-keyword)' });
    expect(styleOf(code, 'sysmlv2', '// a lamp')).toEqual({ color: 'var(--syntax-comment)', fontStyle: 'italic' });
  });

  it('leaves a language without a grammar of live to the plain block', () => {
    expect(highlight('echo hello', 'bash')).toBeNull();
  });
});

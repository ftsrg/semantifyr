/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { createHighlighterCoreSync, getTokenStyleObject, type LanguageRegistration } from 'shiki/core';
import { createJavaScriptRegexEngine } from 'shiki/engine/javascript';
import { SYNTAX_RULES } from '@semantifyr/editor-common/syntax';
import gammaGrammar from '@grammars/gamma.tmLanguage.json';
import oxstsGrammar from '@grammars/oxsts.tmLanguage.json';
import sysmlGrammar from '@grammars/sysml.tmLanguage.json';

export interface TokenStyle {
  color?: string;
  fontStyle?: 'italic';
}

export interface Token {
  text: string;
  style: TokenStyle;
}

const THEME = 'tokens';

// the grammars live's editor loads, by the language of a code block
const LANGUAGES: readonly string[] = [
  'oxsts',
  'gamma',
  'sysmlv2',
];

const highlighter = createHighlighterCoreSync({
  themes: [
    {
      name: THEME,
      fg: 'inherit',
      bg: 'inherit',
      settings: SYNTAX_RULES.map((rule) => {
        return { scope: [...rule.scopes], settings: { foreground: `var(--${rule.token})`, fontStyle: rule.italic ? 'italic' : '' } };
      }),
    },
  ],
  langs: [
    { ...oxstsGrammar, name: 'oxsts' },
    { ...gammaGrammar, name: 'gamma' },
    { ...sysmlGrammar, name: 'sysmlv2' },
  ] as LanguageRegistration[],
  engine: createJavaScriptRegexEngine(),
});

// the lines of a code block in the tokens' syntax colours, or null for a language live has no grammar for
export function highlight(code: string, language: string): Token[][] | null {
  if (!LANGUAGES.includes(language)) {
    return null;
  }
  return highlighter.codeToTokensBase(code, { lang: language, theme: THEME }).map((line) => {
    return line.map((token) => {
      const { color = 'inherit', 'font-style': fontStyle } = getTokenStyleObject(token);
      return { text: token.content, style: fontStyle === 'italic' ? { color, fontStyle } : { color } };
    });
  });
}

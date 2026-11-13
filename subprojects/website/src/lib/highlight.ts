/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import { SYNTAX_RULES } from '@semantifyr/editor-common/syntax';
import { createHighlighterCoreSync, getTokenStyleObject, type LanguageRegistration } from 'shiki/core';
import { createJavaScriptRegexEngine } from 'shiki/engine/javascript';
import gammaGrammar from '@grammars/gamma.tmLanguage.json';
import oxstsGrammar from '@grammars/oxsts.tmLanguage.json';
import sysmlGrammar from '@grammars/sysml.tmLanguage.json';

export interface TokenStyle {
  color: string;
  fontStyle?: string;
}

export interface Token {
  text: string;
  style: TokenStyle;
}

const THEME = 'semantifyr-tokens';

// the grammars live's editor loads, by the language of a code block
const highlighter = createHighlighterCoreSync({
  langs: [
    { ...oxstsGrammar, name: 'oxsts' },
    { ...gammaGrammar, name: 'gamma' },
    { ...sysmlGrammar, name: 'sysmlv2' },
  ] as LanguageRegistration[],
  themes: [
    {
      name: THEME,
      fg: 'inherit',
      settings: SYNTAX_RULES.map((rule) => {
        const foreground = `var(--${rule.token})`;
        return { scope: [...rule.scopes], settings: rule.italic ? { foreground, fontStyle: 'italic' } : { foreground } };
      }),
    },
  ],
  engine: createJavaScriptRegexEngine(),
});

// the lines of a code block in the language's tokens, or null for a language live has no grammar for
export function highlight(code: string, language: string): Token[][] | null {
  if (!highlighter.getLoadedLanguages().includes(language)) {
    return null;
  }
  return highlighter.codeToTokensBase(code, { lang: language, theme: THEME }).map((line) => {
    return line.map((token) => {
      const { color = 'inherit', 'font-style': fontStyle } = getTokenStyleObject(token);
      return { text: token.content, style: fontStyle === undefined ? { color } : { color, fontStyle } };
    });
  });
}

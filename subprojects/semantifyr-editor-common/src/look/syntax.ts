/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

export interface SyntaxRule {
  scopes: readonly string[]
  token: string
  italic?: true
}

// the TextMate scopes of the grammars, each coloured by a syntax token of tokens.css; a later rule wins over an earlier one on the same scope depth
export const SYNTAX_RULES: readonly SyntaxRule[] = [
  {
    scopes: [
      'comment',
      'punctuation.definition.comment',
    ],
    token: 'syntax-comment',
    italic: true,
  },
  {
    scopes: [
      'string',
      'punctuation.definition.string',
    ],
    token: 'syntax-string',
  },
  {
    scopes: [
      'constant.numeric',
    ],
    token: 'syntax-number',
  },
  {
    scopes: [
      'constant.language',
      'keyword',
      'keyword.control',
      'storage.type',
      'storage.modifier',
    ],
    token: 'syntax-keyword',
  },
  {
    scopes: [
      'keyword.operator',
    ],
    token: 'syntax-operator',
  },
  {
    scopes: [
      'punctuation',
    ],
    token: 'syntax-punctuation',
  },
  {
    scopes: [
      'entity.name.type',
      'entity.name.class',
      'support.type',
    ],
    token: 'syntax-type',
  },
  {
    scopes: [
      'entity.name.function',
      'support.function',
    ],
    token: 'syntax-function',
  },
]

/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type { Position } from '@semantifyr/editor-common'

// the block that holds a case's property, searched from the case's definition on
const OPENINGS: Readonly<Record<string, RegExp>> = {
  sysml: /\brequire\s+constraint\s*\{/,
  gamma: /\bproperty\s*\{/,
  oxsts: /\bprop\s*\{/,
}

function offsetOf(text: string, position: Position): number {
  let offset = 0
  for (let line = 0; line < position.line; line++) {
    const end = text.indexOf('\n', offset)
    if (end < 0) {
      return text.length
    }
    offset = end + 1
  }
  return Math.min(text.length, offset + position.character)
}

// the body of the first block the opening starts between the two positions, in one line
export function propertyText(languageId: string, text: string, from: Position, until: Position | null): string | null {
  const region = text.slice(offsetOf(text, from), until ? offsetOf(text, until) : text.length)
  const match = OPENINGS[languageId]?.exec(region)
  if (!match) {
    return null
  }
  const open = region.indexOf('{', match.index + match[0].length - 1)
  let depth = 0
  for (let index = open; index >= 0 && index < region.length; index++) {
    if (region[index] === '{') {
      depth++
    } else if (region[index] === '}') {
      depth--
      if (depth === 0) {
        return region.slice(open + 1, index).replace(/^\s*return\s/, '').replace(/\s+/g, ' ').trim()
      }
    }
  }
  return null
}

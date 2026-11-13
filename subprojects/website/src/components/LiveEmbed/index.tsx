/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type React from 'react';
import type { LiveEditorLines, LiveEditorView } from '@semantifyr/editor-common';
import type { ExampleLanguage } from '@semantifyr/editor-common/examples';
import { EmbeddedExample } from './EmbeddedExample';

// a served example as a page imports it, by its address @served-examples/<language>/<id>
export interface ServedText {
  language: ExampleLanguage;
  id: string;
  text: string;
}

interface Props {
  example: ServedText;
  library?: 'gamma' | 'sysml';
  view?: LiveEditorView;
  show?: string[];
}

const VERIFICATION_CASE = /@VerificationCase|\bverification\s+def\b/;

const DECLARATION_NAME = [
  /^(package|import)\s/,
  /^(?:private\s+|public\s+)(import)\s/,
  /^(?:abstract\s+)?(?:class|enum|annotation)\s+(\w+)/,
  /^extern\s+datatype\s+(\w+)/,
  /^global\s+\w+\s+(\w+)/,
  /^(?:abstract\s+)?\w+\s+def\s+(?:<\w+>\s+)?(\w+)/,
];

interface Selection {
  text: string;
  lines: LiveEditorLines;
}

// relies on the models separating top-level declarations with blank lines
function select(code: string, names: string[]): Selection {
  const blocks: { names: string[]; lines: string[]; first: number }[] = [];
  let current: { names: string[]; lines: string[]; first: number } | null = null;
  let depth = 0;
  code.split('\n').forEach((line, index) => {
    if (depth === 0 && line.trim() === '') {
      current = null;
      return;
    }
    if (current === null) {
      current = { names: [], lines: [], first: index + 1 };
      blocks.push(current);
    }
    current.lines.push(line);
    const name = DECLARATION_NAME.map((pattern) => pattern.exec(line)?.[1]).find((match) => match !== undefined);
    if (depth === 0 && name !== undefined) {
      current.names.push(name);
    }
    const uncommented = line.replace(/\/\/.*$/, '');
    depth += (uncommented.match(/\{/g)?.length ?? 0) - (uncommented.match(/\}/g)?.length ?? 0);
  });
  const missing = names.filter((name) => !blocks.some((block) => block.names.includes(name)));
  if (missing.length > 0) {
    throw new Error(`The model declares no ${missing.join(', ')}`);
  }
  const selected = blocks.filter((block) => block.names.some((name) => names.includes(name)));
  const first = selected[0];
  const last = selected[selected.length - 1];
  if (first === undefined || last === undefined) {
    throw new Error('No declaration is selected');
  }
  return {
    text: selected.map((block) => block.lines.join('\n')).join('\n\n'),
    lines: { first: first.first, last: last.first + last.lines.length - 1 },
  };
}

export function selectDeclarations(code: string, names: string[]): string {
  return select(code, names).text;
}

// a served example shown whole or by its declarations, which opens in live's editor by its address at the declarations shown
export default function LiveEmbed({ example, library, view, show }: Props): React.JSX.Element {
  const model = example.text;
  const language = example.language;
  const selection = show ? select(model, show) : null;
  return (
    <EmbeddedExample
      model={model}
      example={example.id}
      shown={selection?.text ?? model}
      lines={selection?.lines}
      language={language}
      library={library}
      view={view ?? (VERIFICATION_CASE.test(model) ? 'embed' : 'edit-only')}
      open={selection === null && language !== 'sysml'}
    />
  );
}

/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type React from 'react';
import type { LiveEditorLines } from '@semantifyr/editor-common';
import type { CaseManifest, ExampleLanguage } from '@semantifyr/editor-common/examples';
import { EmbeddedExample } from './EmbeddedExample';

export interface PageModel {
  language: ExampleLanguage;
  // from the repository's root
  file: string;
  // a served example's, by which live opens it; any other model opens with its text in the address
  id?: string | undefined;
  text: string;
  compressedModel: string;
  verdicts: Readonly<Record<string, ExpectedVerdict>>;
}

export type ExpectedVerdict = CaseManifest['models'][number]['cases'][number]['verdict'];

interface Props {
  example: PageModel;
  show?: string[];
}

const VERIFICATION_CASE = /@VerificationCase|\bverification\s+(?:def|case)\b/;

const DECLARATION_NAME = [
  /^(package|import)\s/,
  /^(?:private\s+|public\s+)(import)\s/,
  /^(?:abstract\s+)?(?:class|enum|annotation)\s+(\w+)/,
  /^extern\s+datatype\s+(\w+)/,
  /^global\s+\w+\s+(\w+)/,
  /^(?:abstract\s+)?\w+\s+def\s+(?:<\w+>\s+)?(\w+)/,
  /^(?:interface|statechart|sync|cascade|async|adapter)\s+(\w+)/,
  /^verification\s+case\s+(\w+)/,
];

interface Selection {
  text: string;
  lines: LiveEditorLines;
}

interface Block {
  names: string[];
  lines: string[];
  first: number;
}

// relies on the models separating top-level declarations with blank lines
function select(code: string, names: string[]): Selection {
  const blocks: Block[] = [];
  let current: Block | null = null;
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
    const name = DECLARATION_NAME.map((pattern) => {
      return pattern.exec(line)?.[1];
    }).find((match) => {
      return match !== undefined;
    });
    if (depth === 0 && name !== undefined) {
      current.names.push(name);
    }
    const uncommented = line.replace(/\/\/.*$/, '');
    depth += (uncommented.match(/\{/g)?.length ?? 0) - (uncommented.match(/\}/g)?.length ?? 0);
  });
  const missing = names.filter((name) => {
    return !blocks.some((block) => {
      return block.names.includes(name);
    });
  });
  if (missing.length > 0) {
    throw new Error(`The model declares no ${missing.join(', ')}`);
  }
  const selected = blocks.filter((block) => {
    return block.names.some((name) => {
      return names.includes(name);
    });
  });
  const first = selected[0];
  const last = selected[selected.length - 1];
  if (first === undefined || last === undefined) {
    throw new Error('No declaration is selected');
  }
  return {
    text: selected.map((block) => {
      return block.lines.join('\n');
    }).join('\n\n'),
    lines: { first: first.first, last: last.first + last.lines.length - 1 },
  };
}

export function selectDeclarations(code: string, names: string[]): string {
  return select(code, names).text;
}

export default function LiveEmbed({ example, show }: Props): React.JSX.Element {
  const model = example.text;
  const selection = show ? select(model, show) : null;
  return (
    <EmbeddedExample
      model={model}
      example={example.id}
      shown={selection?.text ?? model}
      lines={selection?.lines}
      language={example.language}
      view={VERIFICATION_CASE.test(model) ? 'embed' : 'edit-only'}
      open={selection === null && example.language !== 'sysml'}
    />
  );
}

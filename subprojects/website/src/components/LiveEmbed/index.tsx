/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type React from 'react';
import { useState } from 'react';
import CodeBlock from '@theme/CodeBlock';
import { liveEditorUrl, useLiveBackendUrl } from '@site/src/lib/live';
import styles from './styles.module.css';

interface Props {
  code: string;
  flavor?: string;
  show?: string[];
}

function stripLicenseHeader(code: string): string {
  const header = /^\/\*[\s\S]*?\*\/\s*/.exec(code);
  return header?.[0].includes('SPDX-') ? code.slice(header[0].length) : code;
}

const DECLARATION_NAME = [
  /^(package|import)\s/,
  /^(?:private\s+|public\s+)(import)\s/,
  /^(?:abstract\s+)?(?:class|enum|annotation)\s+(\w+)/,
  /^global\s+\w+\s+(\w+)/,
  /^(?:abstract\s+)?\w+\s+def\s+(?:<\w+>\s+)?(\w+)/,
];

// relies on the models separating top-level declarations with blank lines
export function selectDeclarations(code: string, names: string[]): string {
  const blocks: { names: string[]; lines: string[] }[] = [];
  let current: { names: string[]; lines: string[] } | null = null;
  let depth = 0;
  for (const line of stripLicenseHeader(code).split('\n')) {
    if (depth === 0 && line.trim() === '') {
      current = null;
      continue;
    }
    if (current === null) {
      current = { names: [], lines: [] };
      blocks.push(current);
    }
    current.lines.push(line);
    const name = DECLARATION_NAME.map((pattern) => pattern.exec(line)?.[1]).find((match) => match !== undefined);
    if (depth === 0 && name !== undefined) {
      current.names.push(name);
    }
    const uncommented = line.replace(/\/\/.*$/, '');
    depth += (uncommented.match(/\{/g)?.length ?? 0) - (uncommented.match(/\}/g)?.length ?? 0);
  }
  const missing = names.filter((name) => !blocks.some((block) => block.names.includes(name)));
  if (missing.length > 0) {
    throw new Error(`The model declares no ${missing.join(', ')}`);
  }
  return blocks
    .filter((block) => block.names.some((name) => names.includes(name)))
    .map((block) => block.lines.join('\n'))
    .join('\n\n');
}

export default function LiveEmbed({ code, flavor = 'oxsts', show }: Props): React.JSX.Element {
  const liveBackendUrl = useLiveBackendUrl();
  const source = stripLicenseHeader(code);
  const [editorUrl, setEditorUrl] = useState<string | null>(null);

  const openEditor = async (): Promise<void> => {
    setEditorUrl(await liveEditorUrl(liveBackendUrl, flavor, source, true));
  };

  if (editorUrl !== null) {
    return (
      <div className={styles.embed}>
        <iframe className={styles.frame} src={editorUrl} title="Semantifyr live editor" allow="clipboard-write" />
        <div className={styles.toolbar}>
          <button
            type="button"
            className={styles.button}
            onClick={() => {
              setEditorUrl(null);
            }}
          >
            Close the editor
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className={styles.embed}>
      <CodeBlock language={flavor} metastring={show ? '' : 'verify open'}>
        {show ? selectDeclarations(source, show) : source}
      </CodeBlock>
      <div className={styles.toolbar}>
        <button
          type="button"
          className={`${styles.button} ${styles.buttonPrimary}`}
          onClick={() => {
            void openEditor();
          }}
        >
          {show ? 'Open this step in the editor' : 'Edit and verify here'}
        </button>
      </div>
    </div>
  );
}

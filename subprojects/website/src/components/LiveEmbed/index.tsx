/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type React from 'react';
import { useEffect, useRef, useState } from 'react';
import CodeBlock from '@theme/CodeBlock';
import { useColorMode } from '@docusaurus/theme-common';
import { liveEditorUrl, normalizeBaseUrl, type LiveEditorView } from '@semantifyr/editor-common';
import { useLiveBackendUrl } from '@site/src/lib/live';
import styles from './styles.module.css';

interface Props {
  code: string;
  language?: 'oxsts' | 'gamma' | 'sysml';
  library?: 'gamma' | 'sysml';
  view?: LiveEditorView;
  show?: string[];
}

const INITIAL_FRAME_HEIGHT = 360;
const VERIFICATION_CASE = /@VerificationCase|\bverification\s+def\b/;

function EmbedFrame({ url }: { url: string }): React.JSX.Element {
  const frame = useRef<HTMLIFrameElement>(null);
  const [height, setHeight] = useState(INITIAL_FRAME_HEIGHT);

  useEffect(() => {
    const origin = new URL(url).origin;
    const receive = (event: MessageEvent): void => {
      const message = event.data as { type?: string; height?: unknown } | null;
      if (event.source !== frame.current?.contentWindow || event.origin !== origin) {
        return;
      }
      if (message?.type === 'semantifyr-live:height' && typeof message.height === 'number') {
        setHeight(Math.ceil(message.height));
      }
    };
    window.addEventListener('message', receive);
    return () => {
      window.removeEventListener('message', receive);
    };
  }, [url]);

  return <iframe ref={frame} className={styles.frame} style={{ height }} src={url} title="Semantifyr live editor" allow="clipboard-write" />;
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

export default function LiveEmbed({ code, language = 'oxsts', library, view, show }: Props): React.JSX.Element {
  const liveBackendUrl = useLiveBackendUrl();
  const source = stripLicenseHeader(code);
  const embedView = view ?? (VERIFICATION_CASE.test(source) ? 'embed' : 'edit-only');
  const { colorMode } = useColorMode();
  const [opened, setOpened] = useState(false);
  const [editorUrl, setEditorUrl] = useState<string | null>(null);

  useEffect(() => {
    if (!opened) {
      setEditorUrl(null);
      return;
    }
    let active = true;
    void liveEditorUrl(`${normalizeBaseUrl(liveBackendUrl).http}/`, { language, library, view: embedView, theme: colorMode, model: source }).then((url) => {
      if (active) {
        setEditorUrl(url);
      }
    });
    return () => {
      active = false;
    };
  }, [opened, liveBackendUrl, language, library, embedView, colorMode, source]);

  if (opened && editorUrl !== null) {
    return (
      <div className={styles.embed}>
        <EmbedFrame url={editorUrl} />
        <div className={styles.toolbar}>
          <button
            type="button"
            className={styles.button}
            onClick={() => {
              setOpened(false);
            }}
          >
            Close the editor
          </button>
        </div>
      </div>
    );
  }

  const verifying = embedView === 'embed';
  return (
    <div className={`${styles.embed} ${styles.block}`}>
      <CodeBlock language={language === 'sysml' ? 'sysmlv2' : language} metastring={show || language === 'sysml' ? '' : 'open'}>
        {show ? selectDeclarations(source, show) : source}
      </CodeBlock>
      <button
        type="button"
        className={styles.run}
        title={show ? `Opens the model of this step in an editor here${verifying ? ', where its cases verify' : ''}` : verifying ? 'Opens the model in an editor here, where its cases verify' : 'Opens the model in an editor here'}
        onClick={() => {
          setOpened(true);
        }}
      >
        <svg className={styles.runIcon} viewBox="0 0 16 16" aria-hidden="true">
          <path d="M4.5 2.8v10.4L13 8z" fill="currentColor" />
        </svg>
        {verifying ? 'Verify' : 'Edit'}
      </button>
    </div>
  );
}

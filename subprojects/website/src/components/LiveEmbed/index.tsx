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
}

function stripLicenseHeader(code: string): string {
  const header = /^\/\*[\s\S]*?\*\/\s*/.exec(code);
  return header?.[0].includes('SPDX-') ? code.slice(header[0].length) : code;
}

export default function LiveEmbed({ code, flavor = 'oxsts' }: Props): React.JSX.Element {
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
      <CodeBlock language={flavor} metastring="verify open">
        {source}
      </CodeBlock>
      <div className={styles.toolbar}>
        <button
          type="button"
          className={`${styles.button} ${styles.buttonPrimary}`}
          onClick={() => {
            void openEditor();
          }}
        >
          Edit and verify here
        </button>
      </div>
    </div>
  );
}

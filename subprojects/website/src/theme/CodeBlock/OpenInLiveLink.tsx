/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type React from 'react';
import { useEffect, useState } from 'react';
import { liveEditorUrl, normalizeBaseUrl } from '@semantifyr/editor-common';
import { terms } from '@semantifyr/editor-common/terms';
import { useLiveBackendUrl } from '@site/src/lib/live';
import styles from './Buttons/styles.module.css';

interface Props {
  language: string;
  code: string;
  address?: string | undefined;
}

export default function OpenInLiveLink({ language, code, address }: Props): React.JSX.Element | null {
  const liveBackendUrl = useLiveBackendUrl();
  const [href, setHref] = useState<string | null>(address ?? null);

  useEffect(() => {
    if (address !== undefined) {
      setHref(address);
      return;
    }
    let active = true;
    void liveEditorUrl(`${normalizeBaseUrl(liveBackendUrl).http}/`, { language, model: code }).then((url) => {
      if (active) {
        setHref(url);
      }
    });
    return () => {
      active = false;
    };
  }, [liveBackendUrl, language, code, address]);

  if (href === null) {
    return null;
  }

  return (
    <a href={href} className={styles.control} target="_blank" rel="noopener noreferrer">
      <svg className={styles.controlIcon} viewBox="0 0 24 24" aria-hidden="true">
        <path d="M7 17L17 7M9 7h8v8" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
      </svg>
      {`Open in ${terms.live.short ?? terms.live.term}`}
    </a>
  );
}

/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type React from 'react';
import { useEffect, useState } from 'react';
import { liveEditorUrl, useLiveBackendUrl } from '@site/src/lib/live';
import styles from './styles.module.css';

interface Props {
  language: string;
  code: string;
}

export default function OpenInLiveLink({ language, code }: Props): React.JSX.Element | null {
  const liveBackendUrl = useLiveBackendUrl();
  const [href, setHref] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    void liveEditorUrl(liveBackendUrl, language, code).then((url) => {
      if (active) {
        setHref(url);
      }
    });
    return () => {
      active = false;
    };
  }, [liveBackendUrl, language, code]);

  if (href === null) {
    return null;
  }

  return (
    <div className={styles.openLinkContainer}>
      <a href={href} className={styles.openLink} target="_blank" rel="noopener noreferrer">
        Open in live editor ↗
      </a>
    </div>
  );
}

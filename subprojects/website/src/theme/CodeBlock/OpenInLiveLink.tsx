/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import React from 'react';
import Link from '@docusaurus/Link';
import useDocusaurusContext from '@docusaurus/useDocusaurusContext';
import { encodeBase64Url } from './lib';
import styles from './styles.module.css';

interface Props {
  language: string;
  code: string;
  /**
   * Optional id of a snippet that the live frontend has registered in its examples
   * registry. When supplied, the link points at `?example=<id>` (a stable, pretty URL);
   * when omitted, the snippet is base64-encoded and shipped inline as `?code=<...>` so
   * the link is fully self-contained.
   */
  exampleId?: string;
}

/**
 * Tiny "Open in live editor ↗" link rendered below a code block. Points at the
 * standalone Semantifyr Live frontend (live.semantifyr.org by default) — see
 * docusaurus.config.ts:customFields.liveBackendUrl. Pure presentational component:
 * no Monaco, no @codingame, no WebSocket; the chunk impact on a docs page is essentially
 * zero.
 */
export default function OpenInLiveLink({ language, code, exampleId }: Props): React.JSX.Element | null {
  const { siteConfig } = useDocusaurusContext();
  const liveBackendUrl = (siteConfig.customFields?.liveBackendUrl as string | undefined) ?? '';
  if (!liveBackendUrl) return null;

  const params = new URLSearchParams();
  params.set('flavor', language);
  if (exampleId) {
    params.set('example', exampleId);
  } else {
    params.set('code', encodeBase64Url(code));
  }
  const href = `${liveBackendUrl.replace(/\/$/, '')}/?${params.toString()}`;

  return (
    <div className={styles.openLinkContainer}>
      <Link to={href} className={styles.openLink} target="_blank" rel="noopener noreferrer">
        Open in live editor ↗
      </Link>
    </div>
  );
}

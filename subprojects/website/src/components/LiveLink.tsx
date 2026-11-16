/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type React from 'react';
import { liveExampleUrl, normalizeBaseUrl } from '@semantifyr/editor-common';
import type { ServedText } from '@site/src/components/LiveEmbed';
import { useLiveBackendUrl } from '@site/src/lib/live';

export default function LiveLink({ example, children }: { example: ServedText; children: React.ReactNode }): React.JSX.Element {
  const liveBackendUrl = useLiveBackendUrl();
  return <a href={liveExampleUrl(`${normalizeBaseUrl(liveBackendUrl).http}/`, example.language, example.id)}>{children}</a>;
}

/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type React from 'react';
import { compressedLiveEditorUrl, liveExampleUrl, normalizeBaseUrl } from '@semantifyr/editor-common';
import type { PageModel } from '@site/src/components/LiveEmbed';
import { useLiveBackendUrl } from '@site/src/lib/live';

export default function LiveLink({ example, children }: { example: PageModel; children: React.ReactNode }): React.JSX.Element {
  const base = `${normalizeBaseUrl(useLiveBackendUrl()).http}/`;
  const address = example.id === undefined
    ? compressedLiveEditorUrl(base, { language: example.language, compressedModel: example.compressedModel })
    : liveExampleUrl(base, example.language, example.id);
  return <a href={address}>{children}</a>;
}

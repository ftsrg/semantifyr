/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import useDocusaurusContext from '@docusaurus/useDocusaurusContext';

export function useLiveBackendUrl(): string {
  const { siteConfig } = useDocusaurusContext();
  return siteConfig.customFields?.liveBackendUrl as string;
}

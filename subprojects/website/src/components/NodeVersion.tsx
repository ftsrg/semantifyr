/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type React from 'react';
import useDocusaurusContext from '@docusaurus/useDocusaurusContext';

// the build's Node.js, nodeVersion in gradle.properties; the major version, or the whole one with full
export default function NodeVersion({ full = false }: { full?: boolean }): React.JSX.Element {
  const { siteConfig } = useDocusaurusContext();
  const version = siteConfig.customFields?.nodeVersion as string;
  return <>{full ? version : version.split('.')[0]}</>;
}

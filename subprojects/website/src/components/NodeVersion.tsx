/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type React from 'react';
import useDocusaurusContext from '@docusaurus/useDocusaurusContext';

export default function NodeVersion(): React.JSX.Element {
  const { siteConfig } = useDocusaurusContext();
  return <>{(siteConfig.customFields?.nodeVersion as string).split('.')[0]}</>;
}

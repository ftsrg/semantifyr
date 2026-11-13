/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type React from 'react';
import useDocusaurusContext from '@docusaurus/useDocusaurusContext';

// the public instance's limit, VERIFY_TIMEOUT_SECONDS of deploy/live/env.example
export default function LiveLimit(): React.JSX.Element {
  const { siteConfig } = useDocusaurusContext();
  return <>{siteConfig.customFields?.liveLimitSeconds as string} seconds</>;
}

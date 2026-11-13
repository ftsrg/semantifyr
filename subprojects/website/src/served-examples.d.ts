/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

// written by the served-examples plugin of docusaurus.config.ts
declare module '@served-examples/*' {
  import type { ServedText } from '@site/src/components/LiveEmbed';

  const example: ServedText;
  export default example;
}

/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

// written by the page-models plugin of docusaurus.config.ts
declare module '@served-examples/*' {
  import type { PageModel } from '@site/src/components/LiveEmbed';

  const example: PageModel;
  export default example;
}

declare module '@lesson-stages/*' {
  import type { PageModel } from '@site/src/components/LiveEmbed';

  const stage: PageModel;
  export default stage;
}

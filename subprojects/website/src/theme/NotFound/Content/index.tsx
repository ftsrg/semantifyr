/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type React from 'react';
import Head from '@docusaurus/Head';

export default function NotFoundContent(): React.JSX.Element {
  return (
    <main className="container margin-vert--xl">
      <Head>
        <meta name="description" content="This address opens no page of the Semantifyr site." />
      </Head>
      <h1>404: page not found</h1>
      <p>This address opens no page of the site.</p>
    </main>
  );
}

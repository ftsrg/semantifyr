/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type React from 'react';
import Link from '@docusaurus/Link';
import Head from '@docusaurus/Head';
import { useLiveBackendUrl } from '@site/src/lib/live';

export default function NotFoundContent(): React.JSX.Element {
  const liveUrl = useLiveBackendUrl();
  return (
    <main className="container margin-vert--xl">
      <Head>
        <meta name="description" content="This address opens no page of the Semantifyr site." />
      </Head>
      <h1>No page has this address</h1>
      <p>This address opens no page of the site. Learn leads to the documentation, and Live opens the examples.</p>
      <ul>
        <li><Link to="/learn/">Learn</Link></li>
        <li><a href={liveUrl}>Semantifyr Live</a></li>
      </ul>
    </main>
  );
}

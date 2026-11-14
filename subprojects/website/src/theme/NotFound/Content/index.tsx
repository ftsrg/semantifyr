/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type React from 'react';
import Link from '@docusaurus/Link';
import Head from '@docusaurus/Head';
import { useLiveBackendUrl } from '@site/src/lib/live';

// every old address lands here, since none is redirected before 1.0
export default function NotFoundContent(): React.JSX.Element {
  const liveUrl = useLiveBackendUrl();
  return (
    <main className="container margin-vert--xl">
      <Head>
        <meta name="description" content="No page of the Semantifyr site has this address; its pages moved when the site was rearranged." />
      </Head>
      <h1>No page has this address</h1>
      <p>The pages of this site moved when it was rearranged, and an old address leads nowhere. Learn lists every page, and Live opens the examples.</p>
      <ul>
        <li><Link to="/learn/">Learn</Link></li>
        <li><a href={liveUrl}>Semantifyr Live</a></li>
      </ul>
    </main>
  );
}

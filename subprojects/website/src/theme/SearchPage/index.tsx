/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type React from 'react';
import Head from '@docusaurus/Head';
import SearchPage from '@theme-original/SearchPage';

export default function SearchPageWithDescription(): React.JSX.Element {
  return (
    <>
      <Head>
        <meta name="description" content="Search the pages of the Semantifyr site, in your browser." />
      </Head>
      <SearchPage />
    </>
  );
}

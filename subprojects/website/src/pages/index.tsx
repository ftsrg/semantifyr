/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type React from 'react';
import Link from '@docusaurus/Link';
import useBaseUrl from '@docusaurus/useBaseUrl';
import { LanguageMark } from '@semantifyr/editor-common/marks';
import Architecture from '@site/src/components/Architecture';
import { EmbeddedExample } from '@site/src/components/LiveEmbed/EmbeddedExample';
import { addressOf, learnParts } from '@site/src/map';
import spacecraft from '@served-examples/sysml/spacecraft';
import Layout from '@theme/Layout';
import ThemedImage from '@theme/ThemedImage';
import clsx from 'clsx';

import styles from './index.module.css';

const SHOWN_PART = 'part def Mission {';

function partAddress(label: string): string {
  const part = learnParts.flatMap((candidate) => {
    return [candidate, ...(candidate.parts ?? [])];
  }).find((candidate) => {
    return candidate.label === label;
  });
  if (part === undefined) {
    throw new Error(`Learn has no part ${label}`);
  }
  return addressOf(part);
}

const DOORS = [
  {
    language: 'sysml',
    title: 'SysML v2',
    text: 'Build and verify SysML v2 models, including state machines, communicating parts, items, and item flows.',
    part: 'SysML v2',
  },
  {
    language: 'gamma',
    title: 'Gamma',
    text: 'Build and verify Gamma models, including statecharts, timeouts, and synchronous, cascade, and asynchronous compositions.',
    part: 'Gamma',
  },
  {
    language: 'oxsts',
    title: 'Semantifyr Language',
    text: 'Define the semantics of your own modeling language as a semantic library.',
    part: 'Language',
  },
] as const;

function Doors(): React.JSX.Element {
  return (
    <div className={styles.doors}>
      {DOORS.map((door) => {
        return (
          <Link key={door.language} to={partAddress(door.part)} className={clsx(styles.door)} data-language={door.language}>
            <span className={styles.doorTitle}>
              <LanguageMark languageId={door.language} />
              {door.title}
            </span>
            <span className={styles.doorText}>{door.text}</span>
          </Link>
        );
      })}
    </div>
  );
}

function Still(): React.JSX.Element {
  const lines = spacecraft.text.split('\n');
  const start = lines.indexOf(SHOWN_PART);
  if (start === -1) {
    throw new Error(`The spacecraft has no line ${SHOWN_PART}`);
  }
  const verification = lines.findIndex((line, index) => {
    return index > start && line.startsWith('verification def ');
  });
  if (verification === -1) {
    throw new Error('The spacecraft has no verification case after its mission');
  }
  const end = lines.indexOf('}', verification);
  const shown = lines.slice(start, end + 1).join('\n');
  return (
    <div className={styles.still}>
      <EmbeddedExample model={spacecraft.text} example={spacecraft.id} shown={shown} lines={{ first: start + 1, last: end + 1 }} language="sysml" view="embed" />
      <p className={styles.stillNote}>{'A SysML v2 model after OpenMBEE\'s Simple Space Mission: a battery-limited satellite sending its data down to a ground station.'}</p>
    </div>
  );
}

function Ways(): React.JSX.Element {
  return (
    <div className={styles.ways}>
      <div className={styles.way}>
        <h2 className={styles.wayTitle}>Frontends</h2>
        <p className={styles.wayText}>The supported high-level languages.</p>
        <Link to={partAddress('Frontends')}>See the frontends</Link>
      </div>
      <div className={styles.way}>
        <h2 className={styles.wayTitle}>Backends</h2>
        <p className={styles.wayText}>Verification backends: Theta, nuXmv, Spin, and Uppaal.</p>
        <Link to={partAddress('Backends')}>See the backends</Link>
      </div>
      <div className={styles.way}>
        <h2 className={styles.wayTitle}>Learn</h2>
        <p className={styles.wayText}>Reference and tutorials for the Semantifyr Language.</p>
        <Link to="/learn/">Go to Learn</Link>
      </div>
    </div>
  );
}

export default function Home(): React.JSX.Element {
  const light = useBaseUrl('/img/logo-full-light.svg');
  const dark = useBaseUrl('/img/logo-full-dark.svg');
  return (
    <Layout title="A framework to define the semantics of engineering modeling languages" description="Semantifyr is a framework to declaratively define the semantics of engineering modeling languages.">
      <main className={styles.page}>
        <div className="container">
          <h1 className={styles.title}>
            <ThemedImage className={styles.logo} alt="Semantifyr" sources={{ light, dark }} />
          </h1>
          <p className={styles.sentences}>
            Semantifyr is a framework to declaratively define the semantics of engineering modeling languages. Currently it has frontends for the <Link to={partAddress('SysML v2')}>SysML v2</Link> and <Link to={partAddress('Gamma')}>Gamma</Link> languages.
          </p>
          <div className={styles.try}>
            <Still />
            <Doors />
          </div>
          <h2 className={styles.section}>Approach</h2>
          <p className={styles.sentences}>
            Instead of defining the semantics as a transpiler, Semantifyr defines the semantics in a <Link to="/learn/language/lessons/semantic-libraries/">semantic library</Link>. A <Link to={partAddress('Frontends')}>frontend</Link> maps a model into a Semantifyr model on that library. Semantifyr then verifies the model using <Link to="/learn/backends/portfolios-and-verdicts/">portfolios</Link> of its <Link to={partAddress('Backends')}>backends</Link>.
          </p>
          <Architecture />
          <Ways />
        </div>
      </main>
    </Layout>
  );
}

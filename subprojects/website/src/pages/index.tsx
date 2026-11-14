/*
 * SPDX-FileCopyrightText: 2025-2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

import type React from 'react';
import Link from '@docusaurus/Link';
import useBaseUrl from '@docusaurus/useBaseUrl';
import { LanguageMark } from '@semantifyr/editor-common/marks';
import { terms } from '@semantifyr/editor-common/terms';
import Architecture from '@site/src/components/Architecture';
import { EmbeddedExample } from '@site/src/components/LiveEmbed/EmbeddedExample';
import { useLiveBackendUrl } from '@site/src/lib/live';
import { addressOf, learnParts } from '@site/src/map';
import crossroads from '@served-examples/sysml/crossroads';
import Layout from '@theme/Layout';
import ThemedImage from '@theme/ThemedImage';
import clsx from 'clsx';

import styles from './index.module.css';

const SENTENCES = 'Semantifyr is a framework to support the declarative definition of engineering model semantics: the meaning of a modelling language is written once, as a semantic library, and every model of the language can then be verified by established model checkers. SysML v2 and Gamma are its frontends: a model in either is verified as it is written, and the trace of a case comes back in the language of the model.';

// the served crossroads' comment and its first declarations
const STILL_LINES = 22;

function partAddress(label: string): string {
  const part = learnParts.find((candidate) => {
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
    title: 'Verify a SysML v2 model',
    text: 'Write verification cases beside your parts and state machines, and read the trace of a case as a SysML v2 model that refines yours.',
    part: 'SysML v2',
  },
  {
    language: 'gamma',
    title: 'Verify a Gamma model',
    text: 'Verify Gamma statecharts and their composition against the cases you state, and read each trace in Gamma\'s own trace language.',
    part: 'Gamma',
  },
  {
    language: 'oxsts',
    title: 'Define semantics in OXSTS',
    text: 'Write the semantics of a language of your own as a library in OXSTS, the language both frontends are built on, and verify its models the same way.',
    part: 'OXSTS Lessons',
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
  const shown = crossroads.text.split('\n').slice(0, STILL_LINES).join('\n');
  return (
    <div className={styles.still}>
      <EmbeddedExample model={crossroads.text} example={crossroads.id} shown={shown} lines={{ first: 1, last: STILL_LINES }} language="sysml" view="embed" />
      <p className={styles.stillNote}>The crossroads, a SysML v2 model of two traffic lights and their controller. Run opens it in Live here and verifies its cases.</p>
    </div>
  );
}

function Ways(): React.JSX.Element {
  const liveUrl = useLiveBackendUrl();
  return (
    <div className={styles.ways}>
      <div className={styles.way}>
        <h2 className={styles.wayTitle}>Learn</h2>
        <p className={styles.wayText}>From a first verification in the browser to the full specification of OXSTS.</p>
        <Link to="/learn/">Go to Learn</Link>
      </div>
      <div className={styles.way}>
        <h2 className={styles.wayTitle}>{terms.live.term}</h2>
        <p className={styles.wayText}>Edit and verify SysML v2, Gamma and OXSTS models in the browser, with nothing to install.</p>
        <a href={liveUrl}>Open Live</a>
      </div>
      <div className={styles.way}>
        <h2 className={styles.wayTitle}>Publications</h2>
        <p className={styles.wayText}>The papers on the approach, the framework and its language.</p>
        <Link to="/publications/">See the publications</Link>
      </div>
    </div>
  );
}

export default function Home(): React.JSX.Element {
  const light = useBaseUrl('/img/logo-full-light.svg');
  const dark = useBaseUrl('/img/logo-full-dark.svg');
  return (
    <Layout title="A framework for the semantics of engineering models" description={SENTENCES}>
      <main className={styles.page}>
        <div className="container">
          <h1 className={styles.title}>
            <ThemedImage className={styles.logo} alt="Semantifyr" sources={{ light, dark }} />
          </h1>
          <p className={styles.sentences}>{SENTENCES}</p>
          <div className={styles.try}>
            <Still />
            <Doors />
          </div>
          <h2 className={styles.section}>How it works</h2>
          <Architecture />
          <Ways />
        </div>
      </main>
    </Layout>
  );
}
